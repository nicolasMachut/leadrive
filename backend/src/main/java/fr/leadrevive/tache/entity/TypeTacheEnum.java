package fr.leadrevive.tache.entity;

import lombok.Getter;

@Getter
public enum TypeTacheEnum {

    APPEL("APPEL"),
    EMAIL("EMAIL"),
    RDV("RDV"),
    ESSAI("ESSAI"),
    RELANCE("RELANCE"),
    DEVIS("DEVIS"),
    AUTRE("AUTRE");

    private final String valeur;

    TypeTacheEnum(final String valeur) {
        this.valeur = valeur;
    }
}
