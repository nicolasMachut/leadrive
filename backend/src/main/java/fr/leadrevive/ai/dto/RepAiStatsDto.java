package fr.leadrevive.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepAiStatsDto {

    private BigDecimal coutTotalMois;
    private Long nombreAppelsMois;
    private Map<String, RepAiTypeStatsDto> parType;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RepAiTypeStatsDto {
        private Long nombreAppels;
        private BigDecimal coutTotal;
    }
}
