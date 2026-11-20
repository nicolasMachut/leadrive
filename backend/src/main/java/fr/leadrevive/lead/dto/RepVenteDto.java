package fr.leadrevive.lead.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepVenteDto {

    private Long id;
    private Long leadId;
    private BigDecimal montant;
    private LocalDate dateVente;
    private String source;
    private String commentaire;
    private LocalDateTime creeLe;
}
