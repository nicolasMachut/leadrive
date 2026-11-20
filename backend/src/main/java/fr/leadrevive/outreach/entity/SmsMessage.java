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
import org.hibernate.annotations.ParamDef;

@Entity
@Table(name = "sms_message")
@Getter
@Setter
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = Long.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class SmsMessage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sms_message")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "conversation_id", nullable = false)
    private String conversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private DirectionSmsEnum direction;

    @Column(name = "contenu", nullable = false, columnDefinition = "text")
    private String contenu;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private StatutSmsEnum statut;

    @Column(name = "brevo_message_id", columnDefinition = "text")
    private String brevoMessageId;

    @Column(name = "erreur")
    private String erreur;

    /**
     * Content SID Twilio du template utilise pour ce message (premier contact,
     * relance, rappel RDV). {@code null} pour un message de session en texte libre.
     * Permet de rejouer fidelement un template lors d'un "Reessayer" au lieu de
     * renvoyer le texte rendu en message libre (qui echoue hors fenetre 24h).
     */
    @Column(name = "content_sid")
    private String contentSid;

    /**
     * Variables du template serialisees en JSON ({@code {"1":"Jean","2":"Nora"}}).
     * Couple a {@link #contentSid} pour le rejeu d'un template.
     */
    @Column(name = "content_variables", columnDefinition = "text")
    private String contentVariables;

    @Column(name = "ordre", nullable = false)
    private Integer ordre;

    @Enumerated(EnumType.STRING)
    @Column(name = "expediteur")
    private ExpediteurMessageEnum expediteur = ExpediteurMessageEnum.IA;
}
