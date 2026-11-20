package fr.leadrevive.user.entity;

import lombok.Getter;

@Getter
public enum RoleEnum {

    SUPER_ADMIN("SUPER_ADMIN"),
    MANAGER("MANAGER"),
    VENDEUR("VENDEUR");

    private final String valeur;

    RoleEnum(final String valeur) {
        this.valeur = valeur;
    }
}
