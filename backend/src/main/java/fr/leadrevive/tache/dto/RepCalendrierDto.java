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
public class RepCalendrierDto {

    private int mois;
    private int annee;
    private List<JourCalendrier> jours;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JourCalendrier {

        private LocalDate date;
        private List<RepTacheDto> taches;
        private int nombreTotal;
    }
}
