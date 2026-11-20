package fr.leadrevive.lead.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepRdvEnAttenteDto {
    private Long leadId;
    private String conversationId;
    private String nomProspect;
    private String marque;
    private String modele;
    private Integer joursDepuisRdv;
    private String nomVendeur;
}
