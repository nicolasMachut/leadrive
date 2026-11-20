package fr.leadrevive.common.security;

/**
 * Levee quand Clerk refuse une invitation parce que l'organisation a atteint son
 * quota de membres (code Clerk {@code organization_membership_quota_exceeded}).
 * Le compte inclut les membres actifs ET les invitations en attente.
 *
 * <p>C'est une condition permanente (pas transitoire) : elle est donc exclue du
 * retry Resilience4j (voir {@code ignore-exceptions} du bucket {@code clerkCall}
 * dans application.yml) et remontee en 409 avec un message clair par le
 * {@code GlobalExceptionHandler} plutot qu'en 500 avec le JSON brut de Clerk.
 */
public class OrganizationMembershipQuotaExceededException extends RuntimeException {

    public OrganizationMembershipQuotaExceededException(final String message) {
        super(message);
    }
}
