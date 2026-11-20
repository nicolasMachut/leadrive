package fr.leadrevive.lead.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReqCreateLeadFromTexteDto {

    private String nom;
    private String prenom;
    private String telephone;
    private String email;
    private String marque;
    private String modele;
    private String vehiculeInteret;
    private String sourceDetectee;
    private Map<String, Object> donneesSupplementaires;
    private String texteOriginal;
}
