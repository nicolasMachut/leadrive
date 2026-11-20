package fr.leadrevive.lead.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Slf4j
public class LeadValidationService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern TELEPHONE_FR_PATTERN = Pattern.compile("^\\+33[1-9][0-9]{8}$");
    /** E.164 générique : accepte les numéros internationaux (Belgique +32, Suisse +41,
     *  Luxembourg +352…), fréquents en concession frontalière. La normalisation FR
     *  (0X → +33) reste appliquée par défaut dans nettoyerTelephone. */
    private static final Pattern TELEPHONE_E164_PATTERN = Pattern.compile("^\\+[1-9]\\d{6,14}$");
    private static final List<String> TELEPHONES_PLACEHOLDER = List.of(
            "+33600000000", "+33000000000", "+33111111111", "+33666666666"
    );

    public List<String> validerLead(final String nom, final String email, final String telephone) {
        log.info("Validation du lead - nom: {}", nom);
        final List<String> erreurs = new ArrayList<>();

        if (nom == null || nom.isBlank()) {
            erreurs.add("Le nom est obligatoire");
        }

        if (email != null && !email.isBlank() && !EMAIL_PATTERN.matcher(email).matches()) {
            erreurs.add("Email invalide: " + email);
        }

        final List<String> erreursTelephone = validerTelephone(telephone);
        erreurs.addAll(erreursTelephone);

        return erreurs;
    }

    public List<String> validerTelephone(final String telephone) {
        final List<String> erreurs = new ArrayList<>();

        if (telephone == null || telephone.isBlank()) {
            return erreurs;
        }

        final String telephoneNettoye = nettoyerTelephone(telephone);

        if (telephoneNettoye.equals("+33") || telephoneNettoye.length() < 4) {
            erreurs.add("Telephone incomplet: " + telephone);
            return erreurs;
        }

        if (!TELEPHONE_FR_PATTERN.matcher(telephoneNettoye).matches()
                && !TELEPHONE_E164_PATTERN.matcher(telephoneNettoye).matches()) {
            erreurs.add("Telephone invalide (format attendu: +33XXXXXXXXX ou international +CC...): " + telephone);
            return erreurs;
        }

        if (TELEPHONES_PLACEHOLDER.contains(telephoneNettoye)) {
            erreurs.add("Telephone placeholder detecte: " + telephone);
        }

        return erreurs;
    }

    public String nettoyerTelephone(final String telephone) {
        if (telephone == null || telephone.isBlank()) {
            return null;
        }

        String nettoye = telephone.replaceAll("[\\s.\\-()]", "");

        if (nettoye.startsWith("0") && nettoye.length() == 10) {
            nettoye = "+33" + nettoye.substring(1);
        }

        return nettoye;
    }

    public boolean estEmailValide(final String email) {
        return email != null && !email.isBlank() && EMAIL_PATTERN.matcher(email).matches();
    }

    public boolean estTelephoneValide(final String telephone) {
        if (telephone == null || telephone.isBlank()) {
            return false;
        }
        final String nettoye = nettoyerTelephone(telephone);
        final boolean formatOk = TELEPHONE_FR_PATTERN.matcher(nettoye).matches()
                || TELEPHONE_E164_PATTERN.matcher(nettoye).matches();
        return formatOk && !TELEPHONES_PLACEHOLDER.contains(nettoye);
    }
}
