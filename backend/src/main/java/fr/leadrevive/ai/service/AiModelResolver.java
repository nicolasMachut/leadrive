package fr.leadrevive.ai.service;

import fr.leadrevive.ai.config.AiModelsProperties;
import fr.leadrevive.ai.entity.TypeGenerationEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Resout le modele Anthropic et le plafond de tokens a utiliser pour un
 * {@link TypeGenerationEnum} donne, a partir de {@link AiModelsProperties}.
 *
 * <p>Source de verite unique du choix de modele : a reutiliser dans chaque
 * service IA (options d'appel + {@code aiLog.setModele} + calcul du cout) pour
 * eviter de re-hardcoder un nom de modele.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiModelResolver {

    private final AiModelsProperties properties;

    /**
     * Options d'appel (modele + max-tokens) pour ce type de generation.
     * Retombe sur les defauts globaux si le type n'est pas mappe.
     */
    public AnthropicChatOptions getOptions(final TypeGenerationEnum type) {
        final AiModelsProperties.ModelConfig config = resoudreConfig(type);
        final String model = modeleOuDefaut(config);
        final int maxTokens = config != null && config.getMaxTokens() > 0
                ? config.getMaxTokens() : properties.getDefaultMaxTokens();
        return AnthropicChatOptions.builder()
                .model(model)
                .maxTokens(maxTokens)
                .build();
    }

    /**
     * Nom du modele pour ce type (pour {@code aiLog.setModele} et
     * {@code AiCoutService.calculerCout}). Coherent avec {@link #getOptions}.
     */
    public String getModelName(final TypeGenerationEnum type) {
        return modeleOuDefaut(resoudreConfig(type));
    }

    private String modeleOuDefaut(final AiModelsProperties.ModelConfig config) {
        return config != null && config.getModel() != null && !config.getModel().isBlank()
                ? config.getModel() : properties.getDefaultModel();
    }

    private AiModelsProperties.ModelConfig resoudreConfig(final TypeGenerationEnum type) {
        if (type == null) {
            return null;
        }
        final Map<String, AiModelsProperties.ModelConfig> mapping = properties.getMapping();
        final AiModelsProperties.ModelConfig direct = mapping.get(type.name());
        if (direct != null) {
            return direct;
        }
        // Tolerance a la casse : le relaxed binding Spring peut normaliser les
        // cles de map. On retente en ignorant la casse avant de retomber au defaut.
        for (final Map.Entry<String, AiModelsProperties.ModelConfig> entry : mapping.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(type.name())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
