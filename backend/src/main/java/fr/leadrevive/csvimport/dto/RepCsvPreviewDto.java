package fr.leadrevive.csvimport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepCsvPreviewDto {

    private Long idImport;
    private List<RepPreviewLineDto> lignes;
    private Integer nombreDoublonsPotentiels;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RepPreviewLineDto {

        private Integer numeroLigne;
        private Map<String, String> donnees;
        private Boolean valide;
        private List<String> erreurs;
        private Boolean doublon;
    }
}
