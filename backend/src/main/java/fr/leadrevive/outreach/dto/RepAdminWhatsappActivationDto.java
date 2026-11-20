package fr.leadrevive.outreach.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Vue admin (SUPER_ADMIN) de l'etat d'avancement de la configuration WhatsApp
 * d'un etablissement, pour la console de suivi / debug SAV.
 *
 * <p>Agrege la config ({@code tenant_whatsapp_config}), l'etape atteinte dans le
 * parcours d'activation (0 -&gt; {@link #etapesTotal}), la raison de blocage
 * eventuelle, les identifiants techniques (pour le SAV / Twilio / Meta) et le
 * detail des modeles de messages.</p>
 *
 * <p><strong>Securite</strong> : ne contient jamais le token d'auth du
 * sous-compte Twilio (secret). Seuls les identifiants non sensibles sont
 * exposes.</p>
 *
 * @param tenantId                identifiant de l'etablissement
 * @param nomEtablissement        nom lisible de l'etablissement
 * @param statut                  statut de la config ({@code DESACTIVE}, {@code EN_VALIDATION}, {@code ACTIF}, {@code ERREUR})
 * @param etape                   etape atteinte (0 = non demarre, {@link #etapesTotal} = actif)
 * @param etapesTotal             nombre total d'etapes du parcours
 * @param etapeLibelle            libelle court de l'etape atteinte
 * @param bloque                  true si un probleme empeche l'avancement
 * @param raisonBlocage           explication du blocage (null si pas bloque)
 * @param numero                  numero WhatsApp E.164 (peut etre null)
 * @param displayName             nom affiche cote WhatsApp
 * @param twilioSubAccountSid     SID du sous-compte Twilio dedie
 * @param twilioSenderSid         SID du sender Twilio
 * @param wabaId                  WhatsApp Business Account ID (Meta)
 * @param metaBusinessId          Meta Business ID
 * @param metaStatus             statut cote Meta ({@code ONLINE}, {@code OFFLINE}, {@code REJECTED}...)
 * @param nameStatus              statut d'examen du nom affiche ({@code APPROVED}, {@code PENDING_REVIEW}, {@code DECLINED}...)
 * @param qualityRating           note de qualite du numero ({@code GREEN}, {@code YELLOW}, {@code RED})
 * @param messagingLimit          plafond de messagerie Meta
 * @param metaRejectReason        raison de refus cote Meta (config)
 * @param dateDerniereSynchroMeta horodatage de la derniere synchro Twilio/Meta
 * @param modifieLe               horodatage de la derniere modification de la config
 * @param nbEchecsRecents         nombre de messages en ECHEC sur les 7 derniers jours
 * @param dernierEchecLe          horodatage du dernier message en ECHEC (ou null)
 * @param motifsEchec             repartition des echecs recents par motif (cause + nombre)
 * @param numeroEnEchec           true si un echec recent indique un numero/sender WhatsApp
 *                                indisponible (envois casses a corriger, ≠ restriction Meta)
 * @param modeles                 detail des modeles de messages du tenant
 */
public record RepAdminWhatsappActivationDto(
        Long tenantId,
        String nomEtablissement,
        String statut,
        int etape,
        int etapesTotal,
        String etapeLibelle,
        boolean bloque,
        String raisonBlocage,
        String numero,
        String displayName,
        String twilioSubAccountSid,
        String twilioSenderSid,
        String wabaId,
        String metaBusinessId,
        String metaStatus,
        String nameStatus,
        String qualityRating,
        String messagingLimit,
        String metaRejectReason,
        LocalDateTime dateDerniereSynchroMeta,
        LocalDateTime modifieLe,
        long nbEchecsRecents,
        LocalDateTime dernierEchecLe,
        List<MotifEchecDto> motifsEchec,
        boolean numeroEnEchec,
        List<ModeleDto> modeles
) {

    /**
     * Repartition d'un motif d'echec d'envoi sur la fenetre recente.
     *
     * @param motif libelle clair (FR, sans jargon) de la cause de l'echec
     * @param nombre nombre de messages tombes en echec pour ce motif
     */
    public record MotifEchecDto(String motif, long nombre) {
    }

    /**
     * Detail d'un modele de message WhatsApp pour un tenant.
     *
     * @param nom                 nom technique du modele (ex: leadrive_premier_contact)
     * @param statut              statut local ({@code EN_ATTENTE_SOUMISSION}, {@code SOUMIS}, {@code APPROUVE}, {@code REJETE}, {@code DESACTIVE})
     * @param templateSid         SID Twilio Content API (null tant que pas soumis)
     * @param metaRejectReason    raison de refus Meta (si rejete)
     * @param nbTentatives        nombre de tentatives de soumission
     * @param soumisLe            horodatage de la derniere soumission
     * @param prochaineTentativeLe date avant laquelle on ne re-soumet pas (back-off)
     * @param dateDerniereSynchro horodatage du dernier polling de statut
     */
    public record ModeleDto(
            String nom,
            String statut,
            String templateSid,
            String metaRejectReason,
            int nbTentatives,
            LocalDateTime soumisLe,
            LocalDateTime prochaineTentativeLe,
            LocalDateTime dateDerniereSynchro
    ) {
    }
}
