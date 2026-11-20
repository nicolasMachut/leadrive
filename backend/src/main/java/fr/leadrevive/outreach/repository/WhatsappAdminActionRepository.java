package fr.leadrevive.outreach.repository;

import fr.leadrevive.outreach.entity.WhatsappAdminAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository d'audit des actions admin sur les configs WhatsApp.
 *
 * <p>Append-only au niveau applicatif. Consultation via les endpoints
 * /api/admin/whatsapp/admin-actions.</p>
 */
@Repository
public interface WhatsappAdminActionRepository extends JpaRepository<WhatsappAdminAction, Long> {

    /** Actions sur un tenant (consultation admin du detail d'une concession). */
    List<WhatsappAdminAction> findByTenantIdOrderByCreeLeDesc(Long tenantId);

    /** Audit log global, pagine pour l'admin. */
    Page<WhatsappAdminAction> findAllByOrderByCreeLeDesc(Pageable pageable);
}
