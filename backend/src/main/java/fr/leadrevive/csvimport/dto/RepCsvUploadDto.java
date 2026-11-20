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
public class RepCsvUploadDto {

    private Long idImport;
    private List<String> colonnesDetectees;
    private List<Map<String, String>> apercu;
}
