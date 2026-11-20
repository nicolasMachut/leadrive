package fr.leadrevive.lead.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Element leger de la liste "Messages auto a venir" (dashboard) : envoi automatique
 * du premier message WhatsApp en attente pour un lead import email.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepEnvoiAutoAVenirDto {

    private Long idLead;
    private String nom;
    /** Echeance BRUTE armee (creation + delai). Conserve pour l'undo de "Annuler". */
    private LocalDateTime premierMessageAutoPrevuLe;
    /** Heure d'envoi EFFECTIVE calculee (tenant compte de la plage horaire). A afficher. */
    private LocalDateTime premierMessageAutoEffectifLe;
    /** true si l'envoi est repousse hors plage horaire. */
    private boolean premierMessageAutoHorsPlage;
}
