package fr.leadrevive.csvimport.dto;

import java.util.List;

public record MappingProposition(
    List<ColonneProposition> mappings,
    String formatDateDetecte,
    SplitProposition splitDetecte
) {}
