package fr.leadrevive.emailimport.service;

import fr.leadrevive.emailimport.config.EmailImportConfig;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.repository.LeadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de la deduplication import email (epic 24).
 *
 * <p>Verifie que telephone est prioritaire, fallback email, et qu'on ne combine
 * jamais les deux criteres.</p>
 */
@ExtendWith(MockitoExtension.class)
class EmailDeduplicationServiceTest {

    @Mock private LeadRepository leadRepository;
    private EmailImportConfig config;
    private EmailDeduplicationService service;

    @BeforeEach
    void setUp() {
        config = new EmailImportConfig();
        config.setDeduplicationFenetreHeures(48);
        service = new EmailDeduplicationService(leadRepository, config);
    }

    @Test
    void chercheDoublon_telephoneMatch_retourneLeadExistant() {
        final Lead leadExistant = new Lead();
        leadExistant.setId(42L);
        when(leadRepository.findByTenantIdAndTelephoneAndCreeLeSince(eq(1L), eq("+33612345678"), any(LocalDateTime.class)))
                .thenReturn(List.of(leadExistant));

        final Optional<Lead> resultat = service.chercherDoublon(1L, "+33612345678", "test@example.com");

        assertThat(resultat).contains(leadExistant);
        // Verifie que la query email N'est PAS appelee si match telephone
        verify(leadRepository, never()).findByTenantIdAndEmailAndCreeLeSince(any(), any(), any());
    }

    @Test
    void chercheDoublon_pasDeTelephone_fallbackEmail() {
        final Lead leadExistant = new Lead();
        leadExistant.setId(99L);
        when(leadRepository.findByTenantIdAndEmailAndCreeLeSince(eq(1L), eq("test@example.com"), any(LocalDateTime.class)))
                .thenReturn(List.of(leadExistant));

        final Optional<Lead> resultat = service.chercherDoublon(1L, null, "test@example.com");

        assertThat(resultat).contains(leadExistant);
        verify(leadRepository, never()).findByTenantIdAndTelephoneAndCreeLeSince(any(), any(), any());
    }

    @Test
    void chercheDoublon_telephoneBlank_fallbackEmail() {
        final Lead leadExistant = new Lead();
        leadExistant.setId(99L);
        when(leadRepository.findByTenantIdAndEmailAndCreeLeSince(eq(1L), eq("test@example.com"), any(LocalDateTime.class)))
                .thenReturn(List.of(leadExistant));

        final Optional<Lead> resultat = service.chercherDoublon(1L, "  ", "test@example.com");

        assertThat(resultat).contains(leadExistant);
    }

    @Test
    void chercheDoublon_aucunMatch_retourneEmpty() {
        when(leadRepository.findByTenantIdAndTelephoneAndCreeLeSince(any(), any(), any()))
                .thenReturn(List.of());
        when(leadRepository.findByTenantIdAndEmailAndCreeLeSince(any(), any(), any()))
                .thenReturn(List.of());

        final Optional<Lead> resultat = service.chercherDoublon(1L, "+33612345678", "test@example.com");

        assertThat(resultat).isEmpty();
    }

    @Test
    void chercheDoublon_telephoneEtEmailNull_retourneEmpty() {
        final Optional<Lead> resultat = service.chercherDoublon(1L, null, null);

        assertThat(resultat).isEmpty();
        verify(leadRepository, never()).findByTenantIdAndTelephoneAndCreeLeSince(any(), any(), any());
        verify(leadRepository, never()).findByTenantIdAndEmailAndCreeLeSince(any(), any(), any());
    }

    @Test
    void chercheDoublon_seulLeadDuNumeroEstArchive_retourneEmpty() {
        // Le filtre `archive = false` est applique en SQL dans la @Query : un lead
        // archive ne remonte donc jamais. On simule ce contrat (repository -> liste vide)
        // et on verifie que la dedup conclut "pas de doublon" -> un lead frais sera cree.
        // Fallback email teste dans la foulee (email null ici -> pas d'appel).
        when(leadRepository.findByTenantIdAndTelephoneAndCreeLeSince(any(), any(), any()))
                .thenReturn(List.of());

        final Optional<Lead> resultat = service.chercherDoublon(1L, "+33612345678", null);

        assertThat(resultat).isEmpty();
    }

    @Test
    void chercheDoublon_telephoneMatchMultiple_retourneLePlusRecent() {
        // Le repository renvoie deja la liste ordonnee DESC (cf. @Query). On verifie
        // qu'on prend bien le premier (= le plus recent).
        final Lead recent = new Lead();
        recent.setId(2L);
        final Lead ancien = new Lead();
        ancien.setId(1L);
        when(leadRepository.findByTenantIdAndTelephoneAndCreeLeSince(any(), any(), any()))
                .thenReturn(List.of(recent, ancien));

        final Optional<Lead> resultat = service.chercherDoublon(1L, "+33612345678", null);

        assertThat(resultat).map(Lead::getId).contains(2L);
    }
}
