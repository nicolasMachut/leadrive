package fr.leadrevive.ai.service;

import fr.leadrevive.ai.config.AiModelsProperties;
import fr.leadrevive.ai.dto.RepAiStatsDto;
import fr.leadrevive.ai.repository.AiLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiCoutService {

    // Fallback si un modele n'a pas de tarif configure dans le YAML (equivalent
    // Haiku, prudent). La source de verite des tarifs est application.yml
    // (leadrevive.ai.models.tarifs), co-localisee avec le mapping des modeles.
    private static final BigDecimal PRIX_INPUT_DEFAUT = new BigDecimal("0.000001");
    private static final BigDecimal PRIX_OUTPUT_DEFAUT = new BigDecimal("0.000005");

    private final AiLogRepository aiLogRepository;
    private final AiModelsProperties modelsProperties;

    /**
     * Cout estime d'un appel selon le modele, a partir des tarifs configures dans
     * le YAML. Si le modele n'a pas de tarif (ou est null), on retombe sur un
     * tarif par defaut (equivalent Haiku) + un warning, jamais d'exception.
     */
    public BigDecimal calculerCout(final String modele, final Integer tokensInput, final Integer tokensOutput) {
        final AiModelsProperties.TarifConfig tarif = modele != null
                ? modelsProperties.getTarifs().get(modele) : null;
        if (tarif == null && modele != null) {
            log.warn("Tarif IA non configure pour le modele {} - fallback tarif par defaut", modele);
        }
        final BigDecimal prixInput = tarif != null ? tarif.getPrixInput() : PRIX_INPUT_DEFAUT;
        final BigDecimal prixOutput = tarif != null ? tarif.getPrixOutput() : PRIX_OUTPUT_DEFAUT;
        final BigDecimal coutInput = prixInput.multiply(BigDecimal.valueOf(tokensInput != null ? tokensInput : 0));
        final BigDecimal coutOutput = prixOutput.multiply(BigDecimal.valueOf(tokensOutput != null ? tokensOutput : 0));
        return coutInput.add(coutOutput);
    }

    @Transactional(readOnly = true)
    public RepAiStatsDto getStats(final Long tenantId) {
        log.info("Recuperation stats IA - tenantId: {}", tenantId);
        final LocalDateTime debutMois = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);

        final BigDecimal coutTotal = aiLogRepository.coutTotalDepuis(tenantId, debutMois);
        final Long nombreAppels = aiLogRepository.countDepuis(tenantId, debutMois);

        final Map<String, RepAiStatsDto.RepAiTypeStatsDto> parType = new LinkedHashMap<>();
        aiLogRepository.statsParTypeDepuis(tenantId, debutMois).forEach(row ->
                parType.put(row[0].toString(), RepAiStatsDto.RepAiTypeStatsDto.builder()
                        .nombreAppels((Long) row[1])
                        .coutTotal((BigDecimal) row[2])
                        .build())
        );

        log.info("Stats IA recuperees - coutTotal: {}, nombreAppels: {}", coutTotal, nombreAppels);

        return RepAiStatsDto.builder()
                .coutTotalMois(coutTotal)
                .nombreAppelsMois(nombreAppels)
                .parType(parType)
                .build();
    }
}
