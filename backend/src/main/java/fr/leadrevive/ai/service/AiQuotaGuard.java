package fr.leadrevive.ai.service;

import fr.leadrevive.ai.repository.AiLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Plafond du nombre d'appels IA par tenant et par jour.
 *
 * <p>Sans ce garde-fou, un tenant pouvait dériver budget IA (un manager qui
 * spam "Générer fiche", ou un bug front qui appelle en boucle). Le compteur
 * s'appuie sur {@code ai_log} (1 ligne par appel IA sortant Anthropic).</p>
 *
 * <p>Seuil par defaut : 1000 appels/jour/tenant (≈ 1.5 EUR Haiku). A ajuster
 * via {@code leadrevive.ai.quota-par-jour-tenant}.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiQuotaGuard {

    private final AiLogRepository aiLogRepository;

    @Value("${leadrevive.ai.quota-par-jour-tenant:1000}")
    private int quotaParJour;

    /**
     * Verifie que le tenant n'a pas depasse son quota IA quotidien. Sinon lance
     * une {@link AiQuotaDepasseException} (mapped en 429 par le GlobalExceptionHandler
     * via {@link RuntimeException} - a typer plus precisement si besoin).
     */
    public void verifierQuotaJournalier(final Long tenantId) {
        if (tenantId == null) return;
        final LocalDateTime debutJour = LocalDate.now().atStartOfDay();
        final Long compte = aiLogRepository.countDepuis(tenantId, debutJour);
        if (compte != null && compte >= quotaParJour) {
            log.warn("Quota IA journalier depasse - tenantId: {}, appels: {}, quota: {}",
                    tenantId, compte, quotaParJour);
            throw new AiQuotaDepasseException("Quota IA journalier atteint (" + quotaParJour
                    + " appels). Réessayez demain ou contactez le support.");
        }
    }

    /** Lancée quand le tenant a dépassé son quota IA journalier. */
    public static class AiQuotaDepasseException extends RuntimeException {
        public AiQuotaDepasseException(final String message) { super(message); }
    }
}
