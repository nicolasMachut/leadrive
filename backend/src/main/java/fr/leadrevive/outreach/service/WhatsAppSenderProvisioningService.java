package fr.leadrevive.outreach.service;

import fr.leadrevive.outreach.config.TwilioConfig;
import fr.leadrevive.outreach.entity.StatutWhatsappConfigEnum;
import fr.leadrevive.outreach.entity.TenantWhatsappConfig;
import fr.leadrevive.outreach.exception.WhatsAppOnboardingException;
import fr.leadrevive.outreach.integration.TwilioSendersApiClient;
import fr.leadrevive.outreach.repository.TenantWhatsappConfigRepository;
import fr.leadrevive.tenant.entity.Tenant;
import fr.leadrevive.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enregistre le WhatsApp Sender d'un tenant chez Twilio (epic 25).
 *
 * <p>Etape OBLIGATOIRE et longtemps manquante du flow : apres l'Embedded Signup
 * (qui partage WABA + numero cote Meta), Twilio ne cree PAS le sender tout seul.
 * L'integrateur doit appeler la Senders API ; le SID {@code XE...} retourne est
 * persiste dans {@code twilio_sender_sid}, ce qui permet ensuite a
 * {@link WhatsAppSenderStatusJob} de poller le statut et de basculer la config
 * en {@code ACTIF}. Sans cette etape : aucun sender Twilio, jamais {@code ACTIF},
 * envoi impossible.</p>
 *
 * <p>Appele automatiquement apres {@code connecter-meta}, et manuellement via le
 * bouton admin "Creer / reenregistrer le sender" (deblocage SAV).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppSenderProvisioningService {

    private final TenantWhatsappConfigRepository configRepository;
    private final TenantRepository tenantRepository;
    private final TwilioSendersApiClient sendersApiClient;
    private final TwilioConfig twilioConfig;
    private final WhatsAppTemplateService templateService;

    @Value("${leadrevive.backend-url:}")
    private String backendUrl;

    /**
     * Cree le sender pour un tenant (charge la config). Idempotent au sens
     * "reparable" : si un senderSid existe deja mais pointe sur un sender
     * supprime/orphelin, on en recree un et on ecrase le SID.
     *
     * @throws WhatsAppOnboardingException si un pre-requis manque (sous-compte,
     *         WABA, numero) ou si Twilio refuse la creation.
     */
    @Transactional
    public TenantWhatsappConfig provisionner(final Long tenantId) {
        return provisionner(tenantId, null);
    }

    /**
     * Cree le sender, avec un nom affiche force optionnel (override SAV).
     *
     * @param displayNameOverride si non vide, devient le {@code profile.name}
     *        envoye a Twilio ET le nom affiche stocke (cas admin : l'operateur
     *        saisit le nom valide par Meta). Sinon, on prend le nom Meta deja
     *        connu, sinon le nom de l'etablissement.
     */
    @Transactional
    public TenantWhatsappConfig provisionner(final Long tenantId, final String displayNameOverride) {
        log.info("Creation sender WhatsApp - tenantId: {}", tenantId);
        final TenantWhatsappConfig config = configRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new WhatsAppOnboardingException(
                        "Aucune configuration WhatsApp pour ce tenant.", "CONFIG_INEXISTANTE"));

        // Override admin : on fige le nom choisi comme nom affiche de reference.
        if (!estVide(displayNameOverride)) {
            config.setDisplayName(displayNameOverride.trim());
        }

        if (estVide(config.getTwilioSubAccountSid()) || estVide(config.getTwilioSubAuthToken())) {
            throw new WhatsAppOnboardingException(
                    "Sous-compte Twilio absent : demarrez l'activation avant de creer le sender.",
                    "SUB_ACCOUNT_ABSENT");
        }
        if (estVide(config.getWabaId())) {
            throw new WhatsAppOnboardingException(
                    "Compte WhatsApp Business non connecte : impossible de creer le sender.",
                    "WABA_ABSENT");
        }
        if (estVide(config.getWhatsappFrom())) {
            throw new WhatsAppOnboardingException(
                    "Numero WhatsApp absent : impossible de creer le sender.",
                    "NUMERO_ABSENT");
        }

        if (twilioConfig.isDryRun()) {
            // Simulation : SID prefixe "dryrun-" reconnu par WhatsAppSenderStatusJob.
            config.setTwilioSenderSid("dryrun-" + tenantId);
            config.setMetaStatus("ONLINE");
            config.setMetaRejectReason(null);
            log.info("DRY-RUN : sender simule - tenantId: {}", tenantId);
        } else {
            // sender_id doit etre un E.164 strict : on retire tout espace eventuel.
            final String numeroE164 = config.getWhatsappFrom().replaceAll("\\s", "");
            // profile.name est REQUIS par l'API Senders (Twilio renvoie
            // "profile name is a required field" sinon). On prend le display name
            // deja valide cote Meta s'il existe, sinon le nom de l'etablissement.
            final String profileName = resoudreProfileName(config);
            // Log diagnostic : ce qu'on a recu en override vs ce qu'on envoie a Twilio.
            log.info("Sender provisioning - tenantId: {}, override recu: '{}', displayName en base: '{}', "
                            + "profile.name ENVOYE a Twilio: '{}'",
                    tenantId, displayNameOverride, config.getDisplayName(), profileName);
            // Webhooks du sender : messages entrants (callback), secours (fallback)
            // et status callbacks. Necessaires pour que les reponses prospect
            // remontent a l'app (sinon l'IA ne voit pas les reponses).
            final String callbackUrl = urlWebhook("/api/webhook/twilio/whatsapp");
            final TwilioSendersApiClient.SenderCreationResponse reponse = sendersApiClient.creerSender(
                    numeroE164,
                    config.getWabaId(),
                    profileName,
                    callbackUrl,
                    callbackUrl,
                    urlWebhook("/api/webhook/twilio/status"),
                    config.getTwilioSubAccountSid(),
                    config.getTwilioSubAuthToken());
            config.setTwilioSenderSid(reponse.sid());
            config.setMetaStatus(reponse.status());
            config.setMetaRejectReason(null);
            log.info("Sender WhatsApp cree - tenantId: {}, sid: {}, statut: {}",
                    tenantId, reponse.sid(), reponse.status());
        }

        // On (re)engage le cycle de validation : le job senders fera CREATING/ONLINE -> ACTIF.
        if (config.getStatut() == StatutWhatsappConfigEnum.DESACTIVE
                || config.getStatut() == StatutWhatsappConfigEnum.ERREUR) {
            config.setStatut(StatutWhatsappConfigEnum.EN_VALIDATION);
        }
        return configRepository.save(config);
    }

    /**
     * Variante non-fatale : tente la creation, log un warning en cas d'echec et
     * ne propage pas. Utilisee dans le flow d'activation pour ne pas casser la
     * connexion Meta + soumission des modeles si Twilio hoquette (le bouton admin
     * permet de relancer).
     */
    public void provisionnerSansEchec(final Long tenantId) {
        try {
            provisionner(tenantId);
        } catch (final Exception ex) {
            log.warn("Creation sender WhatsApp echouee - tenantId: {}, cause: {} "
                    + "(deblocable via le bouton admin 'Creer le sender')", tenantId, ex.getMessage());
            // On persiste la cause comme motif de rejet de la config : sans ca, le
            // manager (page d'activation) verrait "validation en cours" indefiniment
            // sans savoir que la creation du sender a echoue (ex: numero deja
            // enregistre sur WhatsApp). Best-effort : un echec ici ne doit pas masquer
            // l'echec d'origine.
            try {
                persisterMotifEchecSender(tenantId, ex.getMessage());
            } catch (final Exception persistEx) {
                log.warn("Persistance du motif d'echec sender impossible - tenantId: {}, cause: {}",
                        tenantId, persistEx.getMessage());
            }
        }
    }

    /** Stocke le motif d'echec de creation du sender comme motif de rejet de la config. */
    @Transactional
    public void persisterMotifEchecSender(final Long tenantId, final String motif) {
        configRepository.findByTenantId(tenantId).ifPresent(config -> {
            config.setMetaRejectReason(motif);
            configRepository.save(config);
        });
    }

    /**
     * Supprime le sender Twilio du tenant (et vide {@code twilio_sender_sid}).
     * Utile pour tester le cycle creation : supprimer puis recreer. Idempotent :
     * un 404 cote Twilio (sender deja absent) n'est pas une erreur.
     */
    @Transactional
    public TenantWhatsappConfig supprimerSender(final Long tenantId) {
        log.info("Suppression sender WhatsApp - tenantId: {}", tenantId);
        final TenantWhatsappConfig config = configRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new WhatsAppOnboardingException(
                        "Aucune configuration WhatsApp pour ce tenant.", "CONFIG_INEXISTANTE"));
        if (estVide(config.getTwilioSenderSid())) {
            throw new WhatsAppOnboardingException(
                    "Aucun sender a supprimer pour ce tenant.", "SENDER_ABSENT");
        }
        if (!twilioConfig.isDryRun()
                && !estVide(config.getTwilioSubAccountSid())
                && !estVide(config.getTwilioSubAuthToken())) {
            sendersApiClient.supprimerSender(
                    config.getTwilioSenderSid(),
                    config.getTwilioSubAccountSid(),
                    config.getTwilioSubAuthToken());
        }
        config.setTwilioSenderSid(null);
        config.setMetaStatus(null);
        config.setQualityRating(null);
        config.setMessagingLimit(null);
        config.setMetaRejectReason(null);
        return configRepository.save(config);
    }

    /**
     * Déconnecte le compte WhatsApp du tenant (self-service manager : « changer
     * de compte / recommencer »). Supprime le sender Twilio et réinitialise les
     * identifiants de connexion (WABA, numéro, sender, nom affiché) pour que la
     * page d'activation réaffiche l'Embedded Signup.
     *
     * <p>On CONSERVE le sous-compte Twilio (réutilisé) et tout l'historique des
     * conversations. La suppression du sender est best-effort (un échec Twilio
     * n'empêche pas la réinitialisation locale, sinon le client resterait coincé).</p>
     */
    @Transactional
    public TenantWhatsappConfig deconnecter(final Long tenantId) {
        log.info("Deconnexion compte WhatsApp - tenantId: {}", tenantId);
        final TenantWhatsappConfig config = configRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new WhatsAppOnboardingException(
                        "Aucune configuration WhatsApp pour ce tenant.", "CONFIG_INEXISTANTE"));

        if (!estVide(config.getTwilioSenderSid())
                && !twilioConfig.isDryRun()
                && !estVide(config.getTwilioSubAccountSid())
                && !estVide(config.getTwilioSubAuthToken())) {
            try {
                sendersApiClient.supprimerSender(
                        config.getTwilioSenderSid(),
                        config.getTwilioSubAccountSid(),
                        config.getTwilioSubAuthToken());
            } catch (final Exception ex) {
                log.warn("Suppression sender a la deconnexion echouee (on reinitialise quand meme) "
                        + "- tenantId: {}, cause: {}", tenantId, ex.getMessage());
            }
        }

        // Réinitialise la connexion (garde le sous-compte + l'historique conversations).
        config.setWabaId(null);
        config.setMetaBusinessId(null);
        config.setWhatsappFrom(null);
        config.setTwilioSenderSid(null);
        config.setDisplayName(null);
        config.setMetaStatus(null);
        config.setQualityRating(null);
        config.setMessagingLimit(null);
        config.setMetaRejectReason(null);
        config.setStatut(StatutWhatsappConfigEnum.DESACTIVE);
        final TenantWhatsappConfig sauvegarde = configRepository.save(config);

        // Les approbations de templates sont liees au WABA : apres deconnexion, on
        // les reinitialise pour qu'elles soient re-soumises a la reconnexion (sous
        // le nouveau WABA). Sinon un changement de compte garderait des approbations
        // caduques et pourrait marquer le tenant ACTIF a tort.
        templateService.reinitialiserTemplates(tenantId);
        return sauvegarde;
    }

    /**
     * profile.name (requis) : privilegie le display name deja valide cote Meta
     * s'il existe, sinon le nom de l'etablissement (le plus conforme aux regles
     * de display name Meta).
     */
    private String resoudreProfileName(final TenantWhatsappConfig config) {
        if (!estVide(config.getDisplayName())) {
            return config.getDisplayName();
        }
        return tenantRepository.findById(config.getTenantId())
                .map(Tenant::getNom)
                .filter(nom -> nom != null && !nom.isBlank())
                .orElse("Etablissement");
    }

    private String urlWebhook(final String chemin) {
        if (backendUrl == null || backendUrl.isBlank()) {
            return null;
        }
        return backendUrl + chemin;
    }

    private static boolean estVide(final String valeur) {
        return valeur == null || valeur.isBlank();
    }
}
