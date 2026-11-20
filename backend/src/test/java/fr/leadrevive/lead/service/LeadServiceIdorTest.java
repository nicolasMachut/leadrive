package fr.leadrevive.lead.service;

import fr.leadrevive.fixtures.LeadFixture;
import fr.leadrevive.lead.dto.ReqUpdateLeadDto;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.entity.LeadStatutEnum;
import fr.leadrevive.lead.exception.LeadNotFoundException;
import fr.leadrevive.lead.mapper.LeadMapper;
import fr.leadrevive.lead.repository.LeadRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de la securite multi-tenant de LeadService.
 * Verifient qu'un user ne peut pas acceder aux leads d'un autre tenant (IDOR).
 */
@ExtendWith(MockitoExtension.class)
class LeadServiceIdorTest {

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private LeadMapper leadMapper;

    @Mock
    private fr.leadrevive.ai.service.TenantAiConfigService tenantAiConfigService;

    @InjectMocks
    private LeadService service;

    @Test
    void getLead_leadAutreTenant_lanceLeadNotFoundException() {
        // GIVEN
        // findByIdAndTenantId(42, 999) → vide car le lead 42 appartient au tenant 1, pas au 999
        when(leadRepository.findByIdAndTenantId(42L, 999L)).thenReturn(Optional.empty());

        // WHEN / THEN
        assertThrows(LeadNotFoundException.class, () -> service.getLead(42L, 999L));
        // Pas d'appel a findById tout court (qui aurait expose le lead)
        verify(leadRepository, never()).findById(any());
    }

    @Test
    void updateLead_leadAutreTenant_lanceExceptionPasDeSave() {
        // GIVEN
        when(leadRepository.findByIdAndTenantId(42L, 999L)).thenReturn(Optional.empty());
        final ReqUpdateLeadDto request = new ReqUpdateLeadDto();

        // WHEN / THEN
        assertThrows(LeadNotFoundException.class, () -> service.updateLead(42L, 999L, request));
        verify(leadRepository, never()).save(any());
    }

    @Test
    void deleteLead_leadAutreTenant_lanceExceptionPasDeDelete() {
        // GIVEN
        when(leadRepository.findByIdAndTenantId(42L, 999L)).thenReturn(Optional.empty());

        // WHEN / THEN
        assertThrows(LeadNotFoundException.class, () -> service.deleteLead(42L, 999L));
        verify(leadRepository, never()).delete(any());
    }

    @Test
    void restaurerLead_leadAutreTenant_lanceExceptionPasDeSave() {
        // GIVEN : le lead 42 n'appartient pas au tenant 999
        when(leadRepository.findByIdAndTenantId(42L, 999L)).thenReturn(Optional.empty());

        // WHEN / THEN
        assertThrows(LeadNotFoundException.class, () -> service.restaurerLead(42L, 999L));
        verify(leadRepository, never()).save(any());
    }

    @Test
    void restaurerLead_leadArchiveMemeTenant_desarchiveEtSave() {
        // GIVEN : lead archive du bon tenant
        final Lead lead = LeadFixture.leadAvecTenant(1L);
        lead.setId(42L);
        lead.setArchive(true);
        when(leadRepository.findByIdAndTenantId(42L, 1L)).thenReturn(Optional.of(lead));

        // WHEN
        service.restaurerLead(42L, 1L);

        // THEN
        org.junit.jupiter.api.Assertions.assertFalse(lead.getArchive());
        verify(leadRepository).save(eq(lead));
    }

    @Test
    void restaurerLead_leadDejaActif_idempotentPasDeSave() {
        // GIVEN : lead deja actif (archive=false)
        final Lead lead = LeadFixture.leadAvecTenant(1L);
        lead.setId(42L);
        lead.setArchive(false);
        when(leadRepository.findByIdAndTenantId(42L, 1L)).thenReturn(Optional.of(lead));

        // WHEN
        service.restaurerLead(42L, 1L);

        // THEN : rien a faire, pas de save inutile (idempotence)
        verify(leadRepository, never()).save(any());
    }

    @Test
    void reprogrammerEnvoiAuto_leadAutreTenant_lanceExceptionPasDeSave() {
        // GIVEN : le lead 42 n'appartient pas au tenant 999
        when(leadRepository.findByIdAndTenantId(42L, 999L)).thenReturn(Optional.empty());

        // WHEN / THEN
        assertThrows(LeadNotFoundException.class,
                () -> service.reprogrammerEnvoiAuto(42L, 999L, java.time.LocalDateTime.now().plusMinutes(5)));
        verify(leadRepository, never()).save(any());
    }

    @Test
    void reprogrammerEnvoiAuto_dateNulle_lanceIllegalArgument() {
        // WHEN / THEN : date obligatoire, aucun acces DB
        assertThrows(IllegalArgumentException.class,
                () -> service.reprogrammerEnvoiAuto(42L, 1L, null));
        verify(leadRepository, never()).save(any());
    }

    @Test
    void reprogrammerEnvoiAuto_leadPasNouveau_lanceIllegalState() {
        // GIVEN : lead deja avance dans le pipeline
        final Lead lead = LeadFixture.leadAvecTenant(1L);
        lead.setId(42L);
        lead.setStatut(LeadStatutEnum.CONTACTE);
        when(leadRepository.findByIdAndTenantId(42L, 1L)).thenReturn(Optional.of(lead));

        // WHEN / THEN
        assertThrows(IllegalStateException.class,
                () -> service.reprogrammerEnvoiAuto(42L, 1L, java.time.LocalDateTime.now().plusMinutes(5)));
        verify(leadRepository, never()).save(any());
    }

    @Test
    void reprogrammerEnvoiAuto_leadNouveauMemeTenant_reArmeLeFlag() {
        // GIVEN
        final Lead lead = LeadFixture.leadAvecTenant(1L);
        lead.setId(42L);
        lead.setStatut(LeadStatutEnum.NOUVEAU);
        lead.setPremierMessageAutoPrevuLe(null);
        when(leadRepository.findByIdAndTenantId(42L, 1L)).thenReturn(Optional.of(lead));
        final java.time.LocalDateTime echeance = java.time.LocalDateTime.now().plusMinutes(5);

        // WHEN
        service.reprogrammerEnvoiAuto(42L, 1L, echeance);

        // THEN
        org.junit.jupiter.api.Assertions.assertEquals(echeance, lead.getPremierMessageAutoPrevuLe());
        verify(leadRepository).save(eq(lead));
    }

    @Test
    void deleteLead_effaceLEnvoiAutoEtArchive() {
        // GIVEN : lead actif avec un envoi auto arme
        final Lead lead = LeadFixture.leadAvecTenant(1L);
        lead.setId(42L);
        lead.setArchive(false);
        lead.setPremierMessageAutoPrevuLe(java.time.LocalDateTime.now().plusHours(1));
        when(leadRepository.findByIdAndTenantId(42L, 1L)).thenReturn(Optional.of(lead));

        // WHEN
        service.deleteLead(42L, 1L);

        // THEN : archive ET flag efface (sinon badge "auto" fantome sur lead archive)
        org.junit.jupiter.api.Assertions.assertTrue(lead.getArchive());
        org.junit.jupiter.api.Assertions.assertNull(lead.getPremierMessageAutoPrevuLe());
        verify(leadRepository).save(eq(lead));
    }

    @Test
    void restaurerLead_collisionTelephoneActif_lanceLeadActifExistant() {
        // GIVEN : lead archive dont le telephone est deja pris par un lead ACTIF
        final Lead lead = LeadFixture.leadAvecTenant(1L);
        lead.setId(42L);
        lead.setArchive(true);
        lead.setTelephone("+33612345678");
        when(leadRepository.findByIdAndTenantId(42L, 1L)).thenReturn(Optional.of(lead));
        when(leadRepository.existsByTenantIdAndTelephoneAndArchiveFalseAndIdNot(1L, "+33612345678", 42L))
                .thenReturn(true);

        // WHEN / THEN : 409 metier, pas de desarchivage
        assertThrows(fr.leadrevive.lead.exception.LeadActifExistantException.class,
                () -> service.restaurerLead(42L, 1L));
        verify(leadRepository, never()).save(any());
    }

    @Test
    void restaurerLead_collisionEmailActif_lanceLeadActifExistant() {
        // GIVEN : lead archive sans telephone mais dont l'email est pris par un actif
        final Lead lead = LeadFixture.leadAvecTenant(1L);
        lead.setId(42L);
        lead.setArchive(true);
        lead.setTelephone(null);
        lead.setEmail("collision@test.com");
        when(leadRepository.findByIdAndTenantId(42L, 1L)).thenReturn(Optional.of(lead));
        when(leadRepository.existsByTenantIdAndEmailAndArchiveFalseAndIdNot(1L, "collision@test.com", 42L))
                .thenReturn(true);

        // WHEN / THEN
        assertThrows(fr.leadrevive.lead.exception.LeadActifExistantException.class,
                () -> service.restaurerLead(42L, 1L));
        verify(leadRepository, never()).save(any());
    }

    @Test
    void restaurerLead_pasDeCollision_desarchive() {
        // GIVEN : lead archive, aucun actif ne partage son contact
        final Lead lead = LeadFixture.leadAvecTenant(1L);
        lead.setId(42L);
        lead.setArchive(true);
        lead.setTelephone("+33612345678");
        lead.setEmail("libre@test.com");
        when(leadRepository.findByIdAndTenantId(42L, 1L)).thenReturn(Optional.of(lead));
        when(leadRepository.existsByTenantIdAndTelephoneAndArchiveFalseAndIdNot(1L, "+33612345678", 42L))
                .thenReturn(false);
        when(leadRepository.existsByTenantIdAndEmailAndArchiveFalseAndIdNot(1L, "libre@test.com", 42L))
                .thenReturn(false);

        // WHEN
        service.restaurerLead(42L, 1L);

        // THEN
        org.junit.jupiter.api.Assertions.assertFalse(lead.getArchive());
        verify(leadRepository).save(eq(lead));
    }

    @Test
    void listerEnvoisAutoAVenir_vendeur_transmetLeFiltreAuRepo() {
        // GIVEN : appelant VENDEUR (vendeurId non nul) -> le repo doit etre scope
        when(leadRepository.findEnvoisAutoAVenir(1L, 7L)).thenReturn(java.util.List.of());

        // WHEN
        service.listerEnvoisAutoAVenir(1L, 7L);

        // THEN : le vendeurId est bien transmis (pas de fuite cross-vendeur)
        verify(leadRepository).findEnvoisAutoAVenir(1L, 7L);
    }

    @Test
    void getLead_memeTenant_appelleLeMapper() {
        // GIVEN
        final Lead lead = LeadFixture.leadAvecTenant(1L);
        lead.setId(42L);
        when(leadRepository.findByIdAndTenantId(42L, 1L)).thenReturn(Optional.of(lead));
        // Le mapper doit renvoyer un DTO non-null : getLead enrichit ensuite les champs
        // "envoi effectif". Sans echeance armee, l'enrichissement s'arrete sans lire la config.
        when(leadMapper.toDto(eq(lead))).thenReturn(
                fr.leadrevive.lead.dto.RepLeadDto.builder().idLead(42L).build());

        // WHEN
        service.getLead(42L, 1L);

        // THEN
        verify(leadMapper).toDto(eq(lead));
    }
}
