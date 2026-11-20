package fr.leadrevive.lead.service;

import fr.leadrevive.ai.entity.TypeGenerationEnum;
import fr.leadrevive.ai.entity.TenantAiConfig;
import fr.leadrevive.ai.repository.AiLogRepository;
import fr.leadrevive.ai.service.TenantAiConfigService;
import fr.leadrevive.lead.dto.RepDashboardAdminDto;
import fr.leadrevive.lead.dto.RepDashboardDto;
import fr.leadrevive.lead.dto.RepDashboardManagerDto;
import fr.leadrevive.lead.dto.RepDashboardVendeurDto;
import fr.leadrevive.lead.dto.RepLeadChaudDto;
import fr.leadrevive.lead.dto.RepRdvEnAttenteDto;
import fr.leadrevive.lead.dto.RepStatJourDto;
import fr.leadrevive.lead.dto.RepStatsMoisDto;
import fr.leadrevive.lead.dto.RepStatsVendeurDto;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.entity.LeadStatutEnum;
import fr.leadrevive.lead.entity.SourceVenteEnum;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.lead.repository.HistoriqueStatutRepository;
import fr.leadrevive.lead.repository.VenteConfirmeeRepository;
import fr.leadrevive.outreach.entity.DirectionSmsEnum;
import fr.leadrevive.outreach.entity.SmsConversation;
import fr.leadrevive.outreach.entity.StatutConversationSmsEnum;
import fr.leadrevive.outreach.repository.SmsConversationRepository;
import fr.leadrevive.outreach.repository.SmsMessageRepository;
import fr.leadrevive.tenant.entity.Tenant;
import fr.leadrevive.tenant.repository.TenantRepository;
import fr.leadrevive.user.entity.User;
import fr.leadrevive.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardStatsService {

    private static final BigDecimal COUT_MOYEN_WHATSAPP = new BigDecimal("0.05");

    private final LeadRepository leadRepository;
    private final HistoriqueStatutRepository historiqueStatutRepository;
    private final SmsConversationRepository smsConversationRepository;
    private final SmsMessageRepository smsMessageRepository;
    private final VenteConfirmeeRepository venteConfirmeeRepository;
    private final AiLogRepository aiLogRepository;
    private final TenantAiConfigService tenantAiConfigService;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    @Cacheable(value = "dashboardStats", key = "#tenantId + '_' + #mois")
    @Transactional(readOnly = true)
    public RepDashboardDto getStatsGlobales(final Long tenantId, final YearMonth mois) {
        log.info("Calcul stats dashboard - tenantId: {}, mois: {}", tenantId, mois);

        final LocalDateTime debutMois = mois.atDay(1).atStartOfDay();
        final LocalDate debutDate = mois.atDay(1);
        final LocalDate finDate = mois.atEndOfMonth();

        // Leads
        final Long totalLeads = leadRepository.countByTenantIdDepuis(tenantId, debutMois);
        final Long leadsNonContactes = leadRepository.countNonContactesDepuis(tenantId, debutMois);
        final Integer pourcentageNonContactes = totalLeads > 0
                ? (int) (leadsNonContactes * 100 / totalLeads) : 0;

        // Conversations WhatsApp
        final Long conversationsLancees = smsConversationRepository.countByTenantIdDepuis(tenantId, debutMois);
        // RDV pris FACTURABLES : définition canonique (cf. CLAUDE.md) = événement immuable
        // historique_statut IA→RDV_PRIS, leads distincts, borné par date de l'événement.
        final long rdvObtenus = historiqueStatutRepository.countRdvPrisIaSince(tenantId, debutMois);
        final Integer tauxConversionRdv = conversationsLancees > 0
                ? (int) (rdvObtenus * 100 / conversationsLancees) : 0;

        // Ventes
        final Long ventesCount = venteConfirmeeRepository.countByTenantIdAndPeriode(tenantId, debutDate, finDate);
        // CA total toutes sources (le filtre par source WHATSAPP_IA ne sert que pour le calcul ROI IA)
        final BigDecimal caGenere = venteConfirmeeRepository.sumMontantByTenantIdAndPeriode(
                tenantId, debutDate, finDate);

        // Couts
        final BigDecimal coutIa = aiLogRepository.coutTotalDepuis(tenantId, debutMois);
        final List<Object[]> messagesParDirection = smsMessageRepository.countByDirectionDepuis(tenantId, debutMois);
        Integer msgEnvoyes = 0;
        Integer msgRecus = 0;
        for (final Object[] row : messagesParDirection) {
            final DirectionSmsEnum direction = (DirectionSmsEnum) row[0];
            final Long count = (Long) row[1];
            if (direction == DirectionSmsEnum.SORTANT) {
                msgEnvoyes = count.intValue();
            } else {
                msgRecus = count.intValue();
            }
        }
        final BigDecimal coutWhatsApp = COUT_MOYEN_WHATSAPP.multiply(BigDecimal.valueOf(msgEnvoyes + msgRecus));
        final BigDecimal coutTotal = coutIa.add(coutWhatsApp);
        // Activite IA
        final List<Object[]> statsIa = aiLogRepository.statsParTypeDepuis(tenantId, debutMois);
        Integer fichesGenerees = 0;
        for (final Object[] row : statsIa) {
            final TypeGenerationEnum type = (TypeGenerationEnum) row[0];
            final Long count = (Long) row[1];
            if (type == TypeGenerationEnum.FICHE_PREPARATION) {
                fichesGenerees = count.intValue();
            }
        }

        // Stats par jour
        final List<RepStatJourDto> statsParJour = construireStatsParJour(tenantId, debutMois, debutDate, finDate);

        log.info("Stats dashboard calculees - tenantId: {}, leads: {}, rdv: {}, ventes: {}",
                tenantId, totalLeads, rdvObtenus, ventesCount);

        return RepDashboardDto.builder()
                .totalLeads(totalLeads.intValue())
                .leadsNonContactes(leadsNonContactes.intValue())
                .pourcentageNonContactes(pourcentageNonContactes)
                .conversationsLancees(conversationsLancees.intValue())
                .rdvObtenus((int) rdvObtenus)
                .tauxConversionRdv(tauxConversionRdv)
                .ventesConfirmees(ventesCount.intValue())
                .caGenere(caGenere != null ? caGenere : BigDecimal.ZERO)
                .coutIa(coutIa)
                .coutWhatsApp(coutWhatsApp)
                .coutTotal(coutTotal)
                .fichesGenerees(fichesGenerees)
                .messagesWhatsAppEnvoyes(msgEnvoyes)
                .messagesWhatsAppRecus(msgRecus)
                .statsParJour(statsParJour)
                .build();
    }

    @Cacheable(value = "dashboardVendeurs", key = "#tenantId + '_' + #mois")
    @Transactional(readOnly = true)
    public List<RepStatsVendeurDto> getStatsVendeurs(final Long tenantId, final YearMonth mois) {
        log.info("Calcul stats vendeurs - tenantId: {}, mois: {}", tenantId, mois);

        final LocalDateTime debutMois = mois.atDay(1).atStartOfDay();
        final LocalDate debutDate = mois.atDay(1);
        final LocalDate finDate = mois.atEndOfMonth();

        final List<Object[]> convosParVendeur = smsConversationRepository.statsParVendeurDepuis(tenantId, debutMois);
        final List<Object[]> ventesParVendeur = venteConfirmeeRepository.statsParVendeur(tenantId, debutDate, finDate);

        final Map<Long, RepStatsVendeurDto.RepStatsVendeurDtoBuilder> buildersParVendeur = new HashMap<>();

        // Init from users
        final List<User> users = userRepository.findByTenantId(tenantId);
        for (final User user : users) {
            // Exclure les comptes désactivés du leaderboard : un ex-salarié ne doit plus
            // apparaître dans la perf équipe (pollution + nom d'un compte "supprimé").
            if (!Boolean.TRUE.equals(user.getActif())) {
                continue;
            }
            buildersParVendeur.put(user.getId(), RepStatsVendeurDto.builder()
                    .userId(user.getId())
                    .nomVendeur(user.getPrenom() + " " + user.getNom())
                    .conversationsLancees(0)
                    .rdvObtenus(0)
                    .ventesConfirmees(0)
                    .caGenere(BigDecimal.ZERO)
                    .fichesGenerees(0));
        }

        // Conversations
        for (final Object[] row : convosParVendeur) {
            final Long userId = (Long) row[0];
            final Long totalConvos = (Long) row[1];
            final Long rdv = (Long) row[2];
            final RepStatsVendeurDto.RepStatsVendeurDtoBuilder builder = buildersParVendeur.get(userId);
            if (builder != null) {
                builder.conversationsLancees(totalConvos.intValue())
                       .rdvObtenus(rdv.intValue());
            }
        }

        // Ventes
        for (final Object[] row : ventesParVendeur) {
            final Long userId = (Long) row[0];
            final Long count = (Long) row[1];
            final BigDecimal montant = (BigDecimal) row[2];
            final RepStatsVendeurDto.RepStatsVendeurDtoBuilder builder = buildersParVendeur.get(userId);
            if (builder != null) {
                builder.ventesConfirmees(count.intValue())
                       .caGenere(montant);
            }
        }

        final List<RepStatsVendeurDto> result = new ArrayList<>();
        for (final RepStatsVendeurDto.RepStatsVendeurDtoBuilder builder : buildersParVendeur.values()) {
            result.add(builder.build());
        }
        result.sort((a, b) -> Integer.compare(b.getRdvObtenus(), a.getRdvObtenus()));

        return result;
    }

    @Cacheable(value = "dashboardEvolution", key = "#tenantId + '_' + #derniersMois")
    @Transactional(readOnly = true)
    public List<RepStatsMoisDto> getStatsMois(final Long tenantId, final int derniersMois) {
        log.info("Calcul stats mensuelles - tenantId: {}, mois: {}", tenantId, derniersMois);

        final YearMonth moisCourant = YearMonth.now();
        final YearMonth moisDebut = moisCourant.minusMonths(derniersMois - 1L);
        final LocalDateTime debutDateTime = moisDebut.atDay(1).atStartOfDay();
        final LocalDate debutDate = moisDebut.atDay(1);

        // 4 requetes groupees par mois au lieu de 5 * N mois
        final Map<String, Long> leadsParMois = new HashMap<>();
        for (final Object[] row : leadRepository.countParMoisDepuis(tenantId, debutDateTime)) {
            leadsParMois.put((String) row[0], ((Number) row[1]).longValue());
        }

        final Map<String, Long> rdvParMois = new HashMap<>();
        for (final Object[] row : smsConversationRepository.rdvParMoisDepuis(tenantId, debutDateTime)) {
            rdvParMois.put((String) row[0], ((Number) row[1]).longValue());
        }

        final Map<String, Long> ventesParMois = new HashMap<>();
        final Map<String, BigDecimal> caParMois = new HashMap<>();
        for (final Object[] row : venteConfirmeeRepository.statsParMoisDepuis(tenantId, debutDate)) {
            final String mois = (String) row[0];
            ventesParMois.put(mois, ((Number) row[1]).longValue());
            caParMois.put(mois, (BigDecimal) row[2]);
        }

        final Map<String, BigDecimal> coutParMois = new HashMap<>();
        for (final Object[] row : aiLogRepository.coutParMoisDepuis(tenantId, debutDateTime)) {
            coutParMois.put((String) row[0], (BigDecimal) row[1]);
        }

        final List<RepStatsMoisDto> result = new ArrayList<>();
        for (int i = derniersMois - 1; i >= 0; i--) {
            final YearMonth mois = moisCourant.minusMonths(i);
            final String moisStr = mois.toString();
            result.add(RepStatsMoisDto.builder()
                    .mois(moisStr)
                    .totalLeads(leadsParMois.getOrDefault(moisStr, 0L).intValue())
                    .rdvObtenus(rdvParMois.getOrDefault(moisStr, 0L).intValue())
                    .ventesConfirmees(ventesParMois.getOrDefault(moisStr, 0L).intValue())
                    .caGenere(caParMois.getOrDefault(moisStr, BigDecimal.ZERO))
                    .coutTotal(coutParMois.getOrDefault(moisStr, BigDecimal.ZERO))
                    .build());
        }

        return result;
    }

    @Transactional(readOnly = true)
    public RepDashboardAdminDto getStatsAdmin(final Long tenantId) {
        log.info("Stats dashboard ADMIN - tenantId: {}", tenantId);
        final YearMonth moisCourant = YearMonth.now();
        final YearMonth moisPrecedent = moisCourant.minusMonths(1);

        final RepDashboardDto global = getStatsGlobales(tenantId, moisCourant);
        final BigDecimal caPrecedent = venteConfirmeeRepository.sumMontantByTenantIdAndPeriode(
                tenantId, moisPrecedent.atDay(1), moisPrecedent.atEndOfMonth());

        final List<RepStatsMoisDto> evolution = getStatsMois(tenantId, 6);

        return RepDashboardAdminDto.builder()
                .caGenereMois(global.getCaGenere())
                .caGenereMoisPrecedent(caPrecedent != null ? caPrecedent : BigDecimal.ZERO)
                .coutTotal(global.getCoutTotal())
                .prospectsNonContactes(global.getLeadsNonContactes())
                .evolution6Mois(evolution)
                .build();
    }

    @Transactional(readOnly = true)
    public RepDashboardManagerDto getStatsManager(final Long tenantId) {
        log.info("Stats dashboard MANAGER - tenantId: {}", tenantId);
        final YearMonth moisCourant = YearMonth.now();
        final LocalDate lundiCourant = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        final LocalDate lundiPrecedent = lundiCourant.minusWeeks(1);
        final LocalDateTime debutSemaine = lundiCourant.atStartOfDay();
        final LocalDateTime debutSemainePrec = lundiPrecedent.atStartOfDay();
        final LocalDateTime debutMois = moisCourant.atDay(1).atStartOfDay();
        final LocalDateTime aujourdhui = LocalDate.now().atStartOfDay();

        // Vendeurs actifs (ayant cree une conversation aujourd'hui)
        final List<User> tousVendeurs = userRepository.findByTenantId(tenantId);
        final int totalVendeurs = tousVendeurs.size();
        final List<SmsConversation> toutesConvos = smsConversationRepository
                .findByTenantIdOrderByDernierMessageLeDesc(tenantId);
        final List<SmsConversation> convosAujourdhui = toutesConvos.stream()
                .filter(c -> c.getCreeLe() != null && c.getCreeLe().isAfter(aujourdhui))
                .toList();
        final int vendeursActifs = (int) convosAujourdhui.stream()
                .map(SmsConversation::getUserId).filter(Objects::nonNull).distinct().count();

        // Prospects en cours (conversations actives à l'instant t)
        final int prospectsEnCours = (int) toutesConvos.stream()
                .filter(c -> c.getStatut() == StatutConversationSmsEnum.EN_COURS)
                .count();

        // RDV pris FACTURABLES (définition canonique : événement immuable IA→RDV_PRIS,
        // leads distincts, borné par date de l'événement). Cf. CLAUDE.md.
        final long rdvSemaine = historiqueStatutRepository.countRdvPrisIaSince(tenantId, debutSemaine);
        final long rdvSemainePrec = historiqueStatutRepository.countRdvPrisIaBetween(
                tenantId, debutSemainePrec, debutSemaine);

        // RDV ce mois + conversations lancées ce mois (pour taux conversion)
        final long rdvMois = historiqueStatutRepository.countRdvPrisIaSince(tenantId, debutMois);
        final Long convosMois = smsConversationRepository.countByTenantIdDepuis(tenantId, debutMois);
        final Integer tauxConversionPct = convosMois > 0
                ? (int) Math.round((rdvMois * 100.0) / convosMois) : 0;

        // Objectif RDV équipe ce mois (depuis tenant — null si non configuré).
        // Pas d'erreur si tenant introuvable, juste objectif null = pas de progress bar.
        final Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        final Integer objectifMois = tenant != null ? tenant.getObjectifMensuelRdvEquipe() : null;

        // RDVs en attente confirmation (3+ jours sans vente)
        final List<RepRdvEnAttenteDto> rdvsEnAttente = listerRdvEnAttente(tenantId, 3);

        // Perf vendeurs (reutilise getStatsVendeurs)
        final List<RepStatsVendeurDto> perf = getStatsVendeurs(tenantId, moisCourant);

        return RepDashboardManagerDto.builder()
                .vendeursActifs(vendeursActifs)
                .totalVendeurs(totalVendeurs)
                .rdvPrisSemaine((int) rdvSemaine)
                .rdvSemaineEquipe((int) rdvSemaine)
                .rdvSemainePrecedente((int) rdvSemainePrec)
                .rdvMoisEquipe((int) rdvMois)
                .prospectsEnCours(prospectsEnCours)
                .tauxConversionPct(tauxConversionPct)
                .objectifMoisEquipe(objectifMois)
                .rdvEnAttenteConfirmation(rdvsEnAttente.size())
                .performanceVendeurs(perf)
                .rdvsEnAttente(rdvsEnAttente)
                .build();
    }

    @Transactional(readOnly = true)
    public RepDashboardVendeurDto getStatsVendeur(final Long tenantId, final Long userId) {
        log.info("Stats dashboard VENDEUR - tenantId: {}, userId: {}", tenantId, userId);
        final LocalDateTime aujourdhui = LocalDate.now().atStartOfDay();
        final LocalDate lundiCourant = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        final LocalDateTime debutSemaine = lundiCourant.atStartOfDay();
        final LocalDateTime debutMois = YearMonth.now().atDay(1).atStartOfDay();

        final List<SmsConversation> mesConvos = smsConversationRepository
                .findByTenantIdAndUserIdOrderByDernierMessageLeDesc(tenantId, userId);

        final int appelsAujourdhui = (int) mesConvos.stream()
                .filter(c -> c.getCreeLe().isAfter(aujourdhui))
                .count();

        // RDV pris FACTURABLES du vendeur (définition canonique : événement immuable
        // IA→RDV_PRIS, attribué au propriétaire actuel du lead). Cf. CLAUDE.md.
        final int rdvSemaine = (int) historiqueStatutRepository.countRdvPrisIaSinceForVendeur(tenantId, userId, debutSemaine);
        final int rdvMois = (int) historiqueStatutRepository.countRdvPrisIaSinceForVendeur(tenantId, userId, debutMois);

        final User vendeur = userRepository.findById(userId).orElse(null);
        final Integer objectif = vendeur != null && vendeur.getObjectifMensuelRdv() != null
                ? vendeur.getObjectifMensuelRdv() : 10;

        // Leads a rappeler : ranking par hotness_score (calcule par l'analyse IA des conversations)
        // On exclut les leads TERMINE et les opt-out.
        // Filtre vendeur strict : un vendeur ne voit QUE les leads qui lui sont assignes.
        // Les leads non-assignes (vendeurId=null) sont visibles uniquement par les managers
        // qui doivent les redistribuer.
        // Surface operationnelle (leads a rappeler) : on exclut les archives des la requete
        // (un lead archive ne doit plus etre pousse a l'action).
        final List<Lead> tousLeads = leadRepository.findByTenantIdAndArchiveFalse(tenantId);
        final List<RepLeadChaudDto> leadsChauds = tousLeads.stream()
                // Strict : un vendeur ne voit QUE ses leads. Avant, le filtre incluait
                // aussi les leads non assignes (vendeurId IS NULL) => 2 vendeurs voyaient
                // le meme lead orphelin et le contactaient en parallele (double WhatsApp).
                // Les leads non assignes doivent etre vus par le MANAGER, pas par les vendeurs.
                .filter(l -> l.getVendeurId() != null && l.getVendeurId().equals(userId))
                .filter(l -> l.getStatut() != LeadStatutEnum.TERMINE)
                .filter(l -> !Boolean.TRUE.equals(l.getOptOutSms()))
                // Tri secondaire par date pour ordonner les egaux (sinon ordre aleatoire
                // entre 10 leads tous a hotnessScore=0 par exemple).
                .sorted((a, b) -> {
                    final int scoreCmp = scoreEffectif(b).compareTo(scoreEffectif(a));
                    if (scoreCmp != 0) return scoreCmp;
                    final java.time.LocalDateTime ca = a.getCreeLe();
                    final java.time.LocalDateTime cb = b.getCreeLe();
                    if (ca == null && cb == null) return 0;
                    if (ca == null) return 1;
                    if (cb == null) return -1;
                    return cb.compareTo(ca);
                })
                .limit(5)
                .map(l -> RepLeadChaudDto.builder()
                        .leadId(l.getId())
                        .nom(l.getNom())
                        .marque(l.getMarque())
                        .modele(l.getModele())
                        .scoring(scoreEffectif(l))
                        .telephone(l.getTelephone())
                        .creeLe(l.getCreeLe())
                        .priorite(calculerPriorite(l))
                        .build())
                .toList();

        // En attente de retour : mes conversations EN_COURS avec dernier message SORTANT
        final List<RepLeadChaudDto> enAttente = mesConvos.stream()
                .filter(c -> c.getStatut() == StatutConversationSmsEnum.EN_COURS)
                .limit(10)
                .map(c -> {
                    final Lead lead = leadRepository.findById(c.getLeadId()).orElse(null);
                    if (lead == null) return null;
                    return RepLeadChaudDto.builder()
                            .leadId(lead.getId())
                            .nom(lead.getNom())
                            .marque(lead.getMarque())
                            .modele(lead.getModele())
                            .scoring(lead.getScoring())
                            .telephone(lead.getTelephone())
                            .dernierContact(c.getDernierMessageLe())
                            .priorite("NORMAL")
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();

        return RepDashboardVendeurDto.builder()
                .mesAppelsAujourdhui(appelsAujourdhui)
                .mesRdvSemaine(rdvSemaine)
                .aRappelerMaintenant(leadsChauds.size())
                .objectifMensuelRdv(objectif)
                .mesRdvMois(rdvMois)
                .leadsChauds(leadsChauds)
                .enAttenteRetour(enAttente)
                .build();
    }

    /**
     * Score effectif du lead pour le ranking : utilise hotness_score si calcule
     * (issu de l'analyse IA des conversations), sinon le scoring marketing initial du CSV.
     */
    // package-private pour permettre les tests unitaires
    BigDecimal scoreEffectif(final Lead lead) {
        if (lead.getHotnessScore() != null) return lead.getHotnessScore();
        if (lead.getScoring() != null) return lead.getScoring();
        return BigDecimal.ZERO;
    }

    // package-private pour permettre les tests unitaires
    String calculerPriorite(final Lead lead) {
        final BigDecimal score = scoreEffectif(lead);
        final long heuresDepuisImport = Duration.between(lead.getCreeLe(), LocalDateTime.now()).toHours();
        if (score.compareTo(new BigDecimal("0.8")) >= 0 && heuresDepuisImport > 24) return "URGENT";
        if (score.compareTo(new BigDecimal("0.5")) >= 0) return "MEDIUM";
        return "NORMAL";
    }

    public List<RepRdvEnAttenteDto> listerRdvEnAttente(final Long tenantId, final int joursMin) {
        final LocalDateTime limiteDate = LocalDateTime.now().minusDays(joursMin);
        final List<SmsConversation> convosRdv = smsConversationRepository
                .findByTenantIdOrderByDernierMessageLeDesc(tenantId).stream()
                .filter(c -> c.getStatut() == StatutConversationSmsEnum.RDV_OBTENU)
                .filter(c -> c.getModifieLe() != null && c.getModifieLe().isBefore(limiteDate))
                .toList();

        if (convosRdv.isEmpty()) return List.of();

        // Fix bug-hunter #5 : suppression du N+1 (3 queries par conversation : vente,
        // lead, user). Pre-charge en batch les ids necessaires : 1 query par entite max
        // au lieu de 3 * N. Sur un tenant avec 50 RDV en attente, on passe de 150 a 3 queries.
        final java.util.Set<Long> leadIds = convosRdv.stream()
                .map(SmsConversation::getLeadId).filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        final java.util.Set<Long> userIds = convosRdv.stream()
                .map(SmsConversation::getUserId).filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        final Map<Long, Lead> leadsById = new HashMap<>();
        leadRepository.findAllById(leadIds).forEach(l -> leadsById.put(l.getId(), l));
        final Map<Long, User> usersById = new HashMap<>();
        userRepository.findAllById(userIds).forEach(u -> usersById.put(u.getId(), u));
        final java.util.Set<Long> leadsAvecVente = venteConfirmeeRepository
                .findByLeadIdIn(leadIds).stream()
                .map(v -> v.getLeadId())
                .collect(java.util.stream.Collectors.toSet());

        return convosRdv.stream()
                .filter(c -> !leadsAvecVente.contains(c.getLeadId()))
                .map(c -> {
                    final Lead lead = leadsById.get(c.getLeadId());
                    final User vendeur = c.getUserId() != null ? usersById.get(c.getUserId()) : null;
                    if (lead == null) return null;
                    final long jours = Duration.between(
                            c.getModifieLe() != null ? c.getModifieLe() : c.getCreeLe(),
                            LocalDateTime.now()).toDays();
                    return RepRdvEnAttenteDto.builder()
                            .leadId(lead.getId())
                            .conversationId(c.getConversationId())
                            .nomProspect(lead.getNom())
                            .marque(lead.getMarque())
                            .modele(lead.getModele())
                            .joursDepuisRdv((int) jours)
                            .nomVendeur(vendeur != null ? vendeur.getPrenom() + " " + vendeur.getNom() : "—")
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private List<RepStatJourDto> construireStatsParJour(final Long tenantId, final LocalDateTime debutMois,
                                                         final LocalDate debutDate, final LocalDate finDate) {
        final List<Object[]> leadsParJour = leadRepository.countParJourDepuis(tenantId, debutMois);
        final List<Object[]> convosParJour = smsConversationRepository.statsParJourDepuis(tenantId, debutMois);
        final List<Object[]> ventesParJour = venteConfirmeeRepository.statsParJour(tenantId, debutDate, finDate);

        final Map<LocalDate, RepStatJourDto.RepStatJourDtoBuilder> builders = new HashMap<>();

        for (final Object[] row : leadsParJour) {
            final LocalDate jour = toLocalDate(row[0]);
            builders.computeIfAbsent(jour, j -> RepStatJourDto.builder().jour(j)
                    .leadsImportes(0).conversationsLancees(0).rdvObtenus(0).ventesConfirmees(0))
                    .leadsImportes(((Number) row[1]).intValue());
        }

        for (final Object[] row : convosParJour) {
            final LocalDate jour = toLocalDate(row[0]);
            builders.computeIfAbsent(jour, j -> RepStatJourDto.builder().jour(j)
                    .leadsImportes(0).conversationsLancees(0).rdvObtenus(0).ventesConfirmees(0))
                    .conversationsLancees(((Number) row[1]).intValue())
                    .rdvObtenus(((Number) row[2]).intValue());
        }

        for (final Object[] row : ventesParJour) {
            final LocalDate jour = toLocalDate(row[0]);
            builders.computeIfAbsent(jour, j -> RepStatJourDto.builder().jour(j)
                    .leadsImportes(0).conversationsLancees(0).rdvObtenus(0).ventesConfirmees(0))
                    .ventesConfirmees(((Number) row[1]).intValue());
        }

        final List<RepStatJourDto> result = new ArrayList<>();
        for (final RepStatJourDto.RepStatJourDtoBuilder builder : builders.values()) {
            result.add(builder.build());
        }
        result.sort((a, b) -> a.getJour().compareTo(b.getJour()));
        return result;
    }

    private LocalDate toLocalDate(final Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }
}
