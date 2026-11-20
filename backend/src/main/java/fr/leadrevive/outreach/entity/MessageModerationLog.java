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
 * Log de la moderation IA des messages manuels (vendeurs / managers).
 *
 * <p>Append-only. Conserve le contenu en clair pour audit Meta en cas de
 * policy violation. Cible : prouver a Meta que nous avons un controle
 * editorial sur les messages envoyes par les utilisateurs finaux,
 * minimisant le risque de degradation du quality rating de la WABA partagee.</p>
 *
 * <p>Mode fail-open du service de moderation : si l'IA est indisponible,
 * le message est AUTORISE par defaut (avec raison "Erreur IA: ..." pour
 * audit). Plutot ne pas bloquer l'utilisateur que de tout casser.</p>
 *
 * <p>Pas de filtre Hibernate tenant : audit global consultable par les
 * super admins.</p>
 */
@Entity
@Table(name = "message_moderation_log")
@Getter
@Setter
public class MessageModerationLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_moderation")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "conversation_id")
    private String conversationId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "contenu", nullable = false, columnDefinition = "text")
    private String contenu;

    /** AUTORISE ou BLOQUE. */
    @Column(name = "decision", nullable = false)
    private String decision;

    @Column(name = "raison", columnDefinition = "text")
    private String raison;

    @Column(name = "duree_ms")
    private Integer dureeMs;
}
