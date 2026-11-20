package fr.leadrevive.feedback.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Payload de l'undo d'une action RDV (Effectue / Abandonner) : rouvre la tache et
 * supprime le feedback cree, dans un court delai apres l'action (bouton "Annuler"
 * du toast). {@code rdvId} = id de la tache RDV concernee.
 */
public record ReqAnnulerActionRdvDto(
        @NotNull Long leadId,
        @NotNull Long rdvId
) {
}
