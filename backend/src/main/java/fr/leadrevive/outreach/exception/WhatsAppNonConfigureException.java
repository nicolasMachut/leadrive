package fr.leadrevive.outreach.exception;

import lombok.Getter;

/**
 * Levee quand un tenant tente d'utiliser WhatsApp sans avoir de configuration
 * en statut ACTIF.
 *
 * <p>Geree par {@code GlobalExceptionHandler} -> 422 Unprocessable Entity
 * avec body JSON {@code {code, message, action}}. Le frontend utilise le
 * champ {@code action} pour proposer un lien direct vers la page de
 * configuration.</p>
 */
@Getter
public class WhatsAppNonConfigureException extends RuntimeException {

    private final Long tenantId;

    public WhatsAppNonConfigureException(final Long tenantId) {
        super("WhatsApp n'est pas encore configure pour votre concession. "
            + "Rendez-vous dans Ma concession > WhatsApp Business pour activer votre numero.");
        this.tenantId = tenantId;
    }
}
