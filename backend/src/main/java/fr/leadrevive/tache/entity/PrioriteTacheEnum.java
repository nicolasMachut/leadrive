package fr.leadrevive.tache.entity;

import lombok.Getter;

@Getter
public enum PrioriteTacheEnum {

    HAUTE("HAUTE"),
    NORMALE("NORMALE"),
    BASSE("BASSE");

    private final String valeur;

    PrioriteTacheEnum(final String valeur) {
        this.valeur = valeur;
    }
}
