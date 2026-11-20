package fr.leadrevive.lead.dto;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReqUpdateLeadDto {

    @Pattern(regexp = "NOUVEAU|EN_COURS|CONTACTE|RDV_PRIS|TERMINE",
             message = "Le statut doit etre NOUVEAU, EN_COURS, CONTACTE, RDV_PRIS ou TERMINE")
    private String statut;

    private Long vendeurId;
    private String nom;
    private String telephone;
    private String email;
    private String etape;
}
