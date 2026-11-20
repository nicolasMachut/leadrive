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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.time.LocalDateTime;

/**
 * Etat de soumission d'un template WhatsApp pour un tenant donne (epic 25).
 *
 * <p>La source de verite du wording reste les fichiers YAML versionnes dans
 * {@code resources/whatsapp-templates/}. Cette entite stocke uniquement la
 * trace cote LeadRive : SID Twilio du template, statut local, raison de
 * rejet Meta, back-off de re-soumission.</p>
 *
 * <p>Multi-tenant : filtre Hibernate {@code tenantFilter} applique par defaut.</p>
 */
@Entity
@Table(
        name = "tenant_whatsapp_template",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_template_tenant_name",
                columnNames = {"tenant_id", "template_name"}
        )
)
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = Long.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
public class TenantWhatsappTemplate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tenant_whatsapp_template")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /**
     * Nom Meta du template (ex: "leadrive_premier_contact").
     * Correspond au {@code name} dans le YAML.
     */
    @Column(name = "template_name", nullable = false, length = 100)
    private String templateName;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 30)
    private StatutTemplateEnum statut = StatutTemplateEnum.EN_ATTENTE_SOUMISSION;

    /**
     * SID Twilio Content API (HXxxxx...). Null tant que pas encore soumis.
     */
    @Column(name = "template_sid", length = 50)
    private String templateSid;

    /**
     * SHA-256 hex du contenu YAML au moment de la soumission. Permet de
     * detecter qu'un template a evolue cote source de verite et qu'il faut
     * potentiellement le re-soumettre.
     */
    @Column(name = "yaml_content_hash", length = 64)
    private String yamlContentHash;

    @Column(name = "soumis_le")
    private LocalDateTime soumisLe;

    @Column(name = "meta_reject_reason", columnDefinition = "text")
    private String metaRejectReason;

    @Column(name = "nb_tentatives", nullable = false)
    private int nbTentatives = 0;

    /**
     * Back-off exponentiel : ne pas re-soumettre avant cette date apres un rejet.
     */
    @Column(name = "prochaine_tentative_le")
    private LocalDateTime prochaineTentativeLe;

    @Column(name = "date_derniere_synchro")
    private LocalDateTime dateDerniereSynchro;

    @Version
    @Column(name = "version", nullable = false)
    private int version;
}
