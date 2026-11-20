package fr.leadrevive.emailimport.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration pour l'import automatique de leads par email (epic 24).
 *
 * <p>Toutes les valeurs ont des defauts senses dans application.yml,
 * aucune n'est obligatoire au demarrage. En local, les emails sont
 * desactives par defaut (pas de webhook SendGrid).</p>
 */
@Configuration
@ConfigurationProperties(prefix = "leadrevive.email-import")
@Getter
@Setter
public class EmailImportConfig {

    /**
     * Domaine utilise pour generer les adresses email des concessions.
     * Defaut : {@code leads.leadrive.fr}.
     */
    private String domaine = "leads.leadrive.fr";

    /**
     * Fenetre de deduplication en heures. Si un lead avec le meme telephone
     * arrive pour le meme tenant dans cette fenetre, on enrichit au lieu
     * de creer. Si pas de telephone, fallback sur l'email.
     */
    private int deduplicationFenetreHeures = 48;

    /**
     * Rate limiting : nombre max d'emails par heure par concession. Protection
     * contre le spam ou une boucle de redirection.
     */
    private int maxEmailsParHeure = 50;

    /**
     * Taille max du body email en caracteres (protection memoire / abus).
     */
    private int maxBodyChars = 50_000;

    /**
     * Heartbeat : si aucun email recu depuis X jours pour une concession qui
     * en recevait avant, log warning + alerte (extension future).
     */
    private int heartbeatAlerteJours = 7;
}
