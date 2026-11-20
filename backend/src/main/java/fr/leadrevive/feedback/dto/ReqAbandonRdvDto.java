package fr.leadrevive.feedback.dto;

import jakarta.validation.constraints.NotNull;

public record ReqAbandonRdvDto(
        @NotNull Long leadId,
        Long rdvId,
        @NotNull String raisonAbandon
) {
}
