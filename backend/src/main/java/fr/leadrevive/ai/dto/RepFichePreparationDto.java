package fr.leadrevive.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepFichePreparationDto {

    private Long idAiLog;
    private Long leadId;
    private String fiche;
    private Integer tokensPrompt;
    private Integer tokensReponse;
    private BigDecimal coutEstime;
    private Long dureeMs;
    private String promptUtilise;
    /** true si la fiche vient du cache, false si fraichement generee. */
    private Boolean cached;
    /** true si le cache est perime (donnees ont change depuis la generation). */
    private Boolean stale;
}
