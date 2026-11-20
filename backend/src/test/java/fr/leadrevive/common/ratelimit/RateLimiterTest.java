package fr.leadrevive.common.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RateLimiter();
    }

    @Test
    void tryAcquire_dansLaLimite_retourneTrue() {
        // GIVEN / WHEN / THEN
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.tryAcquire("user1", 5),
                    "Appel #" + (i + 1) + " sur 5 max doit passer");
        }
    }

    @Test
    void tryAcquire_auDelaDeLaLimite_retourneFalse() {
        // GIVEN
        for (int i = 0; i < 5; i++) {
            rateLimiter.tryAcquire("user1", 5);
        }

        // WHEN / THEN
        // 6e appel : doit etre bloque
        assertFalse(rateLimiter.tryAcquire("user1", 5));
    }

    @Test
    void tryAcquire_clesDistinctes_compteursIndependants() {
        // GIVEN
        for (int i = 0; i < 5; i++) {
            rateLimiter.tryAcquire("user1", 5);
        }

        // WHEN / THEN
        // user1 est sature, mais user2 a son propre compteur
        assertTrue(rateLimiter.tryAcquire("user2", 5));
    }

    @Test
    void tryAcquire_memeBucketDifferentMax_lesLimitesSontDistinctes() {
        // GIVEN
        rateLimiter.tryAcquire("user-a", 3);
        rateLimiter.tryAcquire("user-a", 3);
        rateLimiter.tryAcquire("user-a", 3);

        // WHEN / THEN
        assertAll(
                () -> assertFalse(rateLimiter.tryAcquire("user-a", 3)),
                // Avec un max plus grand sur la meme cle, le compteur courant (4) est compare a 10 : OK
                () -> assertTrue(rateLimiter.tryAcquire("user-a", 10))
        );
    }

    @Test
    void tryAcquire_limiteZero_bloqueDesLePremierAppel() {
        // GIVEN / WHEN / THEN
        assertFalse(rateLimiter.tryAcquire("user1", 0));
    }

    @Test
    void tryAcquire_limiteUn_seulPremierAppelPasse() {
        // GIVEN / WHEN / THEN
        assertAll(
                () -> assertTrue(rateLimiter.tryAcquire("user1", 1)),
                () -> assertFalse(rateLimiter.tryAcquire("user1", 1))
        );
    }
}
