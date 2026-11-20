package fr.leadrevive.csvimport.entity;

import lombok.Getter;

@Getter
public enum ImportStatutEnum {

    EN_COURS("EN_COURS"),
    TERMINE("TERMINE"),
    ERREUR("ERREUR");

    private final String valeur;

    ImportStatutEnum(final String valeur) {
        this.valeur = valeur;
    }
}
