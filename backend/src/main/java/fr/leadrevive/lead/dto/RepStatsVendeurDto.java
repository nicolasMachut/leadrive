package fr.leadrevive.lead.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepStatsVendeurDto {

    private Long userId;
    private String nomVendeur;
    private Integer conversationsLancees;
    private Integer rdvObtenus;
    private Integer ventesConfirmees;
    private BigDecimal caGenere;
    private Integer fichesGenerees;
}
