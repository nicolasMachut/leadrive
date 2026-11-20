package fr.leadrevive.lead.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepKanbanDto {

    private List<KanbanColonne> colonnes;
    private int totalLeads;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KanbanColonne {
        private String statut;
        private String label;
        private String couleur;
        private List<KanbanCarte> leads;
        private int count;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KanbanCarte {
        private Long id;
        private String nom;
        private String telephone;
        private String email;
        private String vehiculeInteret;
        private Integer scoreIA;
        private LocalDateTime dateCreation;
        private LocalDateTime dernierContact;
        private Long vendeurId;
        private String vendeurNom;
        private String sourceOrigine;
        private String statut;
        private String tempsDepuisDernierContact;
        private String priorite;
        private List<String> tags;
        private Integer kilometrage;
        private String energie;
    }
}
