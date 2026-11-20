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

import java.time.LocalDateTime;

@Entity
@Table(name = "rappel_rdv")
@Getter
@Setter
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = Long.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class RappelRdv extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_rappel_rdv")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "tache_id", nullable = false)
    private Long tacheId;

    @Column(name = "sms_conversation_id", nullable = false)
    private Long smsConversationId;

    @Column(name = "lead_id", nullable = false)
    private Long leadId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_rappel", nullable = false, length = 10)
    private TypeRappelEnum typeRappel;

    @Column(name = "envoi_prevu_at", nullable = false)
    private LocalDateTime envoiPrevuAt;

    @Column(name = "envoye", nullable = false)
    private Boolean envoye = false;

    @Column(name = "envoye_at")
    private LocalDateTime envoyeAt;
}
