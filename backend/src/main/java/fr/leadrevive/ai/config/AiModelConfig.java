package fr.leadrevive.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Active la configuration multi-modeles IA ({@link AiModelsProperties}).
 */
@Configuration
@EnableConfigurationProperties(AiModelsProperties.class)
public class AiModelConfig {
}
