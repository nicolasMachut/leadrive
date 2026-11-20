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
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.ParamDef;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "conversation_analyse")
@Getter
@Setter
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = Long.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class ConversationAnalyse extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_analyse")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "conversation_id", nullable = false)
    private String conversationId;

    @Column(name = "sms_message_id")
    private Long smsMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "intention", nullable = false)
    private IntentionEnum intention;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "signaux_achat", columnDefinition = "jsonb")
    private List<String> signauxAchat;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "objections", columnDefinition = "jsonb")
    private List<String> objections;

    @Column(name = "besoin_handoff", nullable = false)
    private Boolean besoinHandoff = false;

    @Column(name = "raison_handoff", columnDefinition = "text")
    private String raisonHandoff;

    @Column(name = "resume_vendeur", columnDefinition = "text")
    private String resumeVendeur;

    @Column(name = "ton_prospect")
    private String tonProspect;

    @Column(name = "question_en_attente", columnDefinition = "text")
    private String questionEnAttente;

    @Column(name = "rdv_propose", nullable = false)
    private Boolean rdvPropose = false;

    @Column(name = "rdv_confirme", nullable = false)
    private Boolean rdvConfirme = false;

    @Column(name = "rdv_creneau", columnDefinition = "text")
    private String rdvCreneau;

    @Column(name = "rdv_date_heure")
    private java.time.LocalDateTime rdvDateHeure;

    // Champs de decouverte commerciale (Q0-Q4) extraits au fil de la conversation
    @Column(name = "delai_projet", columnDefinition = "text")
    private String delaiProjet;

    @Column(name = "montant_budget", columnDefinition = "text")
    private String montantBudget;

    @Column(name = "apport_prevu", columnDefinition = "text")
    private String apportPrevu;

    @Column(name = "mode_financement", columnDefinition = "text")
    private String modeFinancement;

    @Column(name = "accord_banque", columnDefinition = "text")
    private String accordBanque;

    @Column(name = "reprise_conditionne_achat")
    private Boolean repriseConditionneAchat;

    @Column(name = "demande_essai")
    private Boolean demandeEssai;

    @Column(name = "raison_contact", columnDefinition = "text")
    private String raisonContact;
}
