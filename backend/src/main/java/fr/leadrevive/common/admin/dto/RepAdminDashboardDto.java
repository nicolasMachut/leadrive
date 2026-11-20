package fr.leadrevive.common.admin.dto;

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
public class RepAdminDashboardDto {

    private long concessionsActives;
    private long utilisateursActifs30j;

    // Leads
    private long leadsImportesMois;

    // Conversations & messages
    private long conversationsLanceesMois;
    private long messagesEnvoyesMois;
    private long messagesRecusMois;
    private long rdvObtenusDepuis30j;
    private long messagesEnEchecMois;

    // IA
    private long generationsIaMois;
    private BigDecimal budgetIaMois;

    private List<String> alertes;
}
