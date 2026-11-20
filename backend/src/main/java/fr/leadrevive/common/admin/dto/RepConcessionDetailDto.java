package fr.leadrevive.common.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Vue detaillee d'un etablissement pour la page admin
 * {@code /admin/concessions/{id}}. Regroupe en un seul payload toutes les
 * informations utiles pour le support / pilotage Leadrive :
 * <ul>
 *   <li>Identite : nom, ville (si historique), date creation, clerk org id</li>
 *   <li>WhatsApp Business : config courante + historique numeros</li>
 *   <li>Equipe : managers + vendeurs avec date de derniere connexion</li>
 *   <li>Activite : compteurs (leads, conversations actives, RDV semaine, messages)</li>
 *   <li>Audit : 10 dernieres actions admin sensibles sur WhatsApp</li>
 * </ul>
 *
 * Records imbriques pour rester lisible cote frontend (json typique nested).
 */
public record RepConcessionDetailDto(
        Long id,
        String nom,
        LocalDateTime creeLe,
        String clerkOrganizationId,
        WhatsAppDetailDto whatsapp,
        List<NumeroHistoriqueDto> historiqueNumeros,
        List<UtilisateurDto> utilisateurs,
        CompteursDto compteurs,
        List<AuditAdminDto> auditsAdmin
) {
    /**
     * Configuration WhatsApp courante. {@code null} si le tenant n'a jamais
     * commence l'onboarding.
     */
    public record WhatsAppDetailDto(
            String statut,
            String whatsappFrom,
            String displayName,
            String qualityRating,
            String messagingLimit,
            String metaStatus,
            String metaRejectReason,
            String twilioSenderSid,
            LocalDateTime dateDerniereSynchroMeta,
            LocalDateTime modifieLe
    ) {}

    /** Entree de tenant_whatsapp_number_history. */
    public record NumeroHistoriqueDto(
            String whatsappFrom,
            LocalDateTime associeLe,
            LocalDateTime dissocieLe,
            String raisonDissociation
    ) {}

    /** Manager ou vendeur du tenant. */
    public record UtilisateurDto(
            Long id,
            String prenom,
            String nom,
            String email,
            String role,
            String statut,
            LocalDateTime derniereConnexion
    ) {}

    /**
     * Compteurs d'activite. Tous calcules en SQL count() pour eviter de charger
     * les entites en memoire.
     */
    public record CompteursDto(
            long leadsTotal,
            long conversationsActives,
            long rdvSemaine,
            long messagesEnvoyesTotal
    ) {}

    /** Entree de whatsapp_admin_actions (10 dernieres). */
    public record AuditAdminDto(
            Long id,
            Long adminUserId,
            String action,
            String justification,
            LocalDateTime creeLe
    ) {}

    /**
     * Vue allegee pour la liste "Onboardings bloques" admin :
     * tenants restant trop longtemps dans un statut d'attente.
     */
    public record OnboardingBloqueDto(
            Long tenantId,
            String nom,
            String statut,
            String whatsappFrom,
            LocalDateTime depuis,
            long heuresEcoulees
    ) {}
}
