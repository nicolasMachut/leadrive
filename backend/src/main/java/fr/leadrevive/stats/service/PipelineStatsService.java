package fr.leadrevive.stats.service;

import fr.leadrevive.lead.entity.HistoriqueStatut;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.entity.LeadStatutEnum;
import fr.leadrevive.lead.repository.HistoriqueStatutRepository;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.user.entity.User;
import fr.leadrevive.user.repository.UserRepository;
import fr.leadrevive.stats.dto.RepPipelineStatsDto;
import fr.leadrevive.stats.dto.RepPipelineStatsDto.EntonnoirEtape;
import fr.leadrevive.stats.dto.RepPipelineStatsDto.EvolutionPoint;
import fr.leadrevive.stats.dto.RepPipelineStatsDto.PerformanceVendeur;
import fr.leadrevive.stats.dto.RepPipelineStatsDto.ResumeStats;
import fr.leadrevive.stats.dto.RepPipelineStatsDto.TempsEtape;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PipelineStatsService {

    private final LeadRepository leadRepository;
    private final HistoriqueStatutRepository historiqueStatutRepository;
    private final UserRepository userRepository;

    /** Source d'une transition de statut comptée comme RDV pris facturable.
     *  Seules les transitions posées par l'IA (RDV confirmé + daté par le prospect)
     *  comptent ; les passages manuels (DRAG_DROP/SELECTBOX) sont exclus pour rester
     *  non-manipulables. Cf. ConversationAnalyseService.tracerChangementStatut (source="IA"). */
    private static final String SOURCE_RDV_FACTURABLE = "IA";

    private static final List<StatutConfig> PIPELINE = List.of(
            LeadStatutEnum.NOUVEAU, LeadStatutEnum.CONTACTE, LeadStatutEnum.EN_COURS,
            LeadStatutEnum.RDV_PRIS, LeadStatutEnum.TERMINE
    ).stream().map(s -> {
        final fr.leadrevive.lead.entity.LeadStatutConfig.Config c = fr.leadrevive.lead.entity.LeadStatutConfig.CONFIGS.get(s);
        return new StatutConfig(s, c.label(), c.couleur());
    }).toList();

    record StatutConfig(LeadStatutEnum statut, String label, String couleur) {}

    @Transactional(readOnly = true)
    public RepPipelineStatsDto getStats(final Long tenantId, final Long vendeurId, final String periode) {
        log.info("Calcul stats pipeline - tenantId: {}, vendeurId: {}, periode: {}", tenantId, vendeurId, periode);

        final int jours = switch (periode) {
            case "7j" -> 7;
            case "90j" -> 90;
            case "12m" -> 365;
            default -> 30;
        };

        final LocalDate dateFin = LocalDate.now();
        final LocalDate dateDebut = dateFin.minusDays(jours);
        final LocalDateTime debut = dateDebut.atStartOfDay();
        final LocalDateTime fin = dateFin.plusDays(1).atStartOfDay();

        // Période précédente pour les évolutions
        final LocalDateTime debutPrecedent = dateDebut.minusDays(jours).atStartOfDay();

        // Requete ciblee : si vendeurId est fourni, on charge UNIQUEMENT ses leads
        // (au lieu de charger 50k leads tenant puis filtrer en Java = OOM possible).
        // Exclure les leads archives : /stats reflete le pipeline ACTIF (coherent avec kanban
        // et listes qui excluent deja les archives). Le RDV facturable est compte via
        // historique_statut (evenement immuable), donc non affecte par l'archivage.
        final List<Lead> tousLeads = vendeurId != null
                ? leadRepository.findByTenantIdAndVendeurIdAndArchiveFalse(tenantId, vendeurId)
                : leadRepository.findByTenantIdAndArchiveFalse(tenantId);

        final List<Lead> leadsPeriode = tousLeads.stream()
                .filter(l -> l.getCreeLe() != null && !l.getCreeLe().isBefore(debut) && l.getCreeLe().isBefore(fin))
                .toList();

        final List<HistoriqueStatut> toutHistorique = historiqueStatutRepository
                .findByTenantIdAndCreeLeAfterAndCreeLeBeforeOrderByCreeLeDesc(tenantId, debutPrecedent, fin);

        if (vendeurId != null) {
            final List<Long> leadIds = tousLeads.stream().map(Lead::getId).toList();
            // filtrer l'historique sur les leads du vendeur
            final var filteredHistorique = toutHistorique.stream()
                    .filter(h -> leadIds.contains(h.getLeadId()))
                    .toList();
            return buildStats(periode, dateDebut, dateFin, leadsPeriode, tousLeads, filteredHistorique, debut, fin, debutPrecedent, jours, vendeurId == null);
        }

        return buildStats(periode, dateDebut, dateFin, leadsPeriode, tousLeads, toutHistorique, debut, fin, debutPrecedent, jours, true);
    }

    private RepPipelineStatsDto buildStats(final String periode, final LocalDate dateDebut, final LocalDate dateFin,
                                            final List<Lead> leadsPeriode, final List<Lead> tousLeads,
                                            final List<HistoriqueStatut> historique,
                                            final LocalDateTime debut, final LocalDateTime fin,
                                            final LocalDateTime debutPrecedent, final int jours,
                                            final boolean inclurePerformanceVendeurs) {

        final List<HistoriqueStatut> histoPeriode = historique.stream()
                .filter(h -> h.getCreeLe() != null && !h.getCreeLe().isBefore(debut) && h.getCreeLe().isBefore(fin))
                .toList();
        final List<HistoriqueStatut> histoPrecedent = historique.stream()
                .filter(h -> h.getCreeLe() != null && !h.getCreeLe().isBefore(debutPrecedent) && h.getCreeLe().isBefore(debut))
                .toList();

        return RepPipelineStatsDto.builder()
                .periode(periode)
                .dateDebut(dateDebut)
                .dateFin(dateFin)
                .resume(calculerResume(leadsPeriode, tousLeads, histoPeriode, histoPrecedent, debutPrecedent, debut, jours))
                .entonnoir(calculerEntonnoir(tousLeads))
                .evolution(calculerEvolution(leadsPeriode, histoPeriode, tousLeads, dateDebut, dateFin, jours))
                .tempsParEtape(calculerTempsParEtape(historique))
                .performanceVendeurs(inclurePerformanceVendeurs ? calculerPerformanceVendeurs(tousLeads, histoPeriode) : null)
                .build();
    }

    // ── Résumé ─────────────────────────────────────────────────────────────

    private ResumeStats calculerResume(final List<Lead> leadsPeriode,
                                        final List<Lead> tousLeads,
                                        final List<HistoriqueStatut> histoPeriode,
                                        final List<HistoriqueStatut> histoPrecedent,
                                        final LocalDateTime debutPrecedent,
                                        final LocalDateTime debut,
                                        final int jours) {
        final int total = leadsPeriode.size();
        // COMPTES (volumes, affiches en hero) = evenements de la periode, tous leads confondus
        // (y compris les leads crees AVANT la periode qui aboutissent pendant). Base sur
        // l'evenement immuable de historique_statut. Le "RDV pris" est le chiffre FACTURABLE
        // (transition RDV_PRIS de source "IA" uniquement : l'IA ne pose ce statut que sur un RDV
        // confirme+date par le prospect ; les passages manuels DRAG_DROP/SELECTBOX sont exclus).
        final int termines = (int) histoPeriode.stream()
                .filter(h -> h.getStatutSuivant() == LeadStatutEnum.TERMINE)
                .map(HistoriqueStatut::getLeadId).distinct().count();
        final int rdvPris = (int) histoPeriode.stream()
                .filter(h -> h.getStatutSuivant() == LeadStatutEnum.RDV_PRIS && SOURCE_RDV_FACTURABLE.equals(h.getSource()))
                .map(HistoriqueStatut::getLeadId).distinct().count();

        // TAUX = taux de CONVERSION de la cohorte des leads CREES sur la periode. Le numerateur
        // est restreint aux leads crees sur la periode (donc sous-ensemble du denominateur `total`)
        // => borne mathematiquement a 100%. Sans ca, un compte d'evenements (rdvPris/termines, qui
        // inclut des leads crees avant) divise par les leads crees sur la periode pouvait donner
        // un taux > 100% (bug observe : taux RDV a 200% = 2 leads convertis / 1 lead cree).
        // Coherent avec le tooltip UI ("part de vos leads de la periode ayant obtenu un RDV").
        final java.util.Set<Long> idsLeadsPeriode = leadsPeriode.stream()
                .map(Lead::getId).collect(java.util.stream.Collectors.toSet());
        final int terminesCohorte = (int) histoPeriode.stream()
                .filter(h -> h.getStatutSuivant() == LeadStatutEnum.TERMINE)
                .map(HistoriqueStatut::getLeadId).filter(idsLeadsPeriode::contains).distinct().count();
        final int rdvPrisCohorte = (int) histoPeriode.stream()
                .filter(h -> h.getStatutSuivant() == LeadStatutEnum.RDV_PRIS && SOURCE_RDV_FACTURABLE.equals(h.getSource()))
                .map(HistoriqueStatut::getLeadId).filter(idsLeadsPeriode::contains).distinct().count();
        final double tauxTermine = total > 0 ? (terminesCohorte * 100.0 / total) : 0;
        final double tauxRdv = total > 0 ? (rdvPrisCohorte * 100.0 / total) : 0;
        final int velocite = histoPeriode.size();

        // Évolutions vs période précédente : meme logique de cohorte pour rester coherent et borne.
        final java.util.Set<Long> idsLeadsPrecedent = tousLeads.stream()
                .filter(l -> l.getCreeLe() != null && !l.getCreeLe().isBefore(debutPrecedent) && l.getCreeLe().isBefore(debut))
                .map(Lead::getId).collect(java.util.stream.Collectors.toSet());
        final int totalPrecedent = idsLeadsPrecedent.size();
        final int terminesPrecedentCohorte = (int) histoPrecedent.stream()
                .filter(h -> h.getStatutSuivant() == LeadStatutEnum.TERMINE)
                .map(HistoriqueStatut::getLeadId).filter(idsLeadsPrecedent::contains).distinct().count();
        final int rdvPrisPrecedentCohorte = (int) histoPrecedent.stream()
                .filter(h -> h.getStatutSuivant() == LeadStatutEnum.RDV_PRIS && SOURCE_RDV_FACTURABLE.equals(h.getSource()))
                .map(HistoriqueStatut::getLeadId).filter(idsLeadsPrecedent::contains).distinct().count();
        final int velocitePrecedent = histoPrecedent.size();

        final Double tauxTermineEvol = totalPrecedent > 0
                ? tauxTermine - (terminesPrecedentCohorte * 100.0 / totalPrecedent) : null;
        final Double tauxRdvEvol = totalPrecedent > 0
                ? tauxRdv - (rdvPrisPrecedentCohorte * 100.0 / totalPrecedent) : null;
        final Double velociteEvol = velocitePrecedent > 0
                ? ((velocite - velocitePrecedent) * 100.0 / velocitePrecedent) : null;

        return ResumeStats.builder()
                .totalLeads(total)
                .leadsTermines(termines)
                .tauxTermine(Math.round(tauxTermine * 10) / 10.0)
                .tauxTermineEvolution(tauxTermineEvol != null ? Math.round(tauxTermineEvol * 10) / 10.0 : null)
                .tauxRdv(Math.round(tauxRdv * 10) / 10.0)
                .tauxRdvEvolution(tauxRdvEvol != null ? Math.round(tauxRdvEvol * 10) / 10.0 : null)
                .tempsMoyenTermine(0) // simplifié pour MVP
                .tempsMoyenTermineEvolution(null)
                .velocite(velocite)
                .velociteEvolution(velociteEvol != null ? Math.round(velociteEvol * 10) / 10.0 : null)
                .rdvPris(rdvPris)
                .build();
    }

    // ── Entonnoir ──────────────────────────────────────────────────────────

    private List<EntonnoirEtape> calculerEntonnoir(final List<Lead> tousLeads) {
        final Map<LeadStatutEnum, Long> countParStatut = new LinkedHashMap<>();
        for (final StatutConfig config : PIPELINE) {
            final long count = tousLeads.stream()
                    .filter(l -> l.getStatut() == config.statut())
                    .count();
            countParStatut.put(config.statut(), count);
        }

        final long total = tousLeads.size();
        final long baseNouveau = total > 0 ? total : 1L;
        final List<EntonnoirEtape> etapes = new ArrayList<>();

        for (int i = 0; i < PIPELINE.size(); i++) {
            final StatutConfig config = PIPELINE.get(i);
            final long count = countParStatut.getOrDefault(config.statut(), 0L);
            final double pourcentage = baseNouveau > 0 ? (count * 100.0 / baseNouveau) : 0;
            final long countSuivant = i + 1 < PIPELINE.size()
                    ? countParStatut.getOrDefault(PIPELINE.get(i + 1).statut(), 0L) : 0;
            final double tauxPassage = count > 0 ? (countSuivant * 100.0 / count) : 0;

            etapes.add(EntonnoirEtape.builder()
                    .statut(config.statut().name())
                    .label(config.label())
                    .couleur(config.couleur())
                    .count((int) count)
                    .pourcentage(Math.round(pourcentage * 10) / 10.0)
                    .tauxPassage(Math.round(tauxPassage * 10) / 10.0)
                    .build());
        }
        return etapes;
    }

    // ── Évolution temporelle ───────────────────────────────────────────────

    private List<EvolutionPoint> calculerEvolution(final List<Lead> leadsPeriode,
                                                    final List<HistoriqueStatut> histoPeriode,
                                                    final List<Lead> tousLeads,
                                                    final LocalDate dateDebut, final LocalDate dateFin,
                                                    final int jours) {
        final List<EvolutionPoint> points = new ArrayList<>();
        final int step = jours <= 30 ? 1 : jours <= 90 ? 7 : 30;

        LocalDate cursor = dateDebut;
        while (!cursor.isAfter(dateFin)) {
            final LocalDate pointDate = cursor;
            final LocalDate pointFin = cursor.plusDays(step);

            final int nouveaux = (int) leadsPeriode.stream()
                    .filter(l -> l.getCreeLe() != null
                            && !l.getCreeLe().toLocalDate().isBefore(pointDate)
                            && l.getCreeLe().toLocalDate().isBefore(pointFin))
                    .count();

            final int termines = (int) histoPeriode.stream()
                    .filter(h -> h.getStatutSuivant() == LeadStatutEnum.TERMINE
                            && h.getCreeLe() != null
                            && !h.getCreeLe().toLocalDate().isBefore(pointDate)
                            && h.getCreeLe().toLocalDate().isBefore(pointFin))
                    .count();

            final int actifs = (int) tousLeads.stream()
                    .filter(l -> l.getStatut() != LeadStatutEnum.TERMINE
                            && l.getCreeLe() != null
                            && !l.getCreeLe().toLocalDate().isAfter(pointDate))
                    .count();

            points.add(EvolutionPoint.builder()
                    .date(pointDate)
                    .nouveaux(nouveaux)
                    .termines(termines)
                    .actifs(actifs)
                    .build());

            cursor = pointFin;
        }
        return points;
    }

    // ── Temps par étape ────────────────────────────────────────────────────

    private List<TempsEtape> calculerTempsParEtape(final List<HistoriqueStatut> historique) {
        // Grouper par leadId pour calculer la durée dans chaque statut
        final Map<Long, List<HistoriqueStatut>> parLead = historique.stream()
                .collect(Collectors.groupingBy(HistoriqueStatut::getLeadId));

        final Map<LeadStatutEnum, List<Double>> heuresParStatut = new LinkedHashMap<>();
        for (final StatutConfig config : PIPELINE) {
            if (config.statut() == LeadStatutEnum.TERMINE) continue;
            heuresParStatut.put(config.statut(), new ArrayList<>());
        }

        for (final var entry : parLead.entrySet()) {
            final List<HistoriqueStatut> transitions = entry.getValue().stream()
                    .sorted(Comparator.comparing(HistoriqueStatut::getCreeLe))
                    .toList();

            for (int i = 0; i < transitions.size(); i++) {
                final HistoriqueStatut h = transitions.get(i);
                if (heuresParStatut.containsKey(h.getStatutSuivant()) && i + 1 < transitions.size()) {
                    final Duration duree = Duration.between(h.getCreeLe(), transitions.get(i + 1).getCreeLe());
                    heuresParStatut.get(h.getStatutSuivant()).add(duree.toMinutes() / 60.0);
                }
            }
        }

        final List<TempsEtape> result = new ArrayList<>();
        for (final StatutConfig config : PIPELINE) {
            final List<Double> heures = heuresParStatut.get(config.statut());
            if (heures == null) continue;
            final double moyenne = heures.isEmpty() ? 0 : heures.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            final double mediane = heures.isEmpty() ? 0 : calculerMediane(heures);

            result.add(TempsEtape.builder()
                    .statut(config.statut().name())
                    .label(config.label())
                    .couleur(config.couleur())
                    .tempsMoyenHeures(Math.round(moyenne * 10) / 10.0)
                    .tempsMoyenLabel(formaterDuree(moyenne))
                    .tempsMedianHeures(Math.round(mediane * 10) / 10.0)
                    .build());
        }
        return result;
    }

    // ── Performance vendeurs ───────────────────────────────────────────────

    private List<PerformanceVendeur> calculerPerformanceVendeurs(final List<Lead> tousLeads,
                                                                  final List<HistoriqueStatut> histoPeriode) {
        // Grouper par vendeurId — les leads non assignés (vendeurId=null) sont groupés sous 0L
        final Map<Long, List<Lead>> parVendeur = tousLeads.stream()
                .collect(Collectors.groupingBy(l -> l.getVendeurId() != null ? l.getVendeurId() : 0L));

        // Charger les noms des utilisateurs en UNE seule requete batch (WHERE id IN).
        // Avant : un findById par vendeur dans la boucle (N+1, amplifie par les
        // refetch realtime). Le filtre Hibernate tenant s'applique comme pour findById.
        final List<Long> vendeurIds = parVendeur.keySet().stream()
                .filter(vid -> vid != 0L)
                .toList();
        final Map<Long, String> nomsVendeurs = new LinkedHashMap<>();
        if (!vendeurIds.isEmpty()) {
            userRepository.findAllById(vendeurIds).forEach(u ->
                    nomsVendeurs.put(u.getId(), u.getPrenom() + " " + u.getNom()));
        }

        final List<PerformanceVendeur> result = new ArrayList<>();
        for (final var entry : parVendeur.entrySet()) {
            final Long vid = entry.getKey();
            final List<Lead> leadsVendeur = entry.getValue();
            final List<Long> leadIds = leadsVendeur.stream().map(Lead::getId).toList();

            final int assignes = leadsVendeur.size();
            final int termines = (int) leadsVendeur.stream()
                    .filter(l -> l.getStatut() == LeadStatutEnum.TERMINE).count();
            final double taux = assignes > 0 ? (termines * 100.0 / assignes) : 0;
            final int activite = (int) histoPeriode.stream()
                    .filter(h -> leadIds.contains(h.getLeadId())
                            && ("DRAG_DROP".equals(h.getSource()) || "SELECTBOX".equals(h.getSource())))
                    .count();

            result.add(PerformanceVendeur.builder()
                    .vendeurId(vid)
                    .nom(vid == 0L ? "Non assignés" : nomsVendeurs.getOrDefault(vid, "Utilisateur #" + vid))
                    .leadsAssignes(assignes)
                    .leadsTermines(termines)
                    .tauxTermine(Math.round(taux * 10) / 10.0)
                    .tempsMoyenTermine(0)
                    .scoreActivite(activite)
                    .build());
        }

        // Tie-breaker stable (vendeurId) pour eviter un ordre non deterministe entre
        // ex-aequo (frequent : plusieurs vendeurs a 0%) qui ferait "sauter" les lignes
        // a chaque refetch realtime.
        result.sort(Comparator.comparingDouble(PerformanceVendeur::getTauxTermine).reversed()
                .thenComparing(PerformanceVendeur::getVendeurId));
        return result;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private double calculerMediane(final List<Double> valeurs) {
        final List<Double> triees = valeurs.stream().sorted().toList();
        final int taille = triees.size();
        if (taille % 2 == 0) {
            return (triees.get(taille / 2 - 1) + triees.get(taille / 2)) / 2.0;
        }
        return triees.get(taille / 2);
    }

    private String formaterDuree(final double heures) {
        if (heures < 1) return Math.round(heures * 60) + "min";
        if (heures < 24) return Math.round(heures) + "h";
        return Math.round(heures / 24.0 * 10) / 10.0 + "j";
    }
}
