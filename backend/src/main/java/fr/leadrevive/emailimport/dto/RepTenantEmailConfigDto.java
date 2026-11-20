package fr.leadrevive.emailimport.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reponse de configuration email d'import pour un tenant
 * (vue manager ou admin).
 */
@Getter
@Builder
public class RepTenantEmailConfigDto {

    /** ID interne, peut etre null si le tenant n'a pas encore d'adresse. */
    private final Long id;

    private final Long tenantId;

    /** Adresse complete (ex: garage-dupont-x7k9@leads.leadrive.fr). */
    private final String emailAddress;

    /** ACTIVE ou DESACTIVE. */
    private final String statut;

    /** Compteur cumulatif des emails recus depuis la creation. */
    private final Integer nbEmailsRecus;

    /** Date du dernier email recu, null si jamais. */
    private final LocalDateTime dernierEmailRecuLe;

    private final LocalDateTime creeLe;

    /** Stats periode courante (30j) : nb leads crees, taux parsing OK. */
    private final StatsPeriodeDto stats;

    /** Historique des anciennes adresses (audit). Vide si jamais regenere. */
    private final List<AncienneAdresseDto> anciennesAdresses;

    @Getter
    @Builder
    public static class AncienneAdresseDto {
        private final String adresse;
        private final LocalDateTime remplaceeLe;
    }

    @Getter
    @Builder
    public static class StatsPeriodeDto {
        private final int periodeJours;
        private final long nbEmailsRecus;
        private final long nbLeadsCrees;
        private final long nbLeadsDedupliques;
        private final long nbParseEchec;
        private final long nbRejetSpam;
        /** Taux de parsing OK (= LEAD_CREE + LEAD_DEDUPLIQUE + LEAD_INCOMPLET / total). */
        private final double tauxParsingOk;
    }
}
