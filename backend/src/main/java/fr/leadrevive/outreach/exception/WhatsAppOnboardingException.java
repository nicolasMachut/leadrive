package fr.leadrevive.outreach.exception;

import lombok.Getter;

/**
 * Levee pendant le flow d'onboarding WhatsApp (saisie numero, validation code,
 * configuration profil) en cas d'erreur metier.
 *
 * <p>Geree par {@code GlobalExceptionHandler} -> 400 Bad Request avec body
 * JSON {@code {code, message}}. Le frontend utilise le {@code code} pour
 * afficher un message adapte (NUMERO_DEJA_PRIS_WHATSAPP, CODE_INVALIDE,
 * TROP_DE_TENTATIVES, CONFIG_IA_MANQUANTE, SERVICE_INDISPONIBLE, etc.).</p>
 */
@Getter
public class WhatsAppOnboardingException extends RuntimeException {

    private final String codeErreur;

    public WhatsAppOnboardingException(final String message) {
        this(message, "ERREUR_ONBOARDING");
    }

    public WhatsAppOnboardingException(final String message, final String codeErreur) {
        super(message);
        this.codeErreur = codeErreur;
    }
}
