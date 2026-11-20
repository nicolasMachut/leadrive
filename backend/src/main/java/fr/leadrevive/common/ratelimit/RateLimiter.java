package fr.leadrevive.common.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiter en memoire, fixed-window (1 minute), base sur Caffeine.
 * Cle = identifiant de l'appelant (userId, IP, etc.) + nom du bucket.
 *
 * Limites : non distribue (chaque instance Railway a son propre compteur).
 * Pour Railway en single-instance c'est OK. Si scaling horizontal, migrer vers Redis.
 *
 * Usage :
 * <pre>
 *   if (!rateLimiter.tryAcquire("aide-chat:" + userId, 20)) {
 *       throw new RateLimitExceededException("Trop d'appels, reessayez dans 1 min");
 *   }
 * </pre>
 */
@Component
@Slf4j
public class RateLimiter {

    /**
     * Cache key = "bucket:cle:fenetreMinute". Valeur = compteur.
     * Expire automatiquement au bout de 2 minutes (1 min de fenetre + marge).
     */
    private final Cache<String, AtomicInteger> compteurs = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(2))
            .maximumSize(50_000)
            .build();

    /**
     * Tente d'acquerir un token pour la cle donnee. Retourne true si le quota n'est pas atteint.
     * Le compteur est par minute calendaire (fenetre fixe).
     *
     * @param key      identifiant de l'appelant + nom du bucket (ex: "aide-chat:user42")
     * @param maxPerMinute nombre maximum d'appels autorises dans la fenetre d'une minute
     */
    public boolean tryAcquire(final String key, final int maxPerMinute) {
        final long currentMinute = System.currentTimeMillis() / 60_000L;
        final String windowKey = key + ":" + currentMinute;
        final AtomicInteger counter = compteurs.get(windowKey, k -> new AtomicInteger(0));
        final int count = counter.incrementAndGet();
        if (count > maxPerMinute) {
            log.warn("Rate limit depasse - cle: {}, count: {}/{}", key, count, maxPerMinute);
            return false;
        }
        return true;
    }
}
