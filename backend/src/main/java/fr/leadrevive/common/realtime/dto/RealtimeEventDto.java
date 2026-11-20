package fr.leadrevive.common.realtime.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Event temps reel pousse aux clients via SSE.
 *
 * <p>Type pivots :</p>
 * <ul>
 *   <li>{@code lead.created} : un nouveau lead a ete cree (CSV manuel,
 *       email auto, texte libre).</li>
 * </ul>
 *
 * <p>Le payload {@code data} est libre : id du lead, prenom + source pour
 * l'affichage du toast cote frontend. Pas de donnees sensibles (PII complete) :
 * le frontend recupere la fiche detaillee a la demande.</p>
 */
public record RealtimeEventDto(
        String type,
        Map<String, Object> data,
        LocalDateTime emisLe
) {
    public static RealtimeEventDto leadCreated(final Long leadId, final String nomComplet, final String source) {
        return new RealtimeEventDto(
                "lead.created",
                Map.of(
                        "leadId", leadId,
                        "nom", nomComplet != null ? nomComplet : "",
                        "source", source != null ? source : ""
                ),
                LocalDateTime.now()
        );
    }

    /**
     * Un lead existant a change (statut notamment) : permet aux vues liste / Kanban
     * ouvertes de se rafraichir en temps reel. Pas de toast cote front (ce n'est pas
     * un nouveau lead), juste un signal de refresh.
     */
    public static RealtimeEventDto leadUpdated(final Long leadId, final String statut) {
        return new RealtimeEventDto(
                "lead.updated",
                Map.of(
                        "leadId", leadId,
                        "statut", statut != null ? statut : ""
                ),
                LocalDateTime.now()
        );
    }

    /**
     * Pour les imports en masse (CSV) : un seul event recapitulatif a la fin
     * plutot que N toasts qui spamment l'utilisateur.
     */
    public static RealtimeEventDto leadBatchImported(final int nombre, final String source) {
        return new RealtimeEventDto(
                "lead.batch.imported",
                Map.of(
                        "nombre", nombre,
                        "source", source != null ? source : ""
                ),
                LocalDateTime.now()
        );
    }
}
