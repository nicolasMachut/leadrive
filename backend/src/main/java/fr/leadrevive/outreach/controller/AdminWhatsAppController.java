package fr.leadrevive.outreach.controller;

import fr.leadrevive.common.security.AuthService;
import fr.leadrevive.outreach.dto.RepAdminWhatsappActivationDto;
import fr.leadrevive.outreach.dto.ReqAdminActionDto;
import fr.leadrevive.outreach.entity.MessageModerationLog;
import fr.leadrevive.outreach.entity.StatutWhatsappConfigEnum;
import fr.leadrevive.outreach.entity.TenantWhatsappConfig;
import fr.leadrevive.outreach.entity.WhatsappAdminAction;
import fr.leadrevive.outreach.repository.MessageModerationLogRepository;
import fr.leadrevive.outreach.repository.TenantWhatsappConfigRepository;
import fr.leadrevive.outreach.repository.WhatsappAdminActionRepository;
import fr.leadrevive.outreach.service.AdminWhatsappActivationService;
import fr.leadrevive.outreach.service.WhatsAppConfigService;
import fr.leadrevive.outreach.service.WhatsAppTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Endpoints admin SUPER_ADMIN pour le monitoring et les actions sensibles
 * sur les configs WhatsApp (epic 23).
 *
 * <p>Toutes les actions modifiantes (desactivation, forcer ACTIF) exigent
 * une {@link ReqAdminActionDto justification} (min 20 chars) tracee dans la
 * table {@code whatsapp_admin_actions} pour audit.</p>
 */
@RestController
@RequestMapping("/api/admin/whatsapp")
@RequiredArgsConstructor
@Slf4j
public class AdminWhatsAppController {

    private final WhatsAppConfigService onboardingService;
    private final AdminWhatsappActivationService activationService;
    private final TenantWhatsappConfigRepository configRepository;
    private final WhatsappAdminActionRepository adminActionRepository;
    private final MessageModerationLogRepository moderationLogRepository;
    private final AuthService authService;

    /**
     * Liste de toutes les configs WhatsApp (tous tenants).
     * Pas de filtre tenant : appel SUPER_ADMIN, cross-tenant intentionnel.
     */
    @GetMapping("/configs")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<TenantWhatsappConfig> listerConfigs() {
        log.info("Admin - liste configs WhatsApp");
        final List<TenantWhatsappConfig> configs = configRepository.findAll();
        // Ne jamais sérialiser le token d'auth Twilio sub-account dans une réponse API
        // (même SUPER_ADMIN). Entités détachées hors transaction → ce reset n'est pas persisté.
        configs.forEach(c -> c.setTwilioSubAuthToken(null));
        return configs;
    }

    /**
     * Compteurs par statut pour le tableau de bord admin.
     */
    @GetMapping("/configs/compteurs")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Map<String, Long> compteursParStatut() {
        log.info("Admin - compteurs par statut");
        final Map<String, Long> compteurs = new java.util.LinkedHashMap<>();
        for (final StatutWhatsappConfigEnum statut : StatutWhatsappConfigEnum.values()) {
            compteurs.put(statut.name(), configRepository.countByStatut(statut));
        }
        return compteurs;
    }

    /**
     * Etat d'avancement de l'activation WhatsApp de tous les etablissements
     * (etape atteinte, blocage, identifiants techniques, modeles). Alimente la
     * console de suivi / debug SAV.
     */
    @GetMapping("/activation")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<RepAdminWhatsappActivationDto> listerActivation() {
        log.info("Admin - etat activation WhatsApp (tous tenants)");
        return activationService.listerActivation();
    }

    /**
     * Cree (ou reenregistre) le sender Twilio d'un etablissement. Deblocage SAV :
     * sender jamais cree, ou devenu orphelin (404 cote Twilio).
     */
    @PostMapping("/configs/{tenantId}/creer-sender")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> creerSender(
            @PathVariable("tenantId") final Long tenantId,
            @RequestBody(required = false) final Map<String, String> body) {
        // displayName optionnel : override du nom affiche (l'operateur saisit le
        // nom valide par Meta, lu dans WhatsApp Manager). Sinon nom Meta connu / etablissement.
        final String displayNameOverride = body != null ? body.get("displayName") : null;
        log.info("Admin - creation sender WhatsApp - tenantId: {}", tenantId);
        final TenantWhatsappConfig config = activationService.creerSender(tenantId, displayNameOverride);
        return ResponseEntity.ok(Map.of(
                "twilioSenderSid", config.getTwilioSenderSid() != null ? config.getTwilioSenderSid() : "",
                "statut", config.getStatut() != null ? config.getStatut().name() : ""));
    }

    /**
     * Supprime le sender Twilio d'un etablissement (cycle de test : supprimer
     * puis recreer). Vide {@code twilio_sender_sid}.
     */
    @PostMapping("/configs/{tenantId}/supprimer-sender")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> supprimerSender(@PathVariable("tenantId") final Long tenantId) {
        log.info("Admin - suppression sender WhatsApp - tenantId: {}", tenantId);
        final TenantWhatsappConfig config = activationService.supprimerSender(tenantId);
        return ResponseEntity.ok(Map.of(
                "statut", config.getStatut() != null ? config.getStatut().name() : ""));
    }

    /**
     * Re-soumet les modeles de messages d'un etablissement (relance les rejets).
     * Action SAV pour debloquer une activation coincee a l'etape modeles.
     */
    @PostMapping("/configs/{tenantId}/resoumettre-modeles")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<WhatsAppTemplateService.ResultatSoumission> resoumettreModeles(
            @PathVariable("tenantId") final Long tenantId) {
        log.info("Admin - re-soumission modeles WhatsApp - tenantId: {}", tenantId);
        return ResponseEntity.ok(activationService.resoumettreModeles(tenantId));
    }

    /**
     * Re-soumet UN modele precis (bouton par ligne). Relance un modele en erreur /
     * rejete sans toucher aux modeles deja approuves. {@code templateName} dans le body.
     */
    @PostMapping("/configs/{tenantId}/resoumettre-modele")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<WhatsAppTemplateService.ResultatSoumission> resoumettreModele(
            @PathVariable("tenantId") final Long tenantId,
            @RequestBody final Map<String, String> body) {
        final String templateName = body != null ? body.get("templateName") : null;
        if (templateName == null || templateName.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        log.info("Admin - re-soumission modele unitaire WhatsApp - tenantId: {}, name: {}",
                tenantId, templateName);
        return ResponseEntity.ok(activationService.resoumettreModele(tenantId, templateName));
    }

    /**
     * Detail d'une config + audit log dernieres actions admin + dernieres moderations bloquees.
     */
    @GetMapping("/configs/{tenantId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Map<String, Object> detailConfig(@PathVariable("tenantId") final Long tenantId) {
        log.info("Admin - detail config WhatsApp - tenantId: {}", tenantId);
        final TenantWhatsappConfig config = configRepository.findByTenantId(tenantId).orElse(null);
        if (config != null) {
            config.setTwilioSubAuthToken(null); // masque le secret (entité détachée → pas de persist)
        }
        final List<WhatsappAdminAction> actions = adminActionRepository.findByTenantIdOrderByCreeLeDesc(tenantId);
        final List<MessageModerationLog> moderationsBloquees = moderationLogRepository
                .findByTenantIdOrderByCreeLeDesc(tenantId).stream()
                .filter(m -> "BLOQUE".equals(m.getDecision()))
                .limit(20)
                .toList();
        return Map.of(
                "config", config != null ? config : Map.of(),
                "auditAdmin", actions,
                "moderationsBloquees", moderationsBloquees
        );
    }

    /**
     * Forcer le statut ACTIF (bypass validation Meta). Cas de support :
     * restauration manuelle apres incident. Justification + audit obligatoires.
     */
    @PostMapping("/configs/{tenantId}/forcer-actif")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> forcerActif(@PathVariable("tenantId") final Long tenantId,
                                                            @Valid @RequestBody final ReqAdminActionDto request) {
        final Long adminUserId = authService.getCurrentUserId();
        log.info("Admin - force ACTIF - tenantId: {}, admin: {}", tenantId, adminUserId);
        onboardingService.forcerActif(tenantId, adminUserId, request.justification());
        return ResponseEntity.ok(Map.of("statut", "ACTIF"));
    }

    /**
     * Desactiver la config (passage en DESACTIVE + dissociation history).
     * Justification + audit obligatoires.
     */
    @PostMapping("/configs/{tenantId}/desactiver")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> desactiver(@PathVariable("tenantId") final Long tenantId,
                                                           @Valid @RequestBody final ReqAdminActionDto request) {
        final Long adminUserId = authService.getCurrentUserId();
        log.info("Admin - desactivation config WhatsApp - tenantId: {}, admin: {}", tenantId, adminUserId);
        onboardingService.desactiverConfig(tenantId, adminUserId, request.justification());
        return ResponseEntity.ok(Map.of("statut", "DESACTIVE"));
    }

    /**
     * Audit log global des actions admin (pagine).
     */
    @GetMapping("/admin-actions")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Page<WhatsappAdminAction> auditAdminActions(final Pageable pageable) {
        log.info("Admin - audit actions");
        return adminActionRepository.findAllByOrderByCreeLeDesc(pageable);
    }

    /**
     * Logs de moderation IA (pagine).
     */
    @GetMapping("/moderation-logs")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Page<MessageModerationLog> moderationLogs(final Pageable pageable) {
        log.info("Admin - logs moderation IA");
        return moderationLogRepository.findAllByOrderByCreeLeDesc(pageable);
    }
}
