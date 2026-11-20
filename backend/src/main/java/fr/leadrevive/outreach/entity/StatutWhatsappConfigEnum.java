package fr.leadrevive.outreach.entity;

/**
 * Cycle de vie de la configuration WhatsApp Business d'une concession.
 *
 * <pre>
 * EN_VALIDATION - Meta a ete connecte, on attend que les templates soient
 *                 approuves (statut transitoire post Self Sign-up).
 * ACTIF         - Meta connecte + tous les templates approuves : l'envoi de
 *                 messages est techniquement possible.
 * RESTREINT     - le compte etait ACTIF mais l'envoi est desormais bloque par une
 *                 restriction cote WhatsApp Business (numero refuse/hors-ligne ou
 *                 envois rejetes en masse). Distinct de ERREUR (qui est un echec
 *                 d'onboarding/provisioning). L'envoi est bloque (statut != ACTIF) et
 *                 une banniere d'alerte est affichee. Recovery automatique vers ACTIF
 *                 quand le sender redevient ONLINE (WhatsAppSenderStatusJob).
 * ERREUR        - probleme (display name rejete, sender introuvable cote Twilio,
 *                 Meta refus, etc.). Necessite intervention support.
 * DESACTIVE     - concession desactivee, suppression admin explicite, ou tenant
 *                 jamais onboarded. Aucun envoi possible.
 * </pre>
 *
 * <p><strong>Invariant</strong> : envois bloques sur tous les statuts != ACTIF.
 * Aussi bien cote backend ({@code InboxService}, {@code TwilioWhatsAppService})
 * que cote frontend ({@code useSelfSignupStatus.tousTemplatesApprouves}).</p>
 *
 * <p>Les statuts intermediaires du mode LEGACY (EN_ATTENTE_NUMERO, CODE_ENVOYE,
 * NUMERO_VERIFIE, EN_VALIDATION_META) ont ete supprimes lors du sunset epic 23
 * (2026-Q2). Le flow Self Sign-up (epic 25) transite DESACTIVE -> EN_VALIDATION
 * -> ACTIF, la derniere bascule etant pilotee par {@code WhatsAppSenderStatusJob}
 * quand Meta confirme {@code ONLINE} et que tous les templates sont approuves.</p>
 */
public enum StatutWhatsappConfigEnum {
    EN_VALIDATION,
    ACTIF,
    RESTREINT,
    ERREUR,
    DESACTIVE
}
