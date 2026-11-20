package fr.leadrevive.ai.repository;

import fr.leadrevive.ai.entity.AiLog;
import fr.leadrevive.ai.entity.TypeGenerationEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AiLogRepository extends JpaRepository<AiLog, Long> {

    List<AiLog> findByLeadIdAndTypeGenerationOrderByCreeLe(Long leadId, TypeGenerationEnum typeGeneration);

    List<AiLog> findByLeadIdOrderByCreeLe(Long leadId);

    /**
     * Suppression cible des AI logs pour un lead donne. Utilisee par RgpdService
     * pour le droit a l'oubli (art. 17). Remplace l'ancien findAll() + filter qui
     * chargeait toute la table en memoire et risquait l'OOM en prod.
     */
    @Modifying
    @Query("DELETE FROM AiLog a WHERE a.leadId = :leadId AND a.tenantId = :tenantId")
    int deleteByLeadIdAndTenantId(@Param("leadId") Long leadId, @Param("tenantId") Long tenantId);

    @Query("""
            SELECT COALESCE(SUM(a.coutEstime), 0) FROM AiLog a
            WHERE a.tenantId = :tenantId
            AND a.creeLe >= :depuis
            """)
    java.math.BigDecimal coutTotalDepuis(@Param("tenantId") Long tenantId, @Param("depuis") LocalDateTime depuis);

    @Query("""
            SELECT a.typeGeneration, COUNT(a), COALESCE(SUM(a.coutEstime), 0)
            FROM AiLog a
            WHERE a.tenantId = :tenantId
            AND a.creeLe >= :depuis
            GROUP BY a.typeGeneration
            """)
    List<Object[]> statsParTypeDepuis(@Param("tenantId") Long tenantId, @Param("depuis") LocalDateTime depuis);

    @Query("""
            SELECT COUNT(a) FROM AiLog a
            WHERE a.tenantId = :tenantId
            AND a.creeLe >= :depuis
            """)
    Long countDepuis(@Param("tenantId") Long tenantId, @Param("depuis") LocalDateTime depuis);

    @Query(value = """
            SELECT TO_CHAR(a.cree_le, 'YYYY-MM') as mois, COALESCE(SUM(a.cout_estime), 0)
            FROM ai_log a
            WHERE a.tenant_id = :tenantId
            AND a.cree_le >= :depuis
            GROUP BY TO_CHAR(a.cree_le, 'YYYY-MM')
            """, nativeQuery = true)
    List<Object[]> coutParMoisDepuis(@Param("tenantId") Long tenantId, @Param("depuis") LocalDateTime depuis);

    @Query("""
            SELECT COALESCE(SUM(a.coutEstime), 0) FROM AiLog a
            WHERE a.creeLe >= :depuis
            """)
    java.math.BigDecimal coutTotalGlobalDepuis(@Param("depuis") LocalDateTime depuis);

    @Query("""
            SELECT COUNT(a) FROM AiLog a
            WHERE a.creeLe >= :depuis
            """)
    Long countGlobalDepuis(@Param("depuis") LocalDateTime depuis);

    @Query(value = """
            SELECT a.tenant_id, COUNT(a.id_ai_log), COALESCE(SUM(a.cout_estime), 0), MAX(a.cree_le)
            FROM ai_log a
            WHERE a.cree_le >= :depuis
            GROUP BY a.tenant_id
            """, nativeQuery = true)
    List<Object[]> usageParTenantDepuis(@Param("depuis") LocalDateTime depuis);
}
