package fr.leadrevive.csvimport.dto;

import fr.leadrevive.csvimport.entity.FormatDetecte;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportAnalyseRep {

    private Long importId;
    private List<String> headers;
    private List<List<String>> echantillon;
    private FormatDetecte formatDetecte;
    private int nombreLignesTotal;
}
