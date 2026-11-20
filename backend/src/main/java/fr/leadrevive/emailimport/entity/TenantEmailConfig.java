package fr.leadrevive.emailimport.entity;

import fr.leadrevive.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration email d'import par tenant.
 *
 * <p>Chaque concession dispose d'une adresse dediee (slug-token@leads.leadrive.fr).
 * Les emails recus sur cette adresse sont parses par l'IA et transformes en leads.
 * Une seule adresse par tenant.</p>
 *
 * <p>Le filtre Hibernate tenantFilter s'applique : un manager ne peut voir que la
 * config de son propre tenant. Le webhook SendGrid utilise un acces cross-tenant
 * via {@code emailAddress} (cle unique globale).</p>
 */
@Entity
@Table(name = "tenant_email_config")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = Long.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
public class TenantEmailConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tenant_email_config")
    private Long id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private Long tenantId;

    @Column(name = "email_slug", nullable = false, length = 80)
    private String emailSlug;

    @Column(name = "email_address", nullable = false, unique = true, length = 150)
    private String emailAddress;

    @Column(name = "statut", nullable = false, length = 20)
    private String statut = "ACTIVE";

    @Column(name = "nb_emails_recus", nullable = false)
    private Integer nbEmailsRecus = 0;

    @Column(name = "dernier_email_recu_le")
    private LocalDateTime dernierEmailRecuLe;

    /**
     * Historique des anciennes adresses email (alimente a chaque regeneration
     * depuis l'admin). Format JSONB : liste d'objets
     * {@code {adresse, remplaceeLe}}. Conserve pour audit / debug ; aucune
     * adresse historique n'est jamais reactivee automatiquement.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "anciennes_adresses", columnDefinition = "jsonb")
    private List<AncienneAdresse> anciennesAdresses = new ArrayList<>();

    /** Entree historique d'une adresse remplacee. */
    public record AncienneAdresse(String adresse, LocalDateTime remplaceeLe) {}
}
