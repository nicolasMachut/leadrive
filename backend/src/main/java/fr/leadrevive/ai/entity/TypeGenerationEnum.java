package fr.leadrevive.ai.entity;

import lombok.Getter;

@Getter
public enum TypeGenerationEnum {

    FICHE_PREPARATION("FICHE_PREPARATION"),
    BRIEFING_QUOTIDIEN("BRIEFING_QUOTIDIEN"),
    WHATSAPP_CONVERSATION("WHATSAPP_CONVERSATION"),
    WHATSAPP_EXTRACTION("WHATSAPP_EXTRACTION"),
    AIDE_CHAT("AIDE_CHAT"),
    AIDE_SIMULATION("AIDE_SIMULATION"),
    MAPPING_CSV("MAPPING_CSV"),
    PARSING_TEXTE_LIBRE("PARSING_TEXTE_LIBRE"),
    PARSING_EMAIL("PARSING_EMAIL"),
    /** @deprecated Conserve pour compatibilite avec les anciennes lignes en base. */
    @Deprecated
    SIMULATION_PROSPECT("SIMULATION_PROSPECT");

    private final String valeur;

    TypeGenerationEnum(final String valeur) {
        this.valeur = valeur;
    }
}
