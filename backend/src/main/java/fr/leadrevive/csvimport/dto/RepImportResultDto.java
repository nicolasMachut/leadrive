package fr.leadrevive.csvimport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepImportResultDto {

    private Long idImport;
    private Integer nombreLignes;
    private Integer nombreImportees;
    private Integer nombreErreurs;
    private Integer nombreDoublons;
    private String statut;
}
