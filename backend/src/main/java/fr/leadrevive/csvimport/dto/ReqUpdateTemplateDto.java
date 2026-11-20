package fr.leadrevive.csvimport.dto;

import java.util.Map;

public record ReqUpdateTemplateDto(
    String nom,
    Map<String, String> mappings
) {}
