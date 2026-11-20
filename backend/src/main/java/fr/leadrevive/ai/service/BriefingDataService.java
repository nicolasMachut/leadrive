package fr.leadrevive.ai.service;

import fr.leadrevive.ai.entity.TenantAiConfig;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.lead.repository.HistoriqueStatutRepository;
import fr.leadrevive.lead.repository.VenteConfirmeeRepository;
import fr.leadrevive.outreach.entity.CanalConversationEnum;
import fr.leadrevive.outreach.entity.DirectionSmsEnum;
import fr.leadrevive.outreach.entity.ModePilotageEnum;
import fr.leadrevive.outreach.entity.SmsConversation;
import fr.leadrevive.outreach.entity.StatutConversationSmsEnum;
import fr.leadrevive.outreach.repository.SmsConversationRepository;
import fr.leadrevive.outreach.repository.SmsMessageRepository;
import fr.leadrevive.user.entity.User;
import fr.leadrevive.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BriefingDataService {

    private final LeadRepository leadRepository;
    private final HistoriqueStatutRepository historiqueStatutRepository;
    private final SmsConversationRepository smsConversationRepository;
    private final SmsMessageRepository smsMessageRepository;
    private final VenteConfirmeeRepository venteConfirmeeRepository;
    private final UserRepository userRepository;
    private final TenantAiConfigService tenantAiConfigService;

    @Transactional(readOnly = true)
    public BriefingData collecterDonnees(final Long tenantId, final Long userId) {
        log.info("Collecte donnees briefing - tenantId: {}, userId: {}", tenantId, userId);

        final LocalDateTime hier = LocalDateTime.now().minusHours(24);
        final LocalDate debutSemaine = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        final LocalDateTime debutSemaineDateTime = debutSemaine.atStartOfDay();
        final LocalDate finSemaine = LocalDate.now();

        final TenantAiConfig config = tenantAiConfigService.getConfig(tenantId);
        final String nomConcession = config != null ? config.getNomConcession() : "la concession";

        // Get all conversations for scope (tenant or user)
        final List<SmsConversation> toutesConvos = userId != null
                ? smsConversationRepository.findByTenantIdAndUserIdOrderByDernierMessageLeDesc(tenantId, userId)
                : smsConversationRepository.findByTenantIdOrderByDernierMessageLeDesc(tenantId);

        // Filter WhatsApp only for stats
        final List<SmsConversation> whatsAppConvos = toutesConvos.stream()
                .filter(c -> c.getCanal() == CanalConversationEnum.WHATSAPP)
                .toList();

        // HIER
        final long convosHier = whatsAppConvos.stream().filter(c -> c.getCreeLe().isAfter(hier)).count();
        // RDV pris FACTURABLES (définition canonique : événement immuable IA→RDV_PRIS,
        // cf. CLAUDE.md). Scope : vendeur si userId fourni, sinon tenant entier.
        final long rdvHier = userId != null
                ? historiqueStatutRepository.countRdvPrisIaSinceForVendeur(tenantId, userId, hier)
                : historiqueStatutRepository.countRdvPrisIaSince(tenantId, hier);
        final long leadsHier = leadRepository.countByTenantIdDepuis(tenantId, hier);
        final long ventesHier = venteConfirmeeRepository.countByTenantIdAndPeriode(tenantId, LocalDate.now().minusDays(1), LocalDate.now());

        // Messages hier
        final List<Object[]> msgDirectionHier = smsMessageRepository.countByDirectionDepuis(tenantId, hier);
        long msgRecusHier = 0;
        for (final Object[] row : msgDirectionHier) {
            if (row[0] == DirectionSmsEnum.ENTRANT) {
                msgRecusHier = ((Number) row[1]).longValue();
            }
        }

        // Messages non repondus (conversations en mode HUMAIN avec dernier message ENTRANT)
        final long msgNonRepondus = whatsAppConvos.stream()
                .filter(c -> c.getStatut() == StatutConversationSmsEnum.EN_COURS)
                .filter(c -> c.getModePilotage() == ModePilotageEnum.HUMAIN)
                .count();

        // SEMAINE
        final long convosSemaine = whatsAppConvos.stream().filter(c -> c.getCreeLe().isAfter(debutSemaineDateTime)).count();
        final long rdvSemaine = userId != null
                ? historiqueStatutRepository.countRdvPrisIaSinceForVendeur(tenantId, userId, debutSemaineDateTime)
                : historiqueStatutRepository.countRdvPrisIaSince(tenantId, debutSemaineDateTime);
        final int nombreLeadsSemaine = Long.valueOf(leadRepository.countByTenantIdDepuis(tenantId, debutSemaineDateTime)).intValue();
        final Long ventesSemaine = venteConfirmeeRepository.countByTenantIdAndPeriode(tenantId, debutSemaine, finSemaine);
        final BigDecimal caSemaine = venteConfirmeeRepository.sumMontantByTenantIdAndPeriode(tenantId, debutSemaine, finSemaine);
        final Long leadsNonContactes = leadRepository.countNonContactesDepuis(tenantId, debutSemaineDateTime);

        // TENDANCES
        final List<Object[]> parMarque = leadRepository.countByTenantIdGroupByMarque(tenantId);
        String marqueTop = "N/A";
        int nombreLeadsMarqueTop = 0;
        for (final Object[] row : parMarque) {
            final int count = ((Number) row[1]).intValue();
            if (count > nombreLeadsMarqueTop && row[0] != null) {
                marqueTop = (String) row[0];
                nombreLeadsMarqueTop = count;
            }
        }

        final List<Object[]> parTerritoire = leadRepository.countByTenantIdGroupByTerritoire(tenantId);
        String territoireTop = "N/A";
        int maxTerritoire = 0;
        for (final Object[] row : parTerritoire) {
            final int count = ((Number) row[1]).intValue();
            if (count > maxTerritoire && row[0] != null) {
                territoireTop = (String) row[0];
                maxTerritoire = count;
            }
        }

        final double tauxReponse = convosSemaine > 0
                ? whatsAppConvos.stream().filter(c -> c.getNombreMessages() != null && c.getNombreMessages() > 1 && c.getCreeLe().isAfter(debutSemaineDateTime)).count() * 100.0 / convosSemaine
                : 0;
        final double tauxConversion = convosSemaine > 0 ? rdvSemaine * 100.0 / convosSemaine : 0;

        // TOP VENDEURS
        final List<VendeurPerf> topVendeurs = new ArrayList<>();
        if (userId == null) { // Only for global briefing
            // RDV par vendeur = définition canonique (événement immuable IA→RDV_PRIS,
            // attribué au propriétaire actuel du lead). Cf. CLAUDE.md.
            final Map<Long, Long> rdvParVendeur = historiqueStatutRepository
                    .countRdvPrisIaSinceByVendeur(tenantId, debutSemaineDateTime).stream()
                    .collect(Collectors.toMap(
                            row -> ((Number) row[0]).longValue(),
                            row -> ((Number) row[1]).longValue()));
            final Map<Long, Long> convosParVendeur = whatsAppConvos.stream()
                    .filter(c -> c.getCreeLe().isAfter(debutSemaineDateTime))
                    .collect(Collectors.groupingBy(SmsConversation::getUserId, Collectors.counting()));

            final List<User> users = userRepository.findByTenantId(tenantId);
            final Map<Long, User> usersMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));

            rdvParVendeur.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .limit(3)
                    .forEach(e -> {
                        final User u = usersMap.get(e.getKey());
                        if (u != null) {
                            topVendeurs.add(new VendeurPerf(u.getPrenom(), e.getValue().intValue(),
                                    convosParVendeur.getOrDefault(e.getKey(), 0L).intValue()));
                        }
                    });
        }

        // PROSPECTS PRIORITAIRES (top 5)
        final List<ProspectPrioritaire> prioritaires = new ArrayList<>();
        // 1. Conversations HUMAIN avec message entrant recent
        whatsAppConvos.stream()
                .filter(c -> c.getStatut() == StatutConversationSmsEnum.EN_COURS && c.getModePilotage() == ModePilotageEnum.HUMAIN)
                .limit(2)
                .forEach(c -> {
                    final Lead lead = leadRepository.findById(c.getLeadId()).orElse(null);
                    if (lead != null) {
                        prioritaires.add(new ProspectPrioritaire(lead.getNom(), lead.getMarque(), lead.getModele(),
                                lead.getScoring(), "en attente de reponse vendeur"));
                    }
                });

        // 2. High scoring leads not contacted (surface operationnelle : archives exclus)
        if (prioritaires.size() < 5) {
            leadRepository.findByTenantIdAndArchiveFalse(tenantId).stream()
                    .filter(l -> l.getScoring() != null && l.getScoring().compareTo(new BigDecimal("0.7")) > 0)
                    .filter(l -> whatsAppConvos.stream().noneMatch(c -> c.getLeadId().equals(l.getId())))
                    .limit(5L - prioritaires.size())
                    .forEach(l -> prioritaires.add(new ProspectPrioritaire(l.getNom(), l.getMarque(), l.getModele(),
                            l.getScoring(), "scoring eleve, pas encore contacte")));
        }

        // User info
        String prenomManager = "Manager";
        if (userId != null) {
            final User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                prenomManager = user.getPrenom();
            }
        } else {
            final List<User> admins = userRepository.findByTenantId(tenantId).stream()
                    .filter(u -> u.getRole() == fr.leadrevive.user.entity.RoleEnum.MANAGER)
                    .toList();
            if (!admins.isEmpty()) {
                prenomManager = admins.getFirst().getPrenom();
            }
        }

        return new BriefingData(
                (int) leadsHier, (int) convosHier, (int) rdvHier, (int) ventesHier, (int) msgRecusHier, (int) msgNonRepondus,
                (int) convosSemaine, (int) rdvSemaine, ventesSemaine != null ? ventesSemaine.intValue() : 0,
                caSemaine != null ? caSemaine : BigDecimal.ZERO,
                leadsNonContactes != null ? leadsNonContactes.intValue() : 0, nombreLeadsSemaine,
                marqueTop, nombreLeadsMarqueTop, territoireTop,
                Math.round(tauxReponse * 10.0) / 10.0, Math.round(tauxConversion * 10.0) / 10.0,
                topVendeurs, prioritaires, nomConcession, prenomManager
        );
    }

    public record BriefingData(
            int leadsImportesHier, int conversationsLanceesHier, int rdvObtenuSHier, int ventesConfirmeesHier,
            int messagesRecusHier, int messagesNonRepondus,
            int totalConversationsSemaine, int totalRdvSemaine, int totalVentesSemaine, BigDecimal caGenereSemaine,
            int leadsNonContactes, int nombreLeadsSemaine,
            String marqueTopSemaine, int nombreLeadsMarqueTop, String territoireTopSemaine,
            double tauxReponseWhatsApp, double tauxConversionRdv,
            List<VendeurPerf> topVendeurs, List<ProspectPrioritaire> prospectsPrioritaires,
            String nomConcession, String prenomManager
    ) {}

    public record ProspectPrioritaire(String nom, String marque, String modele, BigDecimal scoring, String raison) {}

    public record VendeurPerf(String prenom, int rdvObtenus, int conversationsLancees) {}
}
