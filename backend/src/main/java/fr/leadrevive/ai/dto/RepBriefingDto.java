package fr.leadrevive.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepBriefingDto {

    private Long id;
    private LocalDate dateBriefing;
    private String contenu;
    private Integer tokensPrompt;
    private Integer tokensReponse;
    private BigDecimal coutEstime;
    private LocalDateTime creeLe;
    /**
     * Si non-null : la generation IA a echoue, le contenu est absent et le frontend
     * doit afficher ce message + un bouton "Reessayer" plutot qu'une carte vide.
     */
    private String erreurGeneration;
}
