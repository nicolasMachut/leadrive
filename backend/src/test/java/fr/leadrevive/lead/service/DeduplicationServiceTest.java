package fr.leadrevive.lead.service;

import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.repository.LeadRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeduplicationServiceTest {

    @Mock
    private LeadRepository leadRepository;

    @InjectMocks
    private DeduplicationService deduplicationService;

    @Test
    void estDoublon_emailExistant_retourneTrue() {
        when(leadRepository.findByTenantIdAndEmailAndArchiveFalse(1L, "test@test.com"))
                .thenReturn(Optional.of(new Lead()));

        final boolean resultat = deduplicationService.estDoublon(1L, "test@test.com", null);
        assertThat(resultat).isTrue();
    }

    @Test
    void estDoublon_telephoneExistant_retourneTrue() {
        when(leadRepository.findByTenantIdAndTelephoneAndArchiveFalse(1L, "+33612345678"))
                .thenReturn(Optional.of(new Lead()));

        final boolean resultat = deduplicationService.estDoublon(1L, null, "+33612345678");
        assertThat(resultat).isTrue();
    }

    @Test
    void estDoublon_aucunDoublon_retourneFalse() {
        when(leadRepository.findByTenantIdAndEmailAndArchiveFalse(1L, "nouveau@test.com"))
                .thenReturn(Optional.empty());
        when(leadRepository.findByTenantIdAndTelephoneAndArchiveFalse(1L, "+33699999999"))
                .thenReturn(Optional.empty());

        final boolean resultat = deduplicationService.estDoublon(1L, "nouveau@test.com", "+33699999999");
        assertThat(resultat).isFalse();
    }

    @Test
    void estDoublon_seulLeadArchivePartageLeContact_retourneFalse() {
        // La dedup ignore les archives (variantes ...AndArchiveFalse) : un contact
        // archive ne doit pas provoquer un 409 contre un lead invisible. Le repository
        // (filtre archive=false) renvoie donc vide -> pas de doublon, creation autorisee.
        when(leadRepository.findByTenantIdAndEmailAndArchiveFalse(1L, "archive@test.com"))
                .thenReturn(Optional.empty());
        when(leadRepository.findByTenantIdAndTelephoneAndArchiveFalse(1L, "+33612345678"))
                .thenReturn(Optional.empty());

        final boolean resultat = deduplicationService.estDoublon(1L, "archive@test.com", "+33612345678");
        assertThat(resultat).isFalse();
    }

    @Test
    void estDoublon_emailEtTelephoneNull_retourneFalse() {
        final boolean resultat = deduplicationService.estDoublon(1L, null, null);
        assertThat(resultat).isFalse();
    }

    @Test
    void estDoublon_emailBlank_retourneFalse() {
        final boolean resultat = deduplicationService.estDoublon(1L, "", "");
        assertThat(resultat).isFalse();
    }
}
