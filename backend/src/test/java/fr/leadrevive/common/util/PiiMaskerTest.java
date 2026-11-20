package fr.leadrevive.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PiiMaskerTest {

    // ===== email =====

    @Test
    void email_formatStandard_masqueLocalPart() {
        // GIVEN / WHEN / THEN
        assertEquals("k***@example.com", PiiMasker.email("karim@example.com"));
    }

    @Test
    void email_unSeulCharAvantArobase_garde1Char() {
        // GIVEN / WHEN / THEN
        assertEquals("a***@b.com", PiiMasker.email("a@b.com"));
    }

    @Test
    void email_avecSousDomaines_preserveDomaine() {
        // GIVEN / WHEN / THEN
        assertEquals("j***@mail.example.co.uk", PiiMasker.email("john.doe@mail.example.co.uk"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void email_nullOuVide_retournePlaceholder(final String input) {
        // GIVEN / WHEN / THEN
        assertEquals("[null]", PiiMasker.email(input));
    }

    @Test
    void email_sansArobase_retourneInvalid() {
        // GIVEN / WHEN / THEN
        assertEquals("[invalid-email]", PiiMasker.email("pas-un-email"));
    }

    @Test
    void email_arobaseAuDebut_retourneInvalid() {
        // GIVEN / WHEN / THEN
        // "@gmail.com" : pas de local-part
        assertEquals("[invalid-email]", PiiMasker.email("@gmail.com"));
    }

    // ===== phone =====

    @Test
    void phone_formatE164_garde4DerniersChiffres() {
        // GIVEN / WHEN / THEN
        assertEquals("****5678", PiiMasker.phone("+33612345678"));
    }

    @Test
    void phone_formatLocal_garde4DerniersChiffres() {
        // GIVEN / WHEN / THEN
        assertEquals("****5678", PiiMasker.phone("0612345678"));
    }

    @Test
    void phone_avecEspacesEtSeparateurs_lesIgnore() {
        // GIVEN / WHEN / THEN
        assertAll(
                () -> assertEquals("****5678", PiiMasker.phone("06 12 34 56 78")),
                () -> assertEquals("****5678", PiiMasker.phone("06.12.34.56.78"))
        );
    }

    @Test
    void phone_courtMoinsDe4Chiffres_retourneSeparateurs() {
        // GIVEN / WHEN / THEN
        assertEquals("****", PiiMasker.phone("123"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void phone_nullOuVide_retournePlaceholder(final String input) {
        // GIVEN / WHEN / THEN
        assertEquals("[null]", PiiMasker.phone(input));
    }

    @Test
    void phone_uniquementTexte_retourneSeparateurs() {
        // GIVEN / WHEN / THEN
        // 0 digit -> "****" (placeholder, pas de chiffres a montrer)
        assertEquals("****", PiiMasker.phone("abcdefgh"));
    }

    // ===== name =====

    @Test
    void name_deuxMots_masqueChaqueMot() {
        // GIVEN / WHEN / THEN
        assertEquals("M***n K***m", PiiMasker.name("Martin Karim"));
    }

    @Test
    void name_unSeulMot_masqueLeMot() {
        // GIVEN / WHEN / THEN
        assertEquals("E***a", PiiMasker.name("Eva"));
    }

    @Test
    void name_courtDeuxLettres_masqueTout() {
        // GIVEN / WHEN / THEN
        assertEquals("**", PiiMasker.name("Li"));
    }

    @Test
    void name_avecEspacesMultiples_lesIgnore() {
        // GIVEN / WHEN / THEN
        assertEquals("J***n D***t", PiiMasker.name("  Jean    Dupont  "));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void name_nullOuVide_retournePlaceholder(final String input) {
        // GIVEN / WHEN / THEN
        assertEquals("[null]", PiiMasker.name(input));
    }

    // ===== content =====

    @Test
    void content_messageLong_retourneTaille() {
        // GIVEN / WHEN / THEN
        assertEquals("[25 chars]", PiiMasker.content("Bonjour je suis interesse"));
    }

    @Test
    void content_messageVide_retourneTailleZero() {
        // GIVEN / WHEN / THEN
        assertEquals("[0 chars]", PiiMasker.content(""));
    }

    @Test
    void content_null_retournePlaceholder() {
        // GIVEN / WHEN / THEN
        assertEquals("[null]", PiiMasker.content(null));
    }

    @ParameterizedTest
    @CsvSource({
        "a, '[1 chars]'",
        "ab, '[2 chars]'",
        "abc, '[3 chars]'"
    })
    void content_messageCourt_retourneBonneTaille(final String input, final String expected) {
        // GIVEN / WHEN / THEN
        assertEquals(expected, PiiMasker.content(input));
    }
}
