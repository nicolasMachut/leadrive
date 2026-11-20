package fr.leadrevive.outreach.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests de capitalize() utilisee pour formater les noms dans les WhatsApp.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WhatsAppConversationServiceCapitalizeTest {

    @InjectMocks
    private WhatsAppConversationService service;

    @ParameterizedTest
    @CsvSource({
        "martin karim,           Martin Karim",
        "MARTIN KARIM,           Martin Karim",
        "Martin Karim,           Martin Karim",
        "mArTiN KaRiM,           Martin Karim",
        "jean,                    Jean",
        "JEAN,                    Jean"
    })
    void capitalize_majusculesMinusculesMelangees_retourneFormeNormalisee(final String input, final String expected) {
        // GIVEN / WHEN / THEN
        assertEquals(expected, service.capitalize(input));
    }

    @Test
    void capitalize_nomComposeAvecTiret_capitaliseChaquePartie() {
        // GIVEN / WHEN / THEN
        assertEquals("Jean-Pierre Dupont", service.capitalize("jean-pierre dupont"));
    }

    @Test
    void capitalize_nomComposeComplexe_capitaliseChaquePartie() {
        // GIVEN / WHEN / THEN
        assertEquals("Marie-Claire Durand-Dupont", service.capitalize("MARIE-CLAIRE DURAND-DUPONT"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void capitalize_nullOuVide_retourneBonjour(final String input) {
        // GIVEN / WHEN / THEN
        assertEquals("Bonjour", service.capitalize(input));
    }

    @Test
    void capitalize_avecEspacesAuxBords_lesIgnore() {
        // GIVEN / WHEN / THEN
        assertEquals("Martin Karim", service.capitalize("  martin karim  "));
    }

    @Test
    void capitalize_unSeulCaractere_capitaliseLaLettre() {
        // GIVEN / WHEN / THEN
        assertEquals("A", service.capitalize("a"));
    }

    @Test
    void capitalize_apostrophe_neDecoupePasLeMot() {
        // GIVEN / WHEN / THEN
        // L'apostrophe ne fait pas partie des delimiteurs
        assertEquals("D'artagnan Dupont", service.capitalize("d'artagnan dupont"));
    }
}
