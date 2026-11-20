package fr.leadrevive.csvimport.dto;

public record ColonneProposition(
    String headerOriginal,
    String cible,
    int confiance,
    String raison
) {}
