package fr.leadrevive.lead.service;

import fr.leadrevive.fixtures.LeadFixture;
import fr.leadrevive.fixtures.UserFixture;
import fr.leadrevive.fixtures.VenteConfirmeeFixture;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.entity.SourceVenteEnum;
import fr.leadrevive.lead.entity.VenteConfirmee;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.lead.repository.VenteConfirmeeRepository;
import fr.leadrevive.user.entity.User;
import fr.leadrevive.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VenteExportServiceTest {

    @Mock
    private VenteConfirmeeRepository venteConfirmeeRepository;

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private VenteExportService service;

    @Test
    void exporterVentes_aucuneVente_retourneHeaderAvecBom() {
        // GIVEN
        when(venteConfirmeeRepository.findByTenantIdAndDateVenteBetweenOrderByDateVenteDesc(anyLong(), any(), any()))
                .thenReturn(List.of());
        when(leadRepository.findAllById(any())).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of());

        // WHEN
        final String csv = service.exporterVentes(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        // THEN
        assertAll(
                () -> assertThat(csv).startsWith("\uFEFF"),
                () -> assertThat(csv).contains("Date vente;Prospect;Telephone;Email;Marque;Modele;Vendeur;Montant EUR;Source;Commentaire"),
                () -> assertEquals(1L, csv.lines().count())
        );
    }

    @Test
    void exporterVentes_uneVenteComplete_retourneCsvFormate() {
        // GIVEN
        final Lead lead = LeadFixture.lead();
        lead.setId(10L);
        lead.setNom("Martin Karim");
        lead.setEmail("k@example.fr");
        lead.setTelephone("+33612345678");
        lead.setMarque("Peugeot");
        lead.setModele("2008");

        final User vendeur = UserFixture.userAvecNom(20L, "Eva", "Dupont");

        final VenteConfirmee v = VenteConfirmeeFixture.venteComplete(
                10L, 20L, new BigDecimal("25000.50"),
                LocalDate.of(2026, 4, 15), SourceVenteEnum.WHATSAPP_IA,
                "Vente conclue rapidement");

        when(venteConfirmeeRepository.findByTenantIdAndDateVenteBetweenOrderByDateVenteDesc(anyLong(), any(), any()))
                .thenReturn(List.of(v));
        when(leadRepository.findAllById(any())).thenReturn(List.of(lead));
        when(userRepository.findAllById(any())).thenReturn(List.of(vendeur));

        // WHEN
        final String csv = service.exporterVentes(1L, LocalDate.now(), LocalDate.now());

        // THEN
        assertAll(
                () -> assertThat(csv).contains("15/04/2026"),
                () -> assertThat(csv).contains("Martin Karim;+33612345678;k@example.fr;Peugeot;2008"),
                () -> assertThat(csv).contains("Eva Dupont"),
                () -> assertThat(csv).contains("25000,50"),
                () -> assertThat(csv).contains("WHATSAPP_IA"),
                () -> assertThat(csv).contains("Vente conclue rapidement")
        );
    }

    @Test
    void exporterVentes_valeursAvecSeparateurOuGuillemets_lesEchappe() {
        // GIVEN
        final Lead lead = LeadFixture.lead();
        lead.setId(1L);
        lead.setNom("Dupont; Jean");   // contient ; → doit etre quote
        lead.setEmail("a\"b@c.com");   // contient " → doit etre echappe
        lead.setTelephone("0612345678");
        lead.setMarque(null);
        lead.setModele(null);

        final User u = UserFixture.userAvecNom(2L, "X", "Y");

        final VenteConfirmee v = VenteConfirmeeFixture.venteComplete(
                1L, 2L, new BigDecimal("100"),
                LocalDate.of(2026, 1, 1), SourceVenteEnum.APPEL_MANUEL, null);

        when(venteConfirmeeRepository.findByTenantIdAndDateVenteBetweenOrderByDateVenteDesc(anyLong(), any(), any()))
                .thenReturn(List.of(v));
        when(leadRepository.findAllById(any())).thenReturn(List.of(lead));
        when(userRepository.findAllById(any())).thenReturn(List.of(u));

        // WHEN
        final String csv = service.exporterVentes(1L, LocalDate.now(), LocalDate.now());

        // THEN
        assertAll(
                () -> assertThat(csv).contains("\"Dupont; Jean\""),
                () -> assertThat(csv).contains("\"a\"\"b@c.com\"")
        );
    }

    @Test
    void exporterVentes_leadIntrouvable_remplitAvecChainesVides() {
        // GIVEN
        final VenteConfirmee v = VenteConfirmeeFixture.venteComplete(
                999L, 20L, new BigDecimal("100"),
                LocalDate.of(2026, 1, 1), SourceVenteEnum.APPEL_MANUEL, null);

        when(venteConfirmeeRepository.findByTenantIdAndDateVenteBetweenOrderByDateVenteDesc(anyLong(), any(), any()))
                .thenReturn(List.of(v));
        when(leadRepository.findAllById(any())).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of());

        // WHEN
        final String csv = service.exporterVentes(1L, LocalDate.now(), LocalDate.now());

        // THEN
        // Pas d'exception, juste des champs vides — header + 1 ligne
        assertEquals(2L, csv.lines().count());
    }

    @Test
    void exporterVentes_montantNull_neLanceAucuneException() {
        // GIVEN
        final Lead lead = LeadFixture.lead();
        lead.setId(1L);
        lead.setNom("Test");

        final VenteConfirmee v = VenteConfirmeeFixture.venteComplete(
                1L, 2L, null,
                LocalDate.of(2026, 1, 1), SourceVenteEnum.APPEL_MANUEL, null);

        when(venteConfirmeeRepository.findByTenantIdAndDateVenteBetweenOrderByDateVenteDesc(anyLong(), any(), any()))
                .thenReturn(List.of(v));
        when(leadRepository.findAllById(any())).thenReturn(List.of(lead));
        when(userRepository.findAllById(any())).thenReturn(List.of());

        // WHEN
        final String csv = service.exporterVentes(1L, LocalDate.now(), LocalDate.now());

        // THEN
        assertThat(csv).isNotEmpty();
    }
}
