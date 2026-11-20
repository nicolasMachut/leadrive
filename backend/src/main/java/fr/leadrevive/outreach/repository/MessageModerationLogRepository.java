package fr.leadrevive.outreach.repository;

import fr.leadrevive.outreach.entity.MessageModerationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository d'audit de la moderation IA des envois manuels.
 *
 * <p>Append-only au niveau applicatif. Consultation par les super admins
 * via /api/admin/whatsapp/moderation-logs. Conserve le contenu en clair
 * pour audit Meta en cas de policy violation.</p>
 */
@Repository
public interface MessageModerationLogRepository extends JpaRepository<MessageModerationLog, Long> {

    /** Logs de moderation pour un tenant donne (recents en premier). */
    List<MessageModerationLog> findByTenantIdOrderByCreeLeDesc(Long tenantId);

    /** Vue paginee de tous les logs (admin global). */
    Page<MessageModerationLog> findAllByOrderByCreeLeDesc(Pageable pageable);

    /** Compteur des messages BLOQUES depuis une date (pour metriques quotidiennes). */
    long countByDecisionAndCreeLeAfter(String decision, LocalDateTime depuis);
}
