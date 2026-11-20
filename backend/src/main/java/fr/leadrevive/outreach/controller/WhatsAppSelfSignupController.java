package fr.leadrevive.outreach.controller;

import fr.leadrevive.common.ratelimit.RateLimitExceededException;
import fr.leadrevive.common.ratelimit.RateLimiter;
import fr.leadrevive.common.security.AuthService;
import fr.leadrevive.outreach.dto.RepSelfSignupStatutDto;
import fr.leadrevive.outreach.dto.ReqMetaConnexionDto;
import fr.leadrevive.outreach.service.WhatsAppSelfSignupService;
import fr.leadrevive.outreach.service.WhatsAppSenderProvisioningService;
import fr.leadrevive.outreach.service.WhatsAppTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints du flow Self Sign-up WhatsApp Business (epic 25).
 *
 * <p>Route prefixe : {@code /api/parametres/whatsapp/self-signup}.</p>
 *
 * <p>Volontairement separe de {@link WhatsAppLogoController} (epic 23 LEGACY)
 * pour clarifier la transition : un tenant LEGACY peut consulter
 * {@code GET .../statut} pour savoir s'il doit migrer, et l'UI affiche une
 * banner specifique.</p>
 */
@RestController
@RequestMapping("/api/parametres/whatsapp/self-signup")
@RequiredArgsConstructor
@Slf4j
public class WhatsAppSelfSignupController {

    private final WhatsAppSelfSignupService selfSignupService;
    private final WhatsAppTemplateService templateService;
    private final WhatsAppSenderProvisioningService senderProvisioningService;
    private final AuthService authService;
    private final RateLimiter rateLimiter;

    /** Rate-limit des actions self-signup mutatrices (création sub-account Twilio,
     *  resoumission templates = appels Twilio Content API). 5 actions/min/user. */
    private void verifierRateLimit(final String action) {
        final Long userId = authService.getCurrentUserId();
        if (!rateLimiter.tryAcquire("wa-selfsignup:" + action + ":" + (userId != null ? userId : "anon"), 5)) {
            throw new RateLimitExceededException("Trop d'actions en peu de temps. Patientez une minute.");
        }
    }

    /**
     * Statut courant du flow Self Sign-up pour le tenant connecte. Lu en
     * permanence par l'UI pour decider :
     * <ul>
     *   <li>afficher la banner "Migration WhatsApp Business" (LEGACY)</li>
     *   <li>afficher la page d'activation (SELF_SIGNUP non termine)</li>
     *   <li>afficher l'etat "actif" (SELF_SIGNUP + tous templates approuves)</li>
     * </ul>
     *
     * <p>Accessible aussi pour le VENDEUR (lecture seule) afin que le bandeau
     * de blocage puisse s'afficher sur toutes les pages, pas seulement
     * manager. Le declenchement des actions reste reserve au manager.</p>
     */
    @GetMapping("/statut")
    public RepSelfSignupStatutDto statut() {
        final Long tenantId = authService.getCurrentTenantId();
        log.info("Statut Self Sign-up demande - tenantId: {}", tenantId);
        return selfSignupService.statut(tenantId);
    }

    /**
     * Demarre le flow : cree le sub-account Twilio si absent, provisionne les
     * entrees templates en BDD. Idempotent.
     */
    @PostMapping("/demarrer")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<RepSelfSignupStatutDto> demarrer() {
        verifierRateLimit("demarrer");
        final Long tenantId = authService.getCurrentTenantId();
        selfSignupService.demarrer(tenantId);
        return ResponseEntity.ok(selfSignupService.statut(tenantId));
    }

    /**
     * Recoit le retour du flow Facebook Embedded Signup cote front : WABA ID,
     * Meta Business ID, et numero choisi. Persiste + declenche la soumission
     * des templates a Twilio.
     */
    @PostMapping("/connecter-meta")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<RepSelfSignupStatutDto> connecterMeta(
            @Valid @RequestBody final ReqMetaConnexionDto request) {
        verifierRateLimit("connecter-meta");
        final Long tenantId = authService.getCurrentTenantId();
        selfSignupService.connecterMeta(tenantId, request);
        // Etape cle : enregistrer le sender chez Twilio (Twilio ne le cree pas
        // automatiquement). Non-fatal : si ca echoue, la connexion Meta + la
        // soumission des modeles restent valides, et l'admin peut relancer la
        // creation via le bouton dedie.
        senderProvisioningService.provisionnerSansEchec(tenantId);
        return ResponseEntity.ok(selfSignupService.statut(tenantId));
    }

    /**
     * Self-service : deconnecte le compte WhatsApp (changer de compte / recommencer).
     * Supprime le sender Twilio et reinitialise la connexion ; la page d'activation
     * reaffiche alors l'Embedded Signup. Conserve le sous-compte + l'historique.
     */
    @PostMapping("/deconnecter")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<RepSelfSignupStatutDto> deconnecter() {
        verifierRateLimit("deconnecter");
        final Long tenantId = authService.getCurrentTenantId();
        log.info("Deconnexion WhatsApp self-service - tenantId: {}", tenantId);
        senderProvisioningService.deconnecter(tenantId);
        return ResponseEntity.ok(selfSignupService.statut(tenantId));
    }

    /**
     * Force la re-soumission des templates eligibles (rejets retentables ou
     * EN_ATTENTE_SOUMISSION). Utile si l'admin du tenant a modifie quelque
     * chose cote Meta et veut declencher un nouveau check.
     */
    @PostMapping("/templates/relancer")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<WhatsAppTemplateService.ResultatSoumission> relancerTemplates() {
        verifierRateLimit("relancer");
        final Long tenantId = authService.getCurrentTenantId();
        return ResponseEntity.ok(templateService.soumettreTous(tenantId));
    }
}
