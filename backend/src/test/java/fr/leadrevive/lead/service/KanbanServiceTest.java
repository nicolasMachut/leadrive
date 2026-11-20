package fr.leadrevive.lead.service;

import fr.leadrevive.fixtures.LeadFixture;
import fr.leadrevive.lead.entity.HistoriqueStatut;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.entity.LeadStatutEnum;
import fr.leadrevive.lead.dto.RepKanbanDto;
import fr.leadrevive.lead.dto.RepKanbanDto.KanbanCarte;
import fr.leadrevive.lead.exception.LeadNotFoundException;
import fr.leadrevive.lead.repository.HistoriqueStatutRepository;
import fr.leadrevive.lead.repository.LeadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KanbanServiceTest {

    @Mock
    private LeadRepository leadRepository;
    @Mock
    private HistoriqueStatutRepository historiqueStatutRepository;
    @Mock
    private fr.leadrevive.user.repository.UserRepository userRepository;
    @Mock
    private fr.leadrevive.tache.repository.TacheRepository tacheRepository;

    private KanbanService kanbanService;

    @BeforeEach
    void setUp() {
        kanbanService = new KanbanService(leadRepository, historiqueStatutRepository, userRepository, tacheRepository);
    }

    @Test
    void getKanban_retourne6Colonnes() {
        when(leadRepository.findByTenantIdAndArchiveFalse(1L)).thenReturn(List.of());

        final RepKanbanDto result = kanbanService.getKanban(1L, null, null, null, null, null, null);

        assertThat(result.getColonnes()).hasSize(5);
        assertThat(result.getColonnes().get(0).getStatut()).isEqualTo("NOUVEAU");
        assertThat(result.getColonnes().get(4).getStatut()).isEqualTo("TERMINE");
        assertThat(result.getTotalLeads()).isZero();
    }

    @Test
    void getKanban_groupeLeadsParStatut() {
        final Lead nouveau = leadAvecStatut(1L, LeadStatutEnum.NOUVEAU);
        final Lead contacte = leadAvecStatut(2L, LeadStatutEnum.CONTACTE);
        final Lead termine = leadAvecStatut(3L, LeadStatutEnum.TERMINE);
        when(leadRepository.findByTenantIdAndArchiveFalse(1L)).thenReturn(List.of(nouveau, contacte, termine));

        final RepKanbanDto result = kanbanService.getKanban(1L, null, null, null, null, null, null);

        assertThat(result.getTotalLeads()).isEqualTo(3);
        assertThat(colonneParStatut(result, "NOUVEAU").getCount()).isEqualTo(1);
        assertThat(colonneParStatut(result, "CONTACTE").getCount()).isEqualTo(1);
        assertThat(colonneParStatut(result, "TERMINE").getCount()).isEqualTo(1);
        assertThat(colonneParStatut(result, "EN_COURS").getCount()).isZero();
    }

    @Test
    void getKanban_filtreParVendeur() {
        final Lead leadVendeur1 = leadAvecStatut(1L, LeadStatutEnum.NOUVEAU);
        leadVendeur1.setVendeurId(10L);
        // Le filtrage par vendeur est désormais poussé en SQL : on stube la requête scopée.
        when(leadRepository.findByTenantIdAndVendeurIdAndArchiveFalse(1L, 10L)).thenReturn(List.of(leadVendeur1));

        final RepKanbanDto result = kanbanService.getKanban(1L, 10L, null, null, null, null, null);

        assertThat(result.getTotalLeads()).isEqualTo(1);
    }

    @Test
    void getKanban_filtreNonAssignes() {
        final Lead leadNonAssigne = leadAvecStatut(2L, LeadStatutEnum.NOUVEAU);
        leadNonAssigne.setVendeurId(null);
        // Le filtre "non assignés" (vendeurId=0) est poussé en SQL.
        when(leadRepository.findByTenantIdAndVendeurIdIsNullAndArchiveFalse(1L)).thenReturn(List.of(leadNonAssigne));

        final RepKanbanDto result = kanbanService.getKanban(1L, 0L, null, null, null, null, null);

        assertThat(result.getTotalLeads()).isEqualTo(1);
    }

    @Test
    void getKanban_filtreParRecherche() {
        final Lead lead1 = leadAvecStatut(1L, LeadStatutEnum.NOUVEAU);
        lead1.setNom("Dupont Jean");
        final Lead lead2 = leadAvecStatut(2L, LeadStatutEnum.NOUVEAU);
        lead2.setNom("Martin Paul");
        when(leadRepository.findByTenantIdAndArchiveFalse(1L)).thenReturn(List.of(lead1, lead2));

        final RepKanbanDto result = kanbanService.getKanban(1L, null, "dupont", null, null, null, null);

        assertThat(result.getTotalLeads()).isEqualTo(1);
    }

    @Test
    void deplacerLead_transitionValide_nouveau_vers_enCours() {
        final Lead lead = leadAvecStatut(1L, LeadStatutEnum.NOUVEAU);
        when(leadRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(lead));
        when(leadRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historiqueStatutRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final KanbanCarte carte = kanbanService.deplacerLead(1L, 1L, LeadStatutEnum.EN_COURS, "DRAG_DROP", 10L, null);

        assertThat(carte.getStatut()).isEqualTo("EN_COURS");
        assertThat(lead.getStatut()).isEqualTo(LeadStatutEnum.EN_COURS);
        assertThat(lead.getStatutModifiePar()).isEqualTo("USER");
        verify(historiqueStatutRepository).save(any());
    }

    @Test
    void deplacerLead_transitionValide_nouveau_vers_termine() {
        final Lead lead = leadAvecStatut(1L, LeadStatutEnum.NOUVEAU);
        when(leadRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(lead));
        when(leadRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historiqueStatutRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final KanbanCarte carte = kanbanService.deplacerLead(1L, 1L, LeadStatutEnum.TERMINE, "DRAG_DROP", 10L, null);

        assertThat(carte.getStatut()).isEqualTo("TERMINE");
    }

    @Test
    void deplacerLead_depuisTermine_versNouveau_ok() {
        final Lead lead = leadAvecStatut(1L, LeadStatutEnum.TERMINE);
        when(leadRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(lead));
        when(leadRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historiqueStatutRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final KanbanCarte carte = kanbanService.deplacerLead(1L, 1L, LeadStatutEnum.NOUVEAU, "DRAG_DROP", 10L, null);

        assertThat(carte.getStatut()).isEqualTo("NOUVEAU");
    }

    @Test
    void deplacerLead_transitionRetour_contacteVersNouveau_autorisee() {
        // Toute transition manuelle est desormais autorisee : le user assume
        // sa correction (ex : remettre en NOUVEAU apres un drag-drop par erreur).
        final Lead lead = leadAvecStatut(1L, LeadStatutEnum.CONTACTE);
        when(leadRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(lead));
        when(leadRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historiqueStatutRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final KanbanCarte carte = kanbanService.deplacerLead(1L, 1L, LeadStatutEnum.NOUVEAU, "DRAG_DROP", 10L, null);

        assertThat(carte.getStatut()).isEqualTo("NOUVEAU");
        assertThat(lead.getStatut()).isEqualTo(LeadStatutEnum.NOUVEAU);
        verify(historiqueStatutRepository).save(any());
    }

    @Test
    void deplacerLead_transitionMemeStatut_noop() {
        final Lead lead = leadAvecStatut(1L, LeadStatutEnum.CONTACTE);
        when(leadRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(lead));

        final KanbanCarte carte = kanbanService.deplacerLead(1L, 1L, LeadStatutEnum.CONTACTE, "DRAG_DROP", 10L, null);

        assertThat(carte.getStatut()).isEqualTo("CONTACTE");
        // Aucune sauvegarde ni historique pour un X -> X
        verify(leadRepository, org.mockito.Mockito.never()).save(any());
        verify(historiqueStatutRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void deplacerLead_leadInexistant_lanceException() {
        when(leadRepository.findByIdAndTenantId(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> kanbanService.deplacerLead(999L, 1L, LeadStatutEnum.EN_COURS, "DRAG_DROP", 10L, null))
                .isInstanceOf(LeadNotFoundException.class);
    }

    @Test
    void getKanban_masqueTelephoneEtEmail() {
        final Lead lead = leadAvecStatut(1L, LeadStatutEnum.NOUVEAU);
        lead.setTelephone("+33612345678");
        lead.setEmail("jean.dupont@example.com");
        when(leadRepository.findByTenantIdAndArchiveFalse(1L)).thenReturn(List.of(lead));

        final RepKanbanDto result = kanbanService.getKanban(1L, null, null, null, null, null, null);

        final KanbanCarte carte = result.getColonnes().get(0).getLeads().get(0);
        assertThat(carte.getTelephone()).doesNotContain("612345");
        assertThat(carte.getEmail()).doesNotContain("jean.dupont");
        assertThat(carte.getEmail()).contains("@example.com");
    }

    @Test
    void getKanban_calculePrioriteHaute_siScoreEleve() {
        final Lead lead = leadAvecStatut(1L, LeadStatutEnum.NOUVEAU);
        lead.setScoring(new BigDecimal("0.85"));
        lead.setHotnessScore(null);
        when(leadRepository.findByTenantIdAndArchiveFalse(1L)).thenReturn(List.of(lead));

        final RepKanbanDto result = kanbanService.getKanban(1L, null, null, null, null, null, null);

        assertThat(result.getColonnes().get(0).getLeads().get(0).getPriorite()).isEqualTo("HAUTE");
    }

    @Test
    void getKanban_calculeTagChaud_siScore80Plus() {
        final Lead lead = leadAvecStatut(1L, LeadStatutEnum.NOUVEAU);
        lead.setScoring(new BigDecimal("0.85"));
        lead.setHotnessScore(null);
        when(leadRepository.findByTenantIdAndArchiveFalse(1L)).thenReturn(List.of(lead));

        final RepKanbanDto result = kanbanService.getKanban(1L, null, null, null, null, null, null);

        assertThat(result.getColonnes().get(0).getLeads().get(0).getTags()).contains("Chaud");
    }

    @Test
    void getKanban_filtreParStatut() {
        final Lead nouveau = leadAvecStatut(1L, LeadStatutEnum.NOUVEAU);
        final Lead contacte = leadAvecStatut(2L, LeadStatutEnum.CONTACTE);
        when(leadRepository.findByTenantIdAndArchiveFalse(1L)).thenReturn(List.of(nouveau, contacte));

        final RepKanbanDto result = kanbanService.getKanban(1L, null, null, "NOUVEAU", null, null, null);

        assertThat(result.getTotalLeads()).isEqualTo(1);
        assertThat(colonneParStatut(result, "NOUVEAU").getCount()).isEqualTo(1);
        assertThat(colonneParStatut(result, "CONTACTE").getCount()).isZero();
    }

    @Test
    void getKanban_filtreParTerritoire() {
        final Lead leadParis = leadAvecStatut(1L, LeadStatutEnum.NOUVEAU);
        leadParis.setTerritoire("Paris");
        final Lead leadLyon = leadAvecStatut(2L, LeadStatutEnum.NOUVEAU);
        leadLyon.setTerritoire("Lyon");
        when(leadRepository.findByTenantIdAndArchiveFalse(1L)).thenReturn(List.of(leadParis, leadLyon));

        final RepKanbanDto result = kanbanService.getKanban(1L, null, null, null, "Paris", null, null);

        assertThat(result.getTotalLeads()).isEqualTo(1);
    }

    @Test
    void getKanban_filtreParMarque() {
        final Lead leadPeugeot = leadAvecStatut(1L, LeadStatutEnum.NOUVEAU);
        leadPeugeot.setMarque("Peugeot");
        final Lead leadRenault = leadAvecStatut(2L, LeadStatutEnum.NOUVEAU);
        leadRenault.setMarque("Renault");
        when(leadRepository.findByTenantIdAndArchiveFalse(1L)).thenReturn(List.of(leadPeugeot, leadRenault));

        final RepKanbanDto result = kanbanService.getKanban(1L, null, null, null, null, "Peugeot", null);

        assertThat(result.getTotalLeads()).isEqualTo(1);
    }

    @Test
    void getKanban_filtresCombines() {
        final Lead leadMatch = leadAvecStatut(1L, LeadStatutEnum.NOUVEAU);
        leadMatch.setTerritoire("Paris");
        leadMatch.setMarque("Peugeot");
        final Lead leadMarqueDiff = leadAvecStatut(2L, LeadStatutEnum.NOUVEAU);
        leadMarqueDiff.setTerritoire("Paris");
        leadMarqueDiff.setMarque("Renault");
        final Lead leadTerritoireDiff = leadAvecStatut(3L, LeadStatutEnum.NOUVEAU);
        leadTerritoireDiff.setTerritoire("Lyon");
        leadTerritoireDiff.setMarque("Peugeot");
        when(leadRepository.findByTenantIdAndArchiveFalse(1L)).thenReturn(List.of(leadMatch, leadMarqueDiff, leadTerritoireDiff));

        final RepKanbanDto result = kanbanService.getKanban(1L, null, null, null, "Paris", "Peugeot", null);

        assertThat(result.getTotalLeads()).isEqualTo(1);
    }

    // ── Helpers ─────────────────────────────────────────────

    private Lead leadAvecStatut(final Long id, final LeadStatutEnum statut) {
        final Lead lead = LeadFixture.leadAvecTenant(1L);
        lead.setId(id);
        lead.setStatut(statut);
        lead.setCreeLe(LocalDateTime.now());
        lead.setModifieLe(LocalDateTime.now());
        return lead;
    }

    private RepKanbanDto.KanbanColonne colonneParStatut(final RepKanbanDto dto, final String statut) {
        return dto.getColonnes().stream().filter(c -> c.getStatut().equals(statut)).findFirst().orElseThrow();
    }
}
