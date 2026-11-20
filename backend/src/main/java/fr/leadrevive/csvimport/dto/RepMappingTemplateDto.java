package fr.leadrevive.csvimport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepMappingTemplateDto {

    private Long id;
    private String nom;
    private List<String> headersOriginaux;
    private Map<String, String> mappings;
    private Map<String, Object> optionsSplit;
    private String formatDate;
    private Integer nombreUtilisations;
    private LocalDateTime derniereUtilisation;
}
