package fr.leadrevive.outreach.entity;

import fr.leadrevive.common.entity.BaseEntity;
import fr.leadrevive.outreach.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.time.LocalDateTime;

/**
 * Configuration WhatsApp Business par tenant (epic 23).
 *
 * <p>Chaque concession a sa propre ligne avec son numero, son branding
 * (display name, logo, description) et son statut Meta (quality rating,
 * messaging limit).</p>
 *
 * <p>Le filtre Hibernate `tenantFilter` est applique par defaut. Pour les
 * lookup cross-tenant (webhook routing par `To`), desactiver le filtre via
 * {@code session.disableFilter("tenantFilter")} ou utiliser une methode
 * repository dediee.</p>
 *
 * <p>Lock optimiste via {@link Version} : protege contre les ecritures
 * concurrentes (2 managers du meme tenant modifiant en parallele).</p>
 */
@Entity
@Table(name = "tenant_whatsapp_config")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = Long.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
public class TenantWhatsappConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tenant_whatsapp_config")
    private Long id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private Long tenantId;

    @Column(name = "whatsapp_from")
    private String whatsappFrom;

    @Column(name = "twilio_sender_sid")
    private String twilioSenderSid;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "whatsapp_description")
    private String whatsappDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private StatutWhatsappConfigEnum statut = StatutWhatsappConfigEnum.DESACTIVE;

    @Column(name = "quality_rating")
    private String qualityRating;

    /**
     * Plafond de messagerie Meta renvoye par Twilio Senders v2 dans
     * {@code properties.messaging_limit} (ex: "10K Customers/24hr",
     * "1K Customers/24hr"). String libre, pas un entier — d'ou la rectif
     * vs l'ancien typage Integer suite a l'audit doc Twilio.
     */
    @Column(name = "messaging_limit_tier")
    private String messagingLimit;

    @Column(name = "meta_status")
    private String metaStatus;

    /**
     * Statut d'examen Meta du nom affiche (valeurs Twilio/Meta : APPROVED,
     * PENDING_REVIEW, DECLINED, AVAILABLE_WITHOUT_REVIEW, EXPIRED, NONE).
     * Distinct de {@link #metaStatus} (statut du sender) : le nom peut etre en
     * examen alors que le compte est deja ONLINE, auquel cas le prospect voit le
     * numero brut a la place du nom de l'etablissement.
     */
    @Column(name = "name_status")
    private String nameStatus;

    @Column(name = "meta_reject_reason", columnDefinition = "text")
    private String metaRejectReason;

    @Column(name = "date_derniere_synchro_meta")
    private LocalDateTime dateDerniereSynchroMeta;

    @Column(name = "notes_internes", columnDefinition = "text")
    private String notesInternes;

    // ── Epic 25 : multi-WABA ─────────────────────────────────────────────────
    /**
     * SID du sub-account Twilio dedie a ce tenant (mode SELF_SIGNUP).
     */
    @Column(name = "twilio_sub_account_sid", length = 50)
    private String twilioSubAccountSid;

    /**
     * Auth token du sub-account Twilio, chiffre AES-256-GCM en BDD via
     * {@link EncryptedStringConverter}. Ne jamais logger ni mettre en cache.
     */
    @Column(name = "twilio_sub_auth_token", columnDefinition = "text")
    @Convert(converter = EncryptedStringConverter.class)
    private String twilioSubAuthToken;

    /**
     * WhatsApp Business Account ID (Meta) propre au tenant.
     */
    @Column(name = "waba_id", length = 50)
    private String wabaId;

    /**
     * Meta Business ID du tenant (recupere depuis Facebook OAuth lors du
     * Self Sign-up).
     */
    @Column(name = "meta_business_id", length = 50)
    private String metaBusinessId;

    /**
     * UUID v4 genere AVANT l'appel Twilio {@code createSubAccount}. Permet le
     * retry-then-lookup au lieu de creer un sub orphelin si la BDD timeout
     * apres le call Twilio.
     */
    @Column(name = "sub_account_idempotency_key", length = 80)
    private String subAccountIdempotencyKey;

    @Version
    @Column(name = "version", nullable = false)
    private int version;
}
