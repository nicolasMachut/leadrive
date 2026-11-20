package fr.leadrevive.lead.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepLeadChaudDto {
    private Long leadId;
    private String nom;
    private String marque;
    private String modele;
    private BigDecimal scoring;
    private String telephone;
    private LocalDateTime creeLe;
    private LocalDateTime dernierContact;
    private String priorite; // URGENT, MEDIUM, NORMAL
}
