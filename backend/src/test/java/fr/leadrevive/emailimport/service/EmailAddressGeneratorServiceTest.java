package fr.leadrevive.emailimport.service;

import fr.leadrevive.emailimport.config.EmailImportConfig;
import fr.leadrevive.emailimport.repository.TenantEmailConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests de la generation d'adresse email d'import (epic 24).
 *
 * <p>Couvre la normalisation slug (ASCII + tirets), la presence du token, et
 * le cas de collision.</p>
 */
@ExtendWith(MockitoExtension.class)
class EmailAddressGeneratorServiceTest {

    @Mock private TenantEmailConfigRepository repository;
    private EmailImportConfig config;
    private EmailAddressGeneratorService service;

    @BeforeEach
    void setUp() {
        config = new EmailImportConfig();
        config.setDomaine("leads.leadrive.fr");
        service = new EmailAddressGeneratorService(config, repository);
    }

    @Test
    void genere_normaliseAccents() {
        when(repository.existsByEmailAddress(anyString())).thenReturn(false);

        final EmailAddressGeneratorService.EmailGenere genere =
                service.genererEmailAddress("Garage Été à Bordeaux");

        assertThat(genere.slug()).isEqualTo("garage-ete-a-bordeaux");
        assertThat(genere.emailAddress()).startsWith("garage-ete-a-bordeaux-");
        assertThat(genere.emailAddress()).endsWith("@leads.leadrive.fr");
    }

    @Test
    void genere_supprimeCaracteresSpeciaux() {
        when(repository.existsByEmailAddress(anyString())).thenReturn(false);

        final EmailAddressGeneratorService.EmailGenere genere =
                service.genererEmailAddress("Garage Dupont & Fils !!");

        assertThat(genere.slug()).isEqualTo("garage-dupont-fils");
    }

    @Test
    void genere_tokenLongueur12Alphanumerique() {
        when(repository.existsByEmailAddress(anyString())).thenReturn(false);

        final EmailAddressGeneratorService.EmailGenere genere =
                service.genererEmailAddress("Test");

        // Format : test-XXXXXXXXXXXX@leads.leadrive.fr (token 12 chars, anti-brute-force)
        final String partieLocale = genere.emailAddress().split("@")[0];
        final String[] morceaux = partieLocale.split("-");
        final String token = morceaux[morceaux.length - 1];
        assertThat(token).hasSize(12).matches("[a-z0-9]{12}");
    }

    @Test
    void genere_nomVide_utiliseFallback() {
        when(repository.existsByEmailAddress(anyString())).thenReturn(false);

        final EmailAddressGeneratorService.EmailGenere genere =
                service.genererEmailAddress("");

        assertThat(genere.slug()).isEqualTo("concession");
    }

    @Test
    void genere_collisionRepetee_lanceException() {
        // Simule 11 collisions consecutives - apres 10 tentatives, on abandonne.
        when(repository.existsByEmailAddress(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.genererEmailAddress("Garage"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Impossible de generer une adresse email unique");
    }

    @Test
    void genere_tronqueSlugTropLong() {
        when(repository.existsByEmailAddress(anyString())).thenReturn(false);
        final String nomLong = "a".repeat(100);

        final EmailAddressGeneratorService.EmailGenere genere =
                service.genererEmailAddress(nomLong);

        assertThat(genere.slug()).hasSize(70);
    }
}
