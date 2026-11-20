package fr.leadrevive.lead.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepSuggestionDto {

    private Long idLead;
    private String nom;
    private String telephone;
    private String marque;
    private String modele;
    private String territoire;
    private String source;
    private BigDecimal scoring;
    private BigDecimal hotnessScore;
    private String statut;
    private LocalDateTime creeLe;
    /** Raison pour laquelle le systeme suggere de contacter ce lead. */
    private String raison;
    /** Priorite : HAUTE, MOYENNE, BASSE */
    private String priorite;
}
