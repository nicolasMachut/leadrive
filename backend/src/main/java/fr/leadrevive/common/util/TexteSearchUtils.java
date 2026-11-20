package fr.leadrevive.common.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Utilitaires de normalisation pour la recherche textuelle insensible aux accents
 * et à la casse. Utilisé par les filtres de recherche prospect (inbox, leads).
 *
 * <p>Sans cette normalisation, taper "come" ne retrouve pas "Côme" (caractères latins
 * étendus), et taper "PEUGEOT" ne retrouve pas "peugeot" si la BDD n'a pas la collation
 * adéquate. C'est une attente UX standard sur les CRM.
 */
public final class TexteSearchUtils {

    private TexteSearchUtils() {}

    /**
     * Normalise une chaîne pour comparaison : décomposition Unicode (NFD) + suppression
     * des diacritiques + minuscules + trim. Retourne une chaîne vide si l'entrée est null.
     *
     * <p>Exemples : "Côme" -> "come", "PEUGEOT" -> "peugeot", null -> "".
     */
    public static String normaliser(final String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    /**
     * Vérifie si {@code haystack} contient {@code needle}, insensiblement à la casse et
     * aux accents. Retourne {@code false} si l'un des deux est null.
     */
    public static boolean containsNormalise(final String haystack, final String needle) {
        if (haystack == null || needle == null) return false;
        return normaliser(haystack).contains(normaliser(needle));
    }
}
