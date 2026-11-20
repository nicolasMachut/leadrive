package fr.leadrevive.common.util;

/**
 * Utilitaire de masquage des donnees personnelles (PII) avant logging.
 *
 * Usage : `log.info("Reponse prospect - numero: {}", PiiMasker.phone(numero))`.
 *
 * RGPD art. 5(1)(f) : les logs ne doivent pas exposer en clair les donnees
 * personnelles identifiables (telephones, emails, contenus de messages).
 */
public final class PiiMasker {

    private PiiMasker() { /* utility */ }

    /**
     * Masque un email : `j***@gmail.com`.
     * Garde la 1ere lettre du local-part et le domaine entier.
     */
    public static String email(final String email) {
        if (email == null || email.isBlank()) return "[null]";
        final int at = email.indexOf('@');
        if (at <= 0) return "[invalid-email]";
        final String local = email.substring(0, at);
        final String domain = email.substring(at);
        final String prefix = local.length() == 1 ? local : local.substring(0, 1);
        return prefix + "***" + domain;
    }

    /**
     * Masque un numero de telephone : `****1234` (4 derniers chiffres).
     * Gere les formats E.164 (+33...) et locaux (06...).
     */
    public static String phone(final String phone) {
        if (phone == null || phone.isBlank()) return "[null]";
        final String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() < 4) return "****";
        return "****" + digits.substring(digits.length() - 4);
    }

    /**
     * Masque un nom complet : `M***n K***m` (premiere et derniere lettre par mot).
     */
    public static String name(final String name) {
        if (name == null || name.isBlank()) return "[null]";
        final String[] parts = name.trim().split("\\s+");
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(' ');
            final String p = parts[i];
            if (p.length() <= 2) sb.append("**");
            else sb.append(p.charAt(0)).append("***").append(p.charAt(p.length() - 1));
        }
        return sb.toString();
    }

    /**
     * Masque le contenu d'un message : indique seulement la longueur.
     * Le contenu reel ne doit JAMAIS apparaitre dans les logs (RGPD + secret correspondance).
     */
    public static String content(final String content) {
        if (content == null) return "[null]";
        return "[" + content.length() + " chars]";
    }
}
