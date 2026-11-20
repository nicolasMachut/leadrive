package fr.leadrevive.outreach.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests du helper pur {@link PremierMessageAutoFenetre} qui calcule l'heure d'envoi
 * EFFECTIVE d'un premier message auto en fonction de la plage horaire d'envoi.
 */
class PremierMessageAutoFenetreTest {

    private static final LocalDate JOUR = LocalDate.of(2026, 7, 13);

    @Test
    void dansLaPlage_retourneLEcheanceBrute() {
        // maintenant 9h, plage 8h-20h : l'echeance prevue a 10h est dans la plage.
        final LocalDateTime maintenant = JOUR.atTime(9, 0);
        final LocalDateTime prevuLe = JOUR.atTime(10, 0);

        assertThat(PremierMessageAutoFenetre.calculerEnvoiEffectif(prevuLe, 8, 20, maintenant))
                .isEqualTo(prevuLe);
        assertThat(PremierMessageAutoFenetre.estHorsPlage(prevuLe, 8, 20, maintenant)).isFalse();
    }

    @Test
    void avantOuverture_reporteAujourdhuiALHeureDeDebut() {
        // maintenant 6h, plage 8h-20h : echeance a 6h -> aujourd'hui 8h.
        final LocalDateTime maintenant = JOUR.atTime(6, 0);
        final LocalDateTime prevuLe = JOUR.atTime(6, 30);

        assertThat(PremierMessageAutoFenetre.calculerEnvoiEffectif(prevuLe, 8, 20, maintenant))
                .isEqualTo(JOUR.atTime(8, 0));
        assertThat(PremierMessageAutoFenetre.estHorsPlage(prevuLe, 8, 20, maintenant)).isTrue();
    }

    @Test
    void apresFermeture_reporteLendemainALHeureDeDebut() {
        // maintenant 21h, plage 8h-20h : echeance a 21h -> demain 8h.
        final LocalDateTime maintenant = JOUR.atTime(21, 0);
        final LocalDateTime prevuLe = JOUR.atTime(21, 30);

        assertThat(PremierMessageAutoFenetre.calculerEnvoiEffectif(prevuLe, 8, 20, maintenant))
                .isEqualTo(JOUR.plusDays(1).atTime(8, 0));
        assertThat(PremierMessageAutoFenetre.estHorsPlage(prevuLe, 8, 20, maintenant)).isTrue();
    }

    @Test
    void aLHeureDeFin_estExclue_reporteLendemain() {
        // Plage 8h-20h, fin exclusive : a 20h pile on est HORS plage -> demain 8h.
        final LocalDateTime maintenant = JOUR.atTime(20, 0);
        final LocalDateTime prevuLe = JOUR.atTime(20, 0);

        assertThat(PremierMessageAutoFenetre.calculerEnvoiEffectif(prevuLe, 8, 20, maintenant))
                .isEqualTo(JOUR.plusDays(1).atTime(8, 0));
        assertThat(PremierMessageAutoFenetre.estHorsPlage(prevuLe, 8, 20, maintenant)).isTrue();
    }

    @Test
    void finAMinuit_jamaisReporteEnHaut() {
        // Plage 8h-24h : a 23h on est encore dans la plage (heure 23 < 24) -> pas de report.
        final LocalDateTime maintenant = JOUR.atTime(23, 0);
        final LocalDateTime prevuLe = JOUR.atTime(23, 30);

        assertThat(PremierMessageAutoFenetre.calculerEnvoiEffectif(prevuLe, 8, 24, maintenant))
                .isEqualTo(prevuLe);
        assertThat(PremierMessageAutoFenetre.estHorsPlage(prevuLe, 8, 24, maintenant)).isFalse();
    }

    @Test
    void echeancePassee_prendMaintenantCommeBase() {
        // prevuLe deja passe (scheduler pas encore tourne) : base = maintenant.
        // maintenant 10h dans la plage 8h-20h -> renvoie maintenant.
        final LocalDateTime maintenant = JOUR.atTime(10, 0);
        final LocalDateTime prevuLe = JOUR.atTime(9, 0);

        assertThat(PremierMessageAutoFenetre.calculerEnvoiEffectif(prevuLe, 8, 20, maintenant))
                .isEqualTo(maintenant);
        assertThat(PremierMessageAutoFenetre.estHorsPlage(prevuLe, 8, 20, maintenant)).isFalse();
    }
}
