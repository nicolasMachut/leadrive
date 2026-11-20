package fr.leadrevive.outreach.service;

import fr.leadrevive.outreach.dto.RepSelfSignupStatutDto;
import fr.leadrevive.outreach.dto.RepSelfSignupStatutDto.TemplateStatutDto;
import fr.leadrevive.outreach.dto.ReqMetaConnexionDto;
import fr.leadrevive.outreach.exception.WhatsAppOnboardingException;
import fr.leadrevive.outreach.entity.StatutTemplateEnum;
import fr.leadrevive.outreach.entity.StatutWhatsappConfigEnum;
import fr.leadrevive.outreach.entity.TenantWhatsappConfig;
import fr.leadrevive.outreach.entity.TenantWhatsappTemplate;
import fr.leadrevive.outreach.repository.TenantWhatsappConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Orchestrateur du flow Self Sign-up WhatsApp Business par tenant (epic 25).
 *
 * <p>Etapes coordonnees :</p>
 * <ol>
 *   <li>{@link #demarrer(Long)} : initialise la config en SELF_SIGNUP + cree le
 *       sub-account Twilio + initialise les entrees templates en BDD.</li>
 *   <li>{@link #connecterMeta(Long, ReqMetaConnexionDto)} : recoit les
 *       identifiants Meta retournes par le flow Facebook Embedded Signup et
 *       declenche la soumission des templates a Twilio Content API.</li>
 *   <li>{@link #statut(Long)} : statut courant pour l'UI (banner + timeline).</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppSelfSignupService {

    private final TenantWhatsappConfigRepository configRepository;
    private final TwilioSubAccountService subAccountService;
    private final WhatsAppTemplateService templateService;
    private final fr.leadrevive.outreach.integration.MetaGraphApiClient metaGraphApiClient;
    private final fr.leadrevive.outreach.repository.SmsMessageRepository smsMessageRepository;

    /** Fenetre de detection du signal "envois en echec" (numero indisponible). */
    private static final int HEURES_DETECTION_ENVOIS_ECHEC = 48;

    private static boolean estVide(final String valeur) {
        return valeur == null || valeur.isBlank();
    }

    /**
     * Demarre le flow Self Sign-up pour un tenant. Cree le sub-account Twilio
     * (avec idempotence) et provisionne les entrees templates en BDD pour ce
     * tenant. Idempotent : appelable plusieurs fois sans effet de bord.
     */
    @Transactional
    public TenantWhatsappConfig demarrer(final Long tenantId) {
        log.info("Demarrage Self Sign-up - tenantId: {}", tenantId);
        final TenantWhatsappConfig config = subAccountService.assurerSubAccount(tenantId);
        templateService.initialiserTemplatesPourTenant(tenantId);
        // Pre-cree les Content Twilio des maintenant (avant l'Embedded Signup) pour
        // qu'a la connexion Meta il ne reste qu'un appel d'approbation par template
        // -> activation plus rapide. Best-effort (fallback dans soumettreTous).
        templateService.preCreerContentsTwilio(tenantId);
        return config;
    }

    /**
     * Recoit les identifiants Meta (WABA ID, Business ID, phone) apres le flow
     * Embedded Signup cote front. Persiste + declenche la soumission templates.
     */
    @Transactional
    public TenantWhatsappConfig connecterMeta(final Long tenantId, final ReqMetaConnexionDto request) {
        final TenantWhatsappConfig config = configRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "Le flow Self Sign-up doit etre demarre avant la connexion Meta. "
                        + "Appelez POST /api/parametres/whatsapp/self-signup/demarrer en premier."));

        // WABA : normalement fourni par le postMessage Meta côté front. Fallback durci
        // quand ce postMessage n'arrive pas (fréquent : popup, origine, version SDK) :
        // on résout le WABA côté serveur à partir du seul code OAuth (fiable, arrive par
        // FB.login). Sans ce filet, un postMessage manquant laissait l'activation bloquée
        // sur « Continuer sur Facebook » sans aucune récupération automatique.
        String wabaId = request.wabaId();
        if (estVide(wabaId) && !estVide(request.code())) {
            wabaId = metaGraphApiClient.resoudreWabaId(request.code());
            if (!estVide(wabaId)) {
                log.info("WABA resolu cote serveur depuis le code OAuth (postMessage absent) - tenantId: {}", tenantId);
            }
        }
        if (estVide(wabaId)) {
            throw new WhatsAppOnboardingException(
                    "Connexion WhatsApp incomplète : le compte n'a pas pu être identifié. Relancez la connexion.");
        }

        // Garde anti-collision cross-tenant sur le WABA : un même WABA ne peut pas être
        // rattaché à deux établissements (sinon le routage des webhooks entrants devient
        // ambigu). Vérification via requête native (cross-tenant volontaire).
        if (configRepository.countAutresTenantsAvecWabaId(wabaId, tenantId) > 0) {
            throw new WhatsAppOnboardingException(
                    "Ce compte WhatsApp Business est déjà rattaché à un autre établissement.");
        }

        // Récupère le numéro (E.164) ET le nom certifié directement depuis le WABA via Graph.
        // Source fiable même quand l'Embedded Signup ne renvoie pas le numéro (reconnexion /
        // FINISH_ONLY_WABA). Non-fatal : si indisponible, on garde ce que le popup a fourni.
        final fr.leadrevive.outreach.integration.MetaGraphApiClient.WabaPhoneInfo infosMeta =
                metaGraphApiClient.recupererInfosNumero(request.code(), wabaId);

        // Numéro : priorité au popup s'il l'a fourni, sinon celui du WABA. Normalisé E.164.
        String numero = request.phoneNumber();
        if (estVide(numero) && infosMeta != null) {
            numero = infosMeta.phoneNumber();
        }
        if (!estVide(numero)) {
            numero = numero.replaceAll("[^0-9+]", "");
        }

        // Collision sur le numéro résolu.
        if (!estVide(numero)
                && configRepository.countAutresTenantsAvecNumero(numero, tenantId) > 0) {
            throw new WhatsAppOnboardingException(
                    "Ce numéro WhatsApp est déjà utilisé par un autre établissement.");
        }

        config.setWabaId(wabaId);
        config.setMetaBusinessId(request.metaBusinessId());
        if (!estVide(numero)) {
            config.setWhatsappFrom(numero);
        }
        // Nom affiché = nom certifié Meta (ce que voit le prospect) -> servira de profile.name.
        if (infosMeta != null && !estVide(infosMeta.verifiedName())) {
            config.setDisplayName(infosMeta.verifiedName());
            log.info("Numero + nom certifie Meta recuperes via Graph - tenantId: {}", tenantId);
        }
        // Statut d'examen du nom des la connexion (sans attendre le polling Twilio) :
        // permet de signaler immediatement un nom en cours d'examen (le prospect verrait
        // alors le numero brut au lieu du nom de l'etablissement).
        if (infosMeta != null && !estVide(infosMeta.nameStatus())) {
            config.setNameStatus(infosMeta.nameStatus());
        }
        // Statut transitoire : Meta vient d'etre connecte, l'envoi n'est PAS encore
        // possible tant que tous les templates ne sont pas approuves par Meta. La
        // bascule vers ACTIF est pilotee par WhatsAppSenderStatusJob (polling 10min)
        // quand Meta confirme ONLINE et que tous les templates sont APPROUVE.
        config.setStatut(StatutWhatsappConfigEnum.EN_VALIDATION);
        final TenantWhatsappConfig sauvegarde = configRepository.save(config);

        templateService.soumettreTous(tenantId);
        log.info("Meta connecte + templates soumis - tenantId: {}, wabaId: {}",
                tenantId, wabaId);
        return sauvegarde;
    }

    /**
     * Compose le statut Self Sign-up complet pour l'UI : banner + timeline +
     * message contextuel.
     */
    public RepSelfSignupStatutDto statut(final Long tenantId) {
        final Optional<TenantWhatsappConfig> configOpt = configRepository.findByTenantId(tenantId);
        if (configOpt.isEmpty()) {
            // Tenant qui n'a jamais configure WhatsApp : on l'oriente direct vers Self Sign-up.
            return new RepSelfSignupStatutDto(
                    false,
                    null,
                    null,
                    null,
                    null,
                    false,
                    List.of(),
                    "Connectez votre compte WhatsApp Business pour activer l'envoi de messages.",
                    "WhatsApp Business non encore configure.",
                    null,
                    null,
                    false,
                    null
            );
        }
        final TenantWhatsappConfig config = configOpt.get();
        final boolean subCree = config.getTwilioSubAccountSid() != null
                && !config.getTwilioSubAccountSid().isBlank();
        final boolean metaConnecte = config.getWabaId() != null && !config.getWabaId().isBlank();
        final boolean tousPrets = templateService.tousLesTemplatesPrets(tenantId, config);
        final boolean senderManquant = config.getTwilioSenderSid() == null
                || config.getTwilioSenderSid().isBlank();
        // Motif d'echec de creation du sender (ex: numero deja enregistre sur
        // WhatsApp), persiste par WhatsAppSenderProvisioningService. On ne le
        // surface que si le compte est connecte (WABA ok) mais que le sender n'a
        // pas pu etre cree : c'est une action requise cote etablissement, pas une
        // finalisation "de notre cote".
        final String motifEchecSender = (metaConnecte && senderManquant
                && config.getMetaRejectReason() != null && !config.getMetaRejectReason().isBlank())
                ? config.getMetaRejectReason() : null;

        final List<TemplateStatutDto> templates = templateService.tousLesTemplatesDuTenant(tenantId).stream()
                .map(WhatsAppSelfSignupService::toTemplateDto)
                .toList();

        // Etat de sante runtime (≠ progression onboarding). Un compte peut etre
        // active puis bascule RESTREINT (restriction WhatsApp Business) : l'envoi est
        // alors bloque et la banniere doit se reafficher avec un message dedie. On
        // n'expose PAS le motif technique brut au manager (vocabulaire user-facing).
        final boolean estRestreint = config.getStatut() == StatutWhatsappConfigEnum.RESTREINT;
        final String etatCompte = config.getStatut() != null ? config.getStatut().name() : null;
        final String messageEtat = estRestreint
                ? "Votre compte WhatsApp Business est actuellement restreint : l'envoi de messages est "
                    + "suspendu. Vérifiez l'état de votre compte WhatsApp Business, puis réessayez."
                : null;

        // Signal "envois en echec" : le compte est ACTIF mais des envois recents echouent
        // de maniere deterministe car le numero/sender n'est pas joignable (≠ restriction
        // Meta). IMPORTANT : signal d'ETAT COURANT, pas une simple fenetre glissante. Un
        // vieil echec ne doit plus alarmer si un envoi a REUSSI depuis (le numero refonctionne).
        // On ne flague donc que si le dernier echec "numero indisponible" est POSTERIEUR au
        // dernier envoi reussi.
        final java.time.LocalDateTime depuisEchecs =
                java.time.LocalDateTime.now().minusHours(HEURES_DETECTION_ENVOIS_ECHEC);
        final java.time.LocalDateTime dernierEchecIndispo = smsMessageRepository
                .findEchecsAvecDateDepuis(tenantId, depuisEchecs).stream()
                .filter(row -> fr.leadrevive.outreach.util.WhatsAppErreurMessage.estNumeroIndisponible((String) row[0]))
                .map(row -> (java.time.LocalDateTime) row[1])
                .max(java.util.Comparator.naturalOrder())
                .orElse(null);
        final java.time.LocalDateTime dernierEnvoiReussi =
                smsMessageRepository.findDerniereDateEnvoiReussi(tenantId, depuisEchecs);
        final boolean numeroEnEchec = config.getStatut() == StatutWhatsappConfigEnum.ACTIF
                && dernierEchecIndispo != null
                && (dernierEnvoiReussi == null || dernierEchecIndispo.isAfter(dernierEnvoiReussi));
        final String messageEnvoi = numeroEnEchec
                ? "Vos messages WhatsApp ne partent plus : votre numéro n'est pas joignable. "
                    + "Vérifiez la configuration de votre numéro WhatsApp Business, puis réessayez."
                : null;

        return new RepSelfSignupStatutDto(
                subCree,
                config.getWabaId(),
                config.getMetaBusinessId(),
                config.getWhatsappFrom(),
                config.getDisplayName(),
                tousPrets,
                templates,
                guidage(subCree, metaConnecte, motifEchecSender, tousPrets),
                blocage(subCree, metaConnecte, motifEchecSender, tousPrets, templates),
                etatCompte,
                messageEtat,
                numeroEnEchec,
                messageEnvoi
        );
    }

    private static TemplateStatutDto toTemplateDto(final TenantWhatsappTemplate t) {
        return new TemplateStatutDto(
                t.getTemplateName(),
                t.getStatut().name(),
                t.getMetaRejectReason(),
                t.getNbTentatives()
        );
    }

    private static String guidage(final boolean subCree,
                                   final boolean metaConnecte, final String motifEchecSender,
                                   final boolean tousPrets) {
        if (!subCree) {
            return "Cliquez sur 'Activer WhatsApp Business' pour démarrer la configuration.";
        }
        if (!metaConnecte) {
            return "Connectez votre compte WhatsApp Business via Facebook pour finaliser l'activation.";
        }
        if (motifEchecSender != null) {
            return motifEchecSender;
        }
        if (!tousPrets) {
            return "La configuration de votre compte WhatsApp Business est en cours de finalisation de "
                    + "notre côté. L'envoi de messages sera disponible sous peu "
                    + "(quelques minutes à quelques heures).";
        }
        return "WhatsApp Business est entièrement actif sur votre compte. Vous pouvez envoyer des messages.";
    }

    private static String blocage(final boolean subCree,
                                    final boolean metaConnecte, final String motifEchecSender,
                                    final boolean tousPrets,
                                    final List<TemplateStatutDto> templates) {
        if (!subCree) {
            return "Activation incomplète : la configuration technique n'a pas démarré. "
                    + "L'envoi de messages est indisponible.";
        }
        if (!metaConnecte) {
            return "Activation incomplète : votre compte WhatsApp Business n'est pas encore connecté. "
                    + "L'envoi de messages est indisponible.";
        }
        if (motifEchecSender != null) {
            return "Activation incomplète : " + motifEchecSender;
        }
        if (!tousPrets) {
            final long rejets = templates.stream()
                    .filter(t -> StatutTemplateEnum.REJETE.name().equals(t.statut()))
                    .count();
            if (rejets > 0) {
                return "La configuration de votre compte WhatsApp Business n'a pas pu être finalisée. "
                        + "Contactez le support pour la relancer.";
            }
            return "La configuration de votre compte WhatsApp Business est en cours de finalisation de "
                    + "notre côté. L'envoi de messages sera disponible une fois celle-ci terminée.";
        }
        return null;
    }
}
