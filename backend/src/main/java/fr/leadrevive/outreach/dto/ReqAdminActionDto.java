package fr.leadrevive.outreach.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Action admin sensible sur une config WhatsApp (force ACTIF, desactivation,
 * modification statut). Une justification min 20 caracteres est obligatoire
 * et systematiquement loguee dans la table whatsapp_admin_actions.
 *
 * @param justification raison de l'action (audit append-only en BDD)
 */
public record ReqAdminActionDto(
        @NotBlank
        @Size(min = 20, max = 500, message = "La justification doit faire entre 20 et 500 caracteres")
        String justification
) {}
