package fr.leadrevive.outreach.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IaOutputFilterTest {

    private final IaOutputFilter filter = new IaOutputFilter();

    @ParameterizedTest
    @ValueSource(strings = {
        "Bonjour, quand seriez-vous disponible pour echanger ?",
        "Merci, je transmets a notre conseiller.",
        "A quel moment de la journee preferez-vous etre rappele ?",
        "Notre conseiller vous contactera demain matin pour faire le point."
    })
    void estSur_messageNeutre_retourneTrue(final String message) {
        // GIVEN / WHEN / THEN
        assertAll(
                () -> assertTrue(filter.estSur(message)),
                () -> assertThat(filter.verifier(message)).isEmpty()
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    void estSur_messageVideOuNull_retourneTrue(final String message) {
        // GIVEN / WHEN / THEN
        assertTrue(filter.estSur(message));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Le prix est de 25000 EUR",
        "Comptez environ 25 000 €",
        "C'est a partir de 350 euros par mois",
        "Notre offre est a 30k",
        "Le tarif est de 1500€"
    })
    void estSur_messageAvecPrix_retourneFalseEtViolationPrix(final String message) {
        // GIVEN / WHEN
        final List<String> violations = filter.verifier(message);

        // THEN
        assertAll(
                () -> assertFalse(filter.estSur(message)),
                () -> assertThat(violations)
                        .anyMatch(v -> v.toLowerCase().contains("prix") || v.toLowerCase().contains("montant"))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Le TAEG est de 1.99%",
        "Mensualite a partir de 199 €/mois",
        "Avec un taux de 3,5%",
        "Loyer mensuel competitif"
    })
    void estSur_messageAvecTauxOuMensualite_retourneFalse(final String message) {
        // GIVEN / WHEN / THEN
        assertFalse(filter.estSur(message));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "C'est garanti, vous serez satisfait",
        "Je vous promets le meilleur deal",
        "Notre remise exclusive est valable maintenant",
        "Prix casse exceptionnellement",
        "Offre limitee dans le temps"
    })
    void estSur_messageAvecEngagementCommercial_retourneFalseEtViolationEngagement(final String message) {
        // GIVEN / WHEN
        final List<String> violations = filter.verifier(message);

        // THEN
        assertAll(
                () -> assertFalse(filter.estSur(message)),
                () -> assertThat(violations).anyMatch(v -> v.toLowerCase().contains("engagement"))
        );
    }

    @Test
    void verifier_messageAvecPlusieursViolations_lesListeToutes() {
        // GIVEN
        final String message = "Le prix garanti est de 25000 EUR avec un taux de 1.99%";

        // WHEN
        final List<String> violations = filter.verifier(message);

        // THEN
        assertThat(violations).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void verifier_majusculesMotInterdit_estDetecte() {
        // GIVEN
        final String message = "C'est GARANTI";

        // WHEN / THEN
        assertFalse(filter.estSur(message));
    }

    @Test
    void estSur_mentionDeKilometres_passe() {
        // GIVEN
        // "30k km" ne doit PAS etre bloque : c'est du kilometrage, pas un prix.
        // L'IA a besoin de pouvoir mentionner le kilometrage (info technique du vehicule).
        // Fix : le pattern PRIX_PATTERN exclut "k" suivi de "km/kg/m/h".
        final String message = "Le vehicule a 30k km au compteur";

        // WHEN / THEN
        assertTrue(filter.estSur(message));
    }

    @Test
    void estSur_mentionDeKilogrammes_passe() {
        // GIVEN : poids cargo
        final String message = "Capacite de chargement : 800k kg";

        // WHEN / THEN
        assertTrue(filter.estSur(message));
    }

    @Test
    void estSur_prixSeulSansContexteKilometrique_bloque() {
        // GIVEN : "Notre offre est a 30k" sans suffixe km/kg = vraiment un prix.
        final String message = "Notre offre est a 30k";

        // WHEN / THEN
        assertFalse(filter.estSur(message));
    }

    @Test
    void estSur_texteLongSansViolation_retourneTrue() {
        // GIVEN
        final String message = "Merci pour votre interet pour ce vehicule. "
                + "Notre conseiller commercial reviendra vers vous tres rapidement "
                + "pour vous proposer un creneau d'echange et repondre a toutes vos questions.";

        // WHEN / THEN
        assertTrue(filter.estSur(message));
    }
}
