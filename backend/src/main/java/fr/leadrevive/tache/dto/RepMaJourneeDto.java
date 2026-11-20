package fr.leadrevive.tache.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepMaJourneeDto {

    private LocalDate date;
    private List<RepTacheDto> enRetard;
    private List<RepTacheDto> aujourdhui;
    private List<RepTacheDto> demain;
    private int totalAujourdhui;
    private int terminees;
    private int nbEnRetard;
}
