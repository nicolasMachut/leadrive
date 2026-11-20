package fr.leadrevive.lead.exception;

/**
 * Levee lorsqu'on tente de desarchiver un lead alors qu'un lead ACTIF du meme tenant
 * partage deja son telephone ou son email. Le desarchivage violerait l'index unique
 * {@code uq_lead_tenant_telephone_actif} / {@code uq_lead_tenant_email_actif} (V068).
 * Mappee en 409 CONFLICT par le GlobalExceptionHandler avec un message user-facing.
 */
public class LeadActifExistantException extends RuntimeException {

    public LeadActifExistantException(final String message) {
        super(message);
    }
}
