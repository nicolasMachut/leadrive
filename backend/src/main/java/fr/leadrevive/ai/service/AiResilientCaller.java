package fr.leadrevive.ai.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Wrapper Resilience4j pour les appels a Anthropic/Spring AI.
 *
 * - @Retry : 3 tentatives avec backoff exponentiel (1s, 2s, 4s) sur erreurs transitoires (5xx, timeout, IO).
 *   Les IllegalArgumentException/IllegalStateException ne sont PAS retentees (erreurs de programmation).
 * - @CircuitBreaker : si > 50% d'echecs sur les 10 derniers appels, le circuit s'ouvre 60s
 *   pour eviter le cascading sous panne longue.
 *
 * Pourquoi un service dedie : les annotations Resilience4j Spring (AOP via proxy) ne fonctionnent
 * que si la methode est appelee depuis un AUTRE bean. Un wrapper centralise resout le probleme,
 * harmonise la config retry, et facilite le tracking des echecs.
 *
 * Usage :
 * <pre>
 *   final String reponse = aiResilientCaller.executer(() ->
 *       chatClient.prompt().messages(messages).call().content());
 * </pre>
 *
 * Si l'appel echoue apres tous les retries OU si le circuit est ouvert, l'appelant attrape
 * l'exception et applique son propre fallback metier (message d'attente, bascule HUMAIN, etc.).
 */
@Service
@Slf4j
public class AiResilientCaller {

    @Retry(name = "aiCall")
    @CircuitBreaker(name = "aiCall")
    public <T> T executer(final Supplier<T> action) {
        return action.get();
    }
}
