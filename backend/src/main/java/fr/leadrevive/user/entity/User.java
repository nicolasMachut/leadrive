package fr.leadrevive.user.entity;

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

import java.time.LocalDateTime;

@Entity
@Table(name = "utilisateur")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = Long.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_utilisateur")
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "clerk_user_id", unique = true)
    private String clerkUserId;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "nom", nullable = false)
    private String nom;

    @Column(name = "prenom", nullable = false)
    private String prenom;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private RoleEnum role;

    @Column(name = "est_actif", nullable = false)
    private Boolean actif;

    @Column(name = "objectif_mensuel_rdv")
    private Integer objectifMensuelRdv = 10;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private StatutUtilisateurEnum statut = StatutUtilisateurEnum.ACTIF;

    @Column(name = "derniere_connexion_le")
    private LocalDateTime derniereConnexionLe;

    @Column(name = "email_synced_at")
    private LocalDateTime emailSyncedAt;

    @Column(name = "onboarding_termine", nullable = false)
    private Boolean onboardingTermine = false;

    @Column(name = "onboarding_etape")
    private Integer onboardingEtape;
}
