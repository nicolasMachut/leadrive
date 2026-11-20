package fr.leadrevive.outreach.service;

import fr.leadrevive.outreach.config.TwilioConfig;
import fr.leadrevive.outreach.repository.TenantWhatsappConfigRepository;
import fr.leadrevive.outreach.repository.TenantWhatsappTemplateRepository;
import fr.leadrevive.outreach.template.WhatsAppTemplateLoader;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TwilioWhatsAppServiceTest {

    private TwilioWhatsAppService service;

    @BeforeEach
    void setUp() {
        // Environment vide (profil "test" non actif) → garde-fou dry-run OK
        final TwilioConfig config = new TwilioConfig(new MockEnvironment());
        config.setDryRun(true);
        config.setAssistantPrenom("Nora");
        final ObjectMapper objectMapper = JsonMapper.builder().build();
        service = new TwilioWhatsAppService(config, mock(TenantWhatsappConfigRepository.class),
                mock(TenantWhatsappTemplateRepository.class), mock(WhatsAppTemplateLoader.class), objectMapper);
    }

    // ===== formaterNumeroFr =====

    @ParameterizedTest
    @CsvSource({
        "0612345678,         +33612345678",
        "0712345678,         +33712345678",
        "+33612345678,       +33612345678",
        "06 12 34 56 78,     +33612345678",
        "06.12.34.56.78,     +33612345678",
        "06-12-34-56-78,     +33612345678",
        "(06) 12 34 56 78,   +33612345678"
    })
    void formaterNumeroFr_formatsVaries_normaliseEnE164(final String input, final String expected) {
        // GIVEN / WHEN / THEN
        assertEquals(expected, service.formaterNumeroFr(input));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void formaterNumeroFr_nullOuVide_retourneVide(final String input) {
        // GIVEN / WHEN / THEN
        assertEquals("", service.formaterNumeroFr(input));
    }

    @Test
    void formaterNumeroFr_numeroEtranger_garderLePlus() {
        // GIVEN / WHEN / THEN
        assertEquals("+447911123456", service.formaterNumeroFr("+447911123456"));
    }

    @Test
    void formaterNumeroFr_sansPrefixe_ajoute33() {
        // GIVEN / WHEN / THEN
        // Comportement actuel : si pas de + et pas commence par 0, prefixe +33
        assertEquals("+33612345678", service.formaterNumeroFr("612345678"));
    }

    // ===== estMobileFrancais =====

    @ParameterizedTest
    @ValueSource(strings = {"0612345678", "0712345678", "+33612345678", "+33712345678", "06 12 34 56 78"})
    void estMobileFrancais_mobileValide_retourneTrue(final String input) {
        // GIVEN / WHEN / THEN
        assertTrue(service.estMobileFrancais(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "0112345678",
        "0512345678",
        "0812345678",
        "+447911123456"
    })
    void estMobileFrancais_nonMobile_retourneFalse(final String input) {
        // GIVEN / WHEN / THEN
        assertFalse(service.estMobileFrancais(input));
    }

    @Test
    void estMobileFrancais_chiffresManquants_retourneFalse() {
        // GIVEN / WHEN / THEN
        assertFalse(service.estMobileFrancais("06123"));
    }

    // ===== appliquerVariables =====

    @Test
    void appliquerVariables_toutesLesVariablesPresentes_lesRemplace() {
        // GIVEN
        final String body = "Bonjour {{1}}, je suis {{2}} de {{3}}.";
        final Map<String, String> vars = Map.of(
                "1", "Martin",
                "2", "Nora",
                "3", "Garage Dupont"
        );

        // WHEN
        final String actual = service.appliquerVariables(body, vars);

        // THEN
        assertEquals("Bonjour Martin, je suis Nora de Garage Dupont.", actual);
    }

    @Test
    void appliquerVariables_bodyNull_retourneNull() {
        // GIVEN / WHEN / THEN
        assertNull(service.appliquerVariables(null, Map.of("1", "x")));
    }

    @Test
    void appliquerVariables_variableAbsente_laisseLePlaceholder() {
        // GIVEN / WHEN
        final String actual = service.appliquerVariables("{{1}} {{2}}", Map.of("1", "Hi"));

        // THEN
        assertEquals("Hi {{2}}", actual);
    }

    @Test
    void appliquerVariables_variableRepetee_remplaceToutesLesOccurrences() {
        // GIVEN / WHEN
        final String actual = service.appliquerVariables("{{1}} et {{1}}", Map.of("1", "Nora"));

        // THEN
        assertEquals("Nora et Nora", actual);
    }

    // ===== getAssistantPrenom =====

    @Test
    void getAssistantPrenom_retourneLaConfig() {
        // GIVEN / WHEN / THEN
        assertEquals("Nora", service.getAssistantPrenom());
    }

}
