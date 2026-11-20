package fr.leadrevive.ai.service;

import fr.leadrevive.ai.config.AiModelsProperties;
import fr.leadrevive.ai.entity.TypeGenerationEnum;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests du resolveur multi-modeles : mapping par type + fallback aux defauts.
 */
class AiModelResolverTest {

    private static final String HAIKU = "claude-haiku-4-5";
    private static final String SONNET = "claude-sonnet-4-6";

    private AiModelResolver resolver(final Map<String, AiModelsProperties.ModelConfig> mapping) {
        final AiModelsProperties props = new AiModelsProperties();
        props.setDefaultModel(HAIKU);
        props.setDefaultMaxTokens(1024);
        props.setMapping(mapping);
        return new AiModelResolver(props);
    }

    private AiModelsProperties.ModelConfig config(final String model, final int maxTokens) {
        final AiModelsProperties.ModelConfig c = new AiModelsProperties.ModelConfig();
        c.setModel(model);
        c.setMaxTokens(maxTokens);
        return c;
    }

    @Test
    void typeMappe_retourneModeleEtMaxTokensDuMapping() {
        final Map<String, AiModelsProperties.ModelConfig> mapping = new HashMap<>();
        mapping.put("BRIEFING_QUOTIDIEN", config(SONNET, 4096));
        final AiModelResolver resolver = resolver(mapping);

        assertThat(resolver.getModelName(TypeGenerationEnum.BRIEFING_QUOTIDIEN)).isEqualTo(SONNET);
        assertThat(resolver.getOptions(TypeGenerationEnum.BRIEFING_QUOTIDIEN).getModel()).isEqualTo(SONNET);
        assertThat(resolver.getOptions(TypeGenerationEnum.BRIEFING_QUOTIDIEN).getMaxTokens()).isEqualTo(4096);
    }

    @Test
    void typeNonMappe_retombeSurLesDefauts() {
        final AiModelResolver resolver = resolver(new HashMap<>());

        assertThat(resolver.getModelName(TypeGenerationEnum.MAPPING_CSV)).isEqualTo(HAIKU);
        assertThat(resolver.getOptions(TypeGenerationEnum.MAPPING_CSV).getModel()).isEqualTo(HAIKU);
        assertThat(resolver.getOptions(TypeGenerationEnum.MAPPING_CSV).getMaxTokens()).isEqualTo(1024);
    }

    @Test
    void cleMappingCasseDifferente_resolueQuandMeme() {
        // Tolerance au relaxed binding qui pourrait normaliser la casse des cles.
        final Map<String, AiModelsProperties.ModelConfig> mapping = new HashMap<>();
        mapping.put("whatsapp_conversation", config(SONNET, 1024));
        final AiModelResolver resolver = resolver(mapping);

        assertThat(resolver.getModelName(TypeGenerationEnum.WHATSAPP_CONVERSATION)).isEqualTo(SONNET);
    }

    @Test
    void typeNull_retombeSurLeDefaut() {
        final AiModelResolver resolver = resolver(new HashMap<>());

        assertThat(resolver.getModelName(null)).isEqualTo(HAIKU);
        assertThat(resolver.getOptions(null).getModel()).isEqualTo(HAIKU);
    }
}
