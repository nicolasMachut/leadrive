package fr.leadrevive.ai.entity;

import lombok.Getter;

@Getter
public enum AiStatutEnum {

    SUCCES("SUCCES"),
    ERREUR("ERREUR"),
    TIMEOUT("TIMEOUT");

    private final String valeur;

    AiStatutEnum(final String valeur) {
        this.valeur = valeur;
    }
}
