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

@Entity
@Table(name = "tenant_ai_config")
@Getter
@Setter
public class TenantAiConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tenant_ai_config")
    private Long id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private Long tenantId;

    @Column(name = "nom_concession", nullable = false)
    private String nomConcession;

    @Column(name = "horaires_ouverture")
    private String horairesOuverture;

    @Column(name = "adresse_complete", columnDefinition = "text")
    private String adresseComplete;

    @Column(name = "telephone_concession", length = 30)
    private String telephoneConcession;

    /**
     * URL du site web public de l'etablissement. Affiche dans la fiche contact
     * WhatsApp Business cote prospect (profile.websites pousse a Twilio).
     */
    @Column(name = "site_web", columnDefinition = "text")
    private String siteWeb;

    /**
     * Email pro de contact. Affiche dans la fiche contact WhatsApp Business
     * cote prospect (profile.emails pousse a Twilio).
     */
    @Column(name = "email_contact")
    private String emailContact;

    /**
     * Envoi automatique du premier message WhatsApp pour les leads recus par import email.
     * OFF par defaut. Necessite une config WhatsApp ACTIF pour reellement partir.
     */
    @Column(name = "premier_message_auto_actif", nullable = false)
    private Boolean premierMessageAutoActif = false;

    /** Delai (minutes) apres reception du lead email avant l'envoi auto. Defaut 5. */
    @Column(name = "premier_message_auto_delai_minutes", nullable = false)
    private Integer premierMessageAutoDelaiMinutes = 5;

    /** Heure de debut de la plage d'envoi (0-23). Hors plage, l'envoi est repousse. Defaut 8. */
    @Column(name = "premier_message_auto_fenetre_debut_heure", nullable = false)
    private Integer premierMessageAutoFenetreDebutHeure = 8;

    /** Heure de fin de la plage d'envoi (1-24, exclue ; 24 = minuit). Defaut 20. */
    @Column(name = "premier_message_auto_fenetre_fin_heure", nullable = false)
    private Integer premierMessageAutoFenetreFinHeure = 20;

}
