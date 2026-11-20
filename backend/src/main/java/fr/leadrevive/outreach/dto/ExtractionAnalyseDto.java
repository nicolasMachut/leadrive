package fr.leadrevive.outreach.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * DTO utilise par Spring AI (BeanOutputConverter) pour parser la reponse JSON du LLM d'extraction.
 * Chaque champ correspond a une cle du JSON renvoye par le prompt whatsapp-extraction.txt.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExtractionAnalyseDto {

    /** RDV | STOP | REFUS | AUTRE */
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

    private String rdvDateHeure;

    private String delaiProjet;

    private String montantBudget;

    private String apportPrevu;

    private String modeFinancement;

    private String accordBanque;

    private Boolean repriseConditionneAchat;

    /** Le prospect a explicitement demande un essai routier. Affiche dans la fiche de preparation. */
    private Boolean demandeEssai;

    /** Texte brut de la reponse a Q0bis (raison du contact / declencheur du projet). null si jamais evoque. */
    private String raisonContact;
}
