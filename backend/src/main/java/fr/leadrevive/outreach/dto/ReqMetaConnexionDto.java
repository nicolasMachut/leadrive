package fr.leadrevive.outreach.dto;

/**
 * Payload du callback Self Sign-up Meta (epic 25).
 *
 * <p>Recu apres que l'utilisateur a complete le flow OAuth Facebook depuis le
 * front. On y retrouve le WABA ID et le Meta Business ID que Facebook
 * communique au front via {@code FB.login} avec le scope
 * {@code whatsapp_business_management}.</p>
 *
 * <p><strong>Robustesse</strong> : {@code wabaId} et {@code metaBusinessId} sont
 * volontairement optionnels. Ils arrivent cote front via un {@code postMessage}
 * Meta (evenement {@code FINISH}) qui n'est PAS fiable a 100% (popup, origine,
 * version SDK). Quand il manque, le front envoie le {@code code} OAuth seul et
 * le backend resout le WABA cote serveur via l'API Graph (debug_token). Regle :
 * au moins l'un de {@code wabaId} ou {@code code} doit etre present, verifie dans
 * {@code WhatsAppSelfSignupService.connecterMeta}.</p>
 *
 * <p><strong>Securite</strong> : ces identifiants sont publiquement visibles
 * cote Meta. Le veritable secret (access_token) est echange backend-to-backend
 * via le {@code code} OAuth.</p>
 */
public record ReqMetaConnexionDto(
        String wabaId,

        String metaBusinessId,

        /**
         * Numero de telephone E.164 selectionne dans le flow Embedded Signup.
         * Optionnel : on peut completer plus tard, mais requis pour activer
         * l'envoi de messages.
         */
        String phoneNumber,

        /**
         * Identifiant du numero WhatsApp cote Meta (renvoye par l'Embedded Signup
         * via postMessage). Optionnel : sert a recuperer le {@code verified_name}
         * via l'API Graph.
         */
        String phoneNumberId,

        /**
         * Code OAuth renvoye par l'Embedded Signup (FB.login, response_type=code).
         * Optionnel : echange backend contre un token pour lire le verified_name.
         * Ne jamais logger.
         */
        String code
) {
}
