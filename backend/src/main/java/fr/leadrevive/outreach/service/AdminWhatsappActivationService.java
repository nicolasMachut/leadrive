package fr.leadrevive.outreach.service;

import fr.leadrevive.outreach.dto.RepAdminWhatsappActivationDto;
import fr.leadrevive.outreach.dto.RepAdminWhatsappActivationDto.ModeleDto;
import fr.leadrevive.outreach.entity.StatutTemplateEnum;
import fr.leadrevive.outreach.entity.StatutWhatsappConfigEnum;
import fr.leadrevive.outreach.entity.TenantWhatsappConfig;
import fr.leadrevive.outreach.entity.TenantWhatsappTemplate;
import fr.leadrevive.outreach.repository.SmsMessageRepository;
import fr.leadrevive.outreach.repository.TenantWhatsappConfigRepository;
import fr.leadrevive.outreach.repository.TenantWhatsappTemplateRepository;
import fr.leadrevive.outreach.util.WhatsAppErreurMessage;
import fr.leadrevive.tenant.entity.Tenant;
import fr.leadrevive.tenant.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service admin (SUPER_ADMIN) qui agrege l'etat d'avancement de la configuration
 * WhatsApp de tous les etablissements, pour la console de suivi / debug SAV.
 *
 * <p>Calcule, pour chaque config, l'etape atteinte dans le parcours d'activation
 * et la raison de blocage eventuelle. C'est la <strong>source de verite unique</strong>
 * de cette logique d'etape : a reutiliser plutot que de la recalculer ailleurs.</p>
 *
 * <p><strong>Cross-tenant</strong> : ce service tourne pour un SUPER_ADMIN sans
 * contexte tenant pertinent. Le filtre Hibernate {@code tenantFilter} est donc
 * desactive temporairement pour lister les configs / modeles de tous les tenants
 * (meme pattern que les jobs de polling).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminWhatsappActivationService {

    /** Nombre total d'etapes du parcours d'activation (0 = non demarre, 6 = actif). */
    public static final int ETAPES_TOTAL = 6;

    /** Fenetre du rollup "messages en erreur" affiche cote admin. */
    private static final int JOURS_ROLLUP_ECHECS = 7;

    private final TenantWhatsappConfigRepository configRepository;
    private final TenantWhatsappTemplateRepository templateRepository;
    private final TenantRepository tenantRepository;
    private final WhatsAppTemplateService templateService;
    private final WhatsAppSenderProvisioningService senderProvisioningService;
    private final SmsMessageRepository smsMessageRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Liste l'etat d'activation WhatsApp de tous les etablissements, trie pour
     * remonter en premier les cas a traiter (bloques, puis les moins avances).
     */
    @Transactional(readOnly = true)
    public List<RepAdminWhatsappActivationDto> listerActivation() {
        log.info("Admin - liste etat activation WhatsApp (tous tenants)");
        final Session session = entityManager.unwrap(Session.class);
        session.disableFilter("tenantFilter");
        try {
            final Map<Long, String> nomsByTenantId = tenantRepository.findAll().stream()
                    .collect(Collectors.toMap(Tenant::getId, Tenant::getNom, (a, b) -> a));
            final Map<Long, List<TenantWhatsappTemplate>> modelesByTenant =
                    templateRepository.findAll().stream()
                            .collect(Collectors.groupingBy(TenantWhatsappTemplate::getTenantId));

            // Rollup "messages en erreur" calcule a la volee (pas de compteur denormalise) :
            // [tenantId -> {count, dernierEchecLe}] sur la fenetre recente.
            final LocalDateTime depuis = LocalDateTime.now().minusDays(JOURS_ROLLUP_ECHECS);
            final Map<Long, Object[]> echecsByTenant = smsMessageRepository
                    .countEchecsParTenantDepuis(depuis).stream()
                    .collect(Collectors.toMap(row -> (Long) row[0], row -> row, (a, b) -> a));

            // Detail des motifs (cause + nombre) par tenant : les codes Twilio bruts
            // (row[1]) sont traduits en libelle clair puis re-agreges (plusieurs codes
            // peuvent mapper sur le meme motif lisible), tries du plus frequent au moins.
            final List<Object[]> motifsRows = smsMessageRepository.countEchecsParTenantParMotifDepuis(depuis);
            final Map<Long, List<RepAdminWhatsappActivationDto.MotifEchecDto>> motifsByTenant =
                    motifsRows.stream()
                            .collect(Collectors.groupingBy(
                                    row -> (Long) row[0],
                                    Collectors.collectingAndThen(
                                            Collectors.groupingBy(
                                                    row -> WhatsAppErreurMessage.lisible((String) row[1]),
                                                    Collectors.summingLong(row -> ((Number) row[2]).longValue())),
                                            AdminWhatsappActivationService::trierMotifs)));
            // Tenants dont un echec recent indique un numero/sender indisponible (a corriger).
            // ETAT COURANT, pas une simple fenetre : on ne flague que si le dernier echec
            // "numero indisponible" est POSTERIEUR au dernier envoi reussi (sinon un vieil
            // echec continuerait d'alarmer alors que les envois repartent).
            final Map<Long, LocalDateTime> dernierEnvoiReussiParTenant = smsMessageRepository
                    .findDerniereDateEnvoiReussiParTenant(depuis).stream()
                    .collect(Collectors.toMap(row -> (Long) row[0], row -> (LocalDateTime) row[1], (a, b) -> a));
            final Map<Long, LocalDateTime> dernierEchecIndispoParTenant = smsMessageRepository
                    .findEchecsParTenantParMotifAvecDerniereDate(depuis).stream()
                    .filter(row -> WhatsAppErreurMessage.estNumeroIndisponible((String) row[1]))
                    .collect(Collectors.toMap(
                            row -> (Long) row[0],
                            row -> (LocalDateTime) row[2],
                            (a, b) -> a.isAfter(b) ? a : b));
            final java.util.Set<Long> numeroEnEchecTenants = dernierEchecIndispoParTenant.entrySet().stream()
                    .filter(e -> {
                        final LocalDateTime dernierSucces = dernierEnvoiReussiParTenant.get(e.getKey());
                        return dernierSucces == null || e.getValue().isAfter(dernierSucces);
                    })
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            return configRepository.findAll().stream()
                    .map(config -> construireDto(config, nomsByTenantId, modelesByTenant, echecsByTenant,
                            motifsByTenant, numeroEnEchecTenants))
                    .sorted(Comparator
                            .comparing(RepAdminWhatsappActivationDto::bloque).reversed()
                            .thenComparingInt(RepAdminWhatsappActivationDto::etape)
                            .thenComparing(dto -> dto.nomEtablissement() == null
                                    ? "" : dto.nomEtablissement().toLowerCase()))
                    .toList();
        } finally {
            // Re-active le filtre pour ne pas polluer la session si reutilisee.
            session.enableFilter("tenantFilter").setParameter("tenantId", -1L);
        }
    }

    /**
     * Re-soumet les modeles de messages d'un etablissement (relance les rejets
     * dont le back-off est expire). Action SAV pour debloquer une activation.
     *
     * <p>Cross-tenant : le filtre est desactive pour que {@link WhatsAppTemplateService}
     * (qui rejoint cette transaction) voie bien les modeles du tenant cible.</p>
     */
    /**
     * Cree (ou reenregistre) le sender Twilio d'un etablissement. Action SAV de
     * deblocage : utile quand le sender n'a jamais ete cree, ou qu'il est devenu
     * orphelin (supprime cote Twilio). Cross-tenant (filtre desactive).
     */
    @Transactional
    public TenantWhatsappConfig creerSender(final Long tenantId, final String displayNameOverride) {
        log.info("Admin - (re)creation sender WhatsApp - tenantId: {}", tenantId);
        final Session session = entityManager.unwrap(Session.class);
        session.disableFilter("tenantFilter");
        try {
            return senderProvisioningService.provisionner(tenantId, displayNameOverride);
        } finally {
            session.enableFilter("tenantFilter").setParameter("tenantId", -1L);
        }
    }

    /**
     * Supprime le sender Twilio d'un etablissement (pour le cycle de test
     * supprimer -> recreer). Cross-tenant (filtre desactive).
     */
    @Transactional
    public TenantWhatsappConfig supprimerSender(final Long tenantId) {
        log.info("Admin - suppression sender WhatsApp - tenantId: {}", tenantId);
        final Session session = entityManager.unwrap(Session.class);
        session.disableFilter("tenantFilter");
        try {
            return senderProvisioningService.supprimerSender(tenantId);
        } finally {
            session.enableFilter("tenantFilter").setParameter("tenantId", -1L);
        }
    }

    /**
     * Re-soumet UN seul modele d'un etablissement (bouton par ligne). Contrairement
     * a resoumettreModeles (tout), ne touche pas les modeles deja approuves : utile
     * pour relancer uniquement celui en erreur / rejete sans coupure d'envoi.
     * Cross-tenant (filtre desactive).
     */
    @Transactional
    public WhatsAppTemplateService.ResultatSoumission resoumettreModele(
            final Long tenantId, final String templateName) {
        log.info("Admin - re-soumission unitaire modele WhatsApp - tenantId: {}, name: {}",
                tenantId, templateName);
        final Session session = entityManager.unwrap(Session.class);
        session.disableFilter("tenantFilter");
        try {
            return templateService.resoumettreUn(tenantId, templateName);
        } finally {
            session.enableFilter("tenantFilter").setParameter("tenantId", -1L);
        }
    }

    @Transactional
    public WhatsAppTemplateService.ResultatSoumission resoumettreModeles(final Long tenantId) {
        log.info("Admin - re-soumission forcee modeles WhatsApp - tenantId: {}", tenantId);
        final Session session = entityManager.unwrap(Session.class);
        session.disableFilter("tenantFilter");
        try {
            // Force la recreation complete depuis les YAML (tous statuts), contrairement
            // a soumettreTous qui ignore les templates deja SOUMIS / APPROUVE.
            return templateService.resoumettreTous(tenantId);
        } finally {
            session.enableFilter("tenantFilter").setParameter("tenantId", -1L);
        }
    }

    /**
     * Re-agrege les motifs (libelle -&gt; total) en liste triee du plus frequent au
     * moins frequent, plafonnee aux 5 principaux motifs (lisibilite console).
     */
    private static List<RepAdminWhatsappActivationDto.MotifEchecDto> trierMotifs(
            final Map<String, Long> parMotif) {
        return parMotif.entrySet().stream()
                .map(e -> new RepAdminWhatsappActivationDto.MotifEchecDto(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(
                        RepAdminWhatsappActivationDto.MotifEchecDto::nombre).reversed())
                .limit(5)
                .toList();
    }

    private RepAdminWhatsappActivationDto construireDto(
            final TenantWhatsappConfig config,
            final Map<Long, String> noms,
            final Map<Long, List<TenantWhatsappTemplate>> modelesByTenant,
            final Map<Long, Object[]> echecsByTenant,
            final Map<Long, List<RepAdminWhatsappActivationDto.MotifEchecDto>> motifsByTenant,
            final java.util.Set<Long> numeroEnEchecTenants) {

        final List<TenantWhatsappTemplate> modeles =
                modelesByTenant.getOrDefault(config.getTenantId(), List.of());

        final int etape = calculerEtape(config, modeles);
        final String raisonBlocage = calculerRaisonBlocage(config, modeles);

        // Rollup messages en erreur (row = [tenantId, count(Long), dernierEchec(LocalDateTime)]).
        final Object[] rollup = echecsByTenant.get(config.getTenantId());
        final long nbEchecsRecents = rollup != null && rollup[1] != null
                ? ((Number) rollup[1]).longValue() : 0L;
        final LocalDateTime dernierEchecLe = rollup != null ? (LocalDateTime) rollup[2] : null;

        return new RepAdminWhatsappActivationDto(
                config.getTenantId(),
                noms.getOrDefault(config.getTenantId(), "(etablissement introuvable)"),
                config.getStatut() != null ? config.getStatut().name() : null,
                etape,
                ETAPES_TOTAL,
                libelleEtape(etape),
                raisonBlocage != null,
                raisonBlocage,
                config.getWhatsappFrom(),
                config.getDisplayName(),
                config.getTwilioSubAccountSid(),
                config.getTwilioSenderSid(),
                config.getWabaId(),
                config.getMetaBusinessId(),
                config.getMetaStatus(),
                config.getNameStatus(),
                config.getQualityRating(),
                config.getMessagingLimit(),
                config.getMetaRejectReason(),
                config.getDateDerniereSynchroMeta(),
                config.getModifieLe(),
                nbEchecsRecents,
                dernierEchecLe,
                motifsByTenant.getOrDefault(config.getTenantId(), List.of()),
                numeroEnEchecTenants.contains(config.getTenantId()),
                modeles.stream()
                        .sorted(Comparator.comparing(TenantWhatsappTemplate::getTemplateName))
                        .map(AdminWhatsappActivationService::toModeleDto)
                        .toList()
        );
    }

    /**
     * Etape atteinte (0..{@link #ETAPES_TOTAL}) :
     * 1 sous-compte Twilio cree, 2 WhatsApp connecte, 3 modeles soumis,
     * 4 modeles approuves, 5 numero en ligne, 6 actif.
     */
    private int calculerEtape(final TenantWhatsappConfig config,
                              final List<TenantWhatsappTemplate> modeles) {
        final boolean subCree = estRenseigne(config.getTwilioSubAccountSid());
        final boolean metaConnecte = estRenseigne(config.getWabaId());
        final boolean auMoinsUnSoumis = modeles.stream().anyMatch(m ->
                m.getStatut() == StatutTemplateEnum.SOUMIS
                        || m.getStatut() == StatutTemplateEnum.APPROUVE
                        || m.getStatut() == StatutTemplateEnum.REJETE);
        final boolean tousApprouves = !modeles.isEmpty()
                && modeles.stream().allMatch(m -> m.getStatut() == StatutTemplateEnum.APPROUVE);
        final boolean enLigne = "ONLINE".equalsIgnoreCase(config.getMetaStatus());
        final boolean actif = config.getStatut() == StatutWhatsappConfigEnum.ACTIF;

        int etape = 0;
        if (subCree) {
            etape = 1;
        }
        if (metaConnecte) {
            etape = 2;
        }
        if (metaConnecte && auMoinsUnSoumis) {
            etape = 3;
        }
        if (tousApprouves) {
            etape = 4;
        }
        if (tousApprouves && enLigne) {
            etape = 5;
        }
        if (actif) {
            etape = ETAPES_TOTAL;
        }
        return etape;
    }

    /**
     * Raison de blocage lisible (ou null si l'avancement est normal). Priorise
     * le probleme le plus actionnable pour le SAV.
     */
    private String calculerRaisonBlocage(final TenantWhatsappConfig config,
                                         final List<TenantWhatsappTemplate> modeles) {
        if (config.getStatut() == StatutWhatsappConfigEnum.RESTREINT) {
            return estRenseigne(config.getMetaRejectReason())
                    ? "Compte restreint : " + config.getMetaRejectReason()
                    : "Compte WhatsApp Business restreint - envoi suspendu.";
        }
        if (config.getStatut() == StatutWhatsappConfigEnum.ERREUR) {
            return estRenseigne(config.getMetaRejectReason())
                    ? config.getMetaRejectReason()
                    : "Configuration en erreur, intervention requise.";
        }
        final Optional<TenantWhatsappTemplate> rejete = modeles.stream()
                .filter(m -> m.getStatut() == StatutTemplateEnum.REJETE)
                .findFirst();
        if (rejete.isPresent()) {
            final TenantWhatsappTemplate modele = rejete.get();
            return "Modele « " + modele.getTemplateName() + " » refuse"
                    + (estRenseigne(modele.getMetaRejectReason())
                        ? " : " + modele.getMetaRejectReason() : ".");
        }
        if ("RED".equalsIgnoreCase(config.getQualityRating())) {
            return "Qualite du numero degradee (RED), envoi a risque.";
        }
        return null;
    }

    private String libelleEtape(final int etape) {
        return switch (etape) {
            case 1 -> "Sous-compte cree";
            case 2 -> "WhatsApp connecte";
            case 3 -> "Modeles soumis";
            case 4 -> "Modeles approuves";
            case 5 -> "Numero en ligne";
            case ETAPES_TOTAL -> "Actif";
            default -> "Non demarre";
        };
    }

    private static ModeleDto toModeleDto(final TenantWhatsappTemplate modele) {
        return new ModeleDto(
                modele.getTemplateName(),
                modele.getStatut() != null ? modele.getStatut().name() : null,
                modele.getTemplateSid(),
                modele.getMetaRejectReason(),
                modele.getNbTentatives(),
                modele.getSoumisLe(),
                modele.getProchaineTentativeLe(),
                modele.getDateDerniereSynchro()
        );
    }

    private static boolean estRenseigne(final String valeur) {
        return valeur != null && !valeur.isBlank();
    }
}
