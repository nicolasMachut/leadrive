package fr.leadrevive.outreach.entity;

import fr.leadrevive.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.time.LocalDateTime;

@Entity
@Table(name = "sms_conversation")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = Long.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
public class SmsConversation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sms_conversation")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    // Optimistic locking (bug-hunter #13). Webhook Twilio + scheduler relances +
    // UI toggle IA/HUMAIN peuvent muter en parallele : sans @Version, last writer wins.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "lead_id", nullable = false)
    private Long leadId;

    // Nullable : une conversation demarree par le SYSTEME (envoi auto du premier
    // message, PremierMessageAutoJob) n'a pas d'utilisateur declencheur.
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "conversation_id", nullable = false, unique = true)
    private String conversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private StatutConversationSmsEnum statut;

    @Column(name = "numero_prospect")
    private String numeroProspect;

    @Column(name = "nombre_messages")
    private Integer nombreMessages;

    @Column(name = "dernier_message_le")
    private LocalDateTime dernierMessageLe;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_pilotage")
    private ModePilotageEnum modePilotage = ModePilotageEnum.IA_AUTO;

    @Column(name = "reprise_par_user_id")
    private Long repriseParUserId;

    @Column(name = "reprise_par_le")
    private LocalDateTime repriseParLe;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal")
    private CanalConversationEnum canal = CanalConversationEnum.WHATSAPP;

    @Column(name = "system_prompt", columnDefinition = "text")
    private String systemPrompt;

    @Column(name = "derniere_lecture_le")
    private LocalDateTime derniereLectureLe;

    @Column(name = "rdv_propose_le")
    private LocalDateTime rdvProposeLe;

    @Column(name = "rdv_confirme_le")
    private LocalDateTime rdvConfirmeLe;

    @Column(name = "nombre_relances", nullable = false)
    private Integer nombreRelances = 0;

    @Column(name = "derniere_relance_le")
    private LocalDateTime derniereRelanceLe;

    @Column(name = "prochaine_relance_le")
    private LocalDateTime prochaineRelanceLe;

    /**
     * Numero WhatsApp Business expediteur utilise pour cette conversation
     * (epic 23 - multi-numero par concession). NULL pour les conversations
     * historiques creees avant le deploiement de l'epic 23 (sur le numero
     * partage LeadRive). Renseigne au demarrage de toute nouvelle conversation.
     */
    @Column(name = "whatsapp_from")
    private String whatsappFrom;
}
