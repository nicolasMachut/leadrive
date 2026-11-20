package fr.leadrevive.outreach.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Filtre de sortie pour les messages IA WhatsApp.
 *
 * Verifie que le contenu genere par l'IA ne contient pas :
 * - Des prix ou montants en EUR/euros
 * - Des promesses commerciales explicites ("garanti", "promet", etc.)
 * - Des engagements (taux, mensualites)
 *
 * Si une violation est detectee : on bascule la conversation en mode HUMAIN
 * pour que le vendeur reprenne la main au lieu d'envoyer le message.
 *
 * Conformite IA Act + protection juridique (la concession ne peut pas etre
 * engagee par un message IA sans validation humaine).
 */
@Component
@Slf4j
public class IaOutputFilter {

    /**
     * Detecte des montants : "25000 EUR", "25 000 €", "350 euros", "30k", "1500€/mois".
     * Le pattern "30k" est exclu si suivi de km/kg/m/h (kilometrage, poids, etc.) :
     * ex. "Le vehicule a 30k km au compteur" ne doit PAS etre bloque.
     */
    private static final Pattern PRIX_PATTERN = Pattern.compile(
            "(?i)\\b\\d{1,3}(?:[\\s.,]?\\d{3})*(?:[.,]\\d+)?\\s*(?:k(?!\\s*(?:km|kg|m\\b|h\\b))\\b|€|eur|euro|euros)|\\b\\d+\\s*€",
            Pattern.UNICODE_CASE);

    /**
     * Detecte taux/mensualites : "350 €/mois", "1.99%", "TAEG 3,5%".
     * Gere les variations : "mensualite", "mensualites", "mensualité", "mensualités".
     */
    private static final Pattern TAUX_PATTERN = Pattern.compile(
            "(?i)\\b(?:taeg|taux|mensualit[eés]s?|loyer)\\b|\\b\\d+([.,]\\d+)?\\s*%",
            Pattern.UNICODE_CASE);

    /** Mots-cles d'engagement commercial a eviter sans validation humaine. */
    private static final List<String> MOTS_INTERDITS = List.of(
            "garanti", "garantie", "promets", "promet ", "promettre",
            "remise exclusive", "prix casse", "offre limitee", "derniere chance",
            "100% sur", "engagement ferme", "remboursement integral"
    );

    /**
     * Verifie le contenu d'un message IA. Retourne la liste des violations detectees.
     * Liste vide si le message est OK a envoyer.
     */
    public List<String> verifier(final String contenu) {
        final List<String> violations = new ArrayList<>();
        if (contenu == null || contenu.isBlank()) return violations;

        if (PRIX_PATTERN.matcher(contenu).find()) {
            violations.add("Mention d'un prix ou montant en euros");
        }
        if (TAUX_PATTERN.matcher(contenu).find()) {
            violations.add("Mention d'un taux ou mensualite");
        }

        final String lower = contenu.toLowerCase();
        for (final String mot : MOTS_INTERDITS) {
            if (lower.contains(mot)) {
                violations.add("Engagement commercial detecte : \"" + mot + "\"");
            }
        }

        if (!violations.isEmpty()) {
            log.warn("Filtre IA : message bloque - violations: {}", violations);
        }
        return violations;
    }

    /**
     * Retourne true si le message est sur a envoyer (aucune violation).
     */
    public boolean estSur(final String contenu) {
        return verifier(contenu).isEmpty();
    }
}
