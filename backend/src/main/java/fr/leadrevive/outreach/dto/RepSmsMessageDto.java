package fr.leadrevive.outreach.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepSmsMessageDto {

    private Long id;
    private String direction;
    /** IA / VENDEUR / SYSTEME — distingue qui a émis le message sortant
     *  (coaching manager + rendu des messages système). */
    private String expediteur;
    private String contenu;
    private String statut;
    /** Motif d'echec lisible (FR, sans jargon) quand statut == ECHEC, sinon null. */
    private String motifEchec;
    private Integer ordre;
    private LocalDateTime creeLe;
}
