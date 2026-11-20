package fr.leadrevive.lead.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Corps de la re-programmation d'un envoi auto du premier message WhatsApp.
 * Sert a l'undo cote frontend : apres avoir annule l'envoi, on peut le re-armer
 * avec la date d'echeance qui etait prevue avant l'annulation.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReqReprogrammerEnvoiAutoDto {

    @NotNull(message = "La date d'echeance est obligatoire")
    private LocalDateTime dateEcheance;
}
