package fr.leadrevive.lead.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReqVenteRapideDto {
    @NotNull
    private Long leadId;
    @NotNull
    private Boolean vendu;
    private BigDecimal montant;
    private String commentaire;
}
