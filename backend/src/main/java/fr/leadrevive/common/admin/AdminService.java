package fr.leadrevive.common.admin;

import fr.leadrevive.ai.entity.TenantAiConfig;
import fr.leadrevive.ai.repository.AiLogRepository;
import fr.leadrevive.ai.repository.TenantAiConfigRepository;
import fr.leadrevive.common.admin.dto.RepAdminDashboardDto;
import fr.leadrevive.common.admin.dto.RepConcessionSimpleDto;
import fr.leadrevive.common.admin.dto.RepUsageIaDto;
import fr.leadrevive.common.admin.dto.ReqCreateConcessionDto;
import fr.leadrevive.common.admin.dto.ReqInviterManagerDto;
import fr.leadrevive.common.admin.dto.ReqInviterVendeurDto;
import fr.leadrevive.emailimport.entity.TenantEmailConfig;
import fr.leadrevive.emailimport.repository.TenantEmailConfigRepository;
import fr.leadrevive.emailimport.service.EmailAddressGeneratorService;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.outreach.entity.DirectionSmsEnum;
import fr.leadrevive.outreach.entity.StatutConversationSmsEnum;
import fr.leadrevive.outreach.repository.SmsConversationRepository;
import fr.leadrevive.outreach.repository.SmsMessageRepository;
import fr.leadrevive.tenant.exception.TenantNotFoundException;
import fr.leadrevive.common.security.AuthService;
import fr.leadrevive.common.security.ClerkAdminClient;
import fr.leadrevive.outreach.entity.StatutWhatsappConfigEnum;
import fr.leadrevive.outreach.entity.TenantWhatsappConfig;
import fr.leadrevive.outreach.repository.RappelRdvRepository;
import fr.leadrevive.outreach.repository.TenantWhatsappConfigRepository;
import fr.leadrevive.outreach.repository.TenantWhatsappNumberHistoryRepository;
import fr.leadrevive.outreach.repository.WhatsappAdminActionRepository;
import fr.leadrevive.outreach.service.WhatsAppConfigService;
import fr.leadrevive.outreach.service.WhatsAppSenderStatusJob;
import fr.leadrevive.user.dto.RepImpactDesactivationDto;
import fr.leadrevive.user.dto.RepVendeurDto;
import fr.leadrevive.user.exception.UserAlreadyExistsException;
import fr.leadrevive.user.exception.UserNotFoundException;
import fr.leadrevive.user.service.UserService;
import fr.leadrevive.tenant.entity.Tenant;
import fr.leadrevive.tenant.repository.TenantRepository;
import fr.leadrevive.user.entity.RoleEnum;
import fr.leadrevive.common.tenant.TenantContext;
import fr.leadrevive.common.util.PiiMasker;
import fr.leadrevive.user.entity.StatutUtilisateurEnum;
import fr.leadrevive.user.entity.User;
import fr.leadrevive.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final AiLogRepository aiLogRepository;
    private final TenantAiConfigRepository tenantAiConfigRepository;
    private final LeadRepository leadRepository;
    private final SmsConversationRepository smsConversationRepository;
    private final SmsMessageRepository smsMessageRepository;
    private final ClerkAdminClient clerkAdminClient;
    private final AuthService authService;
    private final EntityManager entityManager;
    private final UserService userService;
    private final WhatsAppConfigService whatsAppConfigService;
    private final TenantWhatsappConfigRepository tenantWhatsappConfigRepository;
    private final TenantWhatsappNumberHistoryRepository tenantWhatsappNumberHistoryRepository;
    private final WhatsappAdminActionRepository whatsappAdminActionRepository;
    private final RappelRdvRepository rappelRdvRepository;
    private final WhatsAppSenderStatusJob whatsAppSenderStatusJob;
    private final TenantEmailConfigRepository tenantEmailConfigRepository;
    private final EmailAddressGeneratorService emailAddressGeneratorService;

    @org.springframework.beans.factory.annotation.Value("${leadrevive.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    /**
     * Execute un bloc de code avec le filtre Hibernate `tenantFilter` desactive.
     * Necessaire pour les operations cross-tenant admin (suppression d'une concession,
     * recherche d'un user par email sur tous les tenants, etc.) sinon le filtre global
     * tronque les resultats au tenant courant et masque les donnees existantes.
     * Le filtre est restaure (avec le tenantId courant) en fin de bloc.
     */
    private <T> T executerCrossTenant(final java.util.function.Supplier<T> bloc) {
        final Session session = entityManager.unwrap(Session.class);
        session.disableFilter("tenantFilter");
        try {
            return bloc.get();
        } finally {
            final Long tenantId = TenantContext.getCurrentTenantId();
            if (tenantId != null) {
                session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
            }
        }
    }

    private void executerCrossTenant(final Runnable bloc) {
        executerCrossTenant(() -> { bloc.run(); return null; });
    }

    @Transactional(readOnly = true)
    public RepAdminDashboardDto getDashboard() {
        log.info("Chargement dashboard admin");

        // Source de verite : Clerk
        final long concessionsActives = clerkAdminClient.listOrganizations().size();
        final long utilisateursActifs30j = clerkAdminClient.countUsers();

        final LocalDateTime debutMois = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        final LocalDateTime il30Jours = LocalDateTime.now().minusDays(30);

        // Leads
        final Long leadsImportesMois = leadRepository.countGlobalDepuis(debutMois);

        // Conversations & messages
        final Long conversationsLanceesMois = smsConversationRepository.countGlobalDepuis(debutMois);
        final Long rdvObtenusDepuis30j = smsConversationRepository.countGlobalByStatutDepuis(
                StatutConversationSmsEnum.RDV_OBTENU, il30Jours);
        final Long messagesEnEchecMois = smsMessageRepository.countGlobalEchecsDepuis(debutMois);

        final List<Object[]> messagesByDirection = smsMessageRepository.countGlobalByDirectionDepuis(debutMois);
        long messagesEnvoyesMois = 0L;
        long messagesRecusMois = 0L;
        for (final Object[] row : messagesByDirection) {
            final DirectionSmsEnum direction = (DirectionSmsEnum) row[0];
            final long count = ((Number) row[1]).longValue();
            if (direction == DirectionSmsEnum.SORTANT) {
                messagesEnvoyesMois = count;
            } else if (direction == DirectionSmsEnum.ENTRANT) {
                messagesRecusMois = count;
            }
        }

        // IA
        final Long generationsIaMois = aiLogRepository.countGlobalDepuis(debutMois);
        final BigDecimal budgetIaMois = aiLogRepository.coutTotalGlobalDepuis(debutMois);

        // Alertes
        final List<String> alertes = new ArrayList<>();
        if (budgetIaMois != null && budgetIaMois.compareTo(new BigDecimal("50")) > 0) {
            alertes.add("Budget IA mensuel eleve : " + budgetIaMois + " EUR");
        }
        if (messagesEnEchecMois != null && messagesEnEchecMois > 0) {
            alertes.add(messagesEnEchecMois + " message(s) en echec ce mois");
        }

        return RepAdminDashboardDto.builder()
                .concessionsActives(concessionsActives)
                .utilisateursActifs30j(utilisateursActifs30j)
                .leadsImportesMois(leadsImportesMois != null ? leadsImportesMois : 0L)
                .conversationsLanceesMois(conversationsLanceesMois != null ? conversationsLanceesMois : 0L)
                .messagesEnvoyesMois(messagesEnvoyesMois)
                .messagesRecusMois(messagesRecusMois)
                .rdvObtenusDepuis30j(rdvObtenusDepuis30j != null ? rdvObtenusDepuis30j : 0L)
                .messagesEnEchecMois(messagesEnEchecMois != null ? messagesEnEchecMois : 0L)
                .generationsIaMois(generationsIaMois != null ? generationsIaMois : 0L)
                .budgetIaMois(budgetIaMois != null ? budgetIaMois : BigDecimal.ZERO)
                .alertes(alertes)
                .build();
    }

    @Transactional
    public void creerConcession(final ReqCreateConcessionDto request) {
        log.info("Creation concession admin - nom: {}", request.getNomConcession());

        // 0. Verifie l'unicite du nom (insensible a la casse, sur les orgs Clerk existantes)
        final String nomNormalise = request.getNomConcession().trim();
        final boolean exists = clerkAdminClient.listOrganizations().stream()
                .map(o -> (String) o.get("name"))
                .anyMatch(n -> n != null && n.equalsIgnoreCase(nomNormalise));
        if (exists) {
            throw new IllegalArgumentException("Une concession avec ce nom existe deja : " + nomNormalise);
        }

        // 1. Cree l'organisation Clerk (created_by = super admin courant).
        // L'API Clerk POST /v1/organizations exige un created_by et ajoute
        // automatiquement ce user comme membre admin de l'org. On nettoie cet
        // effet de bord juste apres (cf etape 1bis).
        final String currentClerkUserId = authService.getCurrentClerkUserId();
        final String clerkOrgId;
        try {
            clerkOrgId = clerkAdminClient.createOrganization(
                    request.getNomConcession(),
                    currentClerkUserId
            );
        } catch (final Exception e) {
            log.error("Echec creation organisation Clerk - cause: {}", e.getMessage());
            throw new RuntimeException("Erreur Clerk lors de la creation de la concession: " + e.getMessage(), e);
        }

        // 1bis. Retire le SUPER_ADMIN de l'org Clerk qu'il vient de creer.
        // Sinon il apparait dans la liste des membres cote Clerk, peut recevoir
        // un org_role dans son JWT lors de connexions ulterieures, et pollue les
        // audits. Best-effort : si l'API echoue (rare), on log et on continue -
        // ce n'est pas critique au point de bloquer la creation.
        if (currentClerkUserId != null) {
            try {
                clerkAdminClient.removeMemberFromOrganization(clerkOrgId, currentClerkUserId);
                log.info("SUPER_ADMIN retire de l'org concession - clerkUserId: {}, clerkOrgId: {}",
                        currentClerkUserId, clerkOrgId);
            } catch (final Exception e) {
                log.warn("Retrait SUPER_ADMIN de l'org Clerk echec (non bloquant) - cause: {}", e.getMessage());
            }
        }

        // 2-4. Cree le tenant mirror, le TenantAiConfig et la config email d'import.
        // Rollback Clerk explicite si la persistance echoue : sinon on garde une
        // org Clerk orpheline et le check d'unicite bloque toute recreation
        // avec le meme nom. La transaction Spring rollback la BDD mais ne touche
        // pas Clerk (appel externe non transactionnel).
        try {
            // 2. Cree le tenant mirror
            final Tenant tenant = new Tenant();
            tenant.setNom(request.getNomConcession());
            tenant.setTimezone("Europe/Paris");
            tenant.setActif(true);
            tenant.setClerkOrganizationId(clerkOrgId);
            final Tenant tenantSauvegarde = tenantRepository.save(tenant);

            // 3. TenantAiConfig par defaut avec le nom de la concession
            // (evite le fallback "la concession" dans les messages WhatsApp et autres generations IA)
            final TenantAiConfig config = new TenantAiConfig();
            config.setTenantId(tenantSauvegarde.getId());
            config.setNomConcession(request.getNomConcession());
            tenantAiConfigRepository.save(config);

            // L'onboarding WhatsApp (saisie du numero + code OTP + profil) est laisse
            // au manager depuis "Mon etablissement". Plus simple, et garantit que c'est
            // bien le manager qui choisit + valide son numero.

            // 4. Cree l'adresse email d'import (epic 24). Generation slug + token aleatoire.
            // L'adresse est immediatement ACTIVE : le manager peut configurer la redirection
            // depuis sa boite mail des qu'il accede a "Parametres > Import Email".
            creerConfigEmailPourTenant(tenantSauvegarde.getId(), request.getNomConcession());

            log.info("Concession creee - idTenant: {}, clerkOrgId: {}", tenantSauvegarde.getId(), clerkOrgId);
        } catch (final Exception e) {
            log.error("Echec persistance tenant/config - rollback org Clerk - clerkOrgId: {}, cause: {}",
                    clerkOrgId, e.getMessage());
            try {
                clerkAdminClient.deleteOrganization(clerkOrgId);
            } catch (final Exception clerkErr) {
                log.error("Echec rollback org Clerk - ORG ORPHELINE A NETTOYER MANUELLEMENT - clerkOrgId: {}, cause: {}",
                        clerkOrgId, clerkErr.getMessage());
            }
            throw new RuntimeException("Erreur lors de la persistance du tenant : " + e.getMessage(), e);
        }
    }

    /**
     * Cree la config email d'import pour un tenant (extrait pour reutilisation
     * dans la creation retroactive). Cross-tenant compatible : appelle directement
     * le repository sans dependre du TenantContext.
     */
    private TenantEmailConfig creerConfigEmailPourTenant(final Long tenantId, final String nomConcession) {
        final EmailAddressGeneratorService.EmailGenere genere =
                emailAddressGeneratorService.genererEmailAddress(nomConcession);
        final TenantEmailConfig config = new TenantEmailConfig();
        config.setTenantId(tenantId);
        config.setEmailSlug(genere.slug());
        config.setEmailAddress(genere.emailAddress());
        config.setStatut("ACTIVE");
        config.setNbEmailsRecus(0);
        final TenantEmailConfig sauvegarde = tenantEmailConfigRepository.save(config);
        log.info("Adresse email import creee - tenantId: {}, email: {}",
                tenantId, genere.emailAddress());
        return sauvegarde;
    }

    /**
     * Cree retroactivement une adresse email d'import pour une concession existante
     * qui n'en a pas (cas des tenants crees avant l'epic 24). Retourne la config existante
     * en cas de double appel idempotent.
     *
     * @throws IllegalStateException si une config existe deja
     * @throws TenantNotFoundException si le tenant n'existe pas
     */
    @Transactional
    public TenantEmailConfig creerEmailConfigRetroactif(final Long concessionId) {
        log.info("Creation retroactive email config - concessionId: {}", concessionId);
        return executerCrossTenant(() -> {
            final Tenant tenant = tenantRepository.findById(concessionId)
                    .orElseThrow(() -> new TenantNotFoundException(concessionId));
            if (Boolean.FALSE.equals(tenant.getActif())) {
                throw new IllegalStateException("Concession desactivee - impossible de creer une adresse email");
            }
            final java.util.Optional<TenantEmailConfig> existante =
                    tenantEmailConfigRepository.findByTenantId(concessionId);
            if (existante.isPresent()) {
                throw new IllegalStateException("Une adresse email d'import existe deja pour ce tenant");
            }
            return creerConfigEmailPourTenant(concessionId, tenant.getNom());
        });
    }

    /**
     * Recupere la config email d'import d'une concession (acces admin, cross-tenant).
     * Retourne {@code Optional.empty()} si la concession n'a pas d'adresse email.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<TenantEmailConfig> getEmailConfig(final Long concessionId) {
        return executerCrossTenant(() -> tenantEmailConfigRepository.findByTenantId(concessionId));
    }

    /**
     * Regenere l'adresse email d'import : nouveau slug calcule depuis le nom courant
     * de la concession + nouveau token aleatoire. La ligne BDD est UPDATE (pas
     * d'INSERT) pour preserver les compteurs et l'historique inbound_email_log
     * (lie par tenant_id, pas par adresse). Si la config etait DESACTIVE, elle
     * repasse ACTIVE.
     *
     * <p>L'ancienne adresse devient morte : les emails qui y arrivent encore
     * (redirection client non mise a jour) sont rejetes par le webhook
     * (adresse inconnue). C'est volontaire : on veut justement la couper.</p>
     *
     * @throws IllegalStateException si pas d'adresse email pour ce tenant (utiliser
     *         {@link #creerEmailConfigRetroactif} d'abord)
     */
    @Transactional
    public TenantEmailConfig regenererEmailConfig(final Long concessionId) {
        log.info("Regeneration email config - concessionId: {}", concessionId);
        return executerCrossTenant(() -> {
            final Tenant tenant = tenantRepository.findById(concessionId)
                    .orElseThrow(() -> new TenantNotFoundException(concessionId));
            final TenantEmailConfig config = tenantEmailConfigRepository.findByTenantId(concessionId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Aucune adresse email pour ce tenant - utiliser la creation retroactive"));

            final String ancienneAdresse = config.getEmailAddress();
            final EmailAddressGeneratorService.EmailGenere genere =
                    emailAddressGeneratorService.genererEmailAddress(tenant.getNom());

            // Conserve l'ancienne adresse dans l'historique pour audit / debug.
            // La liste peut etre null sur les rows pre-V062 (default JSONB '[]' garantit
            // la non-nullite cote BDD, mais defensif au cas ou).
            final java.util.List<TenantEmailConfig.AncienneAdresse> historique =
                    config.getAnciennesAdresses() != null
                            ? new java.util.ArrayList<>(config.getAnciennesAdresses())
                            : new java.util.ArrayList<>();
            historique.add(new TenantEmailConfig.AncienneAdresse(ancienneAdresse, LocalDateTime.now()));
            config.setAnciennesAdresses(historique);

            config.setEmailSlug(genere.slug());
            config.setEmailAddress(genere.emailAddress());
            config.setStatut("ACTIVE");
            // On conserve nbEmailsRecus et dernierEmailRecuLe (audit historique)
            final TenantEmailConfig sauvegarde = tenantEmailConfigRepository.save(config);
            log.info("Email config regeneree - tenantId: {}, ancienne: {}, nouvelle: {}",
                    concessionId, ancienneAdresse, genere.emailAddress());
            return sauvegarde;
        });
    }

    /**
     * Desactive l'adresse email d'import d'une concession. Le webhook rejettera
     * les emails futurs (adresse inactive). On NE SUPPRIME PAS la ligne (audit).
     */
    @Transactional
    public void desactiverEmailConfig(final Long concessionId) {
        log.info("Desactivation email config - concessionId: {}", concessionId);
        executerCrossTenant(() -> {
            final TenantEmailConfig config = tenantEmailConfigRepository.findByTenantId(concessionId)
                    .orElseThrow(() -> new IllegalStateException("Aucune adresse email pour ce tenant"));
            config.setStatut("DESACTIVE");
            tenantEmailConfigRepository.save(config);
        });
    }

    @Transactional(readOnly = true)
    public List<RepConcessionSimpleDto> listerConcessions() {
        log.info("Liste concessions depuis Clerk");

        final List<Tenant> tenants = tenantRepository.findAll();
        final Map<String, Tenant> tenantsByClerkOrgId = tenants.stream()
                .filter(t -> t.getClerkOrganizationId() != null)
                .collect(Collectors.toMap(Tenant::getClerkOrganizationId, t -> t));

        // Pre-charge en batch les configs WhatsApp pour eviter un N+1 dans la boucle.
        // Cross-tenant : le filtre Hibernate doit etre desactive pour voir toutes les concessions.
        final Map<Long, TenantWhatsappConfig> waConfigByTenantId = executerCrossTenant(() ->
                tenantWhatsappConfigRepository.findAll().stream()
                        .collect(Collectors.toMap(TenantWhatsappConfig::getTenantId, c -> c)));

        final List<Map<String, Object>> clerkOrgs = clerkAdminClient.listOrganizations();
        return clerkOrgs.stream().map(org -> {
            final String clerkOrgId = (String) org.get("id");
            final String nom = (String) org.get("name");
            final Tenant tenant = tenantsByClerkOrgId.get(clerkOrgId);
            final TenantWhatsappConfig waConfig = tenant != null ? waConfigByTenantId.get(tenant.getId()) : null;
            return RepConcessionSimpleDto.builder()
                    .id(tenant != null ? tenant.getId() : null)
                    .nom(nom)
                    .creeLe(tenant != null ? tenant.getCreeLe() : null)
                    .whatsappStatut(waConfig != null ? waConfig.getStatut().name() : null)
                    .whatsappFrom(waConfig != null ? waConfig.getWhatsappFrom() : null)
                    .build();
        }).sorted((a, b) -> a.getNom().compareToIgnoreCase(b.getNom())).toList();
    }

    /**
     * Vue complete d'un etablissement pour la page admin
     * {@code /admin/concessions/{id}}. Toutes les requetes en mode cross-tenant
     * (filtre Hibernate desactive) car l'admin doit pouvoir consulter n'importe
     * quel tenant.
     */
    @Transactional(readOnly = true)
    public fr.leadrevive.common.admin.dto.RepConcessionDetailDto getDetailsConcession(final Long concessionId) {
        log.info("Details concession admin - tenantId: {}", concessionId);
        return executerCrossTenant(() -> {
            final Tenant tenant = tenantRepository.findById(concessionId)
                    .orElseThrow(() -> new TenantNotFoundException(concessionId));

            // WhatsApp config (peut etre absente)
            final var whatsappDto = tenantWhatsappConfigRepository.findByTenantId(concessionId)
                    .map(c -> new fr.leadrevive.common.admin.dto.RepConcessionDetailDto.WhatsAppDetailDto(
                            c.getStatut() != null ? c.getStatut().name() : null,
                            c.getWhatsappFrom(),
                            c.getDisplayName(),
                            c.getQualityRating(),
                            c.getMessagingLimit(),
                            c.getMetaStatus(),
                            c.getMetaRejectReason(),
                            c.getTwilioSenderSid(),
                            c.getDateDerniereSynchroMeta(),
                            c.getModifieLe()))
                    .orElse(null);

            // Historique numeros
            final var historiqueDto = tenantWhatsappNumberHistoryRepository
                    .findByTenantIdOrderByAssocieLeDesc(concessionId).stream()
                    .map(h -> new fr.leadrevive.common.admin.dto.RepConcessionDetailDto.NumeroHistoriqueDto(
                            h.getWhatsappFrom(),
                            h.getAssocieLe(),
                            h.getDissocieLe(),
                            h.getRaisonDissociation()))
                    .toList();

            // Utilisateurs du tenant
            final var utilisateursDto = userRepository.findByTenantId(concessionId).stream()
                    .map(u -> new fr.leadrevive.common.admin.dto.RepConcessionDetailDto.UtilisateurDto(
                            u.getId(),
                            u.getPrenom(),
                            u.getNom(),
                            u.getEmail(),
                            u.getRole() != null ? u.getRole().name() : null,
                            u.getStatut() != null ? u.getStatut().name() : null,
                            u.getDerniereConnexionLe()))
                    .toList();

            // Compteurs (queries count() pour eviter de charger les entites en memoire)
            final LocalDateTime epoch = LocalDateTime.of(1970, 1, 1, 0, 0);
            final LocalDateTime debutSemaine = LocalDateTime.now().minusDays(7);
            final long leadsTotal = java.util.Optional.ofNullable(
                    leadRepository.countByTenantIdDepuis(concessionId, epoch)).orElse(0L);
            final long conversationsActives = java.util.Optional.ofNullable(
                    smsConversationRepository.countByTenantIdAndStatutDepuis(
                            concessionId, StatutConversationSmsEnum.EN_COURS, epoch)).orElse(0L);
            final long messagesTotal = java.util.Optional.ofNullable(
                    smsConversationRepository.countByTenantIdDepuis(concessionId, epoch)).orElse(0L);
            // RDV semaine : compte les rappels avec envoiPrevu dans les 7 prochains jours.
            // Le filtre Hibernate est desactive ici (executerCrossTenant), mais
            // RappelRdv n'a pas de filtre @Filter declare sur l'entite -> on filtre en JPQL
            // via une projection. Pour rester simple, on charge la liste et on filtre en
            // memoire (volume limite : qq dizaines de RDV par tenant max sur 1 semaine).
            final long rdvSemaine = rappelRdvRepository
                    .countByEnvoyeFalseAndEnvoiPrevuAtBetween(debutSemaine, LocalDateTime.now().plusDays(7));

            final var compteursDto = new fr.leadrevive.common.admin.dto.RepConcessionDetailDto.CompteursDto(
                    leadsTotal, conversationsActives, rdvSemaine, messagesTotal);

            // Audits admin (10 derniers)
            final var auditsDto = whatsappAdminActionRepository
                    .findByTenantIdOrderByCreeLeDesc(concessionId).stream()
                    .limit(10)
                    .map(a -> new fr.leadrevive.common.admin.dto.RepConcessionDetailDto.AuditAdminDto(
                            a.getId(),
                            a.getAdminUserId(),
                            a.getAction(),
                            a.getJustification(),
                            a.getCreeLe()))
                    .toList();

            return new fr.leadrevive.common.admin.dto.RepConcessionDetailDto(
                    tenant.getId(),
                    tenant.getNom(),
                    tenant.getCreeLe(),
                    tenant.getClerkOrganizationId(),
                    whatsappDto,
                    historiqueDto,
                    utilisateursDto,
                    compteursDto,
                    auditsDto);
        });
    }

    /**
     * Liste les configs WhatsApp en erreur (statut ERREUR) :
     * cas terminal, intervention support requise.
     *
     * Permet au support Leadrive de detecter proactivement les tenants en
     * difficulte et de les relancer.
     */
    @Transactional(readOnly = true)
    public List<fr.leadrevive.common.admin.dto.RepConcessionDetailDto.OnboardingBloqueDto>
            listerOnboardingsBloques() {
        log.info("Liste onboardings WhatsApp bloques");
        return executerCrossTenant(() -> {
            final LocalDateTime maintenant = LocalDateTime.now();
            final Map<Long, Tenant> tenantsById = tenantRepository.findAll().stream()
                    .collect(Collectors.toMap(Tenant::getId, t -> t));

            return tenantWhatsappConfigRepository.findAll().stream()
                    .filter(c -> c.getStatut() == StatutWhatsappConfigEnum.ERREUR)
                    .map(c -> {
                        final Tenant t = tenantsById.get(c.getTenantId());
                        final LocalDateTime depuis = c.getModifieLe() != null
                                ? c.getModifieLe() : maintenant;
                        final long heures = java.time.Duration.between(depuis, maintenant).toHours();
                        return new fr.leadrevive.common.admin.dto.RepConcessionDetailDto.OnboardingBloqueDto(
                                c.getTenantId(),
                                t != null ? t.getNom() : "(tenant introuvable)",
                                c.getStatut() != null ? c.getStatut().name() : null,
                                c.getWhatsappFrom(),
                                depuis,
                                heures
                        );
                    })
                    .sorted((a, b) -> Long.compare(b.heuresEcoulees(), a.heuresEcoulees()))
                    .toList();
        });
    }

    /**
     * Force la synchronisation du statut d'un sender depuis Twilio (GET
     * /v2/Channels/Senders/{Sid}). Met a jour la BDD avec le vrai statut,
     * quality rating, plafond, raisons OFFLINE. Utile pour diagnostiquer un
     * sender bloque en ERREUR / OFFLINE.
     */
    @Transactional
    public void synchroniserStatutTwilio(final Long concessionId) {
        log.info("Force synchro statut Twilio - tenantId: {}", concessionId);
        executerCrossTenant(() -> {
            final TenantWhatsappConfig config = tenantWhatsappConfigRepository.findByTenantId(concessionId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Aucune configuration WhatsApp pour ce tenant."));
            whatsAppSenderStatusJob.synchroniserUneConfig(config);
        });
    }

    // Note : le re-push profil WhatsApp (anciennement forcerRePushProfilWhatsApp)
    // a ete supprime avec le sunset LEGACY. Le profil Meta Business est gere
    // directement par le manager via Facebook — pas de push Twilio Senders API.

    @Transactional
    public void supprimerConcession(final Long concessionId) {
        log.info("Soft delete concession - concessionId: {}", concessionId);

        final Tenant tenant = tenantRepository.findById(concessionId)
                .orElseThrow(() -> new TenantNotFoundException(concessionId));

        // 1. Soft delete des users mirror du tenant (anonymisation email + statut INACTIF).
        // Cross-tenant : le filtre Hibernate global est desactive temporairement, sinon
        // findByTenantId(concessionId) ne trouverait rien (le filtre est cale sur le tenant
        // du SUPER_ADMIN appelant).
        //
        // Pour chaque user, on SUPPRIME le compte Clerk (pas juste un lock) :
        // - En B2B Leadrive, un user appartient a UNE seule concession. Si on supprime
        //   la concession, son compte n'a plus de raison d'exister.
        // - deleteUser revoque toutes les sessions ET supprime le compte (vs deactivateUser
        //   qui lock = compte reste dans la liste Clerk en "locked").
        // - Conformite RGPD : droit a l'oubli applique cote Clerk aussi.
        executerCrossTenant(() -> {
            final List<User> utilisateurs = userRepository.findByTenantId(concessionId);
            log.info("Utilisateurs a desactiver - concessionId: {}, nombre: {}", concessionId, utilisateurs.size());
            for (final User user : utilisateurs) {
                if (user.getClerkUserId() != null) {
                    try {
                        clerkAdminClient.deleteUser(user.getClerkUserId());
                    } catch (final Exception e) {
                        log.warn("Suppression user Clerk echec (on continue) - userId: {}, cause: {}",
                                user.getId(), e.getMessage());
                    }
                }
                userService.desactiverUtilisateur(user);
            }
        });

        // 2. Supprime l'organisation Clerk (si liee). Note : on le fait avant le soft delete
        // du tenant pour ne pas garder un clerk_organization_id qui pointe vers une org
        // inexistante (au cas ou on voudrait recreer une concession avec le meme nom plus tard).
        if (tenant.getClerkOrganizationId() != null) {
            try {
                clerkAdminClient.deleteOrganization(tenant.getClerkOrganizationId());
            } catch (final Exception e) {
                log.warn("Echec suppression org Clerk (continue quand meme) - orgId: {}, cause: {}",
                        tenant.getClerkOrganizationId(), e.getMessage());
            }
        }

        // 3. Nettoyage COMPLET WhatsApp : delete sender chez Twilio (CRITIQUE - sinon
        // les senders orphelins s'accumulent et Meta throttle ce numero pendant 24-72h
        // avec RECENT_ACCOUNT_DELETION lors d'une eventuelle recreation), archive le
        // numero dans l'historique, et reset la config locale. Cross-tenant.
        executerCrossTenant(() -> {
            whatsAppConfigService.nettoyerCompletPourSuppressionTenant(concessionId);
        });

        // 4. Desactive l'adresse email d'import (epic 24) si presente.
        // On ne supprime PAS la ligne tenant_email_config (statut passe a DESACTIVE)
        // et on conserve l'historique inbound_email_log pour audit. Le webhook
        // rejettera les emails futurs (adresse inactive).
        executerCrossTenant(() -> {
            tenantEmailConfigRepository.findByTenantId(concessionId).ifPresent(config -> {
                config.setStatut("DESACTIVE");
                tenantEmailConfigRepository.save(config);
                log.info("Email config desactivee - concessionId: {}, email: {}",
                        concessionId, config.getEmailAddress());
            });
        });

        // 5. Soft delete du tenant : actif=false + libere clerk_organization_id pour
        // que la contrainte UNIQUE ne bloque pas une eventuelle recreation.
        // L'historique (leads, conversations, RDV, analyses IA, emails) reste intact en BDD,
        // accessible via l'admin pour audit ou recuperation.
        tenant.setActif(false);
        tenant.setClerkOrganizationId(null);
        tenantRepository.save(tenant);
    }

    @Transactional(readOnly = true)
    public List<RepVendeurDto> listerManagers(final Long concessionId) {
        log.info("Liste managers - concessionId: {}", concessionId);
        tenantRepository.findById(concessionId).orElseThrow(() -> new TenantNotFoundException(concessionId));

        // Exclut les managers desactives (statut INACTIF apres soft delete) pour
        // ne pas polluer l'UI avec des comptes anonymises "deleted-X-Y@deleted.local".
        return userRepository.findByTenantId(concessionId).stream()
                .filter(u -> u.getRole() == RoleEnum.MANAGER)
                .filter(u -> u.getStatut() != StatutUtilisateurEnum.INACTIF)
                .map(u -> RepVendeurDto.builder()
                        .id(u.getId())
                        .email(u.getEmail())
                        .nom(u.getNom())
                        .prenom(u.getPrenom())
                        .role(u.getRole().getValeur())
                        .statut(u.getStatut().name())
                        .actif(u.getActif())
                        .derniereConnexion(u.getDerniereConnexionLe())
                        .build())
                .toList();
    }

    /**
     * Desactive un manager sur une concession (action admin).
     * Garde-fou : refuse si c'est le dernier manager actif (sinon la concession serait
     * orpheline et plus aucun ne pourrait inviter de vendeurs).
     * Retourne un message d'erreur a afficher au front si garde-fou viole ou si la cible
     * est invalide, null sinon.
     */
    @Transactional
    public String supprimerManager(final Long concessionId, final Long userId) {
        log.info("Suppression manager - concessionId: {}, userId: {}", concessionId, userId);

        final Tenant tenant = tenantRepository.findById(concessionId)
                .orElseThrow(() -> new TenantNotFoundException(concessionId));
        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!concessionId.equals(user.getTenantId())) {
            return "Cet utilisateur n'appartient pas a cette concession.";
        }
        if (user.getRole() != RoleEnum.MANAGER) {
            return "Cet utilisateur n'est pas un manager (utilisez l'action vendeur).";
        }
        if (user.getStatut() == StatutUtilisateurEnum.INACTIF) {
            return "Ce manager est deja desactive.";
        }

        // Garde-fou : au moins 1 manager ACTIF doit rester APRES la desactivation.
        // On ne bloque QUE si la cible est elle-meme ACTIF : retirer un manager INVITE
        // (invitation jamais acceptee) ne reduit pas le nombre d'actifs -> l'etablissement
        // n'est jamais orphelin, la suppression doit toujours etre autorisee. Sans cette
        // condition, on bloquait a tort la revocation d'une invitation en attente quand il
        // ne restait qu'un seul manager reellement actif ("il reste encore un manager").
        // On compte STRICTEMENT les ACTIF (un INVITE ne peut pas reprendre conversations/taches).
        // Cross-tenant car l'admin n'a pas le tenant courant cale sur la concession ciblee.
        final List<User> managersConcession = executerCrossTenant(() ->
                userRepository.findByTenantIdAndRole(concessionId, RoleEnum.MANAGER));
        final long managersActifs = managersConcession.stream()
                .filter(u -> u.getStatut() == StatutUtilisateurEnum.ACTIF).count();
        if (user.getStatut() == StatutUtilisateurEnum.ACTIF && managersActifs <= 1) {
            final boolean invitationEnAttente = managersConcession.stream()
                    .anyMatch(u -> u.getStatut() == StatutUtilisateurEnum.INVITE);
            return invitationEnAttente
                    ? "Impossible de desactiver le dernier manager actif : l'autre manager n'a pas encore accepte son invitation. Attendez qu'il rejoigne l'etablissement avant de desactiver celui-ci."
                    : "Impossible de desactiver le dernier manager actif. Invitez un autre manager d'abord.";
        }

        // 1. Clerk : retirer le membre OU revoquer l'invitation selon le statut
        if (tenant.getClerkOrganizationId() != null) {
            try {
                if (user.getClerkUserId() != null) {
                    // User deja actif dans Clerk → retirer de l'org
                    clerkAdminClient.removeMemberFromOrganization(tenant.getClerkOrganizationId(), user.getClerkUserId());
                } else {
                    // User INVITE (pas encore de clerkUserId) → revoquer l'invitation pending
                    clerkAdminClient.revokeInvitationByEmail(tenant.getClerkOrganizationId(), user.getEmail());
                }
            } catch (final Exception e) {
                log.error("Echec suppression Clerk - orgId: {}, email: {}, cause: {}",
                        tenant.getClerkOrganizationId(), user.getEmail(), e.getMessage());
                throw new RuntimeException("Erreur Clerk lors de la suppression du manager : " + e.getMessage(), e);
            }
        }

        // 2. Soft delete du mirror local + anonymisation de l'email
        // (libere l'email pour une eventuelle reinvitation future).
        userService.desactiverUtilisateur(user);
        log.info("Manager desactive par admin - concessionId: {}, userId: {}", concessionId, userId);
        return null;
    }

    @Transactional
    public void inviterManager(final Long concessionId, final ReqInviterManagerDto request) {
        // Fix bug-hunter #11 : PII (email) en clair dans logs interdit, utiliser PiiMasker.email().
        log.info("Invitation manager - concessionId: {}, email: {}", concessionId, PiiMasker.email(request.getEmail()));

        final Tenant tenant = tenantRepository.findById(concessionId)
                .orElseThrow(() -> new TenantNotFoundException(concessionId));
        if (tenant.getClerkOrganizationId() == null) {
            throw new IllegalStateException("La concession n'a pas d'organisation Clerk liee - id: " + concessionId);
        }

        // Pre-check : email deja utilise (autre concession ou autre role).
        // Cross-tenant : un user d'un autre tenant ne serait pas vu par le filtre actif,
        // d'ou la desactivation explicite. Sans ca, on tombe sur la contrainte UNIQUE en
        // BDD et on renvoie un 500 cryptique au lieu d'un 409 explicite.
        executerCrossTenant(() -> {
            userRepository.findByEmail(request.getEmail()).ifPresent(existant -> {
                throw new UserAlreadyExistsException(request.getEmail());
            });
        });

        // 1. Cree le mirror utilisateur (statut INVITE)
        final User manager = new User();
        manager.setTenantId(tenant.getId());
        manager.setEmail(request.getEmail());
        manager.setPrenom(request.getPrenom());
        manager.setNom(request.getNom());
        manager.setRole(RoleEnum.MANAGER);
        manager.setActif(true);
        manager.setStatut(StatutUtilisateurEnum.INVITE);
        userRepository.save(manager);

        // 2. Invite Clerk
        try {
            clerkAdminClient.inviteUserToOrganization(tenant.getClerkOrganizationId(), request.getEmail(), "org:manager", frontendUrl);
        } catch (final fr.leadrevive.common.security.OrganizationMembershipQuotaExceededException e) {
            log.warn("Invitation manager refusee (quota membres) - rollback mirror - concessionId: {}", tenant.getId());
            throw e;
        } catch (final Exception e) {
            log.error("Echec invitation manager Clerk - rollback mirror: {}", e.getMessage());
            throw new RuntimeException("Erreur Clerk lors de l'invitation: " + e.getMessage(), e);
        }
    }

    /**
     * Liste les utilisateurs actifs (managers + vendeurs) d'une concession.
     * Filtre les utilisateurs INACTIF dont l'email a ete anonymise lors d'une
     * desactivation precedente : ils n'ont plus de sens fonctionnel et leur
     * email "deleted-X-Y@deleted.local" pollue l'UI.
     */
    @Transactional(readOnly = true)
    public List<RepVendeurDto> listerUtilisateurs(final Long concessionId) {
        log.info("Liste utilisateurs concession - concessionId: {}", concessionId);
        tenantRepository.findById(concessionId).orElseThrow(() -> new TenantNotFoundException(concessionId));

        return userRepository.findByTenantId(concessionId).stream()
                .filter(u -> u.getStatut() != StatutUtilisateurEnum.INACTIF)
                .map(u -> RepVendeurDto.builder()
                        .id(u.getId())
                        .email(u.getEmail())
                        .nom(u.getNom())
                        .prenom(u.getPrenom())
                        .role(u.getRole().getValeur())
                        .statut(u.getStatut().name())
                        .actif(u.getActif())
                        .derniereConnexion(u.getDerniereConnexionLe())
                        .build())
                .toList();
    }

    /**
     * Invite un vendeur sur une concession (action admin, parite avec EquipeController cote manager).
     */
    @Transactional
    public void inviterVendeur(final Long concessionId, final ReqInviterVendeurDto request) {
        // Fix bug-hunter #11 : PII (email) en clair dans logs interdit, utiliser PiiMasker.email().
        log.info("Invitation vendeur admin - concessionId: {}, email: {}", concessionId, PiiMasker.email(request.getEmail()));

        final Tenant tenant = tenantRepository.findById(concessionId)
                .orElseThrow(() -> new TenantNotFoundException(concessionId));
        if (tenant.getClerkOrganizationId() == null) {
            throw new IllegalStateException("La concession n'a pas d'organisation Clerk liee - id: " + concessionId);
        }

        // Pre-check email unique (cross-tenant : voir inviterManager pour le pourquoi).
        executerCrossTenant(() -> {
            userRepository.findByEmail(request.getEmail()).ifPresent(existant -> {
                throw new UserAlreadyExistsException(request.getEmail());
            });
        });

        // 1. Cree le mirror utilisateur (statut INVITE)
        final User vendeur = new User();
        vendeur.setTenantId(tenant.getId());
        vendeur.setEmail(request.getEmail());
        vendeur.setPrenom(request.getPrenom());
        vendeur.setNom(request.getNom());
        vendeur.setRole(RoleEnum.VENDEUR);
        vendeur.setActif(true);
        vendeur.setStatut(StatutUtilisateurEnum.INVITE);
        userRepository.save(vendeur);

        // 2. Invite Clerk avec role org:vendeur
        try {
            clerkAdminClient.inviteUserToOrganization(tenant.getClerkOrganizationId(), request.getEmail(), "org:vendeur", frontendUrl);
        } catch (final fr.leadrevive.common.security.OrganizationMembershipQuotaExceededException e) {
            log.warn("Invitation vendeur (admin) refusee (quota membres) - rollback mirror - concessionId: {}", tenant.getId());
            throw e;
        } catch (final Exception e) {
            log.error("Echec invitation vendeur Clerk - rollback mirror: {}", e.getMessage());
            throw new RuntimeException("Erreur Clerk lors de l'invitation: " + e.getMessage(), e);
        }
    }

    /**
     * Calcule l'impact d'une desactivation de vendeur cote admin.
     * Le destinataire automatique est le 1er manager actif de la concession.
     * Utilise par le frontend (GET) pour afficher la modal de confirmation
     * avant de declencher la desactivation reelle avec {@code reassigner=true}.
     */
    @Transactional(readOnly = true)
    public RepImpactDesactivationDto calculerImpactVendeur(final Long concessionId, final Long userId) {
        log.info("Calcul impact desactivation vendeur admin - concessionId: {}, userId: {}", concessionId, userId);

        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        if (!concessionId.equals(user.getTenantId())) {
            throw new IllegalArgumentException("L'utilisateur n'appartient pas a cette concession");
        }
        if (user.getRole() != RoleEnum.VENDEUR) {
            throw new IllegalArgumentException("L'utilisateur n'est pas un vendeur");
        }

        // Destinataire = 1er manager actif de la concession. Cross-tenant car
        // SUPER_ADMIN n'a pas de tenant courant cale sur la concession ciblee.
        final User destinataire = executerCrossTenant(() ->
                userRepository.findByTenantIdAndRole(concessionId, RoleEnum.MANAGER).stream()
                        .filter(u -> u.getStatut() != StatutUtilisateurEnum.INACTIF)
                        .findFirst()
                        .orElse(null)
        );

        return userService.calculerImpactDesactivation(userId, concessionId, destinataire);
    }

    /**
     * Desactive un vendeur sur une concession (action admin, parite avec EquipeController cote manager).
     *
     * <p>Avec {@code reassigner=false} (defaut) : verifie les garde-fous (conversations
     * EN_COURS, leads assignes) et refuse si non vides, en demandant a l'appelant de
     * reassigner d'abord. Pratique pour les anciens clients de l'API.
     *
     * <p>Avec {@code reassigner=true} : transfere automatiquement le portefeuille
     * (leads -> non-assignes, conversations EN_COURS et taches non terminees -> 1er
     * manager actif de la concession) puis desactive le vendeur dans la meme
     * transaction. Echoue si aucun manager actif n'est disponible.
     *
     * <p>Retourne un message d'erreur a afficher au front si garde-fou viole ou si la
     * cible est invalide (mauvais role, mauvais tenant, deja desactive), null sinon.
     */
    @Transactional
    public String supprimerVendeur(final Long concessionId, final Long userId, final boolean reassigner) {
        log.info("Suppression vendeur admin - concessionId: {}, userId: {}, reassigner: {}",
                concessionId, userId, reassigner);

        final Tenant tenant = tenantRepository.findById(concessionId)
                .orElseThrow(() -> new TenantNotFoundException(concessionId));
        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!concessionId.equals(user.getTenantId())) {
            return "Cet utilisateur n'appartient pas a cette concession.";
        }
        if (user.getRole() != RoleEnum.VENDEUR) {
            return "Cet utilisateur n'est pas un vendeur (utilisez l'action manager).";
        }
        if (user.getStatut() == StatutUtilisateurEnum.INACTIF) {
            return "Ce vendeur est deja desactive.";
        }

        if (reassigner) {
            // Mode transfert automatique : trouver le destinataire, transferer, desactiver.
            final User destinataire = executerCrossTenant(() ->
                    userRepository.findByTenantIdAndRole(concessionId, RoleEnum.MANAGER).stream()
                            .filter(u -> u.getStatut() != StatutUtilisateurEnum.INACTIF)
                            .findFirst()
                            .orElse(null)
            );
            if (destinataire == null) {
                return "Aucun manager actif disponible pour reprendre les conversations et taches du vendeur. Invitez d'abord un manager.";
            }
            // Transfert effectue dans la meme transaction : atomicite garantie.
            executerCrossTenant(() -> userService.transfererPortefeuille(userId, destinataire.getId(), concessionId));
            log.info("Vendeur reassigne avant desactivation - vendeurId: {}, destinataireId: {}", userId, destinataire.getId());
        } else {
            // Mode legacy : on bloque si garde-fous non vides.
            final long conversationsActives = smsConversationRepository
                    .findByTenantIdAndUserIdOrderByDernierMessageLeDesc(concessionId, userId)
                    .stream()
                    .filter(c -> c.getStatut() == StatutConversationSmsEnum.EN_COURS)
                    .count();
            if (conversationsActives > 0) {
                return "Ce vendeur a " + conversationsActives + " conversation(s) active(s). Reassignez-les avant de le desactiver.";
            }
            // Exclure les archives : un lead archive n'est pas une charge active a reassigner
            // (coherent avec EquipeController et le compte d'impact vendeur).
            final long leadsAssignes = executerCrossTenant(() ->
                    leadRepository.findByTenantIdAndArchiveFalse(concessionId).stream()
                            .filter(l -> userId.equals(l.getVendeurId()))
                            .count()
            );
            if (leadsAssignes > 0) {
                return "Ce vendeur a " + leadsAssignes + " lead(s) assigne(s). Reassignez-les avant de le desactiver.";
            }
        }

        // Clerk : retirer le membre OU revoquer l'invitation selon le statut
        if (tenant.getClerkOrganizationId() != null) {
            try {
                if (user.getClerkUserId() != null) {
                    clerkAdminClient.removeMemberFromOrganization(tenant.getClerkOrganizationId(), user.getClerkUserId());
                } else {
                    clerkAdminClient.revokeInvitationByEmail(tenant.getClerkOrganizationId(), user.getEmail());
                }
            } catch (final Exception e) {
                log.error("Echec suppression vendeur Clerk - orgId: {}, email: {}, cause: {}",
                        tenant.getClerkOrganizationId(), user.getEmail(), e.getMessage());
                throw new RuntimeException("Erreur Clerk lors de la suppression du vendeur : " + e.getMessage(), e);
            }
        }

        // Soft delete + anonymisation email (libere pour reinvitation future).
        userService.desactiverUtilisateur(user);
        log.info("Vendeur desactive par admin - concessionId: {}, userId: {}", concessionId, userId);
        return null;
    }

    @Transactional(readOnly = true)
    public List<RepUsageIaDto> usageIa(final int jours) {
        log.info("Usage IA admin - jours: {}", jours);

        final LocalDateTime depuis = LocalDateTime.now().minusDays(jours);
        final List<Object[]> resultats = aiLogRepository.usageParTenantDepuis(depuis);

        // Nom concession = celui de Clerk uniquement ; tenants sans org Clerk existante sont ignores
        final Map<String, String> nomsParClerkOrg = clerkAdminClient.listOrganizations().stream()
                .collect(Collectors.toMap(o -> (String) o.get("id"), o -> (String) o.get("name"), (a, b) -> a));
        final Map<Long, String> nomsTenants = tenantRepository.findAll().stream()
                .filter(t -> t.getClerkOrganizationId() != null
                        && nomsParClerkOrg.containsKey(t.getClerkOrganizationId()))
                .collect(Collectors.toMap(
                        Tenant::getId,
                        t -> nomsParClerkOrg.get(t.getClerkOrganizationId())
                ));

        return resultats.stream()
                .map(row -> {
                    final Long concessionId = row[0] != null ? ((Number) row[0]).longValue() : null;
                    final long nbAppels = row[1] != null ? ((Number) row[1]).longValue() : 0L;
                    final BigDecimal coutEstime = row[2] != null ? new BigDecimal(row[2].toString()) : BigDecimal.ZERO;
                    final LocalDateTime lastAppel = toLocalDateTime(row[3]);
                    return new Object[]{concessionId, nbAppels, coutEstime, lastAppel};
                })
                .filter(r -> r[0] != null && nomsTenants.containsKey(r[0]))
                .map(r -> RepUsageIaDto.builder()
                        .concessionId((Long) r[0])
                        .nomConcession(nomsTenants.get((Long) r[0]))
                        .nbAppels((Long) r[1])
                        .coutEstime((BigDecimal) r[2])
                        .lastAppel((LocalDateTime) r[3])
                        .build())
                .toList();
    }

    private LocalDateTime toLocalDateTime(final Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime localDateTime) return localDateTime;
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        if (value instanceof java.time.Instant instant) return LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
        return LocalDateTime.parse(value.toString());
    }
}
