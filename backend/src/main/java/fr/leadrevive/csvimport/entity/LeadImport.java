package fr.leadrevive.csvimport.entity;

import fr.leadrevive.common.entity.BaseEntity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.time.LocalDateTime;

@Entity
@Table(name = "lead_import")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = Long.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
public class LeadImport extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_import")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "nom_fichier", nullable = false)
    private String nomFichier;

    @Column(name = "date_import", nullable = false)
    private LocalDateTime dateImport;

    @Column(name = "nombre_lignes")
    private Integer nombreLignes;

    @Column(name = "nombre_importees")
    private Integer nombreImportees;

    @Column(name = "nombre_erreurs")
    private Integer nombreErreurs;

    @Column(name = "nombre_doublons")
    private Integer nombreDoublons;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private ImportStatutEnum statut;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mapping_colonnes", columnDefinition = "jsonb")
    private String mappingColonnes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "erreurs", columnDefinition = "jsonb")
    private String erreurs;

    @Column(name = "importe_par")
    private Long importePar;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_fichier", length = 30)
    private FormatDetecte typeFichier;
}
