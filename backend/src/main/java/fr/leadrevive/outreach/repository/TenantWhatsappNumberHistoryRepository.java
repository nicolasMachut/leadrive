package fr.leadrevive.outreach.repository;

import fr.leadrevive.outreach.entity.TenantWhatsappNumberHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository d'audit historique des associations numero <-> tenant.
 *
 * <p>Append-only au niveau applicatif : aucun UPDATE/DELETE ne doit etre
 * code, uniquement des INSERT et des SELECT.</p>
 *
 * <p>Pas de filtre Hibernate tenant : entite globale.</p>
 */
@Repository
public interface TenantWhatsappNumberHistoryRepository extends JpaRepository<TenantWhatsappNumberHistory, Long> {

    /** Historique complet d'un numero (toutes les concessions qui l'ont eu). */
    List<TenantWhatsappNumberHistory> findByWhatsappFromOrderByAssocieLeDesc(String whatsappFrom);

    /** Historique complet d'un tenant (tous les numeros qu'il a eu). */
    List<TenantWhatsappNumberHistory> findByTenantIdOrderByAssocieLeDesc(Long tenantId);
}
