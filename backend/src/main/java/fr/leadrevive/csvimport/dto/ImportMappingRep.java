package fr.leadrevive.csvimport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportMappingRep {

    /** "TEMPLATE" ou "IA" */
    private String source;
    /** Non null si source = TEMPLATE */
    private Long templateId;
    private List<ColonneMappingRep> colonnes;
    private String formatDateDetecte;
    private SplitProposition splitDetecte;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ColonneMappingRep {
        private String headerOriginal;
        private String cible;
        private int confiance;
        private String raison;
        private List<String> echantillon;
    }
}
