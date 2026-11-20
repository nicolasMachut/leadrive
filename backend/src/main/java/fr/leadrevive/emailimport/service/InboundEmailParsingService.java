package fr.leadrevive.emailimport.service;

import fr.leadrevive.ai.entity.AiLog;
import fr.leadrevive.ai.entity.AiStatutEnum;
import fr.leadrevive.ai.entity.TypeGenerationEnum;
import fr.leadrevive.ai.repository.AiLogRepository;
import fr.leadrevive.ai.service.AiCoutService;
import fr.leadrevive.ai.service.AiModelResolver;
import fr.leadrevive.ai.service.AiResilientCaller;
import fr.leadrevive.emailimport.config.EmailImportConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Parse un email entrant via Claude Haiku et extrait les donnees structurees.
 *
 * <p>Reutilise le prompt {@code parsing-texte-libre.txt} existant. Identifie
 * automatiquement la source (LEBONCOIN, LACENTRALE, AUTOSCOUT24, etc.) et
 * extrait nom, telephone, email, vehicule + 56 champs supplementaires.</p>
 *
 * <p>Cas particuliers :
 * <ul>
 *   <li>{@code {"rejet": true}} : spam / newsletter / notification auto detectes</li>
 *   <li>{@code confiance < 30} : parsing rejete, body stocke pour debug</li>
 * </ul>
 * </p>
 *
 * <p>Cout estime : ~0.001 EUR par email parse (Claude Haiku 4.5).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InboundEmailParsingService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int CONFIANCE_MINIMUM = 30;

    private final ChatClient.Builder chatClientBuilder;
    private final AiModelResolver aiModelResolver;
    private final AiResilientCaller aiResilientCaller;
    private final AiLogRepository aiLogRepository;
    private final AiCoutService aiCoutService;
    private final EmailImportConfig emailImportConfig;

    private String promptTemplate;

    @PostConstruct
    void chargerPrompt() {
        try {
            promptTemplate = new ClassPathResource("prompts/parsing-texte-libre.txt")
                    .getContentAsString(StandardCharsets.UTF_8);
            log.info("Prompt parsing email charge - taille: {} caracteres", promptTemplate.length());
        } catch (final IOException e) {
            throw new IllegalStateException("Impossible de charger le prompt parsing-texte-libre.txt", e);
        }
    }

    /**
     * Parse le contenu d'un email et retourne les donnees extraites.
     *
     * @param emailBody contenu brut de l'email (texte ou HTML deja converti en texte)
     * @param subject objet de l'email, ajoute au texte parse pour contexte
     * @param tenantId tenant cible (pour AiLog)
     * @return resultat de parsing avec champs extraits + meta (duree, source, confiance)
     */
    public ResultatParsing parser(final String emailBody, final String subject, final Long tenantId) {
        log.info("Parsing email IA - tenantId: {}, taille body: {}",
                tenantId, emailBody != null ? emailBody.length() : 0);

        if (emailBody == null || emailBody.isBlank()) {
            return ResultatParsing.echec("Email vide");
        }

        // Tronque le body si trop long (defense en profondeur, le webhook tronque deja).
        final String bodyTronque = emailBody.length() > emailImportConfig.getMaxBodyChars()
                ? emailBody.substring(0, emailImportConfig.getMaxBodyChars())
                : emailBody;

        // Concatene sujet + body : le sujet aide l'IA a identifier la source rapidement
        // ("Nouveau message LeBonCoin : ..."). Indispensable pour la detection de source.
        final String texteComplet = (subject != null && !subject.isBlank())
                ? "Sujet : " + subject + "\n\n" + bodyTronque
                : bodyTronque;

        final String prompt = promptTemplate.replace("{TEXTE}", texteComplet);
        final long debut = System.currentTimeMillis();

        // Modele/max-tokens resolus par type ; temperature 0.1 conservee (JSON deterministe).
        final AnthropicChatOptions aiOptions = aiModelResolver.getOptions(TypeGenerationEnum.PARSING_EMAIL);
        aiOptions.setTemperature(0.1);
        final AiLog aiLog = new AiLog();
        aiLog.setTenantId(tenantId);
        aiLog.setTypeGeneration(TypeGenerationEnum.PARSING_EMAIL);
        // RGPD : on stocke un marqueur audit, pas le prompt complet (PII du prospect).
        aiLog.setPrompt("[PARSING_EMAIL] taille body: " + bodyTronque.length() + " chars");
        aiLog.setModele(aiOptions.getModel());

        try {
            final ChatClient chatClient = chatClientBuilder.build();
            final ChatResponse chatResponse = aiResilientCaller.executer(() ->
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

            aiLog.setReponse(reponse != null && reponse.length() > 2000
                    ? reponse.substring(0, 2000) + "..." : reponse);
            aiLog.setTokensPrompt(tokensInput);
            aiLog.setTokensReponse(tokensOutput);
            aiLog.setCoutEstime(aiCoutService.calculerCout(aiOptions.getModel(), tokensInput, tokensOutput));
            aiLog.setStatut(AiStatutEnum.SUCCES);
            aiLog.setDureeMs(dureeMs);
            aiLogRepository.save(aiLog);

            final ResultatParsing resultat = parseResponse(reponse, dureeMs);
            log.info("Parsing email termine - tenantId: {}, duree: {}ms, source: {}, confiance: {}, rejet: {}",
                    tenantId, dureeMs, resultat.sourceDetectee(), resultat.confiance(), resultat.rejet());
            return resultat;

        } catch (final Exception e) {
            final long dureeMs = System.currentTimeMillis() - debut;
            aiLog.setStatut(AiStatutEnum.ERREUR);
            aiLog.setErreur(e.getMessage() != null && e.getMessage().length() > 500
                    ? e.getMessage().substring(0, 500) : e.getMessage());
            aiLog.setDureeMs(dureeMs);
            aiLogRepository.save(aiLog);
            log.error("Erreur parsing email IA - tenantId: {}, cause: {}", tenantId, e.getMessage());
            return ResultatParsing.echec("Erreur appel IA : " + e.getMessage());
        }
    }

    /**
     * Parse la reponse JSON de l'IA. Gere 3 cas : rejet spam, parsing OK, JSON invalide.
     */
    static ResultatParsing parseResponse(final String json, final long dureeMs) {
        if (json == null || json.isBlank()) {
            return ResultatParsing.echec("Reponse IA vide", null);
        }
        final String nettoye = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

        try {
            final JsonNode root = MAPPER.readTree(nettoye);

            // Cas rejet spam : {"rejet": true, "raison": "..."}
            if (root.has("rejet") && root.get("rejet").asBoolean(false)) {
                final String raison = root.has("raison") ? root.get("raison").asString() : "Spam detecte par IA";
                return ResultatParsing.rejetSpam(raison, nettoye, dureeMs);
            }

            JsonNode principal = root.get("principal");
            final JsonNode supplementaires = root.get("supplementaires");

            // Robustesse : l'IA omet parfois le wrapper "principal" et renvoie les
            // champs de lead directement a la racine. Si la racine ressemble a un
            // "principal" (au moins un champ de lead reconnu), on l'utilise tel quel :
            // on ne perd pas un vrai lead pour un simple ecart de structure.
            if ((principal == null || principal.isNull()) && ressembleAPrincipal(root)) {
                principal = root;
            }

            // Pas de wrapper ET racine sans donnee de lead exploitable (ex. une
            // reponse "Re:" = thread cite, un accuse de reception) : rejet propre
            // plutot qu'un PARSE_ECHEC alarmant. Brut conserve pour audit.
            if (principal == null || principal.isNull()) {
                return ResultatParsing.rejetSpam("Aucune donnee de lead exploitable", nettoye, dureeMs);
            }

            final int confiance = principal.has("confiance") ? principal.get("confiance").asInt(0) : 0;
            if (confiance < CONFIANCE_MINIMUM) {
                return ResultatParsing.confianceInsuffisante(confiance, nettoye, dureeMs);
            }

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

            return new ResultatParsing(
                    StatutResultat.OK,
                    getTextOrNull(principal, "nom"),
                    getTextOrNull(principal, "prenom"),
                    getTextOrNull(principal, "telephone"),
                    getTextOrNull(principal, "email"),
                    getTextOrNull(principal, "marque"),
                    getTextOrNull(principal, "modele"),
                    getTextOrNull(principal, "vehiculeInteret"),
                    getTextOrNull(principal, "sourceDetectee"),
                    confiance,
                    donneesSupp,
                    nettoye,
                    null,
                    dureeMs,
                    false);

        } catch (final Exception e) {
            log.warn("JSON invalide retourne par l'IA - cause: {}", e.getMessage());
            return ResultatParsing.echec("JSON IA invalide : " + e.getMessage(), json);
        }
    }

    /** Champs de lead reconnus a la racine, servant a rattraper une reponse IA
     *  qui aurait omis le wrapper {@code "principal"}. */
    private static final List<String> CHAMPS_LEAD = List.of(
            "nom", "prenom", "telephone", "email", "marque", "modele", "vehiculeInteret");

    /** Vrai si le noeud est un objet portant au moins un champ de lead reconnu. */
    private static boolean ressembleAPrincipal(final JsonNode node) {
        if (node == null || !node.isObject()) return false;
        return CHAMPS_LEAD.stream().anyMatch(node::has);
    }

    private static String getTextOrNull(final JsonNode node, final String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        final String valeur = node.get(field).asString();
        return valeur != null && !valeur.isBlank() ? valeur : null;
    }

    /** Statut interne du parsing (mappe vers {@link fr.leadrevive.emailimport.entity.StatutInboundEmail} cote orchestrateur). */
    public enum StatutResultat {
        OK,
        REJET_SPAM,
        CONFIANCE_INSUFFISANTE,
        ECHEC
    }

    /** Resultat structure du parsing : statut + champs extraits + meta. */
    public record ResultatParsing(
            StatutResultat statut,
            String nom,
            String prenom,
            String telephone,
            String email,
            String marque,
            String modele,
            String vehiculeInteret,
            String sourceDetectee,
            int confiance,
            Map<String, Object> donneesSupplementaires,
            String resultatBrut,
            String raisonRejet,
            long dureeMs,
            boolean rejet) {

        public static ResultatParsing echec(final String raison) {
            return echec(raison, null);
        }

        /** Variante conservant la reponse brute de l'IA pour audit/debug. */
        public static ResultatParsing echec(final String raison, final String brut) {
            return new ResultatParsing(StatutResultat.ECHEC, null, null, null, null,
                    null, null, null, null, 0, new HashMap<>(), brut, raison, 0L, false);
        }

        public static ResultatParsing rejetSpam(final String raison, final String brut, final long duree) {
            return new ResultatParsing(StatutResultat.REJET_SPAM, null, null, null, null,
                    null, null, null, null, 0, new HashMap<>(), brut, raison, duree, true);
        }

        public static ResultatParsing confianceInsuffisante(final int confiance, final String brut, final long duree) {
            return new ResultatParsing(StatutResultat.CONFIANCE_INSUFFISANTE, null, null, null, null,
                    null, null, null, null, confiance, new HashMap<>(), brut,
                    "Confiance IA insuffisante : " + confiance, duree, false);
        }
    }
}
