package fr.leadrevive.ai.entity;

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
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.math.BigDecimal;

@Entity
@Table(name = "ai_log")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = Long.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
public class AiLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ai_log")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "lead_id")
    private Long leadId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_generation", nullable = false)
    private TypeGenerationEnum typeGeneration;

    @Column(name = "prompt", columnDefinition = "text", nullable = false)
    private String prompt;

    @Column(name = "reponse", columnDefinition = "text")
    private String reponse;

    @Column(name = "modele")
    private String modele;

    @Column(name = "tokens_prompt")
    private Integer tokensPrompt;

    @Column(name = "tokens_reponse")
    private Integer tokensReponse;

    @Column(name = "cout_estime", precision = 10, scale = 6)
    private BigDecimal coutEstime;

    @Column(name = "duree_ms")
    private Long dureeMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private AiStatutEnum statut;

    @Column(name = "erreur", columnDefinition = "text")
    private String erreur;
}
