package fr.leadrevive.outreach.integration;

import fr.leadrevive.outreach.exception.WhatsAppOnboardingException;
import fr.leadrevive.outreach.template.WhatsAppTemplateDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client REST pour Twilio Content API (epic 25).
 *
 * <p>Soumission + polling des templates WhatsApp Meta. Contrairement au
 * {@link TwilioSendersApiClient} qui utilise les credentials master, ce client
 * accepte des credentials par appel : chaque tenant a son propre sub-account
 * Twilio, et les templates appartiennent au sub-account.</p>
 *
 * <p>Les credentials passes en parametre sont assembles en Basic Auth a la
 * volee. <strong>Le token n'est jamais persiste ni log.</strong></p>
 *
 * @see <a href="https://www.twilio.com/docs/content/content-api-resources">Twilio Content API</a>
 */
@Component
@Slf4j
public class TwilioContentApiClient {

    private static final String BASE_URL = "https://content.twilio.com/v1";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public TwilioContentApiClient(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // Pas de Basic Auth header par defaut : on l'ajoute per-request avec
        // les credentials du sub-account du tenant courant.
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .build();
    }

    /**
     * Cree un Content (template) cote Twilio. Le SID retourne devra ensuite
     * etre soumis a Meta pour approbation via {@link #soumettreApprobationWhatsApp}.
     */
    public ContentResponse creerContent(final String subAccountSid,
                                          final String subAuthToken,
                                          final WhatsAppTemplateDefinition definition) {
        final Map<String, Object> body = construirePayloadContent(definition);
        try {
            final String response = restClient.post()
                    .uri("/Content")
                    .header(HttpHeaders.AUTHORIZATION, basicAuth(subAccountSid, subAuthToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toJson(body))
                    .retrieve()
                    .body(String.class);
            final ContentResponse parsed = parserContent(response);
            log.info("Twilio Content cree - sid: {}, friendlyName: {}",
                    parsed.sid(), definition.name());
            return parsed;
        } catch (final RestClientResponseException ex) {
            throw mapperErreur(ex, "creerContent");
        }
    }

    /**
     * Soumet un Content pour approbation Meta. La validation Meta prend de
     * quelques minutes a 24h ; on poll ensuite via {@link #recupererApprobations}.
     */
    public void soumettreApprobationWhatsApp(final String subAccountSid,
                                               final String subAuthToken,
                                               final String contentSid,
                                               final String templateName,
                                               final WhatsAppTemplateDefinition.Category category) {
        final Map<String, Object> body = Map.of(
                "name", templateName,
                "category", category.name()
        );
        try {
            restClient.post()
                    .uri("/Content/{sid}/ApprovalRequests/whatsapp", contentSid)
                    .header(HttpHeaders.AUTHORIZATION, basicAuth(subAccountSid, subAuthToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toJson(body))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Approbation WhatsApp soumise - contentSid: {}, name: {}, category: {}",
                    contentSid, templateName, category);
        } catch (final RestClientResponseException ex) {
            throw mapperErreur(ex, "soumettreApprobationWhatsApp");
        }
    }

    /**
     * Recupere l'etat d'approbation Meta d'un Content. Retourne le premier
     * approval matching (un Content peut avoir plusieurs approvals si re-soumis).
     */
    public ApprovalStatus recupererApprobations(final String subAccountSid,
                                                  final String subAuthToken,
                                                  final String contentSid) {
        try {
            final String response = restClient.get()
                    .uri("/Content/{sid}/ApprovalRequests", contentSid)
                    .header(HttpHeaders.AUTHORIZATION, basicAuth(subAccountSid, subAuthToken))
                    .retrieve()
                    .body(String.class);
            return parserApproval(response);
        } catch (final RestClientResponseException ex) {
            throw mapperErreur(ex, "recupererApprobations");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Convertit la {@link WhatsAppTemplateDefinition} en payload Twilio Content API.
     * <p>Pour le MVP on supporte uniquement les templates texte (BODY + optional
     * FOOTER). Les variables {{1}}, {{2}}... sont passees dans le champ
     * {@code variables} en tant qu'exemples.</p>
     */
    private Map<String, Object> construirePayloadContent(final WhatsAppTemplateDefinition def) {
        final String body = def.components().stream()
                .filter(c -> c.type() == WhatsAppTemplateDefinition.ComponentType.BODY)
                .map(WhatsAppTemplateDefinition.Component::text)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Template " + def.name() + " sans BODY"));

        // Variables : map "1" -> "Jean", "2" -> "Peugeot 308"... (exemples Meta)
        final WhatsAppTemplateDefinition.Component bodyComp = def.components().stream()
                .filter(c -> c.type() == WhatsAppTemplateDefinition.ComponentType.BODY)
                .findFirst()
                .orElseThrow();
        final Map<String, String> variables = new LinkedHashMap<>();
        for (int i = 0; i < bodyComp.variables().size(); i++) {
            variables.put(String.valueOf(i + 1), bodyComp.variables().get(i).example());
        }

        // Type Twilio : pour BODY seul on utilise twilio/text. Pour BODY + FOOTER
        // (ex: rappel RDV), idem (FOOTER est inclus dans body en pratique).
        final Map<String, Object> twilioText = new HashMap<>();
        twilioText.put("body", body);

        final Map<String, Object> payload = new HashMap<>();
        payload.put("friendly_name", def.name());
        payload.put("language", def.language());
        payload.put("variables", variables);
        payload.put("types", Map.of("twilio/text", twilioText));
        return payload;
    }

    private String basicAuth(final String sid, final String token) {
        final String creds = sid + ":" + token;
        return "Basic " + Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
    }

    private String toJson(final Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (final Exception e) {
            throw new WhatsAppOnboardingException(
                    "Erreur de serialisation du payload Twilio Content: " + e.getMessage(),
                    "ERREUR_INTERNE");
        }
    }

    @SuppressWarnings("unchecked")
    private ContentResponse parserContent(final String json) {
        try {
            final Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
            return new ContentResponse(
                    (String) map.get("sid"),
                    (String) map.get("friendly_name"),
                    (String) map.get("language")
            );
        } catch (final Exception e) {
            throw new WhatsAppOnboardingException(
                    "Reponse Twilio Content invalide: " + e.getMessage(),
                    "REPONSE_INVALIDE");
        }
    }

    /**
     * Parse la reponse {@code GET /Content/{sid}/ApprovalRequests}. La struct
     * Twilio est : {@code { whatsapp: { status, rejection_reason, ... } }}.
     */
    @SuppressWarnings("unchecked")
    private ApprovalStatus parserApproval(final String json) {
        try {
            final Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
            final Map<String, Object> whatsapp = castMap(map.get("whatsapp"));
            if (whatsapp == null) {
                return new ApprovalStatus(null, null);
            }
            final String status = toStr(whatsapp.get("status"));
            final String reason = toStr(whatsapp.get("rejection_reason"));
            return new ApprovalStatus(status, reason);
        } catch (final Exception e) {
            log.warn("Reponse Twilio Approval non parsable - cause: {}", e.getMessage());
            return new ApprovalStatus(null, null);
        }
    }

    private WhatsAppOnboardingException mapperErreur(final RestClientResponseException ex, final String operation) {
        log.error("Erreur Twilio Content API - operation: {}, status: {}, body: {}",
                operation, ex.getStatusCode().value(), ex.getResponseBodyAsString());
        // 401/403 -> credentials invalides (token sub-account expire / revoque).
        if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
            return new WhatsAppOnboardingException(
                    "Authentification Twilio echouee pour cet etablissement. "
                    + "Le compte dedie peut etre revoque. Contactez le support.",
                    "AUTH_TWILIO_INVALIDE");
        }
        return new WhatsAppOnboardingException(
                "Echec Twilio Content API (" + ex.getStatusCode().value() + ") sur " + operation,
                "TWILIO_CONTENT_ERREUR");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(final Object o) {
        return o instanceof Map<?, ?> ? (Map<String, Object>) o : null;
    }

    private static String toStr(final Object o) {
        return o == null ? null : o.toString();
    }

    public record ContentResponse(String sid, String friendlyName, String language) {
    }

    /**
     * Statut Meta du template (Twilio Content Approval Requests).
     * Valeurs possibles cote Twilio : {@code received}, {@code pending},
     * {@code approved}, {@code rejected}, {@code paused}, {@code disabled}.
     */
    public record ApprovalStatus(String twilioStatus, String rejectionReason) {
        public boolean approuve() {
            return "approved".equalsIgnoreCase(twilioStatus);
        }

        public boolean rejete() {
            return "rejected".equalsIgnoreCase(twilioStatus);
        }

        @SuppressWarnings("unused")
        private static List<String> statutsAttente() {
            // Inutile pour le moment, garde sous la main pour eviter un import unused.
            return new ArrayList<>(List.of("received", "pending"));
        }
    }
}
