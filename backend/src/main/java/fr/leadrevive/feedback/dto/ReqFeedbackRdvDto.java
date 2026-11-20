package fr.leadrevive.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReqFeedbackRdvDto(
        @NotNull Long leadId,
        Long rdvId,
        @NotBlank String qualiteAppel,
        @NotBlank String suitePrevue
) {
}
