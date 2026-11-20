package fr.leadrevive.outreach.service;

import fr.leadrevive.fixtures.ConversationAnalyseFixture;
import fr.leadrevive.fixtures.LeadFixture;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.outreach.entity.ConversationAnalyse;
import fr.leadrevive.outreach.entity.IntentionEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests de la formule appliquerHotness().
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConversationAnalyseServiceHotnessTest {

    @InjectMocks
    private ConversationAnalyseService service;

    @Test
    void appliquerHotness_intentionRdvConfirme_boostDe030() {
        // GIVEN
        final Lead lead = LeadFixture.leadAvecScoring(new BigDecimal("0.50"));
        final ConversationAnalyse analyse = ConversationAnalyseFixture.analyse(IntentionEnum.RDV, null, null, false, true);

        // WHEN
        service.appliquerHotness(lead, analyse);

        // THEN
        assertEquals(0, lead.getHotnessScore().compareTo(new BigDecimal("0.80")));
    }

    @Test
    void appliquerHotness_intentionRdvProposeMaisNonConfirme_boostDe010() {
        // GIVEN
        final Lead lead = LeadFixture.leadAvecScoring(new BigDecimal("0.50"));
        final ConversationAnalyse analyse = ConversationAnalyseFixture.analyse(IntentionEnum.RDV, null, null, false, false);

        // WHEN
        service.appliquerHotness(lead, analyse);

        // THEN
        assertEquals(0, lead.getHotnessScore().compareTo(new BigDecimal("0.60")));
    }

    @Test
    void appliquerHotness_intentionStop_baisseDe050() {
        // GIVEN
        final Lead lead = LeadFixture.leadAvecScoring(new BigDecimal("0.50"));
        final ConversationAnalyse analyse = ConversationAnalyseFixture.analyse(IntentionEnum.STOP, null, null, false);

        // WHEN
        service.appliquerHotness(lead, analyse);

        // THEN
        assertEquals(0, lead.getHotnessScore().compareTo(new BigDecimal("0")));
    }

    @Test
    void appliquerHotness_intentionRefus_baisseDe050() {
        // GIVEN
        final Lead lead = LeadFixture.leadAvecScoring(new BigDecimal("0.80"));
        final ConversationAnalyse analyse = ConversationAnalyseFixture.analyse(IntentionEnum.REFUS, null, null, false);

        // WHEN
        service.appliquerHotness(lead, analyse);

        // THEN
        assertEquals(0, lead.getHotnessScore().compareTo(new BigDecimal("0.30")));
    }

    @Test
    void appliquerHotness_intentionAutre_pasDeChangement() {
        // GIVEN
        final Lead lead = LeadFixture.leadAvecScoring(new BigDecimal("0.50"));
        final ConversationAnalyse analyse = ConversationAnalyseFixture.analyseNeutre();

        // WHEN
        service.appliquerHotness(lead, analyse);

        // THEN
        assertEquals(0, lead.getHotnessScore().compareTo(new BigDecimal("0.50")));
    }

    @Test
    void appliquerHotness_signalFort_boostDe010() {
        // GIVEN
        final Lead lead = LeadFixture.leadAvecScoring(new BigDecimal("0.50"));
        final ConversationAnalyse analyse = ConversationAnalyseFixture.analyse(
                IntentionEnum.AUTRE, List.of("budget_mentionne"), null, false);

        // WHEN
        service.appliquerHotness(lead, analyse);

        // THEN
        assertEquals(0, lead.getHotnessScore().compareTo(new BigDecimal("0.60")));
    }

    @Test
    void appliquerHotness_signalFaible_boostDe005() {
        // GIVEN
        final Lead lead = LeadFixture.leadAvecScoring(new BigDecimal("0.50"));
        final ConversationAnalyse analyse = ConversationAnalyseFixture.analyse(
                IntentionEnum.AUTRE, List.of("modele_precis"), null, false);

        // WHEN
        service.appliquerHotness(lead, analyse);

        // THEN
        assertEquals(0, lead.getHotnessScore().compareTo(new BigDecimal("0.55")));
    }

    @Test
    void appliquerHotness_plusieursSignaux_seCumulent() {
        // GIVEN
        final Lead lead = LeadFixture.leadAvecScoring(new BigDecimal("0.30"));
        final ConversationAnalyse analyse = ConversationAnalyseFixture.analyse(
                IntentionEnum.AUTRE, List.of("budget_mentionne", "urgence_forte"), null, false);

        // WHEN
        service.appliquerHotness(lead, analyse);

        // THEN
        // 0.30 + 0.10 + 0.10 = 0.50
        assertEquals(0, lead.getHotnessScore().compareTo(new BigDecimal("0.50")));
    }

    @Test
    void appliquerHotness_objection_baisseDe010() {
        // GIVEN
        final Lead lead = LeadFixture.leadAvecScoring(new BigDecimal("0.50"));
        final ConversationAnalyse analyse = ConversationAnalyseFixture.analyse(
                IntentionEnum.AUTRE, null, List.of("prix_trop_eleve"), false);

        // WHEN
        service.appliquerHotness(lead, analyse);

        // THEN
        assertEquals(0, lead.getHotnessScore().compareTo(new BigDecimal("0.40")));
    }

    @Test
    void appliquerHotness_handoff_boostDe020() {
        // GIVEN
        final Lead lead = LeadFixture.leadAvecScoring(new BigDecimal("0.50"));
        final ConversationAnalyse analyse = ConversationAnalyseFixture.analyse(
                IntentionEnum.AUTRE, null, null, true);

        // WHEN
        service.appliquerHotness(lead, analyse);

        // THEN
        assertEquals(0, lead.getHotnessScore().compareTo(new BigDecimal("0.70")));
    }

    @Test
    void appliquerHotness_scoreNegatif_clampeAZero() {
        // GIVEN
        final Lead lead = LeadFixture.leadAvecScoring(new BigDecimal("0.10"));
        // -0.50 (STOP) → score negatif
        final ConversationAnalyse analyse = ConversationAnalyseFixture.analyse(
                IntentionEnum.STOP, null, null, false);

        // WHEN
        service.appliquerHotness(lead, analyse);

        // THEN
        assertEquals(0, lead.getHotnessScore().compareTo(new BigDecimal("0")));
    }

    @Test
    void appliquerHotness_scoreSuperieurAUn_clampeAUn() {
        // GIVEN
        final Lead lead = LeadFixture.leadAvecScoring(new BigDecimal("0.90"));
        // +0.30 (RDV confirme) +0.20 (handoff) → 1.40
        final ConversationAnalyse analyse = ConversationAnalyseFixture.analyse(
                IntentionEnum.RDV, null, null, true, true);

        // WHEN
        service.appliquerHotness(lead, analyse);

        // THEN
        assertEquals(0, lead.getHotnessScore().compareTo(new BigDecimal("1.00")));
    }

    @Test
    void appliquerHotness_scoringInitialNull_baseA050() {
        // GIVEN
        final Lead lead = LeadFixture.leadAvecScoring(null);
        final ConversationAnalyse analyse = ConversationAnalyseFixture.analyseNeutre();

        // WHEN
        service.appliquerHotness(lead, analyse);

        // THEN
        assertEquals(0, lead.getHotnessScore().compareTo(new BigDecimal("0.50")));
    }

    @Test
    void appliquerHotness_horodatageRecalcul_estRenseigne() {
        // GIVEN
        final Lead lead = LeadFixture.leadAvecScoring(new BigDecimal("0.50"));
        final ConversationAnalyse analyse = ConversationAnalyseFixture.analyseNeutre();

        // WHEN
        service.appliquerHotness(lead, analyse);

        // THEN
        assertNotNull(lead.getHotnessCalculeLe());
    }

    @Test
    void appliquerHotness_combinaisonComplete_appliqueTousLesAjustements() {
        // GIVEN
        final Lead lead = LeadFixture.leadAvecScoring(new BigDecimal("0.50"));
        // 0.50 +0.30 (RDV confirme) +0.10 (urgence_forte) +0.05 (modele_precis)
        // -0.10 (prix_trop_eleve) -0.10 (delai_trop_long) = 0.75
        final ConversationAnalyse analyse = ConversationAnalyseFixture.analyse(
                IntentionEnum.RDV,
                List.of("urgence_forte", "modele_precis"),
                List.of("prix_trop_eleve", "delai_trop_long"),
                false,
                true);

        // WHEN
        service.appliquerHotness(lead, analyse);

        // THEN
        assertAll(
                () -> assertEquals(0, lead.getHotnessScore().compareTo(new BigDecimal("0.75"))),
                () -> assertNotNull(lead.getHotnessCalculeLe())
        );
    }
}
