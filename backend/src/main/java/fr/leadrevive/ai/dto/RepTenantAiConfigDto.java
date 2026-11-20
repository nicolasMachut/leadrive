package fr.leadrevive.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepTenantAiConfigDto {

    private Long id;
    private String nomConcession;
    private String horairesOuverture;
    private String adresseComplete;
    private String telephoneConcession;
    private String siteWeb;
    private String emailContact;
    private Boolean premierMessageAutoActif;
    private Integer premierMessageAutoDelaiMinutes;
    private Integer premierMessageAutoFenetreDebutHeure;
    private Integer premierMessageAutoFenetreFinHeure;
}
