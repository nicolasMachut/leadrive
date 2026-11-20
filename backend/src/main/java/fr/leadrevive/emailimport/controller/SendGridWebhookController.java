package fr.leadrevive.emailimport.controller;

import fr.leadrevive.common.ratelimit.RateLimiter;
import fr.leadrevive.common.util.PiiMasker;
import fr.leadrevive.emailimport.config.EmailImportConfig;
import fr.leadrevive.emailimport.service.InboundEmailProcessingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook SendGrid Inbound Parse (epic 24).
 *
 * <p>SendGrid envoie un POST multipart/form-data avec les champs :
 * {@code from}, {@code to}, {@code subject}, {@code text}, {@code html},
 * {@code envelope}, {@code headers}, {@code sender_ip}.</p>
 *
 * <p>Cf. <a href="https://docs.sendgrid.com/for-developers/parsing-email/setting-up-the-inbound-parse-webhook">
 * SendGrid Inbound Parse</a>.</p>
 *
 * <p><strong>Securite</strong> :
 * <ul>
 *   <li>Rate limiting par IP (anti-DoS sur endpoint public)</li>
 *   <li>Validation de l'adresse destinataire (existence + statut ACTIVE)</li>
 *   <li>Rate limit par tenant (cf. {@link InboundEmailProcessingService})</li>
 *   <li>Toujours 200 OK : eviter le retry SendGrid sur erreur metier</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/webhook/sendgrid")
@RequiredArgsConstructor
@Slf4j
public class SendGridWebhookController {

    private final InboundEmailProcessingService inboundEmailProcessingService;
    private final EmailImportConfig emailImportConfig;
    private final RateLimiter rateLimiter;

    /**
     * Health-check : SendGrid (et scanners / navigateurs) font parfois un GET
     * sur l'URL du webhook pour verifier qu'elle repond. Sans ce mapping,
     * Spring renvoie une stack-trace ERROR "No static resource for request"
     * polluant les logs et Sentry. Retourne 200 OK avec un message explicite.
     */
    @GetMapping(value = "/inbound", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> inboundEmailPing() {
        return ResponseEntity.ok("SendGrid Inbound Parse endpoint. Send a POST with multipart/form-data.");
    }

    /**
     * Receive un email entrant. SendGrid POST en multipart/form-data
     * (compatible aussi avec form-urlencoded pour la simplicite).
     *
     * <p><strong>Politique de reponse</strong> (cf
     * <a href="https://support.sendgrid.com/hc/en-us/articles/46513815674395">doc SendGrid</a>) :
     * <ul>
     *   <li><b>2xx</b> : email considere comme traite, pas de retry</li>
     *   <li><b>5xx</b> : SendGrid retry pendant 72h (toutes les 5-10min la 1ere heure, puis toutes les 3h)</li>
     *   <li><b>4xx</b> : drop immediat, mail perdu pour toujours</li>
     * </ul>
     * On renvoie 2xx pour tous les cas metier (adresse inconnue, spam rejete, parse echec)
     * - le mail est arrive, le rejet est volontaire et stocke pour audit. On renvoie 5xx
     * UNIQUEMENT pour les erreurs transitoires (BDD HS, IA timeout) pour beneficier du
     * retry automatique. On NE renvoie JAMAIS 4xx (ce serait perdre le mail definitivement).</p>
     */
    @PostMapping(value = "/inbound",
            consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    public ResponseEntity<String> inboundEmail(
            @RequestParam(value = "from", required = false) final String from,
            @RequestParam(value = "to", required = false) final String to,
            @RequestParam(value = "subject", required = false) final String subject,
            @RequestParam(value = "text", required = false) final String text,
            @RequestParam(value = "html", required = false) final String html,
            @RequestParam(value = "envelope", required = false) final String envelope,
            final HttpServletRequest request) {

        final String ipAppelant = ipClient(request);
        // Fix securite bug-hunter : masquer aussi le destinataire (l'adresse de notre tenant
        // = donnee technique mais potentiellement sensible). Subject loggue tronque (peut
        // contenir nom/societe du prospect).
        log.info("Webhook SendGrid inbound recu - from: {}, to: {}, subject: {}, ip: {}",
                PiiMasker.email(from), PiiMasker.email(to), PiiMasker.content(subject), ipAppelant);

        // Rate limit par IP : on renvoie 503 (5xx) plutot que 429, pour declencher
        // le retry SendGrid (72h). Si on est sature ici, c'est un probleme transitoire
        // qu'un retry resoudra (au pire on reverra le mail dans qq heures).
        if (!rateLimiter.tryAcquire("sendgrid-webhook:" + ipAppelant, 60)) {
            log.warn("Rate limit webhook SendGrid depasse - ip: {}", ipAppelant);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Rate limit");
        }

        if (to == null || to.isBlank()) {
            log.warn("Webhook SendGrid : champ 'to' manquant");
            // 200 OK : c'est un payload SendGrid non standard (impossible a traiter),
            // pas une erreur transitoire. Retry inutile.
            return ResponseEntity.ok("Champ to manquant");
        }

        // L'adresse destinataire peut etre formatee "Nom <addr@domaine>".
        // SendGrid envoie generalement juste l'adresse, mais on normalise par precaution.
        final String emailTo = extraireAdresse(to);

        // Selection du corps : prefere le texte brut au HTML. Si seul HTML est dispo,
        // on le passe tel quel a l'IA (Claude se debrouille tres bien avec du HTML brut).
        final String body = (text != null && !text.isBlank()) ? text : htmlVersTexte(html);
        if (body == null || body.isBlank()) {
            // Fix securite bug-hunter : masquer le destinataire dans les logs (PII).
            log.warn("Webhook SendGrid : email vide - to: {}", PiiMasker.email(emailTo));
            return ResponseEntity.ok("Corps vide");
        }

        // Tronque le body (defense en profondeur, le service tronque aussi)
        final String bodyTronque = body.length() > emailImportConfig.getMaxBodyChars()
                ? body.substring(0, emailImportConfig.getMaxBodyChars())
                : body;

        try {
            final InboundEmailProcessingService.ResultatTraitement resultat =
                    inboundEmailProcessingService.traiter(
                            emailTo,
                            from != null ? extraireAdresse(from) : null,
                            subject,
                            bodyTronque);
            log.info("Webhook SendGrid traite - statut: {}, leadId: {}, message: {}",
                    resultat.statut(), resultat.leadId(), resultat.message());
            return ResponseEntity.ok("OK");
        } catch (final Exception e) {
            // Erreur transitoire imprevue (BDD HS, IA timeout, OOM, etc.). Renvoyer 503
            // pour declencher le retry automatique SendGrid (72h). Sans ca, le mail
            // serait perdu definitivement alors qu'un retry quelques minutes plus tard
            // marcherait probablement.
            // Fix securite bug-hunter : masquer le destinataire (PII) dans les logs d'erreur.
            log.error("Erreur transitoire webhook SendGrid - to: {}, cause: {} (SendGrid retry)",
                    PiiMasker.email(emailTo), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Transient error, please retry");
        }
    }

    /**
     * Extrait l'adresse email d'une chaine "Nom &lt;addr@dom&gt;" ou "addr@dom".
     */
    private String extraireAdresse(final String raw) {
        if (raw == null) return null;
        final String trim = raw.trim();
        final int debut = trim.indexOf('<');
        final int fin = trim.indexOf('>');
        if (debut >= 0 && fin > debut) {
            return trim.substring(debut + 1, fin).trim().toLowerCase();
        }
        return trim.toLowerCase();
    }

    /**
     * Conversion HTML -&gt; texte basique. Pas besoin d'une vraie lib (Jsoup) :
     * Claude Haiku gere parfaitement du HTML brut, donc on se contente de retirer
     * les balises les plus genantes pour eviter de saturer le contexte tokens.
     */
    private String htmlVersTexte(final String html) {
        if (html == null) return null;
        return html
                .replaceAll("<script[\\s\\S]*?</script>", "")
                .replaceAll("<style[\\s\\S]*?</style>", "")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String ipClient(final HttpServletRequest request) {
        final String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            final int virgule = xff.indexOf(',');
            return (virgule > 0 ? xff.substring(0, virgule) : xff).trim();
        }
        final String remote = request.getRemoteAddr();
        return remote != null ? remote : "unknown";
    }
}
