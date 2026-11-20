package fr.leadrevive.lead.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepTexteLibreDto {

    private String nom;
    private String prenom;
    private String telephone;
    private String email;
    private String marque;
    private String modele;
    private String vehiculeInteret;
    private String sourceDetectee;
    private int confiance;
    private Map<String, Object> donneesSupplementaires;
}
