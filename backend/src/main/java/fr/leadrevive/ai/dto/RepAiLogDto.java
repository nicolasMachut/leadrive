package fr.leadrevive.ai.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Vue exposable d'un {@code AiLog} : UNIQUEMENT des métadonnées (coût, tokens, durée,
 * statut). On n'expose JAMAIS {@code prompt} (qui embarque le system prompt interne =
 * moat technique + les PII du prospect) ni {@code reponse} brute. Renvoyer l'entité
 * {@code AiLog} directement fuitait tout cela via l'API.
 */
public record RepAiLogDto(
        Long id,
        Long leadId,
        String typeGeneration,
        String modele,
        Integer tokensPrompt,
        Integer tokensReponse,
        BigDecimal coutEstime,
        Long dureeMs,
        String statut,
        boolean enErreur,
        LocalDateTime creeLe
) {
}
