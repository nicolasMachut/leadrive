package fr.leadrevive.common.ratelimit;

/**
 * Exception levee quand un appelant depasse le quota d'un endpoint.
 * Mappee en HTTP 429 (Too Many Requests) par GlobalExceptionHandler.
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(final String message) {
        super(message);
    }
}
