package fr.leadrevive.outreach.entity;

/**
 * Intention principale detectee par l'IA dans un message entrant du prospect.
 */
public enum IntentionEnum {
    /** Le prospect propose un creneau de rappel / confirme un RDV. */
    RDV,
    /** Le prospect demande explicitement a ne plus etre contacte (STOP). */
    STOP,
    /** Le prospect refuse ou dit ne pas etre interesse. */
    REFUS,
    /** Echange neutre / question / autre — aucune action auto. */
    AUTRE
}
