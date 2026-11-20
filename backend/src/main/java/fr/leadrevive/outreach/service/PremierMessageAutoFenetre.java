package fr.leadrevive.outreach.service;

import java.time.LocalDateTime;

/**
 * Helper PUR (sans etat, sans dependance Spring) pour calculer l'heure d'envoi
 * EFFECTIVE d'un premier message WhatsApp automatique, en tenant compte de la
 * plage horaire d'envoi configuree par le tenant.
 *
 * <p>Le scheduler {@link PremierMessageAutoJob} n'envoie que si l'heure courante
 * est dans la plage {@code [debutHeure, finHeure[} (fin exclusive). Hors plage,
 * l'envoi est reporte au prochain creneau. Ce helper reproduit cette regle pour
 * exposer au front l'echeance REELLE (et pas l'echeance brute armee), afin
 * d'eviter un libelle trompeur du type "imminent" alors que le message ne partira
 * qu'a l'ouverture de la plage.</p>
 */
public final class PremierMessageAutoFenetre {

    private PremierMessageAutoFenetre() {
    }

    /**
     * Resultat combine du calcul de fenetre : l'heure d'envoi effective ET le
     * flag "hors plage". Evite le double calcul (calculerEnvoiEffectif appele une
     * fois par {@code estHorsPlage} puis une fois pour la valeur elle-meme) la ou
     * les deux informations sont necessaires (enrichissement DTO, liste envois).
     *
     * @param effectifLe l'heure a laquelle le message partira reellement
     * @param horsPlage   true si {@code effectifLe} est repousse hors de la base (echeance armee ou maintenant)
     */
    public record ResultatFenetre(LocalDateTime effectifLe, boolean horsPlage) {
    }

    /**
     * Calcule en une passe l'heure d'envoi effective et le flag hors plage.
     * A privilegier des que les deux valeurs sont exposees (evite de recalculer
     * {@code calculerEnvoiEffectif} deux fois).
     */
    public static ResultatFenetre calculer(final LocalDateTime prevuLe,
                                           final int debutHeure,
                                           final int finHeure,
                                           final LocalDateTime maintenant) {
        final LocalDateTime base = prevuLe.isAfter(maintenant) ? prevuLe : maintenant;
        final LocalDateTime effectif = calculerEnvoiEffectif(prevuLe, debutHeure, finHeure, maintenant);
        return new ResultatFenetre(effectif, !effectif.isEqual(base));
    }

    /**
     * Calcule l'heure d'envoi effective en fonction de la plage horaire.
     *
     * <p>Regle (coherente avec la condition du scheduler {@code heure >= debut && heure < fin}) :</p>
     * <ul>
     *   <li>base = max(prevuLe, maintenant) : on ne renvoie jamais une echeance passee ;</li>
     *   <li>si l'heure de base est dans la plage {@code [debut, fin[} -> envoi a base ;</li>
     *   <li>si l'heure de base est avant l'ouverture -> aujourd'hui a l'heure de debut ;</li>
     *   <li>si l'heure de base est apres/a la fermeture -> demain a l'heure de debut.</li>
     * </ul>
     *
     * @param prevuLe     echeance armee (creation + delai) ; ne doit pas etre nulle
     * @param debutHeure  heure de debut de plage (0-23)
     * @param finHeure    heure de fin de plage (1-24, exclusive)
     * @param maintenant  instant de reference
     * @return l'heure a laquelle le message partira reellement
     */
    public static LocalDateTime calculerEnvoiEffectif(final LocalDateTime prevuLe,
                                                      final int debutHeure,
                                                      final int finHeure,
                                                      final LocalDateTime maintenant) {
        final LocalDateTime base = prevuLe.isAfter(maintenant) ? prevuLe : maintenant;
        final int heure = base.getHour();
        if (heure >= debutHeure && heure < finHeure) {
            return base;
        }
        if (heure < debutHeure) {
            return base.toLocalDate().atTime(debutHeure, 0);
        }
        return base.toLocalDate().plusDays(1).atTime(debutHeure, 0);
    }

    /**
     * Indique si l'envoi est repousse hors de sa base (echeance armee ou maintenant),
     * c'est-a-dire si l'echeance tombe hors de la plage horaire d'envoi.
     */
    public static boolean estHorsPlage(final LocalDateTime prevuLe,
                                       final int debutHeure,
                                       final int finHeure,
                                       final LocalDateTime maintenant) {
        final LocalDateTime base = prevuLe.isAfter(maintenant) ? prevuLe : maintenant;
        return !calculerEnvoiEffectif(prevuLe, debutHeure, finHeure, maintenant).isEqual(base);
    }
}
