package fr.leadrevive.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration multi-modeles IA (prefix {@code leadrevive.ai.models}).
 *
 * <p>Permet d'assigner un modele Anthropic et un plafond de tokens differents
 * par {@code TypeGenerationEnum} (cle du {@link #mapping}). Le bloc
 * {@code spring.ai.anthropic} reste le default global Spring AI ; ce mapping le
 * surcharge a l'appel via {@code AiModelResolver}.</p>
 *
 * <p>Un type absent du mapping retombe sur {@link #defaultModel} /
 * {@link #defaultMaxTokens}.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "leadrevive.ai.models")
public class AiModelsProperties {

    /** Modele utilise quand un type n'est pas dans le {@link #mapping}. */
    private String defaultModel = "claude-haiku-4-5";

    /** Plafond de tokens utilise quand un type n'est pas dans le {@link #mapping}. */
    private int defaultMaxTokens = 1024;

    /** Cle = nom du {@code TypeGenerationEnum} (ex: WHATSAPP_CONVERSATION). */
    private Map<String, ModelConfig> mapping = new HashMap<>();

    /** Tarifs par id de modele (ex: claude-haiku-4-5). Cf. AiCoutService. */
    private Map<String, TarifConfig> tarifs = new HashMap<>();

    @Getter
    @Setter
    public static class ModelConfig {
        private String model;
        private int maxTokens = 1024;
    }

    /** Tarif d'un modele en USD par token (input / output). */
    @Getter
    @Setter
    public static class TarifConfig {
        private BigDecimal prixInput = BigDecimal.ZERO;
        private BigDecimal prixOutput = BigDecimal.ZERO;
    }
}
