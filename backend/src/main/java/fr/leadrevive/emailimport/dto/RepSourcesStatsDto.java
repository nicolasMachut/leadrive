package fr.leadrevive.emailimport.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Reponse pour le tableau de bord des sources de leads.
 *
 * <p>Aggrege les leads par source sur une periode donnee. Permet au manager
 * d'identifier la plateforme la plus rentable.</p>
 */
@Getter
@Builder
public class RepSourcesStatsDto {

    private final int periodeJours;
    private final long totalLeads;
    private final List<SourceStatDto> sources;

    @Getter
    @Builder
    public static class SourceStatDto {
        private final String source;
        private final long nbLeads;
        private final long nbRdv;
        /** nbRdv / nbLeads * 100, arrondi a 1 decimale. */
        private final double tauxConversion;
    }
}
