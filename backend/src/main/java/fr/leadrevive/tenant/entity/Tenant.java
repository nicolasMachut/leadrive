package fr.leadrevive.tenant.entity;

import fr.leadrevive.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tenant")
@Getter
@Setter
public class Tenant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tenant")
    private Long id;

    @Column(name = "nom", nullable = false)
    private String nom;

    @Column(name = "adresse")
    private String adresse;

    @Column(name = "telephone")
    private String telephone;

    @Column(name = "email_contact")
    private String emailContact;

    @Column(name = "timezone", nullable = false)
    private String timezone;

    @Column(name = "est_actif", nullable = false)
    private Boolean actif;

    @Column(name = "clerk_organization_id", unique = true)
    private String clerkOrganizationId;

    /** Objectif mensuel RDV pour l'équipe entière (manager dashboard).
     *  Null = non configuré, frontend dégrade (pas de progress bar). */
    @Column(name = "objectif_mensuel_rdv_equipe")
    private Integer objectifMensuelRdvEquipe;
}
