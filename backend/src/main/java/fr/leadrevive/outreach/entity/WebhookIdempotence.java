package fr.leadrevive.outreach.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Trace des webhooks externes deja traites (MessageSid Twilio principalement),
 * pour assurer l'idempotence en multi-instance. Cf migration V053.
 *
 * <p>Pas d'extension de BaseEntity : la table n'a pas de tenant_id (les webhooks
 * arrivent avant resolution du tenant) et pas de id auto-genere (PK = messageSid).
 */
@Entity
@Table(name = "webhook_idempotence")
@Getter
@Setter
@NoArgsConstructor
public class WebhookIdempotence {

    @Id
    @Column(name = "message_sid", length = 64)
    private String messageSid;

    @Column(name = "source", length = 32, nullable = false)
    private String source;

    @Column(name = "traite_le", nullable = false)
    private LocalDateTime traiteLe;
}
