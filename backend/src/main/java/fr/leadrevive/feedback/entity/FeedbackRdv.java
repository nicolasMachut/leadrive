package fr.leadrevive.feedback.entity;

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

@Entity
@Table(name = "feedback_rdv")
@Getter
@Setter
public class FeedbackRdv extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_feedback_rdv")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "lead_id", nullable = false)
    private Long leadId;

    @Column(name = "rdv_id")
    private Long rdvId;

    @Column(name = "vendeur_id", nullable = false)
    private Long vendeurId;

    @Enumerated(EnumType.STRING)
    @Column(name = "qualite_qualification", length = 20)
    private QualiteQualificationEnum qualiteAppel;

    @Enumerated(EnumType.STRING)
    @Column(name = "suite_prevue", length = 20)
    private SuitePrevueEnum suitePrevue;

    @Enumerated(EnumType.STRING)
    @Column(name = "raison_abandon", length = 20)
    private RaisonAbandonEnum raisonAbandon;

    @Column(name = "impact_notifie", nullable = false)
    private Boolean impactNotifie = false;
}
