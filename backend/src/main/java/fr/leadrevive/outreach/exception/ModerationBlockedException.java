package fr.leadrevive.outreach.exception;

import lombok.Getter;

/**
 * Levee quand un message manuel (vendeur / manager) est bloque par la
 * moderation IA avant envoi.
 *
 * <p>Geree par {@code GlobalExceptionHandler} -> 422 Unprocessable Entity
 * avec body JSON {@code {code, message, raison}}. Le frontend affiche le
 * message inline dans le composer de l'inbox, avec la raison precise pour
 * que l'utilisateur reformule.</p>
 *
 * <p>Note : la moderation IA fonctionne en mode <strong>fail-open</strong> :
 * si l'IA est indisponible, le message est autorise (avec log warning).
 * Cette exception n'est donc levee que sur une decision IA explicite de
 * blocage.</p>
 */
@Getter
public class ModerationBlockedException extends RuntimeException {

    private final String raisonModeration;

    public ModerationBlockedException(final String raisonModeration) {
        super("Message bloque par la moderation : " + raisonModeration);
        this.raisonModeration = raisonModeration;
    }
}
