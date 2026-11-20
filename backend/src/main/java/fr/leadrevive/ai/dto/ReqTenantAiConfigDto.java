package fr.leadrevive.ai.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReqTenantAiConfigDto {

    @NotBlank(message = "Le nom de la concession est obligatoire")
    private String nomConcession;

    private String horairesOuverture;
    private String adresseComplete;
    private String telephoneConcession;

    /**
     * URL HTTP/HTTPS. Twilio exige HTTPS pour Meta-side display, mais on accepte
     * HTTP cote saisie pour ne pas bloquer les manager qui colle un domaine seul.
     * Format max 500 chars, alignement avec la colonne TEXT cote BDD.
     */
    @Size(max = 500, message = "Le site web ne peut pas depasser 500 caracteres")
    @Pattern(
            regexp = "^$|^(https?://)?([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(:[0-9]+)?(/.*)?$",
            message = "URL invalide. Exemple : https://www.peugeot-lyon.fr"
    )
    private String siteWeb;

    @Email(message = "Email invalide. Exemple : contact@etablissement.fr")
    @Size(max = 255)
    private String emailContact;

    /**
     * Envoi automatique du premier message WhatsApp (leads import email). OFF par defaut.
     * Le mapping cote service laisse la valeur BDD inchangee si null (retro-compat).
     */
    private Boolean premierMessageAutoActif;

    /** Delai (minutes) avant l'envoi auto. Borne 0-1440 (24h). Defaut applique : 5. */
    @Min(value = 0, message = "Le delai doit etre positif")
    @Max(value = 1440, message = "Le delai ne peut pas depasser 1440 minutes (24h)")
    private Integer premierMessageAutoDelaiMinutes;

    /** Heure de debut de la plage d'envoi (0-23). Defaut applique : 8. */
    @Min(value = 0, message = "L'heure de debut doit etre comprise entre 0 et 23")
    @Max(value = 23, message = "L'heure de debut doit etre comprise entre 0 et 23")
    private Integer premierMessageAutoFenetreDebutHeure;

    /** Heure de fin de la plage d'envoi (1-24, exclusive ; 24 = minuit). Defaut applique : 20. */
    @Min(value = 1, message = "L'heure de fin doit etre comprise entre 1 et 24 (minuit)")
    @Max(value = 24, message = "L'heure de fin doit etre comprise entre 1 et 24 (minuit)")
    private Integer premierMessageAutoFenetreFinHeure;
}
