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
public class RepStatsMoisDto {

    private String mois;
    private Integer totalLeads;
    private Integer rdvObtenus;
    private Integer ventesConfirmees;
    private BigDecimal caGenere;
    private BigDecimal coutTotal;
}
