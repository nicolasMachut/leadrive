package fr.leadrevive.stats.service;

import fr.leadrevive.lead.entity.HistoriqueStatut;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.entity.LeadStatutEnum;
import fr.leadrevive.lead.repository.HistoriqueStatutRepository;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.stats.dto.RepPipelineStatsDto;
import fr.leadrevive.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Non-regression du taux de RDV (incident : taux affiche a 200%).
 *
 * <p>Le taux est un taux de CONVERSION de cohorte : parmi les leads CREES sur la periode,
 * combien ont obtenu un RDV IA. Le numerateur est un sous-ensemble du denominateur -> borne
 * a 100%. Le compte hero "RDV pris" (facturable) reste base sur l'evenement de la periode
 * (tous leads confondus, y compris crees avant) et peut donc rester superieur au total.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PipelineStatsServiceTauxTest {

    @Mock
    private LeadRepository leadRepository;
    @Mock
    private HistoriqueStatutRepository historiqueStatutRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PipelineStatsService service;

    private static Lead lead(final Long id, final Long vendeurId, final LocalDateTime creeLe) {
        final Lead l = new Lead();
        l.setId(id);
        l.setTenantId(7L);
        l.setVendeurId(vendeurId);
        l.setStatut(LeadStatutEnum.RDV_PRIS);
        l.setCreeLe(creeLe);
        return l;
    }

    private static HistoriqueStatut rdvIa(final Long leadId, final LocalDateTime creeLe) {
        final HistoriqueStatut h = new HistoriqueStatut();
        h.setLeadId(leadId);
        h.setTenantId(7L);
        h.setStatutPrecedent(LeadStatutEnum.EN_COURS);
        h.setStatutSuivant(LeadStatutEnum.RDV_PRIS);
        h.setSource("IA");
        h.setCreeLe(creeLe);
        return h;
    }

    @Test
    void tauxRdv_neDepassePas100_quandUnLeadPlusAncienConvertitDansLaPeriode() {
        final LocalDateTime maintenant = LocalDateTime.now();
        // 1 lead cree DANS la periode (30j) + 1 lead cree AVANT (60j) : total cohorte = 1.
        final Lead leadNouveau = lead(1L, 99L, maintenant.minusDays(5));
        final Lead leadAncien = lead(2L, 99L, maintenant.minusDays(60));

        // Les DEUX obtiennent un RDV IA pendant la periode -> 2 evenements RDV (hero=2),
        // mais 1 seul provient d'un lead cree sur la periode -> cohorte=1 -> taux=100%.
        final HistoriqueStatut evtNouveau = rdvIa(1L, maintenant.minusDays(4));
        final HistoriqueStatut evtAncien = rdvIa(2L, maintenant.minusDays(3));

        when(leadRepository.findByTenantIdAndVendeurIdAndArchiveFalse(eq(7L), eq(99L)))
                .thenReturn(List.of(leadNouveau, leadAncien));
        when(historiqueStatutRepository
                .findByTenantIdAndCreeLeAfterAndCreeLeBeforeOrderByCreeLeDesc(eq(7L), any(), any()))
                .thenReturn(List.of(evtAncien, evtNouveau));

        final RepPipelineStatsDto stats = service.getStats(7L, 99L, "30j");
        final RepPipelineStatsDto.ResumeStats resume = stats.getResume();

        // Denominateur = leads crees sur la periode = 1
        assertThat(resume.getTotalLeads()).isEqualTo(1);
        // Taux de conversion de cohorte : borne a 100% (le bug donnait 200%)
        assertThat(resume.getTauxRdv()).isLessThanOrEqualTo(100.0);
        assertThat(resume.getTauxRdv()).isEqualTo(100.0);
        // Hero "RDV pris" (facturable) = evenements de la periode, tous leads confondus = 2
        assertThat(resume.getRdvPris()).isEqualTo(2);
    }
}
