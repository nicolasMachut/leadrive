package fr.leadrevive.outreach.entity;

/**
 * Statut local d'un template WhatsApp pour un tenant donne.
 *
 * <p>Le statut local diverge volontairement du statut Meta : on suit le cycle
 * de vie cote LeadRive (creation -> soumission Twilio -> polling Meta ->
 * approbation / rejet).</p>
 */
public enum StatutTemplateEnum {
    /**
     * Le template vient d'etre cree en BDD (a partir d'un YAML) mais n'a pas
     * encore ete soumis a Twilio Content API.
     */
    EN_ATTENTE_SOUMISSION,

    /**
     * Soumis a Twilio, en attente de la validation Meta (polling).
     */
    SOUMIS,

    /**
     * Approuve par Meta, utilisable pour envoyer des messages hors fenetre 24h.
     */
    APPROUVE,

    /**
     * Refuse par Meta. Voir {@code metaRejectReason} pour la cause.
     */
    REJETE,

    /**
     * Template retire manuellement par un admin ou desactive cote Meta.
     */
    DESACTIVE
}
