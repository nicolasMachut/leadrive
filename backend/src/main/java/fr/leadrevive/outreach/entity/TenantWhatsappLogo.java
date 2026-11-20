package fr.leadrevive.outreach.entity;

import fr.leadrevive.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Stockage du logo WhatsApp Business par tenant.
 *
 * <p>Table dediee (pas une colonne sur {@link TenantWhatsappConfig}) pour
 * eviter de charger les bytes a chaque requete Hibernate sur la config.
 * Le job cross-tenant {@code WhatsAppSenderStatusJob} charge toutes les
 * configs ACTIF toutes les 10 min : sans table
 * separee, ca trimballerait les blobs de tous les tenants inutilement.</p>
 *
 * <p>La cle primaire est {@code tenant_id} directement (1-to-1 avec
 * {@code tenant_whatsapp_config}). Le code n'appelle jamais
 * {@code findAll()} sur ce repository : uniquement {@code findById(tenantId)},
 * donc pas besoin de LAZY load (et @Lob casse le mapping BYTEA sur
 * PostgreSQL en forcant le mode Large Object/OID).</p>
 */
@Entity
@Table(name = "tenant_whatsapp_logo")
@Getter
@Setter
public class TenantWhatsappLogo extends BaseEntity {

    @Id
    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "logo_bytes", nullable = false)
    private byte[] logoBytes;
}
