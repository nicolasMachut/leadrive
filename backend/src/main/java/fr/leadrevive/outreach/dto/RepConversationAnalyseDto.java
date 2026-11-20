package fr.leadrevive.outreach.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepConversationAnalyseDto {

    private Long id;
    private String conversationId;
    private String intention;
    private List<String> signauxAchat;
    private List<String> objections;
    private Boolean besoinHandoff;
    private String raisonHandoff;
    private String resumeVendeur;
    private String tonProspect;
    private String questionEnAttente;
    private Boolean rdvPropose;
    private Boolean rdvConfirme;
    private String rdvCreneau;
    private LocalDateTime rdvDateHeure;
    /**
     * Date du RDV REEL en cours du lead (tache RDV A_FAIRE), source de verite mutable de
     * l'agenda. Prioritaire sur {@link #rdvDateHeure} (snapshot fige extrait par l'IA) pour
     * l'affichage : apres un report, la tache est a jour mais l'extraction reste figee.
     * Null si aucune tache RDV en cours.
     */
    private LocalDateTime rdvTacheDateHeure;
    private String delaiProjet;
    private String montantBudget;
    private String apportPrevu;
    private String modeFinancement;
    private String accordBanque;
    private Boolean repriseConditionneAchat;
    private LocalDateTime creeLe;
}
