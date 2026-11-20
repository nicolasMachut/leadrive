package fr.leadrevive.outreach.entity;

import fr.leadrevive.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Audit historique des associations numero WhatsApp <-> tenant.
 *
 * <p>Append-only. Permet de retracer qui a possede un numero a quel moment,
 * et de detecter les eventuelles reattributions de numeros qui pourraient
 * causer des fuites cross-tenant lors du routage webhook.</p>
 *
 * <p>Une ligne est inseree :
 * <ul>
 *   <li>Lors de la 1ere association d'un numero a un tenant (POST /numero) : {@code dissocie_le = NULL}</li>
 *   <li>Lors de la dissociation (changement de numero, desactivation admin) : {@code dissocie_le = NOW()}</li>
 * </ul>
 * </p>
 *
 * <p>Pas de filtre Hibernate tenant : entite globale consultable par les
 * jobs cross-tenant et les requetes de routage webhook.</p>
 */
@Entity
@Table(name = "tenant_whatsapp_number_history")
@Getter
@Setter
public class TenantWhatsappNumberHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_history")
    private Long id;

    @Column(name = "whatsapp_from", nullable = false)
    private String whatsappFrom;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "associe_le", nullable = false)
    private LocalDateTime associeLe;

    @Column(name = "dissocie_le")
    private LocalDateTime dissocieLe;

    @Column(name = "raison_dissociation")
    private String raisonDissociation;
}
