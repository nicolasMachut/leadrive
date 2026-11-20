package fr.leadrevive.csvimport.dto;

import java.util.Map;

public record MappingValidationReq(
    Map<String, String> mappings,
    SplitOption splitOption,
    String formatDate,
    String nomTemplate
) {}
