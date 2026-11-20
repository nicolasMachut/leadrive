package fr.leadrevive.lead.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepDashboardDto {

    private Integer totalLeads;
    private Integer leadsNonContactes;
    private Integer pourcentageNonContactes;

    private Integer conversationsLancees;
    private Integer rdvObtenus;
    private Integer tauxConversionRdv;
    private Integer ventesConfirmees;
    private BigDecimal caGenere;

    private BigDecimal coutIa;
    private BigDecimal coutWhatsApp;
    private BigDecimal coutTotal;

    private Integer fichesGenerees;
    private Integer messagesWhatsAppEnvoyes;
    private Integer messagesWhatsAppRecus;

    private List<RepStatJourDto> statsParJour;
}
