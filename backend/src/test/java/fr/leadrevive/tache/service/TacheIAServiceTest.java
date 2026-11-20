package fr.leadrevive.tache.service;

import fr.leadrevive.fixtures.LeadFixture;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.tache.entity.StatutTacheEnum;
import fr.leadrevive.tache.entity.TypeTacheEnum;
import fr.leadrevive.tache.repository.TacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import fr.leadrevive.tache.entity.Tache;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TacheIAServiceTest {

    @Mock
    private TacheRepository tacheRepository;
    @Mock
    private LeadRepository leadRepository;

    private TacheIAService tacheIAService;

    @BeforeEach
    void setUp() {
        tacheIAService = new TacheIAService(tacheRepository, leadRepository);
    }

    @Test
    void onRdvDetecte_creeTacheRdv() {
        final Lead lead = leadAvecVendeur(1L, 10L);
        when(leadRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(lead));
        when(tacheRepository.findFirstByLeadIdAndTypeTacheAndStatutOrderByCreeLe(1L, TypeTacheEnum.RDV, StatutTacheEnum.A_FAIRE)).thenReturn(Optional.empty());
        when(tacheRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        tacheIAService.onRdvDetecte(1L, 1L, LocalDateTime.of(2026, 4, 18, 14, 0), 99L);

        final ArgumentCaptor<Tache> captor = ArgumentCaptor.forClass(Tache.class);
        verify(tacheRepository).save(captor.capture());
        final Tache tache = captor.getValue();
        assertThat(tache.getTypeTache()).isEqualTo(TypeTacheEnum.RDV);
        assertThat(tache.getSource()).isEqualTo("IA_AUTO");
        assertThat(tache.getLeadId()).isEqualTo(1L);
        assertThat(tache.getVendeurId()).isEqualTo(10L);
        assertThat(tache.getDateEcheance()).isEqualTo(LocalDateTime.of(2026, 4, 18, 14, 0));
        assertThat(tache.getRappelMinutesAvant()).isEqualTo(60);
    }

    @Test
    void onRdvDetecte_memeCreneauExistant_retourneTacheExistante() {
        final Lead lead = leadAvecVendeur(1L, 10L);
        final Tache existante = new Tache();
        existante.setDateEcheance(LocalDateTime.of(2026, 4, 18, 14, 0));
        when(leadRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(lead));
        when(tacheRepository.findFirstByLeadIdAndTypeTacheAndStatutOrderByCreeLe(1L, TypeTacheEnum.RDV, StatutTacheEnum.A_FAIRE)).thenReturn(Optional.of(existante));

        final Tache result = tacheIAService.onRdvDetecte(1L, 1L, LocalDateTime.of(2026, 4, 18, 14, 0), 99L);

        assertThat(result).isSameAs(existante);
        verify(tacheRepository, never()).save(any());
    }

    @Test
    void onRdvDetecte_creneauDifferent_metAJourTacheExistante() {
        final Lead lead = leadAvecVendeur(1L, 10L);
        final Tache existante = new Tache();
        existante.setDateEcheance(LocalDateTime.of(2026, 4, 18, 14, 0));
        when(leadRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(lead));
        when(tacheRepository.findFirstByLeadIdAndTypeTacheAndStatutOrderByCreeLe(1L, TypeTacheEnum.RDV, StatutTacheEnum.A_FAIRE)).thenReturn(Optional.of(existante));
        when(tacheRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final Tache result = tacheIAService.onRdvDetecte(1L, 1L, LocalDateTime.of(2026, 4, 18, 18, 0), 99L);

        assertThat(result).isSameAs(existante);
        assertThat(existante.getDateEcheance()).isEqualTo(LocalDateTime.of(2026, 4, 18, 18, 0));
        verify(tacheRepository).save(existante);
    }

    @Test
    void onRdvDetecte_sansVendeur_utiliseFallbackConversationUserId() {
        final Lead lead = LeadFixture.leadAvecTenant(1L);
        lead.setId(1L);
        lead.setVendeurId(null);
        when(leadRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(lead));
        when(tacheRepository.findFirstByLeadIdAndTypeTacheAndStatutOrderByCreeLe(1L, TypeTacheEnum.RDV, StatutTacheEnum.A_FAIRE)).thenReturn(Optional.empty());
        when(tacheRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        tacheIAService.onRdvDetecte(1L, 1L, LocalDateTime.of(2026, 4, 18, 14, 0), 99L);

        final ArgumentCaptor<Tache> captor = ArgumentCaptor.forClass(Tache.class);
        verify(tacheRepository).save(captor.capture());
        assertThat(captor.getValue().getVendeurId()).isEqualTo(99L);
    }

    @Test
    void onNouveauLead_creeTacheAppel() {
        final Lead lead = leadAvecVendeur(1L, 10L);
        when(leadRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(lead));
        when(tacheRepository.existsByLeadIdAndTypeTacheAndStatut(1L, TypeTacheEnum.APPEL, StatutTacheEnum.A_FAIRE)).thenReturn(false);
        when(tacheRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        tacheIAService.onNouveauLead(1L, 1L);

        final ArgumentCaptor<Tache> captor = ArgumentCaptor.forClass(Tache.class);
        verify(tacheRepository).save(captor.capture());
        assertThat(captor.getValue().getTypeTache()).isEqualTo(TypeTacheEnum.APPEL);
        assertThat(captor.getValue().getTitre()).contains(lead.getNom());
    }

    @Test
    void onNouveauLead_sansVendeur_neCreePas() {
        final Lead lead = LeadFixture.leadAvecTenant(1L);
        lead.setId(1L);
        lead.setVendeurId(null);
        when(leadRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(lead));

        tacheIAService.onNouveauLead(1L, 1L);

        verify(tacheRepository, never()).save(any());
    }

    @Test
    void annulerRdvSiExiste_tacheExistante_marqueeAnnulee() {
        final Tache tache = new Tache();
        tache.setId(50L);
        tache.setLeadId(1L);
        tache.setStatut(StatutTacheEnum.A_FAIRE);
        tache.setDescription("RDV detecte");
        when(tacheRepository.findFirstByLeadIdAndTypeTacheAndStatutOrderByCreeLe(1L, TypeTacheEnum.RDV, StatutTacheEnum.A_FAIRE))
                .thenReturn(Optional.of(tache));

        final boolean annulee = tacheIAService.annulerRdvSiExiste(1L, "Prospect refuse");

        assertThat(annulee).isTrue();
        assertThat(tache.getStatut()).isEqualTo(StatutTacheEnum.ANNULEE);
        assertThat(tache.getDescription()).contains("Prospect refuse");
        verify(tacheRepository).save(tache);
    }

    @Test
    void annulerRdvSiExiste_aucuneTache_retourneFalseSansSave() {
        when(tacheRepository.findFirstByLeadIdAndTypeTacheAndStatutOrderByCreeLe(1L, TypeTacheEnum.RDV, StatutTacheEnum.A_FAIRE))
                .thenReturn(Optional.empty());

        final boolean annulee = tacheIAService.annulerRdvSiExiste(1L, "STOP");

        assertThat(annulee).isFalse();
        verify(tacheRepository, never()).save(any());
    }

    @Test
    void onContactSansRdv_creeTacheRelance48h() {
        final Lead lead = leadAvecVendeur(1L, 10L);
        when(leadRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(lead));
        when(tacheRepository.existsByLeadIdAndTypeTacheAndStatut(1L, TypeTacheEnum.RELANCE, StatutTacheEnum.A_FAIRE)).thenReturn(false);
        when(tacheRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        tacheIAService.onContactSansRdv(1L, 1L);

        final ArgumentCaptor<Tache> captor = ArgumentCaptor.forClass(Tache.class);
        verify(tacheRepository).save(captor.capture());
        assertThat(captor.getValue().getTypeTache()).isEqualTo(TypeTacheEnum.RELANCE);
        assertThat(captor.getValue().getDateEcheance()).isAfter(LocalDateTime.now().plusHours(47));
    }

    private Lead leadAvecVendeur(final Long id, final Long vendeurId) {
        final Lead lead = LeadFixture.leadAvecTenant(1L);
        lead.setId(id);
        lead.setVendeurId(vendeurId);
        return lead;
    }
}
