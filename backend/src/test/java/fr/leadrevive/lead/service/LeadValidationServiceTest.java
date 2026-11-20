package fr.leadrevive.lead.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LeadValidationServiceTest {

    private final LeadValidationService service = new LeadValidationService();

    @Test
    void validerLead_nomManquant_retourneErreur() {
        final List<String> erreurs = service.validerLead(null, "test@test.com", "+33612345678");
        assertThat(erreurs).contains("Le nom est obligatoire");
    }

    @Test
    void validerLead_nomBlank_retourneErreur() {
        final List<String> erreurs = service.validerLead("  ", "test@test.com", "+33612345678");
        assertThat(erreurs).contains("Le nom est obligatoire");
    }

    @Test
    void validerLead_toutValide_retourneListeVide() {
        final List<String> erreurs = service.validerLead("Dupont", "dupont@test.com", "+33612345678");
        assertThat(erreurs).isEmpty();
    }

    @Test
    void validerLead_emailInvalide_retourneErreur() {
        final List<String> erreurs = service.validerLead("Dupont", "pas-un-email", "+33612345678");
        assertThat(erreurs).anyMatch(e -> e.contains("Email invalide"));
    }

    @Test
    void validerLead_emailNull_pasErreur() {
        final List<String> erreurs = service.validerLead("Dupont", null, "+33612345678");
        assertThat(erreurs).isEmpty();
    }

    @Test
    void estEmailValide_emailCorrect_retourneTrue() {
        assertThat(service.estEmailValide("test@example.com")).isTrue();
    }

    @Test
    void estEmailValide_emailInvalide_retourneFalse() {
        assertThat(service.estEmailValide("pas-un-email")).isFalse();
    }

    @Test
    void estTelephoneValide_formatCorrect_retourneTrue() {
        assertThat(service.estTelephoneValide("+33612345678")).isTrue();
    }

    @Test
    void estTelephoneValide_placeholder_retourneFalse() {
        assertThat(service.estTelephoneValide("+33600000000")).isFalse();
    }

    @Test
    void estTelephoneValide_incomplet_retourneFalse() {
        assertThat(service.estTelephoneValide("+33")).isFalse();
    }

    @Test
    void nettoyerTelephone_formatNational_convertitEnInternational() {
        final String resultat = service.nettoyerTelephone("0612345678");
        assertThat(resultat).isEqualTo("+33612345678");
    }

    @Test
    void nettoyerTelephone_avecEspaces_nettoie() {
        final String resultat = service.nettoyerTelephone("+33 6 12 34 56 78");
        assertThat(resultat).isEqualTo("+33612345678");
    }

    @Test
    void nettoyerTelephone_null_retourneNull() {
        assertThat(service.nettoyerTelephone(null)).isNull();
    }

    @Test
    void validerTelephone_telephonePlaceholder666_retourneErreur() {
        final List<String> erreurs = service.validerTelephone("+33666666666");
        assertThat(erreurs).anyMatch(e -> e.contains("placeholder"));
    }

    @Test
    void validerTelephone_telephoneValide_retourneListeVide() {
        final List<String> erreurs = service.validerTelephone("+33600000003");
        assertThat(erreurs).isEmpty();
    }
}
