package fr.leadrevive.outreach.integration;

import fr.leadrevive.outreach.config.TwilioConfig;
import fr.leadrevive.outreach.exception.WhatsAppOnboardingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Client REST pour la creation et la lecture des sub-accounts Twilio (epic 25).
 *
 * <p>Un sub-account Twilio est un compte isole par tenant : il a son propre
 * AccountSid + AuthToken, sa propre facturation (consolidee chez le master),
 * et permet d'isoler les WABAs entre concessions.</p>
 *
 * <p><strong>Securite</strong> : le {@code auth_token} retourne ne doit JAMAIS
 * etre logge ni mis en cache memoire en clair. Le service appelant le passe
 * directement au chiffrement via {@code SecretCryptoService} avant ecriture en BDD.</p>
 *
 * @see <a href="https://www.twilio.com/docs/iam/api/subaccounts">Twilio Subaccounts API</a>
 */
@Component
@Slf4j
public class TwilioSubAccountApiClient {

    private static final String BASE_URL = "https://api.twilio.com/2010-04-01";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public TwilioSubAccountApiClient(final TwilioConfig twilioConfig,
                                     final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        final String credentials = twilioConfig.getAccountSid() + ":" + twilioConfig.getAuthToken();
        final String basicAuth = "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, basicAuth)
                .build();
    }

    /**
     * Cree un nouveau sub-account Twilio.
     *
     * <p>Le {@code friendlyName} doit etre unique cote Twilio par master account.
     * On y inclut une cle d'idempotence (UUID v4) pour permettre de rejouer
     * l'appel sans creer de doublon : si le sub existe deja avec ce friendlyName,
     * on le retrouve via {@link #rechercherParFriendlyName(String)}.</p>
     */
    public SubAccountResponse creerSubAccount(final String friendlyName) {
        final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("FriendlyName", friendlyName);
        try {
            final String response = restClient.post()
                    .uri("/Accounts.json")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            final SubAccountResponse parsed = parser(response);
            // Securite : log SID seulement, jamais l'auth_token.
            log.info("Sub-account Twilio cree - sid: {}, friendlyName: {}",
                    parsed.sid(), friendlyName);
            return parsed;
        } catch (final RestClientResponseException ex) {
            log.error("Echec creation sub-account Twilio - friendlyName: {}, status: {}, body: {}",
                    friendlyName, ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new WhatsAppOnboardingException(
                    "Impossible de creer le compte Twilio dedie pour cet etablissement. "
                            + "Reessayez dans quelques minutes ou contactez le support.",
                    "SUB_ACCOUNT_CREATION_ECHEC");
        }
    }

    /**
     * Recherche un sub-account par friendlyName (utilise pour l'idempotence :
     * si la creation reussit cote Twilio mais qu'on perd la connexion BDD,
     * on retrouve le sub a partir de la cle).
     */
    public SubAccountResponse rechercherParFriendlyName(final String friendlyName) {
        try {
            final String response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/Accounts.json")
                            .queryParam("FriendlyName", friendlyName)
                            .build())
                    .retrieve()
                    .body(String.class);
            return parserListe(response);
        } catch (final RestClientResponseException ex) {
            log.warn("Echec recherche sub-account - friendlyName: {}, status: {}",
                    friendlyName, ex.getStatusCode().value());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private SubAccountResponse parser(final String json) {
        try {
            final Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
            return new SubAccountResponse(
                    (String) map.get("sid"),
                    (String) map.get("auth_token"),
                    (String) map.get("friendly_name"),
                    (String) map.get("status")
            );
        } catch (final Exception ex) {
            throw new WhatsAppOnboardingException(
                    "Reponse Twilio invalide lors de la creation du sub-account.",
                    "SUB_ACCOUNT_REPONSE_INVALIDE");
        }
    }

    @SuppressWarnings("unchecked")
    private SubAccountResponse parserListe(final String json) {
        try {
            final Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
            final Object accountsRaw = map.get("accounts");
            if (!(accountsRaw instanceof java.util.List<?> list) || list.isEmpty()) {
                return null;
            }
            final Map<String, Object> account = (Map<String, Object>) list.get(0);
            return new SubAccountResponse(
                    (String) account.get("sid"),
                    (String) account.get("auth_token"),
                    (String) account.get("friendly_name"),
                    (String) account.get("status")
            );
        } catch (final Exception ex) {
            return null;
        }
    }

    /**
     * Projection minimale d'un sub-account. Le {@code authToken} doit etre
     * chiffre AVANT toute persistance.
     */
    public record SubAccountResponse(String sid, String authToken, String friendlyName, String status) {
    }
}
