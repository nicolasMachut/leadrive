package fr.leadrevive.csvimport.entity;

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
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "mapping_template")
@Getter
@Setter
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = Long.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class MappingTemplate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_mapping_template")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "nom", nullable = false)
    private String nom;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "headers_originaux", columnDefinition = "jsonb", nullable = false)
    private List<String> headersOriginaux;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mappings", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> mappings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options_split", columnDefinition = "jsonb")
    private Map<String, Object> optionsSplit;

    @Column(name = "format_date")
    private String formatDate;

    @Column(name = "nombre_utilisations", nullable = false)
    private Integer nombreUtilisations = 0;

    @Column(name = "derniere_utilisation")
    private LocalDateTime derniereUtilisation;

    @Column(name = "cree_par")
    private Long creePar;
}
