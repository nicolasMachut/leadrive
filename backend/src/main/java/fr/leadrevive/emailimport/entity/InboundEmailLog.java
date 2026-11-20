package fr.leadrevive.emailimport.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.ParamDef;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Log d'un email entrant recu via SendGrid Inbound Parse.
 *
 * <p>Stocke le body brut, la sortie IA et le statut final. Sert d'audit
 * et de debug : si un lead n'est pas cree comme attendu, l'admin retrouve
 * l'email original ici.</p>
 *
 * <p>Pas de purge automatique. La table peut grossir mais reste petite
 * (1 entree par email recu).</p>
 */
@Entity
@Table(name = "inbound_email_log")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = Long.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
public class InboundEmailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_inbound_email")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "email_from", length = 255)
    private String emailFrom;

    @Column(name = "email_subject", length = 500)
    private String emailSubject;

    @Column(name = "email_body_raw", columnDefinition = "text")
    private String emailBodyRaw;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resultat_parsing", columnDefinition = "jsonb")
    private String resultatParsing;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 30)
    private StatutInboundEmail statut;

    @Column(name = "lead_id")
    private Long leadId;

    @Column(name = "source_detectee", length = 50)
    private String sourceDetectee;

    @Column(name = "duree_parsing_ms")
    private Integer dureeParsingMs;

    @Column(name = "raison_rejet", length = 500)
    private String raisonRejet;

    @Column(name = "cree_le", nullable = false, updatable = false)
    private LocalDateTime creeLe = LocalDateTime.now();
}
