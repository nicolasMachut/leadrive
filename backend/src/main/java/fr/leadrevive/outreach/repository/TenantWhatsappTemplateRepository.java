package fr.leadrevive.outreach.repository;

import fr.leadrevive.outreach.entity.StatutTemplateEnum;
import fr.leadrevive.outreach.entity.TenantWhatsappTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository pour les templates WhatsApp par tenant (epic 25).
 *
 * <p>Le filtre Hibernate {@code tenantFilter} est applique automatiquement par
 * Hibernate (declare sur l'entite). Les methodes derivees ci-dessous renvoient
 * donc uniquement les lignes du tenant courant.</p>
 *
 * <p>Methodes cross-tenant (jobs de polling) : utiliser {@code findAllByStatut}
 * avec desactivation explicite du filtre via {@code Session#disableFilter}.</p>
 */
@Repository
public interface TenantWhatsappTemplateRepository extends JpaRepository<TenantWhatsappTemplate, Long> {

    /**
     * Recupere le template d'un tenant par son nom Meta.
     * Utilisable pendant l'envoi : on cherche le SID du template approuve.
     */
    Optional<TenantWhatsappTemplate> findByTenantIdAndTemplateName(Long tenantId, String templateName);

    /**
     * Liste tous les templates d'un tenant (vue admin / monitoring).
     */
    List<TenantWhatsappTemplate> findAllByTenantId(Long tenantId);

    /**
     * Liste les templates dans un statut donne pour un tenant.
     * Utile pour le job de polling : chercher les SOUMIS dont la synchro
     * doit etre rafraichie.
     */
    List<TenantWhatsappTemplate> findAllByTenantIdAndStatut(Long tenantId, StatutTemplateEnum statut);

    /**
     * Liste les templates a re-soumettre : statut EN_ATTENTE_SOUMISSION ou
     * REJETE avec back-off expire.
     */
    List<TenantWhatsappTemplate> findAllByTenantIdAndStatutAndProchaineTentativeLeBefore(
            Long tenantId,
            StatutTemplateEnum statut,
            LocalDateTime maintenant
    );

    /**
     * Fix bug-hunter #5 : usage cross-tenant par le job de polling Meta.
     * Remplace l'ancien {@code findAll().stream().filter()} qui chargeait
     * tous les templates en RAM avant de filtrer. Le filtre Hibernate
     * {@code tenantFilter} doit etre desactive sur la session avant appel.
     */
    List<TenantWhatsappTemplate> findAllByStatut(StatutTemplateEnum statut);

    /**
     * Templates dans un statut donne soumis apres un seuil (cross-tenant).
     * Sert au polling accelere (60s) cible sur les templates fraichement soumis :
     * en categorie UTILITY, Meta auto-approuve en quelques minutes. Le filtre
     * Hibernate {@code tenantFilter} doit etre desactive sur la session avant appel.
     */
    List<TenantWhatsappTemplate> findAllByStatutAndSoumisLeAfter(
            StatutTemplateEnum statut, LocalDateTime seuil);
}
