package fr.leadrevive.outreach.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepConversationInboxDto {
    private String conversationId;
    private Long leadId;
    private String nomProspect;
    private String marque;
    private String modele;
    private String territoire;
    private BigDecimal scoring;
    /** Score d'engagement comportemental recalcule par l'IA (prioritaire sur scoring si present). */
    private BigDecimal hotnessScore;
    private String statut;
    /** Statut du LEAD dans le funnel commercial (NOUVEAU/CONTACTE/EN_COURS/RDV_PRIS/TERMINE), distinct du statut de conversation. */
    private String leadStatut;
    private Integer nombreMessages;
    private LocalDateTime dernierMessageLe;
    private String dernierMessageContenu;
    private String dernierMessageDirection;
    private Boolean messageNonLu;
    private Long userId;
    private String nomVendeur;
    private String modePilotage;
    private String canal;
    /** Lead sensible (prospect mecontent ayant declenche un handoff). Affiche un badge rouge dans l'inbox. */
    private Boolean clientIrrite;
}
