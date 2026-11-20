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
public class RepDashboardAdminDto {
    private BigDecimal caGenereMois;
    private BigDecimal caGenereMoisPrecedent;
    private BigDecimal coutTotal;
    private Integer prospectsNonContactes;
    private List<RepStatsMoisDto> evolution6Mois;
}
