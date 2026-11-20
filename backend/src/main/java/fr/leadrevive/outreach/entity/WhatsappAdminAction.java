package fr.leadrevive.outreach.entity;

import fr.leadrevive.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Audit des actions admin sensibles sur les configs WhatsApp.
 *
 * <p>Append-only : jamais d'UPDATE/DELETE en code applicatif. Permet de
 * retracer les interventions SUPER_ADMIN (force ACTIF apres rejet Meta,
 * desactivation forcee, modification statut, etc.) avec justification.</p>
 *
 * <p>La {@code justification} est obligatoire cote applicatif (min 20 chars
 * verifie au niveau du DTO via @Size).</p>
 *
 * <p>Pas de filtre Hibernate tenant : audit global consultable par les
 * super admins quel que soit leur tenant courant.</p>
 */
@Entity
@Table(name = "whatsapp_admin_actions")
@Getter
@Setter
public class WhatsappAdminAction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_action")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "admin_user_id", nullable = false)
    private Long adminUserId;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "justification", nullable = false, columnDefinition = "text")
    private String justification;

    /**
     * Contexte additionnel sur l'action (ancien numero, ancien statut, etc.).
     * Stocke en TEXT - JSON serialise par Jackson cote applicatif quand pertinent.
     */
    @Column(name = "metadata_json", columnDefinition = "text")
    private String metadataJson;
}
