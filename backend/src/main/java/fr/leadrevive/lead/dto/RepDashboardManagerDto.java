package fr.leadrevive.lead.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepDashboardManagerDto {
    private Integer vendeursActifs;
    private Integer totalVendeurs;
    private Integer rdvPrisSemaine;
    private Integer rdvSemainePrecedente;
    private Integer rdvEnAttenteConfirmation;
    private List<RepStatsVendeurDto> performanceVendeurs;
    private List<RepRdvEnAttenteDto> rdvsEnAttente;

    /** Champs ajoutés pour aligner avec ce que le DashboardHero frontend consomme :
     *  KPI hero (rdvSemaineEquipe), sous-titre (vendeursActifs / prospectsEnCours),
     *  DataSheet (rdvMoisEquipe + tauxConversionPct), progress bar (objectifMoisEquipe). */
    /** Alias de rdvPrisSemaine — le frontend consomme ce nom. */
    private Integer rdvSemaineEquipe;
    /** Nombre de RDV_OBTENU sur le mois en cours (équipe entière). */
    private Integer rdvMoisEquipe;
    /** Nombre de conversations en cours (EN_COURS) à l'instant t. */
    private Integer prospectsEnCours;
    /** Taux conversion mois en cours : rdvObtenus / conversationsLancées × 100. */
    private Integer tauxConversionPct;
    /** Objectif RDV équipe ce mois (depuis tenant.objectif_mensuel_rdv_equipe).
     *  Null si non configuré → frontend masque la progress bar. */
    private Integer objectifMoisEquipe;
}
