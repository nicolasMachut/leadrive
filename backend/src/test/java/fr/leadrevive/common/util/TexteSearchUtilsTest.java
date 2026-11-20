package fr.leadrevive.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de normalisation pour la recherche prospect inbox/leads.
 * Critique car les vendeurs tapent souvent les noms avec/sans accents.
 */
class TexteSearchUtilsTest {

    @Test
    void normaliser_supprimeAccents() {
        assertThat(TexteSearchUtils.normaliser("Côme")).isEqualTo("come");
        assertThat(TexteSearchUtils.normaliser("Hélène")).isEqualTo("helene");
        assertThat(TexteSearchUtils.normaliser("Naïve")).isEqualTo("naive");
        assertThat(TexteSearchUtils.normaliser("Çà va ?")).isEqualTo("ca va ?");
    }

    @Test
    void normaliser_passeEnMinuscules() {
        assertThat(TexteSearchUtils.normaliser("PEUGEOT")).isEqualTo("peugeot");
        assertThat(TexteSearchUtils.normaliser("BMW Série 1")).isEqualTo("bmw serie 1");
    }

    @Test
    void normaliser_trimEspaces() {
        assertThat(TexteSearchUtils.normaliser("  Jean  ")).isEqualTo("jean");
    }

    @Test
    void normaliser_chaineNulle_retourneVide() {
        assertThat(TexteSearchUtils.normaliser(null)).isEqualTo("");
    }

    @Test
    void containsNormalise_accentsIgnorees() {
        // Vrai cas client : vendeur tape "come" alors que le lead est "Côme Dubois".
        assertThat(TexteSearchUtils.containsNormalise("Côme Dubois", "come")).isTrue();
        assertThat(TexteSearchUtils.containsNormalise("Élise Martin", "ELISE")).isTrue();
        assertThat(TexteSearchUtils.containsNormalise("Peugeot 3008", "PEUGEOT")).isTrue();
    }

    @Test
    void containsNormalise_chaineNulle_retourneFalse() {
        assertThat(TexteSearchUtils.containsNormalise(null, "test")).isFalse();
        assertThat(TexteSearchUtils.containsNormalise("test", null)).isFalse();
    }

    @Test
    void containsNormalise_pasDeMatch_retourneFalse() {
        assertThat(TexteSearchUtils.containsNormalise("Jean", "Paul")).isFalse();
    }
}
