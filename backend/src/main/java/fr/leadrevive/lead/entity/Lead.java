package fr.leadrevive.lead.entity;

import fr.leadrevive.common.entity.BaseEntity;
import jakarta.persistence.Column;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "lead")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = Long.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
public class Lead extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_lead")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    // Optimistic locking (bug-hunter #13). IA + UI manuel + scoring concurrent peuvent
    // muter statut/scoring en parallele : sans @Version, ecrasements silencieux.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "nom", nullable = false)
    private String nom;

    @Column(name = "telephone")
    private String telephone;

    @Column(name = "email")
    private String email;

    @Column(name = "marque")
    private String marque;

    @Column(name = "modele")
    private String modele;

    @Column(name = "source")
    private String source;

    @Column(name = "territoire")
    private String territoire;

    @Column(name = "etape")
    private String etape;

    @Column(name = "scoring", precision = 3, scale = 2)
    private BigDecimal scoring;

    @Column(name = "devis")
    private String devis;

    @Column(name = "reprise")
    private Boolean reprise;

    @Column(name = "type_opportunite")
    private String typeOpportunite;

    @Column(name = "date_modification_devis")
    private LocalDateTime dateModificationDevis;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private LeadStatutEnum statut;

    @Column(name = "vendeur_id")
    private Long vendeurId;

    @Column(name = "import_id")
    private Long importId;

    @Column(name = "opt_out_sms")
    private Boolean optOutSms;

    /** Score d'engagement comportemental (0-1), calcule par ConversationAnalyseService. */
    @Column(name = "hotness_score", precision = 3, scale = 2)
    private BigDecimal hotnessScore;

    @Column(name = "hotness_calcule_le")
    private LocalDateTime hotnessCalculeLe;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", columnDefinition = "jsonb")
    private String rawData;

    /** Cache de la fiche de preparation IA. Regeneree si ficheHash ne correspond plus aux donnees. */
    @Column(name = "fiche_preparation", columnDefinition = "text")
    private String fichePreparation;

    @Column(name = "fiche_generee_le")
    private LocalDateTime ficheGenereeLe;

    /** Hash SHA-256 des donnees utilisees pour generer la fiche. Sert a detecter les changements. */
    @Column(name = "fiche_hash", length = 64)
    private String ficheHash;

    @Column(name = "statut_modifie_le")
    private LocalDateTime statutModifieLe;

    @Column(name = "statut_modifie_par", length = 20)
    private String statutModifiePar;

    @Column(name = "raison_perte", length = 500)
    private String raisonPerte;

    /** Données supplémentaires véhicule/prospect extraites par l'IA (immat, km, énergie, etc.) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "donnees_supplementaires", columnDefinition = "jsonb")
    private Map<String, Object> donneesSupplementaires = new HashMap<>();

    /** Texte brut collé par l'utilisateur ou contenu email — pour référence et re-parsing */
    @Column(name = "texte_source", columnDefinition = "text")
    private String texteSource;

    /** Comment le lead a été importé */
    @Enumerated(EnumType.STRING)
    @Column(name = "mode_import", length = 30)
    private ModeImport modeImport = ModeImport.IMPORT_CSV;

    @Column(name = "archive", nullable = false)
    private Boolean archive = false;

    /**
     * RGPD : preuve de consentement initial pour le contact WhatsApp.
     * Mis a true au moment de l'import CSV ou de la premiere reponse spontanee du prospect.
     * Si null/false, les envois sortants sont bloques.
     */
    @Column(name = "consentement_contact")
    private Boolean consentementContact;

    @Column(name = "consentement_date")
    private LocalDateTime consentementDate;

    @Column(name = "consentement_source", length = 50)
    private String consentementSource;

    /**
     * Set a true par ConversationAnalyseService quand tonProspect=mecontent ET besoinHandoff=true.
     * Affiche un badge rouge "Client irrite" dans l'inbox pour signaler aux vendeurs ce lead sensible.
     */
    @Column(name = "client_irrite", nullable = false)
    private Boolean clientIrrite = false;

    /**
     * Envoi automatique du premier message WhatsApp (leads import email uniquement).
     * Horodatage d'armement : si non null, le lead est en attente d'un envoi auto a
     * partir de cette date. Efface apres envoi (anti double-envoi), sur abandon manuel,
     * ou a l'expiration (>24h). NULL = pas d'envoi auto en attente.
     */
    @Column(name = "premier_message_auto_prevu_le")
    private LocalDateTime premierMessageAutoPrevuLe;
}
