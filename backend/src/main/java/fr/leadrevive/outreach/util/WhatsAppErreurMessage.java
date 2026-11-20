package fr.leadrevive.outreach.util;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Traduit le motif d'echec brut d'un envoi WhatsApp (format {@code "[code] message"}
 * stocke sur {@code SmsMessage.erreur}, alimente par le webhook de statut Twilio) en
 * une phrase claire, en francais, destinee a l'utilisateur final (concessionnaire,
 * manager, vendeur).
 *
 * <p>Regles produit : aucun jargon technique cote utilisateur (pas de "Meta", pas de
 * code Twilio, pas de "template/webhook"). On reste factuel et actionnable.</p>
 *
 * <p>Le code Twilio est conserve uniquement pour le mapping interne ; il n'apparait
 * jamais dans la chaine retournee.</p>
 */
public final class WhatsAppErreurMessage {

    private static final Pattern CODE_PATTERN = Pattern.compile("^\\[(\\d+)]");

    /**
     * Codes Twilio signalant un numero / sender WhatsApp indisponible : l'envoi echoue
     * de maniere DETERMINISTE (le numero n'est pas/plus un sender WhatsApp valide), pas
     * une erreur ponctuelle ou par-message. C'est le signal "envois en echec" cote
     * etablissement (a distinguer d'une restriction Meta du compte).
     * 63007 : aucun channel/sender WhatsApp pour ce numero. 63051 : numero non operationnel.
     */
    private static final Set<String> CODES_NUMERO_INDISPONIBLE = Set.of("63007", "63051");

    private WhatsAppErreurMessage() {
    }

    /**
     * @param erreurBrute le motif stocke ({@code "[63016] ..."}), peut etre null/blank
     * @return une phrase claire en francais, ou {@code null} si pas d'erreur
     */
    public static String lisible(final String erreurBrute) {
        if (erreurBrute == null || erreurBrute.isBlank()) {
            return null;
        }
        final String code = extraireCode(erreurBrute);
        return switch (code == null ? "" : code) {
            case "63016" -> "Le prospect n'a pas répondu depuis plus de 24 h : un message libre "
                    + "n'est plus autorisé. Attendez sa réponse ou laissez l'assistant le relancer.";
            case "63021", "63013" -> "Ce message a été refusé par WhatsApp. Reformulez-le avant de réessayer.";
            case "63015", "63003" -> "Ce numéro ne semble pas joignable sur WhatsApp. Vérifiez qu'il est correct.";
            case "21610" -> "Le prospect s'est désinscrit (STOP) : vous ne pouvez plus lui écrire.";
            case "63018", "63017" -> "Trop de messages envoyés en peu de temps. Patientez quelques minutes.";
            case "63024", "63005" -> "Le message n'a pas pu être envoyé (contenu non conforme). Réessayez.";
            case "63007", "63051" -> "Votre numéro WhatsApp n'est pas encore prêt à envoyer. Vérifiez sa configuration.";
            default -> "L'envoi a échoué côté WhatsApp. Réessayez ou contactez le support si cela persiste.";
        };
    }

    /** Code Twilio brut (pour le regroupement admin), ou {@code null}. */
    public static String code(final String erreurBrute) {
        return erreurBrute == null ? null : extraireCode(erreurBrute);
    }

    /**
     * {@code true} si le motif correspond a un numero / sender WhatsApp indisponible
     * (envoi cassé de maniere persistante). Sert a declencher le signal "envois en
     * echec" cote manager (banniere) et admin, sans marquer le compte "restreint".
     */
    public static boolean estNumeroIndisponible(final String erreurBrute) {
        final String c = code(erreurBrute);
        return c != null && CODES_NUMERO_INDISPONIBLE.contains(c);
    }

    private static String extraireCode(final String erreurBrute) {
        final Matcher m = CODE_PATTERN.matcher(erreurBrute.trim());
        return m.find() ? m.group(1) : null;
    }
}
