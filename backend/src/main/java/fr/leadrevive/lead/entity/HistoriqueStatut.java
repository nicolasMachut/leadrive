package fr.leadrevive.lead.entity;

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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.EntityListeners;

import java.time.LocalDateTime;

@Entity
@Table(name = "historique_statut")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class HistoriqueStatut {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historique_statut")
    private Long id;

    @Column(name = "lead_id", nullable = false)
    private Long leadId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_precedent")
    private LeadStatutEnum statutPrecedent;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_suivant", nullable = false)
    private LeadStatutEnum statutSuivant;

    @Column(name = "source", nullable = false, length = 20)
    private String source;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "commentaire", length = 500)
    private String commentaire;

    @CreatedDate
    @Column(name = "cree_le", nullable = false, updatable = false)
    private LocalDateTime creeLe;
}
