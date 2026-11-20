package fr.leadrevive.tache.entity;

import lombok.Getter;

@Getter
public enum StatutTacheEnum {

    A_FAIRE("A_FAIRE"),
    EN_COURS("EN_COURS"),
    TERMINEE("TERMINEE"),
    ANNULEE("ANNULEE");

    private final String valeur;

    StatutTacheEnum(final String valeur) {
        this.valeur = valeur;
    }
}
