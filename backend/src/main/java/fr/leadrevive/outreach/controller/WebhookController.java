package fr.leadrevive.outreach.controller;

import com.twilio.security.RequestValidator;
import fr.leadrevive.common.ratelimit.RateLimiter;
import fr.leadrevive.common.util.PiiMasker;
import fr.leadrevive.outreach.config.TwilioConfig;
import fr.leadrevive.outreach.entity.SmsMessage;
import fr.leadrevive.outreach.entity.StatutSmsEnum;
import fr.leadrevive.outreach.entity.TenantWhatsappConfig;
import fr.leadrevive.outreach.repository.SmsConversationRepository;
import fr.leadrevive.outreach.repository.SmsMessageRepository;
import fr.leadrevive.outreach.repository.TenantWhatsappConfigRepository;
import fr.leadrevive.outreach.service.InboxNotificationService;
import fr.leadrevive.outreach.service.WebhookIdempotenceService;
import fr.leadrevive.outreach.service.WhatsAppConfigService;
import fr.leadrevive.outreach.service.WhatsAppConversationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;

@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final WhatsAppConversationService whatsAppConversationService;
    private final TwilioConfig twilioConfig;
    private final SmsMessageRepository smsMessageRepository;
    private final RateLimiter rateLimiter;
    /**
     * Idempotence DB-based : remplace l'ancien cache Caffeine local. En multi-instance
     * Railway, le cache local ne detecte pas les retries Twilio qui atterrissent sur
     * une autre replica. La table {@code webhook_idempotence} sert de lock partage.
     */
    private final WebhookIdempotenceService webhookIdempotenceService;
    private final TenantWhatsappConfigRepository configRepository;
    private final WhatsAppConfigService whatsAppConfigService;
    private final SmsConversationRepository smsConversationRepository;
    private final InboxNotificationService inboxNotificationService;

    @PostMapping(value = "/twilio/whatsapp", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> twilioWhatsAppWebhook(
            @RequestParam("From") final String from,
            @RequestParam("Body") final String body,
            @RequestParam(value = "To", required = false) final String to,
            @RequestParam(value = "MessageSid", required = false) final String messageSid,
            @RequestParam(value = "OriginalRepliedMessageSid", required = false) final String originalRepliedMessageSid,
            @RequestHeader(value = "X-Twilio-Signature", required = false) final String signature,
            final HttpServletRequest request) {
        log.info("Webhook Twilio WhatsApp recu - from: {}, to: {}, body: {}, sid: {}, repliedTo: {}",
                PiiMasker.phone(from), PiiMasker.phone(to), PiiMasker.content(body), messageSid, originalRepliedMessageSid);

        // Rate limiting par IP : 30 req/min. Protege contre le spam d'un endpoint public.
        final String ipAppelant = ipClient(request);
        if (!rateLimiter.tryAcquire("twilio-webhook:" + ipAppelant, 30)) {
            log.warn("Rate limit webhook Twilio depasse - ip: {}", ipAppelant);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Rate limit exceeded");
        }

        // Verification de la signature Twilio (HMAC-SHA1)
        if (!signatureValide(request, signature, to)) {
            log.warn("Signature Twilio invalide - rejet webhook - from: {}", PiiMasker.phone(from));
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid signature");
        }

        // Idempotence DB : Twilio peut renvoyer le meme MessageSid (retry sur 5xx/timeout).
        // L'INSERT ON CONFLICT DO NOTHING garantit qu'un seul traitement par SID, meme
        // si plusieurs replicas Railway recoivent le webhook en parallele.
        if (!webhookIdempotenceService.enregistrerSiNouveau(messageSid, "twilio-whatsapp")) {
            return ResponseEntity.ok()
                    .header("Content-Type", "text/xml")
                    .body("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response></Response>");
        }

        final String numero = from.replace("whatsapp:", "");
        // Epic 23 - multi-numero : le parametre To est le numero WhatsApp Business
        // qui a recu le message (= numero d'un tenant). Permet le routage par tenant.
        // Peut etre null pour les anciens webhooks (rare en pratique).
        final String numeroTo = to != null ? to.replace("whatsapp:", "") : null;

        if (numero.isBlank() || body == null || body.isBlank()) {
            log.warn("Webhook Twilio invalide - from ou body manquant");
            return ResponseEntity.badRequest().body("Invalid payload");
        }

        // Le marqueur d'idempotence a ete pose EN AMONT (protection anti-concurrence multi-replica).
        // Si le traitement metier echoue, on RETIRE ce marqueur pour ne pas bloquer le retry Twilio :
        // sinon le prospect resterait sans reponse (incident prod 2026-07-06). On renvoie alors un 5xx
        // pour que Twilio rejoue le webhook.
        try {
            whatsAppConversationService.traiterReponseProspect(numero, body, originalRepliedMessageSid, numeroTo);
        } catch (final Exception e) {
            log.error("Echec traitement webhook WhatsApp - retrait idempotence pour autoriser le retry - sid: {}, cause: {}",
                    messageSid, PiiMasker.content(e.getMessage()), e);
            webhookIdempotenceService.supprimerMarqueur(messageSid);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "text/xml")
                    .body("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response></Response>");
        }

        // TwiML vide : indique a Twilio qu'on a bien traite le message mais qu'on ne repond PAS via TwiML.
        // La reponse IA est envoyee separement via l'API REST Twilio (pas via TwiML reply).
        // Content-Type text/xml obligatoire sinon Twilio log un warning 12200.
        return ResponseEntity.ok()
                .header("Content-Type", "text/xml")
                .body("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response></Response>");
    }

    /**
     * Valide la signature X-Twilio-Signature pour garantir que le webhook provient bien de Twilio.
     * En mode dry-run (dev/local), la verification est court-circuitee pour simplifier les tests.
     *
     * <p><strong>Epic 25 — multi-WABA</strong> : Twilio signe les webhooks avec le auth token
     * du sub-account qui possede le numero, pas avec le master account token. On cherche donc
     * le {@link TenantWhatsappConfig} correspondant au numero {@code To} pour recuperer le
     * {@code twilioSubAuthToken} (dechiffre automatiquement par JPA). Fallback sur le master
     * token si aucune config tenant trouvee (edge case, anciens webhooks legacy).</p>
     *
     * @param request   la requete HTTP originale
     * @param signature la valeur du header X-Twilio-Signature
     * @param to        le parametre Twilio {@code To} (numero WhatsApp du tenant, peut etre null)
     */
    private boolean signatureValide(final HttpServletRequest request, final String signature, final String to) {
        if (twilioConfig.isDryRun()) {
            log.debug("Twilio dry-run actif - signature ignoree");
            return true;
        }
        if (signature == null || signature.isBlank()) {
            return false;
        }

        // Determiner le auth token a utiliser pour la validation.
        // Priorite : sub-account du tenant (via le numero To) -> master account (fallback).
        String authToken = null;
        if (to != null && !to.isBlank()) {
            final String numeroTo = to.replace("whatsapp:", "");
            authToken = configRepository.findByWhatsappFrom(numeroTo)
                    .map(TenantWhatsappConfig::getTwilioSubAuthToken)
                    .orElse(null);
        }
        if (authToken == null || authToken.isBlank()) {
            // Fallback : master account token (pour les webhooks de status callback
            // ou les configs sans sub-account).
            authToken = twilioConfig.getAuthToken();
        }
        if (authToken == null || authToken.isBlank()) {
            log.error("Aucun auth token disponible pour valider la signature webhook");
            return false;
        }

        // URL complete telle que vue par Twilio (derriere Railway avec reverse proxy -> utiliser X-Forwarded-*)
        final String url = construireUrlPublique(request);

        // Twilio signe avec les parametres form-urlencoded tries alphabetiquement
        final Map<String, String> params = new TreeMap<>();
        final Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            final String nom = names.nextElement();
            params.put(nom, request.getParameter(nom));
        }

        final RequestValidator validator = new RequestValidator(authToken);
        return validator.validate(url, params, signature);
    }

    /**
     * Reconstruit l'URL publique vue par Twilio (https + host original).
     * Railway et la plupart des PaaS terminent le TLS au niveau du proxy et forwardent en HTTP,
     * donc on s'appuie sur X-Forwarded-Proto / X-Forwarded-Host pour retrouver l'URL d'origine.
     */
    private String construireUrlPublique(final HttpServletRequest request) {
        final String proto = header(request, "X-Forwarded-Proto", request.getScheme());
        final String host = header(request, "X-Forwarded-Host", request.getHeader("Host"));
        final String uri = request.getRequestURI();
        final String query = request.getQueryString();
        return proto + "://" + host + uri + (query != null ? "?" + query : "");
    }

    /**
     * Webhook de callback de statut Twilio.
     * Twilio appelle cette URL à chaque changement de statut d'un message (sent, delivered, failed, undelivered).
     */
    @PostMapping(value = "/twilio/status", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> twilioStatusCallback(
            @RequestParam("MessageSid") final String messageSid,
            @RequestParam("MessageStatus") final String messageStatus,
            @RequestParam(value = "From", required = false) final String from,
            @RequestParam(value = "To", required = false) final String to,
            @RequestParam(value = "ErrorCode", required = false) final String errorCode,
            @RequestParam(value = "ErrorMessage", required = false) final String errorMessage,
            @RequestHeader(value = "X-Twilio-Signature", required = false) final String signature,
            final HttpServletRequest request) {
        log.info("Twilio status callback - sid: {}, statut: {}, errorCode: {}", messageSid, messageStatus, errorCode);

        // Pour les status callbacks, le numero du tenant est dans `From` (c'est l'expediteur
        // du message original). `To` est le prospect. On essaie d'abord `From`, puis `To`
        // en fallback (couverture maximale si Twilio change la semantique).
        if (!signatureValide(request, signature, from != null ? from : to)) {
            log.warn("Signature Twilio invalide - rejet status callback");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Mapping aligne sur la liste Twilio : accepted/queued/sending/sent -> ENVOYE,
        // delivered -> DELIVRE, read -> LU, undelivered/failed -> ECHEC.
        // Les statuts intermediaires (accepted/sending) sont vite ecrases par sent ou delivered
        // mais on les mappe pour eviter les WARN inutiles.
        final StatutSmsEnum nouveauStatut = switch (messageStatus) {
            case "delivered" -> StatutSmsEnum.DELIVRE;
            case "read" -> StatutSmsEnum.LU;
            case "failed", "undelivered" -> StatutSmsEnum.ECHEC;
            case "accepted", "queued", "sending", "sent" -> StatutSmsEnum.ENVOYE;
            default -> null;
        };

        if (nouveauStatut != null) {
            // Note : la colonne s'appelle brevo_message_id pour raisons historiques (migration
            // Brevo -> Twilio), mais elle stocke bien le MessageSid Twilio depuis longtemps.
            // Renommage de la colonne reporte (pas critique tant que c'est documente).
            smsMessageRepository.findByBrevoMessageId(messageSid).ifPresent(msg -> {
                msg.setStatut(nouveauStatut);
                if (nouveauStatut == StatutSmsEnum.ECHEC) {
                    final String detail = errorCode != null
                            ? "[" + errorCode + "] " + (errorMessage != null ? errorMessage : "")
                            : errorMessage;
                    if (detail != null && !detail.isBlank()) {
                        // Persiste le motif d'echec sur le message (visible admin + collecte
                        // des vrais codes Twilio pour alimenter codes-restriction).
                        msg.setErreur(detail);
                        log.warn("Message Twilio ECHEC - sid: {}, detail: {}", messageSid, detail);
                    }
                    // Si le code correspond a une restriction du compte WhatsApp Business,
                    // bascule la config en RESTREINT (envoi bloque + banniere). No-op sinon.
                    whatsAppConfigService.signalerEchecEnvoi(msg.getTenantId(), errorCode, errorMessage);
                }
                smsMessageRepository.save(msg);
                log.info("Statut message mis a jour - sid: {}, statut: {}", messageSid, nouveauStatut);
                // Push SSE : la coche de statut (envoye/delivre/lu/echec) doit se mettre a
                // jour en temps reel dans la conversation ouverte, sans F5. Cible le vendeur
                // titulaire (PII). On envoie l'ordre du message pour que le front patche la
                // bonne bulle.
                smsConversationRepository.findByConversationId(msg.getConversationId()).ifPresent(conv ->
                        inboxNotificationService.notifierPourVendeur(
                                msg.getTenantId(), conv.getUserId(), "statut_message",
                                Map.of("conversationId", msg.getConversationId(),
                                       "ordre", msg.getOrdre(),
                                       "statut", nouveauStatut.name())));
            });
        }

        return ResponseEntity.ok().build();
    }

    private String header(final HttpServletRequest request, final String name, final String fallback) {
        final String value = request.getHeader(name);
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    /**
     * Recupere l'IP cliente en tenant compte des proxies (Railway/CDN).
     * Prend le premier hop dans X-Forwarded-For si present, sinon getRemoteAddr.
     */
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
