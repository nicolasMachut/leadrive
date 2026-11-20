package fr.leadrevive.emailimport.service;

import fr.leadrevive.emailimport.config.EmailImportConfig;
import fr.leadrevive.emailimport.repository.TenantEmailConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.text.Normalizer;

/**
 * Generation de l'adresse email d'import pour une concession.
 *
 * <p>Format : {@code slug-token@domaine}. Le slug est derive du nom de la
 * concession (ASCII, lowercase, tirets), le token est aleatoire (12 chars
 * alphanumeriques) pour empecher l'enumeration. Garantie d'unicite par
 * regeneration du token en cas de collision (improbable).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailAddressGeneratorService {

    // Token aléatoire de l'adresse d'import email. Comme le webhook SendGrid Inbound Parse
    // n'a PAS de signature, l'adresse `slug-token@domaine` fait office de secret : un token
    // long rend le brute-force (injection de leads dans un tenant) non réaliste. 12 chars
    // sur 36 symboles = 36^12 ≈ 4.7e18 combinaisons (vs 1.6M à 4 chars).
    private static final int LONGUEUR_TOKEN = 12;
    private static final int LONGUEUR_SLUG_MAX = 70;
    private static final String ALPHABET_TOKEN = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailImportConfig emailImportConfig;
    private final TenantEmailConfigRepository tenantEmailConfigRepository;

    /**
     * Genere une adresse email unique pour une concession a partir de son nom.
     * Garantit l'unicite via {@code existsByEmailAddress}.
     *
     * @param nomConcession nom de la concession, ex: "Garage Dupont & Fils"
     * @return paire [slug, emailAddress], ex: ["garage-dupont-fils", "garage-dupont-fils-x7k9@leads.leadrive.fr"]
     */
    public EmailGenere genererEmailAddress(final String nomConcession) {
        log.info("Generation adresse email - nomConcession: {}", nomConcession);

        final String slug = normaliserSlug(nomConcession);
        String token = genererToken();
        String emailAddress = slug + "-" + token + "@" + emailImportConfig.getDomaine();

        // Collision possible si le slug + token sont deja pris. Tentative de regeneration
        // jusqu'a 10 fois (probabilite quasi nulle : 36^12 ≈ 4.7e18 tokens distincts).
        int tentatives = 0;
        while (tenantEmailConfigRepository.existsByEmailAddress(emailAddress)) {
            tentatives++;
            if (tentatives > 10) {
                throw new IllegalStateException("Impossible de generer une adresse email unique apres 10 tentatives");
            }
            token = genererToken();
            emailAddress = slug + "-" + token + "@" + emailImportConfig.getDomaine();
        }

        log.info("Adresse email generee - emailAddress: {}", emailAddress);
        return new EmailGenere(slug, emailAddress);
    }

    /**
     * Normalise le nom de la concession en slug ASCII compatible avec une adresse email.
     * "Garage Dupont &amp; Fils" -&gt; "garage-dupont-fils".
     */
    private String normaliserSlug(final String nomConcession) {
        if (nomConcession == null || nomConcession.isBlank()) {
            // Fallback : si le nom est absent (ne devrait pas arriver), utiliser un slug generique
            return "concession";
        }
        String slug = Normalizer.normalize(nomConcession, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");

        if (slug.length() > LONGUEUR_SLUG_MAX) {
            slug = slug.substring(0, LONGUEUR_SLUG_MAX);
        }
        if (slug.isBlank()) {
            slug = "concession";
        }
        return slug;
    }

    /**
     * Genere un token aleatoire de 12 caracteres alphanumeriques lowercase.
     * Utilise SecureRandom pour empecher la prediction (anti-enumeration).
     */
    private String genererToken() {
        final StringBuilder sb = new StringBuilder(LONGUEUR_TOKEN);
        for (int i = 0; i < LONGUEUR_TOKEN; i++) {
            sb.append(ALPHABET_TOKEN.charAt(RANDOM.nextInt(ALPHABET_TOKEN.length())));
        }
        return sb.toString();
    }

    /** Resultat de la generation : slug et adresse complete. */
    public record EmailGenere(String slug, String emailAddress) {}
}
