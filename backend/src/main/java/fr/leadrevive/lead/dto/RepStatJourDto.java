package fr.leadrevive.lead.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepStatJourDto {

    private LocalDate jour;
    private Integer leadsImportes;
    private Integer conversationsLancees;
    private Integer rdvObtenus;
    private Integer ventesConfirmees;
}
