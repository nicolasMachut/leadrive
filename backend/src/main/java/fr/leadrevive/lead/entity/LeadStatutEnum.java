package fr.leadrevive.lead.entity;

import lombok.Getter;

@Getter
public enum LeadStatutEnum {

    NOUVEAU("NOUVEAU"),
    CONTACTE("CONTACTE"),
    EN_COURS("EN_COURS"),
    RDV_PRIS("RDV_PRIS"),
    TERMINE("TERMINE");

    private final String valeur;

    LeadStatutEnum(final String valeur) {
        this.valeur = valeur;
    }
}
