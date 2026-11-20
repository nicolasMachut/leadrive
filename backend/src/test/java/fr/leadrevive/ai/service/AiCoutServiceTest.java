package fr.leadrevive.ai.service;

import fr.leadrevive.ai.config.AiModelsProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests du calcul de cout par modele (tarifs charges depuis la config + fallback).
 */
class AiCoutServiceTest {

    // aiLogRepository non utilise par calculerCout : null acceptable ici.
    // Tarifs injectes via les properties (source de verite = application.yml).
    private final AiCoutService service = new AiCoutService(null, proprietesAvecTarifs());

    private static AiModelsProperties proprietesAvecTarifs() {
        final AiModelsProperties props = new AiModelsProperties();
        final Map<String, AiModelsProperties.TarifConfig> tarifs = new HashMap<>();
        tarifs.put("claude-haiku-4-5", tarif("0.000001", "0.000005"));
        tarifs.put("claude-sonnet-4-6", tarif("0.000003", "0.000015"));
        props.setTarifs(tarifs);
        return props;
    }

    private static AiModelsProperties.TarifConfig tarif(final String input, final String output) {
        final AiModelsProperties.TarifConfig t = new AiModelsProperties.TarifConfig();
        t.setPrixInput(new BigDecimal(input));
        t.setPrixOutput(new BigDecimal(output));
        return t;
    }

    @Test
    void coutHaiku_1MTokensChaque_egale6Dollars() {
        // Haiku : $1 input + $5 output par M tokens.
        final BigDecimal cout = service.calculerCout(
                "claude-haiku-4-5", 1_000_000, 1_000_000);
        assertThat(cout).isEqualByComparingTo(new BigDecimal("6"));
    }

    @Test
    void coutSonnet_1MTokensChaque_egale18Dollars() {
        // Sonnet : $3 input + $15 output par M tokens.
        final BigDecimal cout = service.calculerCout(
                "claude-sonnet-4-6", 1_000_000, 1_000_000);
        assertThat(cout).isEqualByComparingTo(new BigDecimal("18"));
    }

    @Test
    void modeleInconnu_retombeSurTarifHaiku() {
        final BigDecimal cout = service.calculerCout("modele-inexistant", 1_000_000, 1_000_000);
        assertThat(cout).isEqualByComparingTo(new BigDecimal("6"));
    }

    @Test
    void modeleNull_retombeSurTarifHaiku() {
        final BigDecimal cout = service.calculerCout(null, 1_000_000, 1_000_000);
        assertThat(cout).isEqualByComparingTo(new BigDecimal("6"));
    }

    @Test
    void tokensNull_coutZero() {
        final BigDecimal cout = service.calculerCout("claude-sonnet-4-6", null, null);
        assertThat(cout).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
