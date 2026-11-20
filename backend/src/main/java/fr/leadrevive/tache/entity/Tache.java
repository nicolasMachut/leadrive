package fr.leadrevive.tache.entity;

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
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.time.LocalDateTime;

@Entity
@Table(name = "tache")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = Long.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
public class Tache extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tache")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    // Optimistic locking (bug-hunter #13). Webhook RDV report + scheduler rappels +
    // UI manuel "marquer fait" peuvent muter en parallele.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "vendeur_id", nullable = false)
    private Long vendeurId;

    @Column(name = "lead_id")
    private Long leadId;

    @Column(name = "titre", nullable = false, length = 200)
    private String titre;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_tache", nullable = false, length = 20)
    private TypeTacheEnum typeTache;

    @Column(name = "date_echeance", nullable = false)
    private LocalDateTime dateEcheance;

    @Column(name = "duree_minutes")
    private Integer dureeMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "priorite", nullable = false, length = 20)
    private PrioriteTacheEnum priorite;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutTacheEnum statut;

    @Column(name = "source", nullable = false, length = 20)
    private String source;

    @Column(name = "rappel_minutes_avant")
    private Integer rappelMinutesAvant;

    @Column(name = "rappel_envoye", nullable = false)
    private Boolean rappelEnvoye;

    @Column(name = "date_terminee")
    private LocalDateTime dateTerminee;
}
