package fr.leadrevive.outreach.config;

import com.twilio.Twilio;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;

@Configuration
@ConfigurationProperties(prefix = "leadrevive.twilio")
@RequiredArgsConstructor
@Getter
@Setter
@Slf4j
public class TwilioConfig {

    private final Environment environment;

    private String accountSid;
    private String authToken;
    // Note epic 23 : pas de champ whatsappFrom global - resolution par tenant via
    // TenantWhatsappConfigRepository.findByTenantId(). La variable d'env
    // TWILIO_WHATSAPP_FROM et la property leadrevive.twilio.whatsapp-from ont ete
    // retirees (cf. application.yml).
    private boolean dryRun = true;
    // Note epic 25 : les 4 champs template*Sid globaux ont ete supprimes.
    // Chaque tenant a desormais ses propres SID dans tenant_whatsapp_template,
    // resolus a l'execution via TenantWhatsappTemplateRepository.
    private String assistantPrenom = "Nora";

    @PostConstruct
    void initTwilio() {
        // Garde-fou de securite : interdit le mode dry-run en production.
        // Sans ce check, si TWILIO_DRY_RUN=true fuit en prod par erreur de config,
        // tous les envois seraient silencieusement ignores ET le code OTP de
        // l'onboarding accepterait "000000" - permettant a un attaquant d'activer
        // n'importe quel numero. Fail-fast au demarrage plutot que silencieux.
        final boolean enProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (dryRun && enProd) {
            throw new IllegalStateException(
                    "TWILIO_DRY_RUN=true en profil prod - refus de demarrer (faille securite onboarding). "
                            + "Verifiez la variable d'environnement TWILIO_DRY_RUN sur le serveur de prod.");
        }

        // Initialisation du SDK Twilio des que les credentials sont presents
        // (meme en dry-run cote dev, pour permettre les appels en lecture
        // comme fetch template). Le flag dryRun controle uniquement l'envoi
        // effectif des messages.
        if (accountSid != null && !accountSid.isBlank() && authToken != null && !authToken.isBlank()) {
            Twilio.init(accountSid, authToken);
            log.info("Twilio initialise - compte: {}, dryRun: {}, profile: {}",
                    accountSid, dryRun, String.join(",", environment.getActiveProfiles()));
        } else {
            log.info("Twilio non initialise (credentials absents)");
        }
    }
}
