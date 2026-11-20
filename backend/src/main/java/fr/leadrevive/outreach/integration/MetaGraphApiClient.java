package fr.leadrevive.outreach.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Client REST minimal pour l'API Meta Graph, utilise uniquement pour recuperer
 * le {@code verified_name} (nom affiche valide par Meta) d'un numero WhatsApp
 * apres l'Embedded Signup.
 *
 * <p>Flux : echange du {@code code} OAuth renvoye par l'Embedded Signup contre
 * un access token (app-id + app-secret), puis lecture du champ
 * {@code verified_name} sur le node {@code phone_number_id}.</p>
 *
 * <p><strong>Securite</strong> : l'app-secret n'est JAMAIS expose cote frontend.
 * Il vit uniquement ici, cote backend, via la variable d'env {@code FACEBOOK_APP_SECRET}.</p>
 */
@Component
@Slf4j
public class MetaGraphApiClient {

    private final ObjectMapper objectMapper;

    @Value("${leadrevive.facebook.app-id:}")
    private String appId;

    @Value("${leadrevive.facebook.app-secret:}")
    private String appSecret;

    @Value("${leadrevive.facebook.graph-version:v21.0}")
    private String graphVersion;

    public MetaGraphApiClient(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Les credentials backend Meta sont-ils configures ? */
    public boolean estConfigure() {
        return appId != null && !appId.isBlank() && appSecret != null && !appSecret.isBlank();
    }

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("https://graph.facebook.com/" + graphVersion)
                .build();
    }

    /**
     * Recupere le numero (E.164) et le nom certifie (verified_name) du WABA via
     * Graph. Enchaine l'echange du code OAuth contre un token puis la lecture du
     * node {@code {waba_id}/phone_numbers}. C'est la source fiable du numero ET du
     * nom, y compris quand l'Embedded Signup ne les renvoie pas (reconnexion /
     * FINISH_ONLY_WABA).
     *
     * @param code   code OAuth renvoye par l'Embedded Signup (FB.login)
     * @param wabaId WhatsApp Business Account ID
     * @return infos numero+nom, ou {@code null} si indisponible (non configure,
     *         parametres manquants, ou erreur Graph) - jamais une exception.
     */
    public WabaPhoneInfo recupererInfosNumero(final String code, final String wabaId) {
        if (!estConfigure()) {
            log.warn("Meta Graph non configure (FACEBOOK_APP_SECRET absent) - numero/nom non recuperes");
            return null;
        }
        if (code == null || code.isBlank() || wabaId == null || wabaId.isBlank()) {
            log.warn("Code OAuth ou waba_id manquant - numero/nom non recuperes");
            return null;
        }
        try {
            final String token = echangerCodeContreToken(code);
            if (token == null) {
                return null;
            }
            return lirePremierNumeroDuWaba(wabaId, token);
        } catch (final Exception ex) {
            log.warn("Echec recuperation numero/nom Meta - wabaId: {}, cause: {}", wabaId, ex.getMessage());
            return null;
        }
    }

    /**
     * Resout le WhatsApp Business Account ID a partir du seul {@code code} OAuth
     * de l'Embedded Signup, quand le front n'a pas recu le WABA via postMessage
     * (evenement {@code FINISH} non fiable). Enchaine l'echange du code contre un
     * token puis {@code /debug_token}, dont les {@code granular_scopes} du scope
     * {@code whatsapp_business_management} contiennent le(s) WABA partage(s).
     *
     * @param code code OAuth renvoye par l'Embedded Signup (FB.login)
     * @return le WABA ID, ou {@code null} si indisponible (non configure, code
     *         absent, ou erreur Graph) - jamais une exception.
     */
    public String resoudreWabaId(final String code) {
        if (!estConfigure()) {
            log.warn("Meta Graph non configure (FACEBOOK_APP_SECRET absent) - WABA non resolu depuis le code");
            return null;
        }
        if (code == null || code.isBlank()) {
            log.warn("Code OAuth manquant - WABA non resolu");
            return null;
        }
        try {
            final String token = echangerCodeContreToken(code);
            if (token == null) {
                return null;
            }
            return lireWabaIdDepuisToken(token);
        } catch (final Exception ex) {
            log.warn("Echec resolution WABA depuis le code Meta - cause: {}", ex.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String lireWabaIdDepuisToken(final String token) {
        try {
            final String reponse = client().get()
                    .uri(uriBuilder -> uriBuilder.path("/debug_token")
                            .queryParam("input_token", token)
                            // App access token = "{app-id}|{app-secret}" (backend uniquement).
                            .queryParam("access_token", appId + "|" + appSecret)
                            .build())
                    .retrieve()
                    .body(String.class);
            final Map<String, Object> map = objectMapper.readValue(reponse, new TypeReference<>() {});
            if (!(map.get("data") instanceof Map<?, ?> data)
                    || !(data.get("granular_scopes") instanceof List<?> scopes)) {
                log.warn("debug_token sans granular_scopes - WABA introuvable");
                return null;
            }
            for (final Object element : scopes) {
                if (!(element instanceof Map<?, ?> scope)) {
                    continue;
                }
                final Object nomScope = scope.get("scope");
                // Le WABA partage figure dans les scopes whatsapp_business_*.
                if (!"whatsapp_business_management".equals(nomScope)
                        && !"whatsapp_business_messaging".equals(nomScope)) {
                    continue;
                }
                if (scope.get("target_ids") instanceof List<?> ids && !ids.isEmpty()
                        && ids.get(0) instanceof String wabaId && !wabaId.isBlank()) {
                    return wabaId;
                }
            }
            log.warn("Aucun WABA id dans les granular_scopes du token Meta");
            return null;
        } catch (final RestClientResponseException ex) {
            log.warn("Appel debug_token Meta echoue - status: {}, body: {}",
                    ex.getStatusCode().value(), ex.getResponseBodyAsString());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private WabaPhoneInfo lirePremierNumeroDuWaba(final String wabaId, final String token) {
        try {
            final String reponse = client().get()
                    .uri(uriBuilder -> uriBuilder.path("/{id}/phone_numbers")
                            .queryParam("fields", "display_phone_number,verified_name,name_status")
                            .queryParam("access_token", token)
                            .build(wabaId))
                    .retrieve()
                    .body(String.class);
            final Map<String, Object> map = objectMapper.readValue(reponse, new TypeReference<>() {});
            if (!(map.get("data") instanceof List<?> data) || data.isEmpty()
                    || !(data.get(0) instanceof Map<?, ?> premier)) {
                log.warn("WABA sans numero WhatsApp - wabaId: {}", wabaId);
                return null;
            }
            final Map<String, Object> numero = (Map<String, Object>) premier;
            final String displayPhone = numero.get("display_phone_number") instanceof String s ? s : null;
            final String verifiedName = numero.get("verified_name") instanceof String s ? s : null;
            // name_status : statut d'examen Meta du nom affiche (APPROVED, PENDING_REVIEW,
            // DECLINED...). Permet de remonter l'etat des la connexion, sans attendre le
            // premier polling Twilio (WhatsAppSenderStatusJob, toutes les 10 min).
            final String nameStatus = numero.get("name_status") instanceof String s ? s : null;
            // display_phone_number est formate ("+33 7 44 ..."), on le normalise en E.164 strict.
            final String e164 = displayPhone == null ? null : displayPhone.replaceAll("[^0-9+]", "");
            if (e164 == null || e164.isBlank()) {
                return null;
            }
            return new WabaPhoneInfo(e164, verifiedName, nameStatus);
        } catch (final RestClientResponseException ex) {
            log.warn("Lecture phone_numbers WABA echouee - wabaId: {}, status: {}, body: {}",
                    wabaId, ex.getStatusCode().value(), ex.getResponseBodyAsString());
            return null;
        }
    }

    /** Numero E.164 + nom certifie Meta + statut d'examen du nom d'un WABA. */
    public record WabaPhoneInfo(String phoneNumber, String verifiedName, String nameStatus) {
    }

    private String echangerCodeContreToken(final String code) {
        try {
            final String reponse = client().get()
                    .uri(uriBuilder -> uriBuilder.path("/oauth/access_token")
                            .queryParam("client_id", appId)
                            .queryParam("client_secret", appSecret)
                            .queryParam("code", code)
                            .build())
                    .retrieve()
                    .body(String.class);
            final Map<String, Object> map = objectMapper.readValue(reponse, new TypeReference<>() {});
            final Object token = map.get("access_token");
            return token instanceof String s && !s.isBlank() ? s : null;
        } catch (final RestClientResponseException ex) {
            // appId loggue volontairement : c'est une donnee PUBLIQUE (presente dans
            // le bundle front via NEXT_PUBLIC_FACEBOOK_APP_ID). Permet de comparer
            // immediatement au client_id utilise cote front en cas d'"Invalid Client ID".
            // L'app-secret n'est JAMAIS loggue.
            log.warn("Echange code OAuth Meta echoue - appId backend: '{}', status: {}, body: {}",
                    appId, ex.getStatusCode().value(), ex.getResponseBodyAsString());
            return null;
        }
    }
}
