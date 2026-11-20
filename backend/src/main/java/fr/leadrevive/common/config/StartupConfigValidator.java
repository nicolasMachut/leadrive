package fr.leadrevive.common.config;

import fr.leadrevive.outreach.config.TwilioConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Valide au demarrage (hors profil local) que toutes les variables d'environnement critiques sont configurees.
 * Si une variable manque, l'application s'arrete immediatement avec un message clair listant tous les problemes.
 * <p>
 * Le profil "local" est exclu pour permettre le dev sans toutes les integrations (Twilio, Clerk, etc.).
 */
@Component
@Profile("!local & !test")
@RequiredArgsConstructor
@Slf4j
public class StartupConfigValidator {

    private final TwilioConfig twilioConfig;

    @Value("${spring.ai.anthropic.api-key:}")
    private String anthropicApiKey;

    @Value("${clerk.jwks-uri:}")
    private String clerkJwksUri;

    @Value("${clerk.issuer:}")
    private String clerkIssuer;

    @Value("${clerk.secret-key:}")
    private String clerkSecretKey;

    @Value("${leadrevive.frontend-url:}")
    private String frontendUrl;

    @EventListener(ApplicationReadyEvent.class)
    public void validerConfiguration() {
        log.info("Validation de la configuration au demarrage");

        final List<String> erreurs = new ArrayList<>();

        // Claude / Anthropic
        verifier(erreurs, "ANTHROPIC_API_KEY", anthropicApiKey, "cle API Anthropic pour les generations IA");

        // Clerk (auth)
        verifier(erreurs, "CLERK_JWKS_URI", clerkJwksUri, "URL JWKS Clerk pour la verification des JWT");
        verifierNonPlaceholder(erreurs, "CLERK_JWKS_URI", clerkJwksUri, "placeholder.clerk.accounts.dev");
        verifier(erreurs, "CLERK_ISSUER", clerkIssuer, "issuer Clerk pour la verification des JWT");
        verifierNonPlaceholder(erreurs, "CLERK_ISSUER", clerkIssuer, "placeholder.clerk.accounts.dev");
        verifier(erreurs, "CLERK_SECRET_KEY", clerkSecretKey, "cle secrete Clerk pour l'API admin");

        // Frontend URL
        verifier(erreurs, "FRONTEND_URL", frontendUrl, "URL du front pour les redirections (invitations, etc.)");
        verifierNonPlaceholder(erreurs, "FRONTEND_URL", frontendUrl, "localhost");

        // Twilio (uniquement si pas en dry-run)
        // Note epic 23 : TWILIO_WHATSAPP_FROM (numero global) retire. Chaque tenant
        // a son propre numero (cf. TenantWhatsappConfig).
        // Note epic 25 : les template SIDs globaux ont ete retires. Chaque tenant a
        // ses propres SID dans tenant_whatsapp_template, resolus a l'execution.
        // On valide uniquement les credentials du master account (necessaires pour
        // Twilio.init lecture + creation sub-accounts).
        if (!twilioConfig.isDryRun()) {
            verifier(erreurs, "TWILIO_ACCOUNT_SID", twilioConfig.getAccountSid(), "SID du compte Twilio");
            verifier(erreurs, "TWILIO_AUTH_TOKEN", twilioConfig.getAuthToken(), "token d'authentification Twilio");
        } else {
            log.info("Twilio en mode dry-run : validation des variables Twilio ignoree");
        }

        if (!erreurs.isEmpty()) {
            final String message = construireMessageErreur(erreurs);
            log.error(message);
            throw new IllegalStateException(message);
        }

        log.info("Configuration validee : toutes les variables d'environnement requises sont presentes");
    }

    private void verifier(final List<String> erreurs, final String nom, final String valeur, final String description) {
        if (valeur == null || valeur.isBlank()) {
            erreurs.add(String.format("  - %s : manquante (%s)", nom, description));
        }
    }

    private void verifierNonPlaceholder(final List<String> erreurs, final String nom, final String valeur,
                                        final String placeholder) {
        if (valeur != null && !valeur.isBlank() && valeur.contains(placeholder)) {
            erreurs.add(String.format("  - %s : valeur placeholder detectee (contient '%s') — configurer la vraie valeur",
                    nom, placeholder));
        }
    }

    private String construireMessageErreur(final List<String> erreurs) {
        final StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("========================================================================\n");
        sb.append(" ERREUR : configuration incomplete — demarrage impossible\n");
        sb.append("========================================================================\n");
        sb.append(" Variables d'environnement manquantes ou invalides (").append(erreurs.size()).append(") :\n");
        erreurs.forEach(e -> sb.append(e).append("\n"));
        sb.append("========================================================================\n");
        sb.append(" Configurer ces variables sur l'environnement de deploiement (Railway),\n");
        sb.append(" puis redeployer l'application.\n");
        sb.append("========================================================================");
        return sb.toString();
    }
}
