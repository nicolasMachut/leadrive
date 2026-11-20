package fr.leadrevive.tache.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepTacheDto {

    private Long id;
    private String titre;
    private String description;
    private String typeTache;
    private LocalDateTime dateEcheance;
    private Integer dureeMinutes;
    private String priorite;
    private String statut;
    private String source;
    private Integer rappelMinutesAvant;
    private LocalDateTime dateTerminee;
    private Long leadId;
    private String leadNom;
    private String leadStatut;
    private Long vendeurId;
    private Boolean enRetard;
    private String tempsRestant;
}
