package fr.leadrevive.lead.repository;

import fr.leadrevive.lead.entity.HistoriqueStatut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface HistoriqueStatutRepository extends JpaRepository<HistoriqueStatut, Long> {

    List<HistoriqueStatut> findByLeadIdOrderByCreeLeDesc(Long leadId);

    List<HistoriqueStatut> findByTenantIdAndCreeLeAfterAndCreeLeBeforeOrderByCreeLeDesc(
            Long tenantId, LocalDateTime after, LocalDateTime before);

    // ── Comptage des RDV pris FACTURABLES (définition canonique, cf. CLAUDE.md) ──
    // Un RDV pris = transition immuable vers RDV_PRIS de source 'IA' (RDV confirmé+daté
    // par le prospect via l'IA). Compté DISTINCT par lead, borné par la date de l'événement.
    // On EXCLUT les passages manuels (DRAG_DROP/SELECTBOX) non facturables.

    /** Nombre de RDV pris (leads distincts) sur le tenant depuis `debut`. */
    @Query(value = """
            SELECT COUNT(DISTINCT h.lead_id)
            FROM historique_statut h
            WHERE h.tenant_id = :tenantId
              AND h.statut_suivant = 'RDV_PRIS'
              AND h.source = 'IA'
              AND h.cree_le >= :debut
            """, nativeQuery = true)
    long countRdvPrisIaSince(@Param("tenantId") Long tenantId, @Param("debut") LocalDateTime debut);

    /** Nombre de RDV pris (leads distincts) sur le tenant dans [debut, fin[. */
    @Query(value = """
            SELECT COUNT(DISTINCT h.lead_id)
            FROM historique_statut h
            WHERE h.tenant_id = :tenantId
              AND h.statut_suivant = 'RDV_PRIS'
              AND h.source = 'IA'
              AND h.cree_le >= :debut
              AND h.cree_le < :fin
            """, nativeQuery = true)
    long countRdvPrisIaBetween(@Param("tenantId") Long tenantId,
                               @Param("debut") LocalDateTime debut,
                               @Param("fin") LocalDateTime fin);

    /** Nombre de RDV pris (leads distincts) depuis `debut` pour les leads actuellement
     *  assignés à `vendeurId`. Attribution par propriétaire actuel du lead. */
    @Query(value = """
            SELECT COUNT(DISTINCT h.lead_id)
            FROM historique_statut h
            JOIN lead l ON l.id_lead = h.lead_id
            WHERE h.tenant_id = :tenantId
              AND l.vendeur_id = :vendeurId
              AND h.statut_suivant = 'RDV_PRIS'
              AND h.source = 'IA'
              AND h.cree_le >= :debut
            """, nativeQuery = true)
    long countRdvPrisIaSinceForVendeur(@Param("tenantId") Long tenantId,
                                       @Param("vendeurId") Long vendeurId,
                                       @Param("debut") LocalDateTime debut);

    /** RDV pris depuis `debut` groupés par vendeur (propriétaire actuel du lead).
     *  Retourne [vendeurId, nbRdv]. Les leads non assignés (vendeur_id NULL) sont exclus. */
    @Query(value = """
            SELECT l.vendeur_id AS vendeurId, COUNT(DISTINCT h.lead_id) AS nbRdv
            FROM historique_statut h
            JOIN lead l ON l.id_lead = h.lead_id
            WHERE h.tenant_id = :tenantId
              AND l.vendeur_id IS NOT NULL
              AND h.statut_suivant = 'RDV_PRIS'
              AND h.source = 'IA'
              AND h.cree_le >= :debut
            GROUP BY l.vendeur_id
            """, nativeQuery = true)
    List<Object[]> countRdvPrisIaSinceByVendeur(@Param("tenantId") Long tenantId,
                                                @Param("debut") LocalDateTime debut);
}
