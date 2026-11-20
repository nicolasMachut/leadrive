package fr.leadrevive.lead.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepRdvAVenirDto {

    private Long idLead;
    /** ID de la tache RDV associee (null si pas de tache) */
    private Long tacheId;
    private String nom;
    private String marque;
    private String modele;
    private String vendeurNom;
    /** Créneau formaté pour affichage */
    private String creneau;
    /** Date/heure absolue du RDV (null si l'IA n'a pas pu extraire une date précise) */
    private LocalDateTime rdvDateHeure;
    /** Résumé vendeur de la conversation extrait par l'IA */
    private String resumeVendeur;
}
