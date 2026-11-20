package fr.leadrevive.csvimport.service;

import fr.leadrevive.ai.entity.AiLog;
import fr.leadrevive.ai.entity.AiStatutEnum;
import fr.leadrevive.ai.entity.TypeGenerationEnum;
import fr.leadrevive.ai.repository.AiLogRepository;
import fr.leadrevive.ai.service.AiCoutService;
import fr.leadrevive.ai.service.AiModelResolver;
import fr.leadrevive.csvimport.dto.ColonneProposition;
import fr.leadrevive.csvimport.dto.MappingProposition;
import fr.leadrevive.csvimport.dto.SplitProposition;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Analyse les headers + échantillon d'un CSV via Claude Haiku et propose un mapping automatique.
 * Coût estimé : < 0.001€ par analyse (500 tokens input + 400 output).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MappingIAService {

    private final ChatClient.Builder chatClientBuilder;
    private final AiModelResolver aiModelResolver;
    private final fr.leadrevive.ai.service.AiResilientCaller aiResilientCaller;
    private final AiLogRepository aiLogRepository;
    private final AiCoutService aiCoutService;

    private String promptTemplate;

    @PostConstruct
    void chargerPrompt() {
        try {
            promptTemplate = new ClassPathResource("prompts/mapping-csv.txt")
                    .getContentAsString(StandardCharsets.UTF_8);
            log.info("Prompt mapping CSV chargé - taille: {} caractères", promptTemplate.length());
        } catch (final IOException e) {
            throw new IllegalStateException("Impossible de charger le prompt mapping-csv.txt", e);
        }
    }

    /**
     * Analyse les colonnes d'un CSV et propose un mapping via l'IA.
     * Si l'appel IA échoue, retourne un mapping vide (fallback manuel).
     */
    public MappingProposition analyserColonnes(final List<String> headers,
                                                final List<List<String>> echantillon,
                                                final Long tenantId) {
        log.info("Analyse IA mapping CSV - tenantId: {}, headers: {}", tenantId, headers.size());

        final String headersStr = String.join(", ", headers);
        final String echantillonStr = echantillon.stream()
                .map(ligne -> String.join(" | ", ligne))
                .collect(Collectors.joining("\n"));

        final String prompt = promptTemplate
                .replace("{HEADERS}", headersStr)
                .replace("{ECHANTILLON}", echantillonStr);

        final long debut = System.currentTimeMillis();
        // Modele/max-tokens resolus par type ; temperature 0.1 conservee (JSON deterministe).
        final AnthropicChatOptions aiOptions = aiModelResolver.getOptions(TypeGenerationEnum.MAPPING_CSV);
        aiOptions.setTemperature(0.1);
        final AiLog aiLog = new AiLog();
        aiLog.setTenantId(tenantId);
        aiLog.setTypeGeneration(TypeGenerationEnum.MAPPING_CSV);
        aiLog.setPrompt(prompt.length() > 2000 ? prompt.substring(0, 2000) + "..." : prompt);
        aiLog.setModele(aiOptions.getModel());

        try {
            final ChatClient chatClient = chatClientBuilder.build();
            final BeanOutputConverter<MappingProposition> converter = new BeanOutputConverter<>(MappingProposition.class);

            final org.springframework.ai.chat.model.ChatResponse chatResponse = aiResilientCaller.executer(() ->
                    chatClient.prompt()
                            .user(prompt + "\n\n" + converter.getFormat())
                            .options(aiOptions)
                            .call()
                            .chatResponse());

            String reponse = chatResponse.getResult().getOutput().getText();
            // Stripper les blocs markdown ```json ... ``` que Claude ajoute parfois
            reponse = nettoyerReponseJson(reponse);
            final MappingProposition proposition = converter.convert(reponse);
            final long duree = System.currentTimeMillis() - debut;

            final Integer tokensInput = chatResponse.getMetadata().getUsage() != null
                    ? (int) chatResponse.getMetadata().getUsage().getPromptTokens() : null;
            final Integer tokensOutput = chatResponse.getMetadata().getUsage() != null
                    ? (int) chatResponse.getMetadata().getUsage().getCompletionTokens() : null;

            aiLog.setReponse(reponse);
            aiLog.setTokensPrompt(tokensInput);
            aiLog.setTokensReponse(tokensOutput);
            aiLog.setCoutEstime(aiCoutService.calculerCout(aiOptions.getModel(), tokensInput, tokensOutput));
            aiLog.setDureeMs(duree);
            aiLog.setStatut(AiStatutEnum.SUCCES);
            aiLogRepository.save(aiLog);

            log.info("Mapping IA réussi - {} colonnes mappées, durée: {}ms",
                    proposition != null ? proposition.mappings().size() : 0, duree);
            return proposition;

        } catch (final Exception e) {
            final long duree = System.currentTimeMillis() - debut;
            aiLog.setDureeMs(duree);
            aiLog.setStatut(AiStatutEnum.ERREUR);
            aiLog.setErreur(e.getMessage() != null && e.getMessage().length() > 500
                    ? e.getMessage().substring(0, 500) : e.getMessage());
            aiLogRepository.save(aiLog);
            log.error("Échec mapping IA - fallback manuel - cause: {}", e.getMessage());
            // Fallback : mapping vide, le manager mappe tout manuellement
            return new MappingProposition(new ArrayList<>(), null, null);
        }
    }

    /**
     * Nettoie la réponse de Claude qui peut contenir des blocs markdown ```json ... ```
     */
    private String nettoyerReponseJson(final String reponse) {
        if (reponse == null) return null;
        String cleaned = reponse.trim();
        // Retirer le bloc ```json au début
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        // Retirer le ``` de fin
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }
}
