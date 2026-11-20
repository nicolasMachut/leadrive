package fr.leadrevive.ai.entity;

import fr.leadrevive.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "briefing_quotidien")
@Getter
@Setter
public class BriefingQuotidien extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_briefing_quotidien")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "date_briefing", nullable = false)
    private LocalDate dateBriefing;

    @Column(name = "contenu", columnDefinition = "text")
    private String contenu;

    @Column(name = "donnees_contexte", columnDefinition = "text")
    private String donneesContexte;

    @Column(name = "tokens_prompt")
    private Integer tokensPrompt;

    @Column(name = "tokens_reponse")
    private Integer tokensReponse;

    @Column(name = "cout_estime", precision = 10, scale = 6)
    private BigDecimal coutEstime;
}
