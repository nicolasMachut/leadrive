package fr.leadrevive.outreach.integration;

import fr.leadrevive.outreach.exception.WhatsAppOnboardingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client REST pour l'API Twilio "WhatsApp Senders" (v2).
 *
 * <p>Depuis le sunset LEGACY (V064), ce client n'est plus utilise pour
 * l'onboarding (le flow Self Sign-up passe par Facebook Embedded Signup).
 * Il reste utilise pour :</p>
 * <ul>
 *   <li>{@link #recupererStatut} : polling du statut Meta (qualityRating,
 *       messagingLimit, metaStatus) par le {@code WhatsAppSenderStatusJob}.</li>
 *   <li>{@link #supprimerSender} : nettoyage Twilio lors de la suppression
 *       d'un tenant par l'admin.</li>
 * </ul>
 *
 * <p><strong>Auth</strong> : chaque appel recoit les credentials du sub-account
 * Twilio du tenant (plus de master credentials globaux). Le {@code RestClient}
 * est construit a la volee pour chaque requete.</p>
 *
 * @see <a href="https://www.twilio.com/docs/whatsapp/api/senders">Twilio WhatsApp Senders API</a>
 */
@Component
@Slf4j
public class TwilioSendersApiClient {

    private static final String BASE_URL = "https://messaging.twilio.com/v2";

    private final ObjectMapper objectMapper;

    public TwilioSendersApiClient(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ====================================================================
    // Polling du statut (utilise par WhatsAppSenderStatusJob)
    // ====================================================================

    /** Recupere le statut courant du sender chez Twilio avec les credentials du sub-account. */
    public TwilioSenderResponse recupererStatut(final String senderSid,
                                                 final String subAccountSid,
                                                 final String subAuthToken) {
        try {
            final String response = buildClient(subAccountSid, subAuthToken).get()
                    .uri("/Channels/Senders/{sid}", senderSid)
                    .retrieve()
                    .body(String.class);
            return parserSender(response);
        } catch (final RestClientResponseException ex) {
            throw mapperErreur(ex, "recupererStatut");
        }
    }

    // ====================================================================
    // Suppression du sender (nettoyage admin)
    // ====================================================================

    /**
     * Supprime un sender cote Twilio. Utilise lors de la suppression d'un tenant
     * pour eviter les orphelins.
     *
     * <p>Idempotent : un 404 (sender deja supprime ou inexistant) n'est pas
     * une erreur, on log et on retourne.</p>
     */
    public void supprimerSender(final String senderSid,
                                 final String subAccountSid,
                                 final String subAuthToken) {
        try {
            buildClient(subAccountSid, subAuthToken).delete()
                    .uri("/Channels/Senders/{sid}", senderSid)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Sender Twilio supprime - sid: {}", senderSid);
        } catch (final RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                log.info("Sender Twilio deja inexistant (404), idempotent OK - sid: {}", senderSid);
                return;
            }
            throw mapperErreur(ex, "supprimerSender");
        }
    }

    // ====================================================================
    // Creation du sender (post Embedded Signup)
    // ====================================================================

    /**
     * Cree (enregistre) un WhatsApp Sender chez Twilio pour un numero deja
     * rattache a une WABA Meta via l'Embedded Signup. Twilio NE cree PAS le
     * sender automatiquement : c'est a l'integrateur de le faire via cette API
     * (cf. doc Tech Provider). Le SID {@code XE...} retourne doit etre persiste
     * pour permettre le polling de statut.
     *
     * @param numeroE164        numero WhatsApp en E.164 (ex: +33...)
     * @param wabaId            WhatsApp Business Account ID (Meta), de l'Embedded Signup
     * @param profileName       nom affiche (regles display name Meta), peut etre null
     * @param callbackUrl       URL des messages entrants (peut etre null en local)
     * @param fallbackUrl       URL de secours des messages entrants (peut etre null)
     * @param statusCallbackUrl URL des status callbacks (peut etre null en local)
     * @param subAccountSid     SID du sous-compte Twilio du tenant
     * @param subAuthToken      auth token du sous-compte (dechiffre a l'appel)
     * @return SID {@code XE...} + statut initial (typiquement {@code CREATING})
     */
    public SenderCreationResponse creerSender(final String numeroE164,
                                              final String wabaId,
                                              final String profileName,
                                              final String callbackUrl,
                                              final String fallbackUrl,
                                              final String statusCallbackUrl,
                                              final String subAccountSid,
                                              final String subAuthToken) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("sender_id", "whatsapp:" + numeroE164);

        final Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("waba_id", wabaId);
        body.put("configuration", configuration);

        if (profileName != null && !profileName.isBlank()) {
            final Map<String, Object> profile = new LinkedHashMap<>();
            profile.put("name", profileName);
            body.put("profile", profile);
        }
        final Map<String, Object> webhook = new LinkedHashMap<>();
        if (callbackUrl != null && !callbackUrl.isBlank()) {
            webhook.put("callback_url", callbackUrl);
            webhook.put("callback_method", "POST");
        }
        if (fallbackUrl != null && !fallbackUrl.isBlank()) {
            webhook.put("fallback_url", fallbackUrl);
            webhook.put("fallback_method", "POST");
        }
        if (statusCallbackUrl != null && !statusCallbackUrl.isBlank()) {
            webhook.put("status_callback_url", statusCallbackUrl);
            webhook.put("status_callback_method", "POST");
        }
        if (!webhook.isEmpty()) {
            body.put("webhook", webhook);
        }

        try {
            final String response = buildClient(subAccountSid, subAuthToken).post()
                    .uri("/Channels/Senders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parserCreation(response);
        } catch (final RestClientResponseException ex) {
            throw mapperErreur(ex, "creerSender");
        }
    }

    private SenderCreationResponse parserCreation(final String json) {
        if (json == null || json.isBlank()) {
            throw new WhatsAppOnboardingException("Reponse Twilio vide", "REPONSE_VIDE");
        }
        try {
            final Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
            return new SenderCreationResponse((String) map.get("sid"), (String) map.get("status"));
        } catch (final Exception e) {
            log.warn("Reponse creation sender non parsable - cause: {}", e.getMessage());
            throw new WhatsAppOnboardingException(
                    "Reponse Twilio invalide a la creation du sender: " + e.getMessage(), "REPONSE_INVALIDE");
        }
    }

    /** SID + statut initial retournes a la creation d'un sender. */
    public record SenderCreationResponse(String sid, String status) {
    }

    // ====================================================================
    // Construction du client REST per-tenant
    // ====================================================================

    private RestClient buildClient(final String accountSid, final String authToken) {
        final String credentials = accountSid + ":" + authToken;
        final String basicAuth = "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, basicAuth)
                .build();
    }

    // ====================================================================
    // Parsing & mapping erreurs
    // ====================================================================

    private TwilioSenderResponse parserSender(final String json) {
        if (json == null || json.isBlank()) {
            throw new WhatsAppOnboardingException("Reponse Twilio vide", "REPONSE_VIDE");
        }
        try {
            final Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
            final String sid = (String) map.get("sid");
            final String status = (String) map.get("status");

            // properties.quality_rating (GREEN / YELLOW / RED) et properties.messaging_limit
            // (string libre type "10K Customers/24hr", PAS un entier ni "messaging_limit_tier").
            final Map<String, Object> properties = castMap(map.get("properties"));
            final String qualityRating = properties != null ? (String) properties.get("quality_rating") : null;
            final String messagingLimit = properties != null ? toStr(properties.get("messaging_limit")) : null;
            // properties.name_status : statut d'examen Meta du nom affiche (APPROVED,
            // PENDING_REVIEW, DECLINED, AVAILABLE_WITHOUT_REVIEW, EXPIRED, NONE). Distinct
            // du statut du sender (status) et de la qualite : le nom peut etre en examen
            // alors que le compte est deja ONLINE (le prospect voit alors le numero brut).
            final String nameStatus = properties != null ? (String) properties.get("name_status") : null;

            // Le display name approuve est dans profile.name (GET), pas dans configuration.display_name.
            final Map<String, Object> profile = castMap(map.get("profile"));
            final String displayName = profile != null ? (String) profile.get("name") : null;

            // Les raisons de refus / offline sont dans offline_reasons[] au TOP-LEVEL.
            final String rejectReason = extraireOfflineReasons(map.get("offline_reasons"));

            return new TwilioSenderResponse(
                    sid, status, qualityRating, messagingLimit, displayName, rejectReason, nameStatus);
        } catch (final Exception e) {
            log.warn("Reponse Twilio non parsable - cause: {}", e.getMessage());
            throw new WhatsAppOnboardingException("Reponse Twilio invalide: " + e.getMessage(), "REPONSE_INVALIDE");
        }
    }

    /**
     * Concatene les raisons listees dans offline_reasons (array d'objets renvoye par Twilio
     * quand le sender est en OFFLINE/REJECTED).
     */
    @SuppressWarnings("unchecked")
    private String extraireOfflineReasons(final Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) return null;
        final List<String> messages = new java.util.ArrayList<>();
        for (final Object item : list) {
            if (item instanceof Map<?, ?> m) {
                final Object msg = ((Map<String, Object>) m).get("error_message");
                if (msg instanceof String s && !s.isBlank()) {
                    messages.add(s);
                    continue;
                }
                final Object type = ((Map<String, Object>) m).get("type");
                if (type instanceof String s && !s.isBlank()) messages.add(s);
            } else if (item instanceof String s) {
                messages.add(s);
            }
        }
        return messages.isEmpty() ? null : String.join(" · ", messages);
    }

    private static String toStr(final Object o) {
        return o == null ? null : o.toString();
    }

    private WhatsAppOnboardingException mapperErreur(final RestClientResponseException ex, final String operation) {
        final HttpStatusCode statusCode = ex.getStatusCode();
        final String body = ex.getResponseBodyAsString();
        log.error("Erreur Twilio Senders API - operation: {}, status: {}, body: {}",
                operation, statusCode.value(), body);

        Integer twilioCode = null;
        String twilioMessage = null;
        try {
            final Map<String, Object> errorMap = objectMapper.readValue(body, new TypeReference<>() {});
            twilioCode = toInt(errorMap.get("code"));
            twilioMessage = (String) errorMap.get("message");
        } catch (final Exception ignore) { /* body non JSON, on garde le default */ }

        // Un sender supprime / introuvable cote Twilio se manifeste par un 404
        // (code Twilio 20404). Ce cas DOIT etre mappe AVANT le mapping generique
        // "TWILIO_<code>" : sinon, comme le body Twilio porte toujours un "code",
        // on retombait sur "TWILIO_20404" et le sender orphelin n'etait jamais
        // reconnu -> WhatsAppSenderStatusJob ne basculait pas la config en ERREUR.
        if (statusCode.value() == 404 || (twilioCode != null && twilioCode == 20404)) {
            return new WhatsAppOnboardingException(
                    "Le sender Twilio reference en base n'existe plus cote Twilio. "
                            + "Il faut relancer l'onboarding WhatsApp pour cet etablissement.",
                    "SENDER_INTROUVABLE_TWILIO");
        }

        // 63110 = "phone number is already registered on WhatsApp". Twilio tente
        // d'enregistrer le numero (RequestCode) mais il est deja enregistre sur
        // WhatsApp (app WhatsApp Business, autre outil, ou enregistrement residuel).
        // Le 63100 "validation error" generique enveloppe souvent la meme cause a
        // la creation : on les regroupe vers un message actionnable plutot que le
        // "contact Twilio support" brut, illisible pour l'operateur.
        if (twilioCode != null && (twilioCode == 63110 || twilioCode == 63100)) {
            return new WhatsAppOnboardingException(
                    "Ce numero est deja enregistre sur WhatsApp. Supprimez d'abord le compte "
                            + "WhatsApp existant sur ce numero (application WhatsApp ou WhatsApp Business), "
                            + "puis reessayez, ou utilisez un numero dedie. Si vous venez de faire "
                            + "plusieurs tentatives, patientez quelques minutes avant de reessayer.",
                    "NUMERO_DEJA_PRIS_WHATSAPP");
        }

        if (twilioCode != null) {
            return new WhatsAppOnboardingException(
                    twilioMessage != null ? twilioMessage : "Erreur Twilio (" + twilioCode + ")",
                    "TWILIO_" + twilioCode);
        }

        if (statusCode.is5xxServerError()) {
            return new WhatsAppOnboardingException(
                    "Service WhatsApp temporairement indisponible. Reessayez dans quelques minutes.",
                    "SERVICE_INDISPONIBLE");
        }
        return new WhatsAppOnboardingException(
                "Erreur Twilio (" + statusCode.value() + ") sur " + operation,
                "ERREUR_TWILIO");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(final Object o) {
        return o instanceof Map<?, ?> ? (Map<String, Object>) o : null;
    }

    private static Integer toInt(final Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(o.toString()); } catch (final NumberFormatException e) { return null; }
    }

    /**
     * Projection minimale d'une reponse sender Twilio.
     *
     * <p>{@code messagingLimit} : string libre type "10K Customers/24hr" (cle Twilio
     * {@code messaging_limit}). </p>
     */
    public record TwilioSenderResponse(
            String sid,
            String status,
            String qualityRating,
            String messagingLimit,
            String displayName,
            String rejectReason,
            String nameStatus
    ) {}
}
