package fr.leadrevive.outreach.service;

import fr.leadrevive.outreach.entity.StatutWhatsappConfigEnum;
import fr.leadrevive.outreach.entity.TenantWhatsappConfig;
import fr.leadrevive.outreach.entity.TenantWhatsappLogo;
import fr.leadrevive.outreach.entity.WhatsappAdminAction;
import fr.leadrevive.outreach.exception.WhatsAppOnboardingException;
import fr.leadrevive.outreach.repository.TenantWhatsappConfigRepository;
import fr.leadrevive.outreach.repository.TenantWhatsappLogoRepository;
import fr.leadrevive.outreach.repository.TenantWhatsappNumberHistoryRepository;
import fr.leadrevive.outreach.repository.WhatsappAdminActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service de gestion de la configuration WhatsApp Business par tenant.
 *
 * <p>Expose :
 * <ul>
 *   <li>{@link #recupererLogo} : lecture du logo PNG (utilisee par {@code PublicLogoController}
 *       et {@code WhatsAppLogoController})</li>
 *   <li>{@link #nettoyerCompletPourSuppressionTenant} : cleanup BDD sur
 *       suppression d'un tenant (appelee par {@code AdminService})</li>
 *   <li>{@link #forcerActif} / {@link #desactiverConfig} : actions SUPER_ADMIN via
 *       {@code AdminWhatsAppController}</li>
 * </ul>
 * </p>
 *
 * <p>L'onboarding Self Sign-up Meta (epic 25) est gere par {@code WhatsAppSelfSignupService}.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppConfigService {

    private static final long LOGO_TAILLE_MAX = 5L * 1024 * 1024; // 5 Mo

    private final TenantWhatsappConfigRepository configRepository;
    private final TenantWhatsappNumberHistoryRepository historyRepository;
    private final TenantWhatsappLogoRepository logoRepository;
    private final WhatsappAdminActionRepository adminActionRepository;

    /**
     * Codes d'erreur Twilio (CSV) a interpreter comme une RESTRICTION du compte
     * WhatsApp Business (et non un echec ponctuel d'un message). Vide par defaut :
     * a renseigner via la variable d'env une fois les codes reels observes en prod
     * (chaque ECHEC logge deja {@code [code] message} dans WebhookController). Ne JAMAIS
     * y mettre 63016 (hors fenetre 24h) : ce n'est pas une restriction.
     */
    @Value("${leadrevive.whatsapp.codes-restriction:}")
    private String codesRestrictionCsv;

    // ====================================================================
    // LECTURE LOGO
    // ====================================================================

    /**
     * Recupere les bytes du logo du tenant. Retourne null si aucun logo uploade.
     */
    public byte[] recupererLogo(final Long tenantId) {
        return logoRepository.findById(tenantId)
                .map(TenantWhatsappLogo::getLogoBytes)
                .orElse(null);
    }

    // ====================================================================
    // ACTIONS ADMIN
    // ====================================================================

    /**
     * Desactive la config WhatsApp d'un tenant (action SUPER_ADMIN).
     * Le sender Twilio n'est PAS desenregistre cote Twilio (garde en reserve
     * pour eviter de repayer un envoi de verif en cas de reactivation).
     */
    @Transactional
    public void desactiverConfig(final Long tenantId, final Long adminUserId, final String justification) {
        log.info("Admin desactivation config WhatsApp - tenantId: {}, admin: {}", tenantId, adminUserId);

        final TenantWhatsappConfig config = configRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new WhatsAppOnboardingException(
                        "Aucune configuration pour ce tenant.", "CONFIG_INEXISTANTE"));

        final String ancienNumero = config.getWhatsappFrom();
        final StatutWhatsappConfigEnum ancienStatut = config.getStatut();

        if (ancienNumero != null) {
            archiverNumeroHistorique(tenantId, ancienNumero, "desactivation_admin");
        }
        config.setWhatsappFrom(null);
        config.setStatut(StatutWhatsappConfigEnum.DESACTIVE);
        configRepository.save(config);

        loguerActionAdmin(tenantId, adminUserId, "DESACTIVATION", justification,
                String.format("{\"ancienNumero\":\"%s\",\"ancienStatut\":\"%s\"}",
                        ancienNumero != null ? ancienNumero : "", ancienStatut));
    }

    /**
     * Force le passage en ACTIF, en bypassant la validation Meta. Cas de support :
     * restauration manuelle d'une config apres incident.
     */
    @Transactional
    public void forcerActif(final Long tenantId, final Long adminUserId, final String justification) {
        log.info("Admin force ACTIF - tenantId: {}, admin: {}", tenantId, adminUserId);

        final TenantWhatsappConfig config = configRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new WhatsAppOnboardingException(
                        "Aucune configuration pour ce tenant.", "CONFIG_INEXISTANTE"));

        if (config.getWhatsappFrom() == null) {
            throw new WhatsAppOnboardingException(
                    "Impossible de forcer ACTIF sans numero verifie.", "NUMERO_ABSENT");
        }

        final StatutWhatsappConfigEnum ancienStatut = config.getStatut();
        config.setStatut(StatutWhatsappConfigEnum.ACTIF);
        config.setMetaStatus("ONLINE");
        config.setMetaRejectReason(null);
        configRepository.save(config);

        loguerActionAdmin(tenantId, adminUserId, "FORCE_ACTIF", justification,
                String.format("{\"ancienStatut\":\"%s\"}", ancienStatut));
    }

    /**
     * Signale un echec d'envoi WhatsApp remonte par le status callback Twilio. Si le
     * code d'erreur fait partie de la liste {@code codes-restriction} ET que la config
     * est actuellement ACTIF, bascule en {@link StatutWhatsappConfigEnum#RESTREINT} :
     * l'envoi est alors bloque (invariant != ACTIF) et la banniere s'affiche.
     *
     * <p>No-op si le code n'est pas un code de restriction, si la config n'existe pas,
     * ou si elle n'est pas ACTIF (on ne re-degrade pas un statut deja non-actif). La
     * recovery vers ACTIF est pilotee par {@code WhatsAppSenderStatusJob} quand le
     * sender redevient ONLINE.</p>
     *
     * <p>Appele depuis le webhook (cross-tenant, non authentifie) : le filtre Hibernate
     * tenant n'y est pas actif, la lecture par tenantId est donc fiable.</p>
     */
    @Transactional
    public void signalerEchecEnvoi(final Long tenantId, final String errorCode, final String errorMessage) {
        if (tenantId == null || errorCode == null || errorCode.isBlank()) {
            return;
        }
        final Set<String> codesRestriction = Arrays.stream(codesRestrictionCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        if (!codesRestriction.contains(errorCode.trim())) {
            return;
        }
        configRepository.findByTenantId(tenantId)
                .filter(c -> c.getStatut() == StatutWhatsappConfigEnum.ACTIF)
                .ifPresent(config -> {
                    config.setStatut(StatutWhatsappConfigEnum.RESTREINT);
                    config.setMetaRejectReason("[" + errorCode.trim() + "] "
                            + (errorMessage != null && !errorMessage.isBlank()
                                ? errorMessage
                                : "Envoi WhatsApp refuse - compte restreint"));
                    configRepository.save(config);
                    log.warn("Compte WhatsApp RESTREINT suite a echec d'envoi - tenantId: {}, code: {}",
                            tenantId, errorCode.trim());
                });
    }

    // ====================================================================
    // UTILITAIRES
    // ====================================================================

    /**
     * Nettoyage COMPLET de la config WhatsApp lors de la suppression d'un tenant.
     *
     * <p>Contrairement a {@link #desactiverConfig} (action admin "soft"), cette methode
     * desactive definitivement la config. Le sub-account Twilio est conserve cote
     * Twilio (sera facture mais inactive) — la suppression d'un sub-account via API
     * Twilio n'est pas supportee, seule la suspension est possible et est geree
     * par le billing Twilio.</p>
     */
    @Transactional
    public void nettoyerCompletPourSuppressionTenant(final Long tenantId) {
        log.info("Nettoyage complet WhatsApp pour suppression tenant - tenantId: {}", tenantId);

        final TenantWhatsappConfig config = configRepository.findByTenantId(tenantId).orElse(null);
        if (config == null) {
            log.info("Pas de config WhatsApp - skip - tenantId: {}", tenantId);
            return;
        }

        // 1. Archive du numero dans l'historique (audit)
        final String ancienNumero = config.getWhatsappFrom();
        if (ancienNumero != null) {
            archiverNumeroHistorique(tenantId, ancienNumero, "suppression_concession");
        }

        // 2. Reset de la config locale
        config.setWhatsappFrom(null);
        config.setTwilioSenderSid(null);
        config.setStatut(StatutWhatsappConfigEnum.DESACTIVE);
        config.setMetaStatus(null);
        config.setMetaRejectReason(null);
        configRepository.save(config);
    }

    private void archiverNumeroHistorique(final Long tenantId, final String numero, final String raison) {
        historyRepository.findByWhatsappFromOrderByAssocieLeDesc(numero).stream()
                .filter(h -> h.getTenantId().equals(tenantId) && h.getDissocieLe() == null)
                .findFirst()
                .ifPresent(h -> {
                    h.setDissocieLe(LocalDateTime.now());
                    h.setRaisonDissociation(raison);
                    historyRepository.save(h);
                });
    }

    private void loguerActionAdmin(final Long tenantId, final Long adminUserId,
                                    final String action, final String justification,
                                    final String metadataJson) {
        final WhatsappAdminAction trace = new WhatsappAdminAction();
        trace.setTenantId(tenantId);
        trace.setAdminUserId(adminUserId);
        trace.setAction(action);
        trace.setJustification(justification);
        trace.setMetadataJson(metadataJson);
        adminActionRepository.save(trace);
    }
}
