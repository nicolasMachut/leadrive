package fr.leadrevive.lead.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReqVenteDto {

    private BigDecimal montant;

    @NotNull(message = "La date de vente est obligatoire")
    private LocalDate dateVente;

    @NotNull(message = "La source est obligatoire")
    private String source;

    private String commentaire;
}
