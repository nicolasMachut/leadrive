package fr.leadrevive.lead.repository;

import fr.leadrevive.lead.entity.SourceVenteEnum;
import fr.leadrevive.lead.entity.VenteConfirmee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VenteConfirmeeRepository extends JpaRepository<VenteConfirmee, Long> {

    Optional<VenteConfirmee> findByLeadId(Long leadId);

    /**
     * Fix bug-hunter #5 : batch lookup pour eviter le N+1 sur le listing
     * "RDV en attente" du dashboard (1 query au lieu de N).
     */
    List<VenteConfirmee> findByLeadIdIn(Collection<Long> leadIds);

    List<VenteConfirmee> findByTenantIdAndDateVenteBetweenOrderByDateVenteDesc(
            Long tenantId, LocalDate debut, LocalDate fin);

    @Query("""
            SELECT COUNT(v) FROM VenteConfirmee v
            WHERE v.tenantId = :tenantId
            AND v.dateVente >= :debut AND v.dateVente <= :fin
            """)
    Long countByTenantIdAndPeriode(@Param("tenantId") Long tenantId,
                                    @Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    @Query("""
            SELECT COALESCE(SUM(v.montant), 0) FROM VenteConfirmee v
            WHERE v.tenantId = :tenantId
            AND v.dateVente >= :debut AND v.dateVente <= :fin
            AND v.source = :source
            """)
    BigDecimal sumMontantByTenantIdAndPeriodeAndSource(@Param("tenantId") Long tenantId,
                                                        @Param("debut") LocalDate debut,
                                                        @Param("fin") LocalDate fin,
                                                        @Param("source") SourceVenteEnum source);

    @Query("""
            SELECT COALESCE(SUM(v.montant), 0) FROM VenteConfirmee v
            WHERE v.tenantId = :tenantId
            AND v.dateVente >= :debut AND v.dateVente <= :fin
            """)
    BigDecimal sumMontantByTenantIdAndPeriode(@Param("tenantId") Long tenantId,
                                               @Param("debut") LocalDate debut,
                                               @Param("fin") LocalDate fin);

    @Query("""
            SELECT v.userId, COUNT(v), COALESCE(SUM(v.montant), 0)
            FROM VenteConfirmee v
            WHERE v.tenantId = :tenantId
            AND v.dateVente >= :debut AND v.dateVente <= :fin
            GROUP BY v.userId
            """)
    List<Object[]> statsParVendeur(@Param("tenantId") Long tenantId,
                                    @Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    @Query(value = """
            SELECT v.date_vente, COUNT(*), COALESCE(SUM(v.montant), 0)
            FROM vente_confirmee v
            WHERE v.tenant_id = :tenantId
            AND v.date_vente >= :debut AND v.date_vente <= :fin
            GROUP BY v.date_vente
            ORDER BY v.date_vente
            """, nativeQuery = true)
    List<Object[]> statsParJour(@Param("tenantId") Long tenantId,
                                 @Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    @Query(value = """
            SELECT TO_CHAR(v.date_vente, 'YYYY-MM') as mois, COUNT(*), COALESCE(SUM(v.montant), 0)
            FROM vente_confirmee v
            WHERE v.tenant_id = :tenantId
            AND v.date_vente >= :debut
            GROUP BY TO_CHAR(v.date_vente, 'YYYY-MM')
            """, nativeQuery = true)
    List<Object[]> statsParMoisDepuis(@Param("tenantId") Long tenantId, @Param("debut") LocalDate debut);
}
