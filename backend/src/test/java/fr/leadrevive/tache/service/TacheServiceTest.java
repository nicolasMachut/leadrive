package fr.leadrevive.tache.service;

import fr.leadrevive.tache.dto.RepMaJourneeDto;
import fr.leadrevive.tache.dto.RepTacheDto;
import fr.leadrevive.tache.dto.ReqTacheDto;
import fr.leadrevive.tache.entity.PrioriteTacheEnum;
import fr.leadrevive.tache.entity.StatutTacheEnum;
import fr.leadrevive.tache.entity.Tache;
import fr.leadrevive.tache.entity.TypeTacheEnum;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.tache.repository.TacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TacheServiceTest {

    @Mock
    private TacheRepository tacheRepository;
    @Mock
    private LeadRepository leadRepository;
    @Mock
    private fr.leadrevive.outreach.service.RappelRdvService rappelRdvService;
    @Mock
    private fr.leadrevive.common.security.AuthService authService;

    private TacheService tacheService;

    @BeforeEach
    void setUp() {
        tacheService = new TacheService(tacheRepository, leadRepository, rappelRdvService, authService);
    }

    @Test
    void creerTache_avecLead_ok() {
        final ReqTacheDto req = new ReqTacheDto(42L, "Appeler Dupont", null, "APPEL",
                LocalDateTime.of(2026, 4, 18, 14, 0), null, "HAUTE", null);
        when(tacheRepository.save(any())).thenAnswer(inv -> {
            final Tache t = inv.getArgument(0);
            t.setId(1L);
            return t;
        });

        final RepTacheDto result = tacheService.creerTache(req, 1L, 10L, "MANUELLE");

        assertThat(result.getTitre()).isEqualTo("Appeler Dupont");
        assertThat(result.getLeadId()).isEqualTo(42L);

        final ArgumentCaptor<Tache> captor = ArgumentCaptor.forClass(Tache.class);
        verify(tacheRepository).save(captor.capture());
        assertThat(captor.getValue().getTypeTache()).isEqualTo(TypeTacheEnum.APPEL);
        assertThat(captor.getValue().getPriorite()).isEqualTo(PrioriteTacheEnum.HAUTE);
        assertThat(captor.getValue().getStatut()).isEqualTo(StatutTacheEnum.A_FAIRE);
        assertThat(captor.getValue().getSource()).isEqualTo("MANUELLE");
    }

    @Test
    void changerStatut_terminee_remplitDateTerminee() {
        final Tache tache = tacheDe(1L, StatutTacheEnum.A_FAIRE);
        when(tacheRepository.findById(1L)).thenReturn(Optional.of(tache));
        when(tacheRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final RepTacheDto result = tacheService.changerStatut(1L, 1L, StatutTacheEnum.TERMINEE);

        assertThat(tache.getStatut()).isEqualTo(StatutTacheEnum.TERMINEE);
        assertThat(tache.getDateTerminee()).isNotNull();
    }

    @Test
    void changerStatut_rdvDunLeadReassigneAUnAutreVendeur_404_pasDeCloture() {
        // La tache RDV porte encore vendeurId=10 (createur), mais le lead a ete reassigne
        // a un autre vendeur (99). Le vendeur 10 (qui ne voit plus le lead) ne doit PAS
        // pouvoir le cloturer via la cascade RDV -> lead. (bug-hunter P1-1, juillet 2026)
        final Tache tache = tacheDe(1L, StatutTacheEnum.A_FAIRE);
        tache.setTypeTache(TypeTacheEnum.RDV);
        tache.setLeadId(42L);
        when(tacheRepository.findById(1L)).thenReturn(Optional.of(tache));
        when(authService.getCurrentRole()).thenReturn("VENDEUR");
        when(authService.getCurrentUserId()).thenReturn(10L);
        final Lead leadReassigne = new Lead();
        leadReassigne.setVendeurId(99L);
        when(leadRepository.findByIdAndTenantId(42L, 1L)).thenReturn(Optional.of(leadReassigne));

        assertThatThrownBy(() -> tacheService.changerStatut(1L, 1L, StatutTacheEnum.TERMINEE))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);

        verify(tacheRepository, never()).save(any());
    }

    @Test
    void rouvrirTacheDeLead_rdvDunAutreLead_404() {
        // Payload forge : rdvId appartient au lead 42, mais on pretend leadId=999.
        // rouvrirTacheDeLead doit refuser (404) avant toute mutation. (bug-hunter P2-1)
        final Tache tache = tacheDe(1L, StatutTacheEnum.TERMINEE);
        tache.setTypeTache(TypeTacheEnum.RDV);
        tache.setLeadId(42L);
        when(tacheRepository.findById(1L)).thenReturn(Optional.of(tache));

        assertThatThrownBy(() -> tacheService.rouvrirTacheDeLead(1L, 1L, 999L))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);

        verify(tacheRepository, never()).save(any());
    }

    @Test
    void getMaJournee_triCorrect_enRetardDAbord() {
        final LocalDate aujourdhui = LocalDate.now();
        final Tache retard = tacheDe(1L, StatutTacheEnum.A_FAIRE);
        retard.setDateEcheance(LocalDateTime.now().minusDays(1));

        final Tache auj = tacheDe(2L, StatutTacheEnum.A_FAIRE);
        auj.setDateEcheance(aujourdhui.atTime(14, 0));

        final Tache demainTache = tacheDe(3L, StatutTacheEnum.A_FAIRE);
        demainTache.setDateEcheance(aujourdhui.plusDays(1).atTime(10, 0));

        when(tacheRepository.findByTenantIdAndVendeurIdAndStatutAndDateEcheanceBeforeOrderByDateEcheanceAsc(
                eq(1L), eq(10L), eq(StatutTacheEnum.A_FAIRE), any())).thenReturn(List.of(retard));
        when(tacheRepository.findByTenantIdAndVendeurIdAndDateEcheanceBetweenOrderByDateEcheanceAsc(
                eq(1L), eq(10L), any(), any()))
                .thenReturn(List.of(auj))
                .thenReturn(List.of(demainTache));

        final RepMaJourneeDto result = tacheService.getMaJournee(1L, 10L, aujourdhui);

        assertThat(result.getEnRetard()).hasSize(1);
        assertThat(result.getAujourdhui()).hasSize(1);
        assertThat(result.getDemain()).hasSize(1);
        assertThat(result.getNbEnRetard()).isEqualTo(1);
    }

    @Test
    void getMaJournee_tacheEnRetardAujourdhui_pasDeDoblon() {
        final LocalDate aujourdhui = LocalDate.now();

        // Tache dont l'echeance est aujourd'hui mais dans le passe (ex: 11h, il est 15h)
        final Tache tacheRetardAuj = tacheDe(10L, StatutTacheEnum.A_FAIRE);
        tacheRetardAuj.setDateEcheance(aujourdhui.atTime(9, 0));

        // Tache future aujourd'hui (pas en retard)
        final Tache tacheFutureAuj = tacheDe(20L, StatutTacheEnum.A_FAIRE);
        tacheFutureAuj.setDateEcheance(aujourdhui.atTime(18, 0));

        final Tache demainTache = tacheDe(30L, StatutTacheEnum.A_FAIRE);
        demainTache.setDateEcheance(aujourdhui.plusDays(1).atTime(10, 0));

        // En retard: la tache de 9h (passee)
        when(tacheRepository.findByTenantIdAndVendeurIdAndStatutAndDateEcheanceBeforeOrderByDateEcheanceAsc(
                eq(1L), eq(10L), eq(StatutTacheEnum.A_FAIRE), any())).thenReturn(List.of(tacheRetardAuj));

        // Aujourd'hui: les deux taches du jour (9h + 18h) — la requete DB les retourne toutes
        // Demain: une tache
        when(tacheRepository.findByTenantIdAndVendeurIdAndDateEcheanceBetweenOrderByDateEcheanceAsc(
                eq(1L), eq(10L), any(), any()))
                .thenReturn(List.of(tacheRetardAuj, tacheFutureAuj))
                .thenReturn(List.of(demainTache));

        final RepMaJourneeDto result = tacheService.getMaJournee(1L, 10L, aujourdhui);

        // La tache en retard ne doit apparaitre QUE dans enRetard, pas dans aujourdhui
        assertThat(result.getEnRetard()).hasSize(1);
        assertThat(result.getEnRetard().get(0).getId()).isEqualTo(10L);

        assertThat(result.getAujourdhui()).hasSize(1);
        assertThat(result.getAujourdhui().get(0).getId()).isEqualTo(20L);

        assertThat(result.getTotalAujourdhui()).isEqualTo(1);
        assertThat(result.getDemain()).hasSize(1);
    }

    @Test
    void getTachesParLead_retourneBonnesTaches() {
        final Tache t1 = tacheDe(1L, StatutTacheEnum.A_FAIRE);
        t1.setLeadId(42L);
        final Tache t2 = tacheDe(2L, StatutTacheEnum.TERMINEE);
        t2.setLeadId(42L);
        when(leadRepository.findByIdAndTenantId(42L, 1L)).thenReturn(Optional.of(new Lead()));
        when(tacheRepository.findByLeadIdOrderByDateEcheanceDesc(42L)).thenReturn(List.of(t1, t2));

        final List<RepTacheDto> result = tacheService.getTachesParLead(42L, 1L);

        assertThat(result).hasSize(2);
    }

    private Tache tacheDe(final Long id, final StatutTacheEnum statut) {
        final Tache t = new Tache();
        t.setId(id);
        t.setTenantId(1L);
        t.setVendeurId(10L);
        t.setTitre("Tache test " + id);
        t.setTypeTache(TypeTacheEnum.APPEL);
        t.setDateEcheance(LocalDateTime.now().plusHours(2));
        t.setPriorite(PrioriteTacheEnum.NORMALE);
        t.setStatut(statut);
        t.setSource("MANUELLE");
        t.setRappelEnvoye(false);
        return t;
    }
}
