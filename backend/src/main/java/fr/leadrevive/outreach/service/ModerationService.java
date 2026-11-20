package fr.leadrevive.outreach.service;

import fr.leadrevive.outreach.entity.MessageModerationLog;
import fr.leadrevive.outreach.repository.MessageModerationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Moderation IA des messages manuels envoyes par les vendeurs / managers (epic 23).
 *
 * <p>Verifie que le contenu respecte la Meta Business Policy avant envoi :
 * pas de spam, pas d'arnaque, pas de demande de donnees sensibles, pas
 * d'agressivite, pas de contenu interdit. Sois indulgent sur les messages
 * commerciaux normaux.</p>
 *
 * <p><strong>Mode fail-open</strong> : si l'IA est indisponible, le message
 * est AUTORISE par defaut (avec log warning). Plutot ne pas bloquer
 * l'utilisateur que casser le service en cas de panne IA.</p>
 *
 * <p><strong>Court-circuit</strong> : messages < 5 chars sont automatiquement
 * autorises (typiquement "ok", "merci") - economie d'appels IA.</p>
 *
 * <p>Cout estime : ~0.001€/message Claude Haiku 4.5. A 20 msg manuels/jour ×
 * 50 concessions × 30 jours = 30k modes/mois = ~30€/mois max.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModerationService {

    private static final String SYSTEM_PROMPT = """
        Tu es un moderateur de messages WhatsApp envoyes par des vendeurs de concessions automobiles francaises a leurs prospects.

        Verifie que le message respecte la Meta Business Policy. Sois INDULGENT : autorise les messages commerciaux normaux,
        les rappels de RDV, les questions, les confirmations, les remerciements, les chiffres (prix, immatriculations, kilometrage).

        Bloque UNIQUEMENT le clairement problematique :
        - spam evident (envois en masse identiques)
        - fausses promotions, arnaques ("vous avez gagne...", "cliquez ici urgent")
        - contenu interdit Meta (drogues, armes, services illegaux)
        - demande de donnees sensibles (mot de passe, code bancaire, numero de carte)
        - agressivite, insultes, harcelement
        - usurpation d'identite (se faire passer pour un autre service)

        Reponds UNIQUEMENT en JSON strict valide, rien d'autre :
        {"autorise": true|false, "raison": "explication courte si bloque, sinon null"}
        """;

    private final ChatClient.Builder chatClientBuilder;
    private final MessageModerationLogRepository moderationLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Verifie un message manuel et retourne la decision.
     * Log systematique en BDD pour audit Meta.
     */
    @Transactional
    public ResultatModeration verifier(final Long tenantId, final Long userId,
                                        final String conversationId, final String contenu) {
        final long debut = System.currentTimeMillis();

        // Court-circuit : message trop court (typiquement "ok", "merci")
        if (contenu == null || contenu.trim().length() < 5) {
            return logEtRetourner(tenantId, userId, conversationId, contenu,
                    true, null, 0);
        }

        try {
            final String reponse = chatClientBuilder.build()
                    .prompt()
                    .system(SYSTEM_PROMPT)
                    .user(contenu)
                    .call()
                    .content();

            final ResultatModeration resultat = parseReponse(reponse);
            final int duree = (int) (System.currentTimeMillis() - debut);
            return logEtRetourner(tenantId, userId, conversationId, contenu,
                    resultat.autorise(), resultat.raison(), duree);

        } catch (final Exception ex) {
            // Fail-open : si l'IA plante, on autorise mais on log.
            log.warn("Moderation IA indisponible (fail-open) - tenantId: {}, cause: {}",
                    tenantId, ex.getMessage());
            final int duree = (int) (System.currentTimeMillis() - debut);
            return logEtRetourner(tenantId, userId, conversationId, contenu,
                    true, "Erreur IA: " + ex.getMessage(), duree);
        }
    }

    private ResultatModeration logEtRetourner(final Long tenantId, final Long userId,
                                                final String conversationId, final String contenu,
                                                final boolean autorise, final String raison,
                                                final int dureeMs) {
        final MessageModerationLog moderationLog = new MessageModerationLog();
        moderationLog.setTenantId(tenantId);
        moderationLog.setUserId(userId);
        moderationLog.setConversationId(conversationId);
        moderationLog.setContenu(contenu);
        moderationLog.setDecision(autorise ? "AUTORISE" : "BLOQUE");
        moderationLog.setRaison(raison);
        moderationLog.setDureeMs(dureeMs);
        moderationLogRepository.save(moderationLog);
        return new ResultatModeration(autorise, raison);
    }

    /**
     * Parse la reponse JSON Claude. Robuste aux IA bavardes qui renvoient
     * du texte autour du JSON (extrait le 1er bloc JSON valide).
     */
    private ResultatModeration parseReponse(final String reponse) {
        try {
            // Cherche le 1er { et son } correspondant
            final int debut = reponse.indexOf('{');
            final int fin = reponse.lastIndexOf('}');
            if (debut == -1 || fin == -1 || fin <= debut) {
                throw new IllegalArgumentException("JSON introuvable dans la reponse");
            }
            final String json = reponse.substring(debut, fin + 1);
            final JsonNode node = objectMapper.readTree(json);
            final boolean autorise = node.has("autorise") && node.get("autorise").asBoolean(true);
            final String raison = node.has("raison") && !node.get("raison").isNull()
                    ? node.get("raison").asText()
                    : null;
            return new ResultatModeration(autorise, raison);
        } catch (final Exception e) {
            // Si on n'arrive pas a parser, fail-open
            log.warn("Reponse moderation IA non parsable (fail-open) - reponse: {}", reponse);
            return new ResultatModeration(true, "Reponse IA non parsable: " + e.getMessage());
        }
    }

    /** Resultat d'une moderation. */
    public record ResultatModeration(boolean autorise, String raison) {}
}
