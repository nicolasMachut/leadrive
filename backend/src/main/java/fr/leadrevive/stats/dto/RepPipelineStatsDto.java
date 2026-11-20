package fr.leadrevive.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepPipelineStatsDto {

    private String periode;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private ResumeStats resume;
    private List<EntonnoirEtape> entonnoir;
    private List<EvolutionPoint> evolution;
    private List<TempsEtape> tempsParEtape;
    private List<PerformanceVendeur> performanceVendeurs;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResumeStats {
        private int totalLeads;
        private int leadsTermines;
        private double tauxTermine;
        private Double tauxTermineEvolution;
        private double tempsMoyenTermine;
        private Double tempsMoyenTermineEvolution;
        private int velocite;
        private Double velociteEvolution;
        /** Nombre de leads ayant atteint RDV_PRIS sur la période (flux, basé sur
         *  les transitions de l'historique — pas un snapshot du statut actuel).
         *  Sert de numérateur cohérent avec tauxRdv côté frontend. */
        private int rdvPris;
        /** Taux RDV (tauxRdv = rdvPris/total × 100) — exposé pour le frontend
         *  qui le calcule sinon manuellement (rdvCount/totalLeads). */
        private Double tauxRdv;
        /** Évolution du tauxRdv vs période précédente (points de %, signé). */
        private Double tauxRdvEvolution;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EntonnoirEtape {
        private String statut;
        private String label;
        private String couleur;
        private int count;
        private double pourcentage;
        private double tauxPassage;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvolutionPoint {
        private LocalDate date;
        private int nouveaux;
        private int termines;
        private int actifs;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TempsEtape {
        private String statut;
        private String label;
        private String couleur;
        private double tempsMoyenHeures;
        private String tempsMoyenLabel;
        private double tempsMedianHeures;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceVendeur {
        private Long vendeurId;
        private String nom;
        private int leadsAssignes;
        private int leadsTermines;
        private double tauxTermine;
        private double tempsMoyenTermine;
        private int scoreActivite;
    }

}
