package fr.leadrevive.tache.dto;

import java.time.LocalDateTime;

public record ReqTacheDto(
        Long leadId,
        String titre,
        String description,
        String typeTache,
        LocalDateTime dateEcheance,
        Integer dureeMinutes,
        String priorite,
        Integer rappelMinutesAvant
) {
}
