package fr.leadrevive.lead.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepLeadStatsDto {

    private Map<String, Long> parStatut;
    private Map<String, Long> parTerritoire;
    private Map<String, Long> parMarque;
    private Long total;
}
