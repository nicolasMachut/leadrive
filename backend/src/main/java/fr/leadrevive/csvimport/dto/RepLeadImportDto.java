package fr.leadrevive.csvimport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepLeadImportDto {

    private Long idImport;
    private String nomFichier;
    private LocalDateTime dateImport;
    private Integer nombreLignes;
    private Integer nombreImportees;
    private Integer nombreErreurs;
    private Integer nombreDoublons;
    private String statut;
    private String mappingColonnes;
    private String erreurs;
    private Long importePar;
    private String typeFichier;
}
