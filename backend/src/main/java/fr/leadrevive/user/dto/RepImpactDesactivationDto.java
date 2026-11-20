package fr.leadrevive.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Compteurs et destinataire calcules avant la desactivation d'un vendeur.
 * Le frontend s'en sert pour afficher une modal de confirmation precise
 * ("X leads passent en non-assigne, Y conversations vont a Z, ...")
 * avant de declencher la desactivation reelle avec {@code ?reassigner=true}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepImpactDesactivationDto {

    /** Nombre de leads dont le vendeur est titulaire (vendeurId == userId). */
    private long nbLeadsAssignes;

    /** Nombre de conversations WhatsApp EN_COURS pilotees par le vendeur. */
    private long nbConversationsActives;

    /** Nombre de taches non terminees attribuees au vendeur. */
    private long nbTachesNonTerminees;

    /** Sous-ensemble de nbTachesNonTerminees : taches RDV dont la date est dans le futur. */
    private long nbRdvAVenir;

    /** Nom du destinataire qui recevra les conversations et taches. Null si bloque. */
    private String destinataireNom;

    /** Id du destinataire (utilise par le frontend pour rappels eventuels). Null si bloque. */
    private Long destinataireId;

    /** true si la desactivation est faisable, false sinon (ex: pas de manager actif). */
    private boolean desactivationPossible;

    /** Si desactivationPossible == false, raison user-friendly a afficher. */
    private String raisonBlocage;
}
