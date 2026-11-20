package fr.leadrevive.outreach.service;

import fr.leadrevive.common.util.PiiMasker;
import fr.leadrevive.outreach.config.TwilioConfig;
import fr.leadrevive.outreach.entity.StatutWhatsappConfigEnum;
import fr.leadrevive.outreach.entity.TenantWhatsappConfig;
import fr.leadrevive.outreach.integration.TwilioSendersApiClient;
import fr.leadrevive.outreach.repository.TenantWhatsappConfigRepository;
import fr.leadrevive.user.service.EmailService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.hibernate.Session;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Job de polling Meta status via sub-account credentials.
 *
 * <p>Toutes les 10 minutes, interroge Twilio pour chaque config en
 * {@code ACTIF} ou {@code EN_VALIDATION} et met a jour :
 * <ul>
 *   <li>{@code meta_status} (OFFLINE, ONLINE, REJECTED)</li>
 *   <li>{@code quality_rating} (GREEN, YELLOW, RED)</li>
 *   <li>{@code messaging_limit_tier}</li>
 *   <li>{@code meta_reject_reason} si applicable</li>
 * </ul>
 * Transitions automatiques :
 * <ul>
 *   <li>ACTIF + REJECTED -> ERREUR (+ email manager)</li>
 *   <li>ERREUR + ONLINE + !RED -> ACTIF (+ email manager)</li>
 *   <li>EN_VALIDATION + ONLINE + templates prets -> ACTIF</li>
 *   <li>quality_rating GREEN -> YELLOW : alerte admin</li>
 *   <li>quality_rating !RED -> RED : alerte admin critique</li>
 * </ul>
 * </p>
 *
 * <p><strong>Cross-tenant</strong> : le job s'execute sans TenantContext.
 * Le filtre Hibernate {@code tenantFilter} est desactive temporairement
 * pour pouvoir trouver les configs de tous les tenants.</p>
 *
 * <p><strong>Credentials</strong> : chaque config est pollee avec les
 * credentials de son propre sub-account Twilio (plus de master global).</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WhatsAppSenderStatusJob {

    private final TenantWhatsappConfigRepository configRepository;
    private final TwilioConfig twilioConfig;
    private final EmailService emailService;
    private final TwilioSendersApiClient twilioSendersApiClient;
    private final WhatsAppTemplateService templateService;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Toutes les 10 minutes. Premier run 30 sec apres demarrage.
     */
    @Scheduled(initialDelay = 30_000L, fixedDelay = 600_000L)
    @SchedulerLock(name = "synchroniserStatutsSenderMeta", lockAtMostFor = "PT8M", lockAtLeastFor = "PT1M")
    public void synchroniserStatutsMeta() {
        final List<StatutWhatsappConfigEnum> statutsASynchroniser = List.of(
                StatutWhatsappConfigEnum.EN_VALIDATION,
                StatutWhatsappConfigEnum.ACTIF
        );

        // Cross-tenant : le job tourne sans TenantContext, le filtre Hibernate
        // doit etre desactive pour voir toutes les configs.
        final Session session = entityManager.unwrap(Session.class);
        session.disableFilter("tenantFilter");
        try {
            final List<TenantWhatsappConfig> configs = configRepository.findByStatutIn(statutsASynchroniser);
            if (configs.isEmpty()) {
                log.debug("Sync statuts Meta - aucune config a synchroniser");
                return;
            }
            log.info("Sync statuts Meta - configs a synchroniser: {}", configs.size());

            for (final TenantWhatsappConfig config : configs) {
                try {
                    synchroniserUneConfig(config);
                } catch (final Exception ex) {
                    log.error("Echec synchro Meta - tenantId: {}, cause: {}",
                            config.getTenantId(), ex.getMessage());
                }
            }
        } finally {
            session.enableFilter("tenantFilter").setParameter("tenantId", -1L);
        }
    }

    /**
     * Synchronise une config WhatsApp avec Twilio (statut + quality + reject reason).
     * Public car appele aussi depuis l'admin pour debug (bouton "Verifier l'etat
     * chez Twilio" sur la page detail etablissement).
     */
    public void synchroniserUneConfig(final TenantWhatsappConfig config) {
        // Configs en mode dry-run (SID commence par "dryrun-") : simulation directe
        if (config.getTwilioSenderSid() != null && config.getTwilioSenderSid().startsWith("dryrun-")) {
            synchroniserDryRun(config);
            return;
        }

        if (config.getTwilioSenderSid() == null || config.getTwilioSenderSid().isBlank()) {
            log.warn("Sync Meta ignoree - twilio_sender_sid manquant - tenantId: {}", config.getTenantId());
            return;
        }

        // Credentials sub-account obligatoires pour le polling
        if (config.getTwilioSubAccountSid() == null || config.getTwilioSubAuthToken() == null) {
            log.warn("Sync Meta ignoree - credentials sub-account manquants - tenantId: {}",
                    config.getTenantId());
            return;
        }

        final TwilioSendersApiClient.TwilioSenderResponse remote;
        try {
            remote = twilioSendersApiClient.recupererStatut(
                    config.getTwilioSenderSid(),
                    config.getTwilioSubAccountSid(),
                    config.getTwilioSubAuthToken());
        } catch (final fr.leadrevive.outreach.exception.WhatsAppOnboardingException ex) {
            if ("SENDER_INTROUVABLE_TWILIO".equals(ex.getCodeErreur())
                    && config.getStatut() != StatutWhatsappConfigEnum.ERREUR) {
                config.setStatut(StatutWhatsappConfigEnum.ERREUR);
                config.setMetaStatus("MISSING");
                config.setMetaRejectReason("Sender introuvable cote Twilio (404) - relancer l'onboarding");
                config.setDateDerniereSynchroMeta(LocalDateTime.now());
                configRepository.save(config);
                log.warn("Sender Twilio orphelin detecte - tenantId: {}, sid: {}",
                        config.getTenantId(), config.getTwilioSenderSid());
            } else {
                log.warn("Sync Meta echec - tenantId: {}, sid: {}, code: {}, cause: {}",
                        config.getTenantId(), config.getTwilioSenderSid(),
                        ex.getCodeErreur(), ex.getMessage());
            }
            return;
        } catch (final Exception ex) {
            log.warn("Sync Meta echec - tenantId: {}, sid: {}, cause: {}",
                    config.getTenantId(), config.getTwilioSenderSid(), ex.getMessage());
            return;
        }

        final String ancienQuality = config.getQualityRating();
        final StatutWhatsappConfigEnum ancienStatut = config.getStatut();

        // Mise a jour des champs cote BDD.
        config.setMetaStatus(remote.status());
        if (remote.qualityRating() != null) config.setQualityRating(remote.qualityRating());
        if (remote.messagingLimit() != null) config.setMessagingLimit(remote.messagingLimit());
        if (remote.nameStatus() != null) config.setNameStatus(remote.nameStatus());
        if (remote.rejectReason() != null) config.setMetaRejectReason(remote.rejectReason());
        if (remote.displayName() != null && !remote.displayName().isBlank()
                && !remote.displayName().equals(config.getDisplayName())) {
            config.setDisplayName(remote.displayName());
        }
        config.setDateDerniereSynchroMeta(LocalDateTime.now());

        // Transitions metier automatiques.
        final boolean estOnline = "ONLINE".equalsIgnoreCase(remote.status());
        final boolean estRejected = "REJECTED".equalsIgnoreCase(remote.status())
                || "FAILED".equalsIgnoreCase(remote.status());
        final boolean qualityRed = "RED".equalsIgnoreCase(remote.qualityRating());
        final boolean qualityYellow = "YELLOW".equalsIgnoreCase(remote.qualityRating());

        if (estRejected && ancienStatut == StatutWhatsappConfigEnum.ACTIF) {
            // Le compte etait operationnel et le numero vient d'etre refuse/desactive
            // cote WhatsApp Business -> RESTRICTION (pas une erreur d'onboarding).
            // L'envoi se bloque tout seul (statut != ACTIF) et la banniere s'affiche.
            config.setStatut(StatutWhatsappConfigEnum.RESTREINT);
            config.setMetaRejectReason(remote.rejectReason() != null ? remote.rejectReason()
                    : "Compte WhatsApp Business restreint - envoi suspendu");
            log.warn("Compte WhatsApp RESTREINT (sender refuse depuis ACTIF) - tenant: {}, numero: {}, raison: {}",
                    config.getTenantId(), PiiMasker.phone(config.getWhatsappFrom()), remote.rejectReason());
            emailService.notifierRejetMeta(config.getTenantId(),
                    remote.rejectReason() != null ? remote.rejectReason()
                            : "Compte WhatsApp Business restreint - envoi suspendu");
        } else if (estRejected && ancienStatut != StatutWhatsappConfigEnum.ERREUR
                && ancienStatut != StatutWhatsappConfigEnum.RESTREINT) {
            config.setStatut(StatutWhatsappConfigEnum.ERREUR);
            log.warn("Sender Meta REJECTED - tenant: {}, numero: {}, raison: {}",
                    config.getTenantId(), PiiMasker.phone(config.getWhatsappFrom()), remote.rejectReason());
            emailService.notifierRejetMeta(config.getTenantId(),
                    remote.rejectReason() != null ? remote.rejectReason() : "Refus Meta sans raison communiquee");
        } else if (estOnline && !qualityRed && ancienStatut == StatutWhatsappConfigEnum.EN_VALIDATION) {
            if (templateService.tousLesTemplatesPrets(config.getTenantId(), config)) {
                config.setStatut(StatutWhatsappConfigEnum.ACTIF);
                log.info("Sender Meta active (validation terminee) - tenant: {}, numero: {}, quality: {}",
                        config.getTenantId(), PiiMasker.phone(config.getWhatsappFrom()), remote.qualityRating());
                emailService.notifierActivationWhatsApp(config.getTenantId());
            }
        } else if (estOnline && !qualityRed
                && (ancienStatut == StatutWhatsappConfigEnum.ERREUR
                    || ancienStatut == StatutWhatsappConfigEnum.RESTREINT)) {
            // Recovery : le sender est redevenu ONLINE -> on reautorise l'envoi.
            // Couvre la levee d'une restriction (RESTREINT -> ACTIF) et la reparation
            // d'une erreur (ERREUR -> ACTIF). On efface le motif de blocage.
            config.setStatut(StatutWhatsappConfigEnum.ACTIF);
            config.setMetaRejectReason(null);
            log.info("Sender Meta de nouveau actif - tenant: {}, numero: {}, quality: {}",
                    config.getTenantId(), PiiMasker.phone(config.getWhatsappFrom()), remote.qualityRating());
            emailService.notifierActivationWhatsApp(config.getTenantId());
        }

        // Alertes admin sur les transitions de quality_rating (uniquement si change).
        if (!Objects.equals(ancienQuality, remote.qualityRating())) {
            if (qualityRed) {
                emailService.alerterAdminQualityRatingRouge(config.getTenantId());
            } else if (qualityYellow) {
                emailService.alerterAdminQualityRatingJaune(config.getTenantId());
            }
        }

        configRepository.save(config);
    }

    /**
     * Simulation cote dry-run : pas de transition automatique, on touche
     * uniquement le timestamp.
     */
    private void synchroniserDryRun(final TenantWhatsappConfig config) {
        config.setDateDerniereSynchroMeta(LocalDateTime.now());
        configRepository.save(config);
    }
}
