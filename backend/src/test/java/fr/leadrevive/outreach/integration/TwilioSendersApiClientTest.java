package fr.leadrevive.outreach.integration;

import fr.leadrevive.outreach.exception.WhatsAppOnboardingException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests du mapping d'erreur Twilio Senders API.
 *
 * <p>Garde-fou anti-regression : un sender supprime cote Twilio renvoie un 404
 * avec un body {@code {"code":20404,...}}. Ce cas doit etre mappe sur le code
 * {@code SENDER_INTROUVABLE_TWILIO} (et non le generique {@code TWILIO_20404}),
 * sinon {@code WhatsAppSenderStatusJob} ne bascule jamais la config en ERREUR
 * et l'etablissement reste coince en EN_VALIDATION / ACTIF.</p>
 */
class TwilioSendersApiClientTest {

    private final TwilioSendersApiClient client =
            new TwilioSendersApiClient(JsonMapper.builder().build());

    private WhatsAppOnboardingException mapper(final int status, final String body) {
        final RestClientResponseException ex = new RestClientResponseException(
                "erreur", status, "", null, body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        return (WhatsAppOnboardingException) ReflectionTestUtils.invokeMethod(
                client, "mapperErreur", ex, "recupererStatut");
    }

    @Test
    void sender404_avecCode20404_estMappeEnSenderIntrouvable() {
        final WhatsAppOnboardingException result = mapper(404,
                "{\"code\":20404,\"message\":\"The requested resource ... was not found\",\"status\":404}");

        assertThat(result.getCodeErreur()).isEqualTo("SENDER_INTROUVABLE_TWILIO");
    }

    @Test
    void erreurMetierAvecAutreCode_resteGenerique() {
        final WhatsAppOnboardingException result = mapper(400,
                "{\"code\":21211,\"message\":\"Invalid 'To' phone number\"}");

        assertThat(result.getCodeErreur()).isEqualTo("TWILIO_21211");
    }

    @Test
    void numeroDejaEnregistre_63110_estMappeEnNumeroDejaPris() {
        final WhatsAppOnboardingException result = mapper(400,
                "{\"code\":63110,\"message\":\"The phone number is already registered on WhatsApp\",\"status\":400}");

        assertThat(result.getCodeErreur()).isEqualTo("NUMERO_DEJA_PRIS_WHATSAPP");
        assertThat(result.getMessage()).contains("deja enregistre sur WhatsApp");
    }

    @Test
    void validationCreationSender_63100_estMappeeVersMessageActionnable() {
        // Le 63100 "validation error" generique enveloppe souvent le numero deja
        // enregistre a la creation : on remonte un message actionnable, pas le brut.
        final WhatsAppOnboardingException result = mapper(400,
                "{\"code\":63100,\"message\":\"Request has failed due to validation error, "
                        + "please contact Twilio support for more details\",\"status\":400}");

        assertThat(result.getCodeErreur()).isEqualTo("NUMERO_DEJA_PRIS_WHATSAPP");
    }

    @Test
    void erreurServeurSansCode_estServiceIndisponible() {
        final WhatsAppOnboardingException result = mapper(503, "Service Unavailable");

        assertThat(result.getCodeErreur()).isEqualTo("SERVICE_INDISPONIBLE");
    }

    @Test
    void parserSender_extraitNameStatusDepuisProperties() {
        // Le nom peut etre en PENDING_REVIEW alors que le sender est deja ONLINE :
        // on doit remonter properties.name_status pour que la console admin le signale.
        final String json = "{\"sid\":\"XE123\",\"status\":\"ONLINE\","
                + "\"properties\":{\"quality_rating\":\"GREEN\",\"name_status\":\"PENDING_REVIEW\"},"
                + "\"profile\":{\"name\":\"Garage Pinson\"}}";

        final TwilioSendersApiClient.TwilioSenderResponse result =
                ReflectionTestUtils.invokeMethod(client, "parserSender", json);

        assertThat(result).isNotNull();
        assertThat(result.nameStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(result.status()).isEqualTo("ONLINE");
        assertThat(result.displayName()).isEqualTo("Garage Pinson");
    }

    @Test
    void parserSender_nameStatusAbsent_resteNull() {
        final String json = "{\"sid\":\"XE123\",\"status\":\"ONLINE\","
                + "\"properties\":{\"quality_rating\":\"GREEN\"}}";

        final TwilioSendersApiClient.TwilioSenderResponse result =
                ReflectionTestUtils.invokeMethod(client, "parserSender", json);

        assertThat(result).isNotNull();
        assertThat(result.nameStatus()).isNull();
    }
}
