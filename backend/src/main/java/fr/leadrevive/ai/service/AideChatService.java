package fr.leadrevive.ai.service;

import fr.leadrevive.ai.dto.ReqAideChatDto;
import fr.leadrevive.ai.entity.AiLog;
import fr.leadrevive.ai.entity.AiStatutEnum;
import fr.leadrevive.ai.entity.TypeGenerationEnum;
import fr.leadrevive.ai.repository.AiLogRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Service du chat d'aide contextuel.
 * Repond aux questions des utilisateurs sur le fonctionnement de LeadRevive,
 * en s'appuyant sur un system prompt qui contient toute la connaissance de l'app
 * (cf prompts/aide-app.txt).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AideChatService {

    private final ChatClient.Builder chatClientBuilder;
    private final AiModelResolver aiModelResolver;
    private final AiResilientCaller aiResilientCaller;
    private final AiLogRepository aiLogRepository;
    private final AiCoutService aiCoutService;
    private final AiQuotaGuard aiQuotaGuard;

    private String systemPromptTemplate;          // mode "aide" — prompts/aide-app.txt
    private String simulationPromptTemplate;      // mode "simulation" — wrapper + prompts/whatsapp-conversation.txt

    @PostConstruct
    void chargerPrompt() {
        try {
            systemPromptTemplate = new ClassPathResource("prompts/aide-app.txt")
                    .getContentAsString(StandardCharsets.UTF_8);
            log.info("Prompt aide-app charge - taille: {} caracteres", systemPromptTemplate.length());
        } catch (final IOException e) {
            throw new IllegalStateException("Impossible de charger prompts/aide-app.txt", e);
        }

        try {
            final String conversationPrompt = new ClassPathResource("prompts/whatsapp-conversation.txt")
                    .getContentAsString(StandardCharsets.UTF_8);

            // Wrapper qui place l'IA en mode simulation et lui donne des valeurs par defaut
            // pour les variables habituellement injectees a partir du contexte lead/tenant.
            simulationPromptTemplate = """
                Tu es en MODE SIMULATION. L'utilisateur est un manager ou vendeur de concession automobile qui veut tester comment l'IA WhatsApp Leadrive repondrait a un prospect.

                L'utilisateur va ecrire des messages COMME SI il etait un prospect. Tu dois repondre EXACTEMENT comme l'IA WhatsApp le ferait en production, en suivant le prompt de conversation ci-dessous.

                REGLES DU MODE SIMULATION :
                1. Reponds UNIQUEMENT comme l'IA WhatsApp — pas de meta-commentaire, pas d'explication
                2. Utilise le vouvoiement strict
                3. Respecte TOUTES les regles du prompt (formulations positives, pas de prix/montants/taux, confirmation RDV obligatoire, etc.)
                4. Utilise ces valeurs par defaut pour les variables du template :
                   - {PRENOM_ASSISTANT} = Nora
                   - {NOM_CONCESSION} / {NOM_CONCESSION_2} = "votre concession" (ou le nom de la concession si l'utilisateur le precise)
                   - {HORAIRES} = Lundi-Vendredi 9h-19h, Samedi 9h-12h
                   - {ADRESSE_CONCESSION} = "Non renseigne"
                   - {TELEPHONE_CONCESSION} = "Non renseigne"
                   - {NOM_PROSPECT} = le prenom donne par l'utilisateur dans la conversation, sinon "Prospect"
                   - {MARQUE_VEHICULE} / {MODELE_VEHICULE} = ce que l'utilisateur mentionne, sinon "Non renseigne"
                   - {SOURCE} = "Non renseigne"
                   - {REPRISE} = "Non"
                   - {ETAPE} = "Non renseigne"
                   - {DONNEES_SUPPLEMENTAIRES} = (vide)
                5. Si l'utilisateur pose une question sur l'outil Leadrive (hors simulation), reponds : "Je suis en mode simulation — pour les questions sur l'outil, basculez en mode Aide avec le bouton en haut du chat."

                --- PROMPT DE CONVERSATION WHATSAPP (a suivre strictement) ---

                """ + conversationPrompt;

            log.info("Prompt simulation charge - taille: {} caracteres", simulationPromptTemplate.length());
        } catch (final IOException e) {
            log.warn("Impossible de charger le prompt de simulation : {}", e.getMessage());
            simulationPromptTemplate = null; // Le mode simulation sera indisponible — fallback aide.
        }
    }

    @Transactional
    public String repondre(final ReqAideChatDto request, final Long tenantId, final String role) {
        final long debut = System.currentTimeMillis();

        // Quota journalier IA : bloque l'abus (user qui spam le chat ou bug front en boucle).
        aiQuotaGuard.verifierQuotaJournalier(tenantId);

        // Choix du prompt selon le mode demande (par defaut "aide").
        // Si le prompt de simulation n'a pas pu etre charge, on retombe sur le prompt d'aide.
        final boolean isSimulation = "simulation".equalsIgnoreCase(request.getMode())
                && simulationPromptTemplate != null;
        final String basePrompt = isSimulation ? simulationPromptTemplate : systemPromptTemplate;

        // Resolution du modele AVANT l'appel : le type depend du mode (aide vs simulation).
        final TypeGenerationEnum typeGen = isSimulation
                ? TypeGenerationEnum.AIDE_SIMULATION : TypeGenerationEnum.AIDE_CHAT;
        final AnthropicChatOptions aiOptions = aiModelResolver.getOptions(typeGen);

        // System prompt enrichi avec le role et la route courante
        final StringBuilder systemPrompt = new StringBuilder(basePrompt);
        systemPrompt.append("\n\n## CONTEXTE DE LA SESSION COURANTE\n");
        systemPrompt.append("- Role de l'utilisateur : ").append(role != null ? role : "INCONNU").append("\n");
        if (request.getRouteCourante() != null && !request.getRouteCourante().isBlank()) {
            systemPrompt.append("- Page actuellement consultee : ").append(request.getRouteCourante()).append("\n");
        }

        final List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt.toString()));

        // Historique de conversation
        if (request.getHistorique() != null) {
            for (final ReqAideChatDto.MessageHistorique msg : request.getHistorique()) {
                if (msg.getContenu() == null || msg.getContenu().isBlank()) continue;
                if ("assistant".equalsIgnoreCase(msg.getRole())) {
                    messages.add(new AssistantMessage(msg.getContenu()));
                } else {
                    messages.add(new UserMessage(msg.getContenu()));
                }
            }
        }

        // Question courante
        messages.add(new UserMessage(request.getQuestion()));

        // ai_log.tenant_id est NOT NULL — on ne logge que pour un user rattache a un tenant
        // (SUPER_ADMIN qui teste l'aide n'a pas de tenant, on skip le log dans ce cas).
        final boolean canLog = tenantId != null;
        final AiLog aiLog = canLog ? new AiLog() : null;
        if (canLog) {
            aiLog.setTenantId(tenantId);
            aiLog.setTypeGeneration(typeGen);
            aiLog.setPrompt(request.getQuestion());
            aiLog.setModele(aiOptions.getModel());
        }

        try {
            final ChatClient chatClient = chatClientBuilder.build();
            final org.springframework.ai.chat.model.ChatResponse chatResponse = aiResilientCaller.executer(() ->
                    chatClient.prompt()
                            .messages(messages)
                            .options(aiOptions)
                            .call()
                            .chatResponse());

            final String reponse = chatResponse.getResult().getOutput().getText();
            final long duree = System.currentTimeMillis() - debut;

            final Integer tokensInput = chatResponse.getMetadata().getUsage() != null
                    ? (int) chatResponse.getMetadata().getUsage().getPromptTokens() : null;
            final Integer tokensOutput = chatResponse.getMetadata().getUsage() != null
                    ? (int) chatResponse.getMetadata().getUsage().getCompletionTokens() : null;

            if (canLog) {
                aiLog.setReponse(reponse);
                aiLog.setTokensPrompt(tokensInput);
                aiLog.setTokensReponse(tokensOutput);
                aiLog.setCoutEstime(aiCoutService.calculerCout(aiOptions.getModel(), tokensInput, tokensOutput));
                aiLog.setDureeMs(duree);
                aiLog.setStatut(AiStatutEnum.SUCCES);
                aiLogRepository.save(aiLog);
            }

            log.info("Aide chat - mode: {}, role: {}, duree: {}ms, tokens: {}/{}",
                    isSimulation ? "simulation" : "aide", role, duree, tokensInput, tokensOutput);
            return reponse;

        } catch (final Exception e) {
            if (canLog) {
                try {
                    aiLog.setStatut(AiStatutEnum.ERREUR);
                    aiLog.setErreur(e.getMessage() != null && e.getMessage().length() > 500
                            ? e.getMessage().substring(0, 500) : e.getMessage());
                    aiLog.setDureeMs(System.currentTimeMillis() - debut);
                    aiLogRepository.save(aiLog);
                } catch (final Exception logErr) {
                    log.warn("Impossible de logger l'erreur en base : {}", logErr.getMessage());
                }
            }
            log.error("Echec aide chat - cause: {}", e.getMessage(), e);
            throw new RuntimeException("Le service d'aide est temporairement indisponible. Reessayez dans quelques instants.", e);
        }
    }
}
