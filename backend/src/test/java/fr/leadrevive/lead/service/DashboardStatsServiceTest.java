package fr.leadrevive.lead.service;

import fr.leadrevive.fixtures.LeadFixture;
import fr.leadrevive.lead.entity.Lead;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests des methodes utilitaires de DashboardStatsService.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardStatsServiceTest {

    @InjectMocks
    private DashboardStatsService service;

    // ===== scoreEffectif =====

    @Test
    void scoreEffectif_avecHotness_priviligieLeHotness() {
        // GIVEN
        final Lead lead = LeadFixture.lead();
        lead.setScoring(new BigDecimal("0.30"));
        lead.setHotnessScore(new BigDecimal("0.85"));

        // WHEN
        final BigDecimal score = service.scoreEffectif(lead);

        // THEN
        assertEquals(0, score.compareTo(new BigDecimal("0.85")));
    }

    @Test
    void scoreEffectif_sansHotness_fallbackSurScoring() {
        // GIVEN
        final Lead lead = LeadFixture.lead();
        lead.setScoring(new BigDecimal("0.50"));
        lead.setHotnessScore(null);

        // WHEN
        final BigDecimal score = service.scoreEffectif(lead);

        // THEN
        assertEquals(0, score.compareTo(new BigDecimal("0.50")));
    }

    @Test
    void scoreEffectif_aucunScore_retourneZero() {
        // GIVEN
        final Lead lead = LeadFixture.lead();
        lead.setScoring(null);
        lead.setHotnessScore(null);

        // WHEN
        final BigDecimal score = service.scoreEffectif(lead);

        // THEN
        assertEquals(0, score.compareTo(BigDecimal.ZERO));
    }

    @Test
    void scoreEffectif_hotnessZero_estPrisMemeSiScoringEleve() {
        // GIVEN
        // hotness = 0 doit etre prefere a scoring = 0.9 (l'IA dit que le lead est mort)
        final Lead lead = LeadFixture.lead();
        lead.setScoring(new BigDecimal("0.90"));
        lead.setHotnessScore(BigDecimal.ZERO);

        // WHEN
        final BigDecimal score = service.scoreEffectif(lead);

        // THEN
        assertEquals(0, score.compareTo(BigDecimal.ZERO));
    }

    // ===== calculerPriorite =====

    @Test
    void calculerPriorite_scoreEleveLeadAncien_retourneUrgent() {
        // GIVEN
        final Lead lead = LeadFixture.lead();
        lead.setScoring(new BigDecimal("0.85"));
        lead.setHotnessScore(null);
        lead.setCreeLe(LocalDateTime.now().minusDays(2));

        // WHEN / THEN
        assertEquals("URGENT", service.calculerPriorite(lead));
    }

    @Test
    void calculerPriorite_scoreEleveLeadRecent_retourneMedium() {
        // GIVEN
        // Score eleve mais lead < 24h : pas encore urgent
        final Lead lead = LeadFixture.lead();
        lead.setScoring(new BigDecimal("0.95"));
        lead.setHotnessScore(null);
        lead.setCreeLe(LocalDateTime.now().minusHours(2));

        // WHEN / THEN
        assertEquals("MEDIUM", service.calculerPriorite(lead));
    }

    @Test
    void calculerPriorite_scoreMoyen_retourneMedium() {
        // GIVEN
        final Lead lead = LeadFixture.lead();
        lead.setScoring(new BigDecimal("0.55"));
        lead.setHotnessScore(null);
        lead.setCreeLe(LocalDateTime.now().minusDays(5));

        // WHEN / THEN
        assertEquals("MEDIUM", service.calculerPriorite(lead));
    }

    @Test
    void calculerPriorite_scoreFaible_retourneNormal() {
        // GIVEN
        final Lead lead = LeadFixture.lead();
        lead.setScoring(new BigDecimal("0.20"));
        lead.setHotnessScore(null);
        lead.setCreeLe(LocalDateTime.now().minusDays(10));

        // WHEN / THEN
        assertEquals("NORMAL", service.calculerPriorite(lead));
    }

    @Test
    void calculerPriorite_seuilUrgentExact08_retourneUrgent() {
        // GIVEN
        final Lead lead = LeadFixture.lead();
        lead.setScoring(new BigDecimal("0.80"));
        lead.setHotnessScore(null);
        lead.setCreeLe(LocalDateTime.now().minusDays(2));

        // WHEN / THEN
        assertEquals("URGENT", service.calculerPriorite(lead));
    }

    @Test
    void calculerPriorite_seuilMediumExact05_retourneMedium() {
        // GIVEN
        final Lead lead = LeadFixture.lead();
        lead.setScoring(new BigDecimal("0.50"));
        lead.setHotnessScore(null);
        lead.setCreeLe(LocalDateTime.now().minusDays(10));

        // WHEN / THEN
        assertEquals("MEDIUM", service.calculerPriorite(lead));
    }

    @Test
    void calculerPriorite_hotnessPrevautSurScoring_pourLaPriorite() {
        // GIVEN
        // Scoring CSV bas mais hotness recalcule eleve
        final Lead lead = LeadFixture.lead();
        lead.setScoring(new BigDecimal("0.10"));
        lead.setHotnessScore(new BigDecimal("0.90"));
        lead.setCreeLe(LocalDateTime.now().minusDays(2));

        // WHEN / THEN
        assertEquals("URGENT", service.calculerPriorite(lead));
    }

    @Test
    void calculerPriorite_aucunScore_retourneNormal() {
        // GIVEN
        final Lead lead = LeadFixture.lead();
        lead.setScoring(null);
        lead.setHotnessScore(null);
        lead.setCreeLe(LocalDateTime.now().minusDays(2));

        // WHEN / THEN
        assertEquals("NORMAL", service.calculerPriorite(lead));
    }

    @Test
    void calculerPriorite_leadExactement24h_retourneMedium() {
        // GIVEN
        final Lead lead = LeadFixture.lead();
        lead.setScoring(new BigDecimal("0.85"));
        lead.setHotnessScore(null);
        lead.setCreeLe(LocalDateTime.now().minusHours(24));

        // WHEN / THEN
        // 24h pile, le test "> 24" est faux → MEDIUM
        assertEquals("MEDIUM", service.calculerPriorite(lead));
    }
}
