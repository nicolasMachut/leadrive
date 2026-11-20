package fr.leadrevive.lead.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import fr.leadrevive.ai.entity.AiLog;
import fr.leadrevive.ai.entity.AiStatutEnum;
import fr.leadrevive.ai.entity.TypeGenerationEnum;
import fr.leadrevive.ai.repository.AiLogRepository;
import fr.leadrevive.ai.service.AiCoutService;
import fr.leadrevive.ai.service.AiModelResolver;
import fr.leadrevive.lead.dto.RepTexteLibreDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Parse un texte libre (email collé, copie de lead) via l'IA et extrait les données structurées.
 * Retourne une prévisualisation — ne crée PAS le lead.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TexteLibreParsingService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatClient.Builder chatClientBuilder;
    private final AiModelResolver aiModelResolver;
    private final fr.leadrevive.ai.service.AiResilientCaller aiResilientCaller;
    private final AiLogRepository aiLogRepository;
    private final AiCoutService aiCoutService;

    private String promptTemplate;

    @PostConstruct
    void chargerPrompt() {
        try {
            promptTemplate = new ClassPathResource("prompts/parsing-texte-libre.txt")
                    .getContentAsString(StandardCharsets.UTF_8);
            log.info("Prompt parsing texte libre chargé - taille: {} caractères", promptTemplate.length());
        } catch (final IOException e) {
            throw new IllegalStateException("Impossible de charger le prompt parsing-texte-libre.txt", e);
        }
    }

    /**
     * Parse un texte libre et retourne les données extraites.
     */
    public RepTexteLibreDto parser(final String texte, final Long tenantId) {
        log.info("Parsing texte libre - tenantId: {}, taille: {} caractères", tenantId, texte.length());

        final String contenuNettoye = nettoyerTexte(texte);
        if (contenuNettoye.isBlank() || contenuNettoye.length() < 20) {
            throw new IllegalArgumentException("Texte trop court pour être analysé");
        }
        // Garde-fou taille : texte forwardé > 5000 chars => l'IA hallucine ou tronque la
        // sortie JSON. Mieux vaut refuser explicitement que de stocker une analyse partielle.
        if (contenuNettoye.length() > 5000) {
            throw new IllegalArgumentException(
                    "Texte trop long (max 5000 caractères). Collez uniquement la partie pertinente.");
        }

        final String prompt = promptTemplate.replace("{TEXTE}", contenuNettoye);
        final long debut = System.currentTimeMillis();

        final AiLog aiLog = new AiLog();
        aiLog.setTenantId(tenantId);
        aiLog.setTypeGeneration(TypeGenerationEnum.PARSING_TEXTE_LIBRE);
        // RGPD : on ne stocke PAS le prompt complet (contient nom/téléphone/email du
        // prospect en clair, ce qui poserait probleme en audit CNIL et lors d'un droit
        // a l'oubli sur un lead avec leadId=null). On garde juste un marqueur audit.
        aiLog.setPrompt("[PARSING_TEXTE_LIBRE] taille texte: " + contenuNettoye.length() + " chars");
        // Modele/max-tokens resolus par type ; temperature 0.1 conservee (JSON deterministe).
        final AnthropicChatOptions aiOptions = aiModelResolver.getOptions(TypeGenerationEnum.PARSING_TEXTE_LIBRE);
        aiOptions.setTemperature(0.1);
        aiLog.setModele(aiOptions.getModel());

        try {
            final ChatClient chatClient = chatClientBuilder.build();
            final org.springframework.ai.chat.model.ChatResponse chatResponse = aiResilientCaller.executer(() ->
                    chatClient.prompt()
                            .user(prompt)
                            .options(aiOptions)
                            .call()
                            .chatResponse());

            final String reponse = chatResponse.getResult().getOutput().getText();
            final long dureeMs = System.currentTimeMillis() - debut;

            final Integer tokensInput = chatResponse.getMetadata().getUsage() != null
                    ? (int) chatResponse.getMetadata().getUsage().getPromptTokens() : null;
            final Integer tokensOutput = chatResponse.getMetadata().getUsage() != null
                    ? (int) chatResponse.getMetadata().getUsage().getCompletionTokens() : null;

            aiLog.setReponse(reponse != null && reponse.length() > 2000 ? reponse.substring(0, 2000) + "..." : reponse);
            aiLog.setTokensPrompt(tokensInput);
            aiLog.setTokensReponse(tokensOutput);
            aiLog.setCoutEstime(aiCoutService.calculerCout(aiOptions.getModel(), tokensInput, tokensOutput));
            aiLog.setStatut(AiStatutEnum.SUCCES);
            aiLog.setDureeMs(dureeMs);
            aiLogRepository.save(aiLog);

            log.info("Parsing texte libre terminé - tenantId: {}, durée: {} ms", tenantId, dureeMs);
            return parseResponse(reponse);

        } catch (final Exception e) {
            aiLog.setStatut(AiStatutEnum.ERREUR);
            aiLog.setReponse(e.getMessage());
            aiLogRepository.save(aiLog);
            log.error("Erreur parsing texte libre - tenantId: {}, cause: {}", tenantId, e.getMessage());
            throw new RuntimeException("Erreur lors du parsing IA du texte : " + e.getMessage(), e);
        }
    }

    private String nettoyerTexte(final String texte) {
        if (texte == null) return "";
        return texte
                .replaceAll("\\r\\n", "\n")
                .replaceAll("\\t", " ")
                .replaceAll(" +", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private RepTexteLibreDto parseResponse(final String json) {
        final String nettoyé = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

        try {
            final JsonNode root = MAPPER.readTree(nettoyé);
            final JsonNode principal = root.get("principal");
            final JsonNode supplementaires = root.get("supplementaires");

            final Map<String, Object> donneesSupp = new HashMap<>();
            if (supplementaires != null) {
                final Iterator<Map.Entry<String, JsonNode>> fields = supplementaires.properties().iterator();
                while (fields.hasNext()) {
                    final Map.Entry<String, JsonNode> entry = fields.next();
                    if (!entry.getValue().isNull()) {
                        if (entry.getValue().isBoolean()) {
                            donneesSupp.put(entry.getKey(), entry.getValue().booleanValue());
                        } else if (entry.getValue().isInt() || entry.getValue().isLong()) {
                            donneesSupp.put(entry.getKey(), entry.getValue().intValue());
                        } else {
                            donneesSupp.put(entry.getKey(), entry.getValue().asString());
                        }
                    }
                }
            }

            return RepTexteLibreDto.builder()
                    .nom(getTextOrNull(principal, "nom"))
                    .prenom(getTextOrNull(principal, "prenom"))
                    .telephone(getTextOrNull(principal, "telephone"))
                    .email(getTextOrNull(principal, "email"))
                    .marque(getTextOrNull(principal, "marque"))
                    .modele(getTextOrNull(principal, "modele"))
                    .vehiculeInteret(getTextOrNull(principal, "vehiculeInteret"))
                    .sourceDetectee(getTextOrNull(principal, "sourceDetectee"))
                    .confiance(principal != null && principal.has("confiance") ? principal.get("confiance").asInt() : 0)
                    .donneesSupplementaires(donneesSupp)
                    .build();

        } catch (final Exception e) {
            throw new RuntimeException("JSON invalide retourné par l'IA: " + nettoyé, e);
        }
    }

    private String getTextOrNull(final JsonNode node, final String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        return node.get(field).asString();
    }
}
