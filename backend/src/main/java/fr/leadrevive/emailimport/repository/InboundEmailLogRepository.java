package fr.leadrevive.emailimport.repository;

import fr.leadrevive.emailimport.entity.InboundEmailLog;
import fr.leadrevive.emailimport.entity.StatutInboundEmail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface InboundEmailLogRepository extends JpaRepository<InboundEmailLog, Long> {

    /** Liste paginee pour le manager (filtre tenantFilter actif). */
    Page<InboundEmailLog> findByTenantIdOrderByCreeLeDesc(Long tenantId, Pageable pageable);

    /**
     * Liste paginee pour le super admin (cross-tenant). Filtres optionnels.
     * Appel sans tenantFilter cote service.
     */
    @Query("""
            SELECT l FROM InboundEmailLog l
            WHERE (:tenantId IS NULL OR l.tenantId = :tenantId)
              AND (:statut IS NULL OR l.statut = :statut)
            ORDER BY l.creeLe DESC
            """)
    Page<InboundEmailLog> findAllAdmin(@Param("tenantId") Long tenantId,
                                        @Param("statut") StatutInboundEmail statut,
                                        Pageable pageable);

    /** Rate limit : combien d'emails recus dans la derniere heure pour le tenant. */
    @Query("""
            SELECT COUNT(l) FROM InboundEmailLog l
            WHERE l.tenantId = :tenantId
              AND l.creeLe >= :depuis
            """)
    long countByTenantIdSince(@Param("tenantId") Long tenantId,
                              @Param("depuis") LocalDateTime depuis);

    /**
     * Stats admin : nb emails recus / leads crees / taux parsing OK sur la
     * periode pour un tenant donne.
     */
    @Query("""
            SELECT l.statut, COUNT(l) FROM InboundEmailLog l
            WHERE l.tenantId = :tenantId
              AND l.creeLe >= :depuis
            GROUP BY l.statut
            """)
    java.util.List<Object[]> countByStatutSince(@Param("tenantId") Long tenantId,
                                                  @Param("depuis") LocalDateTime depuis);

    /**
     * Purge RGPD : supprime les logs d'emails entrants antérieurs à la limite.
     * Requête NATIVE → cross-tenant volontaire (le job de purge tourne sans contexte
     * tenant). Le contenu d'email (PII) ne doit pas être conservé indéfiniment.
     */
    @Modifying
    @Query(value = "DELETE FROM inbound_email_log WHERE cree_le < :limite", nativeQuery = true)
    int purgerAvant(@Param("limite") LocalDateTime limite);
}
