package fr.leadrevive.lead.repository;

import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.entity.LeadStatutEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LeadRepository extends JpaRepository<Lead, Long> {

    List<Lead> findByTenantId(Long tenantId);

    Page<Lead> findByTenantId(Long tenantId, Pageable pageable);

    /** Valeurs distinctes de territoire pour le tenant — alimente le dropdown filtres. */
    @Query("SELECT DISTINCT l.territoire FROM Lead l WHERE l.tenantId = :tenantId AND l.territoire IS NOT NULL ORDER BY l.territoire")
    List<String> findDistinctTerritoiresByTenantId(@Param("tenantId") Long tenantId);

    /** Valeurs distinctes de marque pour le tenant — alimente le dropdown filtres. */
    @Query("SELECT DISTINCT l.marque FROM Lead l WHERE l.tenantId = :tenantId AND l.marque IS NOT NULL ORDER BY l.marque")
    List<String> findDistinctMarquesByTenantId(@Param("tenantId") Long tenantId);

    /** Valeurs distinctes de source pour le tenant — alimente le dropdown filtres
     *  (LEBONCOIN, LACENTRALE, AUTOSCOUT24, etc.). */
    @Query("SELECT DISTINCT l.source FROM Lead l WHERE l.tenantId = :tenantId AND l.source IS NOT NULL ORDER BY l.source")
    List<String> findDistinctSourcesByTenantId(@Param("tenantId") Long tenantId);

    /** Securise multi-tenant : ne retourne le lead que s'il appartient au tenant. */
    Optional<Lead> findByIdAndTenantId(Long id, Long tenantId);

    /** Chargement batch multi-tenant : resout plusieurs leads en une seule requete
     *  (anti N+1 pour la construction de DTO sur des listes de taches). */
    List<Lead> findByIdInAndTenantId(java.util.Collection<Long> ids, Long tenantId);

    /** Kanban : leads actifs (non archives) du tenant — exclut les archives en SQL
     *  plutot que de charger TOUTE la table puis filtrer en RAM. */
    List<Lead> findByTenantIdAndArchiveFalse(Long tenantId);

    /** Kanban scope vendeur : leads actifs assignes a un vendeur donne. */
    List<Lead> findByTenantIdAndVendeurIdAndArchiveFalse(Long tenantId, Long vendeurId);

    /** Kanban scope "non assignes" : leads actifs sans vendeur. */
    List<Lead> findByTenantIdAndVendeurIdIsNullAndArchiveFalse(Long tenantId);

    @Query("""
            SELECT l FROM Lead l
            WHERE l.tenantId = :tenantId
            AND l.archive = :archive
            AND (:statuts IS NULL OR l.statut IN :statuts)
            AND (:territoire IS NULL OR l.territoire = :territoire)
            AND (:marque IS NULL OR l.marque = :marque)
            AND (:source IS NULL OR l.source = :source)
            AND (:vendeurId IS NULL OR (:vendeurId = 0L AND l.vendeurId IS NULL) OR (:vendeurId <> 0L AND l.vendeurId = :vendeurId))
            AND (:recherche IS NULL
                 OR LOWER(l.nom) LIKE LOWER(CONCAT('%', CAST(:recherche AS string), '%'))
                 OR LOWER(l.email) LIKE LOWER(CONCAT('%', CAST(:recherche AS string), '%'))
                 OR l.telephone LIKE CONCAT('%', CAST(:recherche AS string), '%'))
            """)
    Page<Lead> findByTenantIdWithFilters(@Param("tenantId") Long tenantId,
                                          @Param("archive") boolean archive,
                                          @Param("statuts") java.util.List<LeadStatutEnum> statuts,
                                          @Param("territoire") String territoire,
                                          @Param("marque") String marque,
                                          @Param("source") String source,
                                          @Param("vendeurId") Long vendeurId,
                                          @Param("recherche") String recherche,
                                          Pageable pageable);

    @Query("""
            SELECT l FROM Lead l
            WHERE l.tenantId = :tenantId
            AND l.vendeurId = :vendeurId
            AND l.archive = :archive
            AND (:statut IS NULL OR l.statut = :statut)
            AND (:recherche IS NULL
                 OR LOWER(l.nom) LIKE LOWER(CONCAT('%', CAST(:recherche AS string), '%'))
                 OR LOWER(l.email) LIKE LOWER(CONCAT('%', CAST(:recherche AS string), '%'))
                 OR l.telephone LIKE CONCAT('%', CAST(:recherche AS string), '%'))
            """)
    Page<Lead> findByTenantIdAndVendeurId(@Param("tenantId") Long tenantId,
                                           @Param("vendeurId") Long vendeurId,
                                           @Param("archive") boolean archive,
                                           @Param("statut") LeadStatutEnum statut,
                                           @Param("recherche") String recherche,
                                           Pageable pageable);

    Optional<Lead> findByTenantIdAndEmail(Long tenantId, String email);

    Optional<Lead> findByTenantIdAndTelephone(Long tenantId, String telephone);

    /**
     * Deduplication manuelle "coller texte" (DeduplicationService.estDoublon) : variantes
     * restreintes aux leads ACTIFS (non archives). Un contact archive (invisible dans les
     * listes) ne doit pas provoquer un 409 contre un lead qu'on ne peut pas voir. NE PAS
     * remplacer les variantes sans filtre ci-dessus : elles servent a d'autres lookups.
     */
    Optional<Lead> findByTenantIdAndEmailAndArchiveFalse(Long tenantId, String email);

    Optional<Lead> findByTenantIdAndTelephoneAndArchiveFalse(Long tenantId, String telephone);

    /**
     * Garde-fou desarchivage (LeadService.restaurerLead) : existe-t-il un AUTRE lead ACTIF
     * du meme tenant partageant ce telephone ? Si oui, desarchiver violerait l'index unique
     * {@code uq_lead_tenant_telephone_actif} (V068). {@code IdNot} exclut le lead lui-meme.
     */
    boolean existsByTenantIdAndTelephoneAndArchiveFalseAndIdNot(Long tenantId, String telephone, Long id);

    /** Variante email du garde-fou desarchivage (index {@code uq_lead_tenant_email_actif}). */
    boolean existsByTenantIdAndEmailAndArchiveFalseAndIdNot(Long tenantId, String email, Long id);

    /**
     * Deduplication import email : cherche un lead ACTIF (non archive) du meme tenant
     * avec le meme telephone cree dans la fenetre {@code depuis}. Ordonne par recent
     * d'abord pour enrichir le lead le plus jeune en cas de doublons multiples.
     *
     * <p>Le filtre {@code archive = false} est essentiel : sans lui, un nouvel email
     * pour un contact ARCHIVE enrichissait silencieusement le lead archive (invisible
     * dans les listes) -> demande perdue + envoi auto non arme. Un contact archive ne
     * bloque donc plus la creation d'un lead frais.</p>
     */
    @Query("""
            SELECT l FROM Lead l
            WHERE l.tenantId = :tenantId
            AND l.telephone = :telephone
            AND l.archive = false
            AND l.creeLe >= :depuis
            ORDER BY l.creeLe DESC
            """)
    List<Lead> findByTenantIdAndTelephoneAndCreeLeSince(@Param("tenantId") Long tenantId,
                                                         @Param("telephone") String telephone,
                                                         @Param("depuis") LocalDateTime depuis);

    /**
     * Deduplication import email (fallback si pas de telephone) : cherche par email
     * parmi les leads ACTIFS (non archives). Meme raison que la variante telephone :
     * un contact archive ne doit pas absorber un nouvel email.
     */
    @Query("""
            SELECT l FROM Lead l
            WHERE l.tenantId = :tenantId
            AND l.email = :email
            AND l.archive = false
            AND l.creeLe >= :depuis
            ORDER BY l.creeLe DESC
            """)
    List<Lead> findByTenantIdAndEmailAndCreeLeSince(@Param("tenantId") Long tenantId,
                                                     @Param("email") String email,
                                                     @Param("depuis") LocalDateTime depuis);

    /**
     * Garde-fou RGPD (opt-out preserve par numero) : indique si un lead du tenant,
     * ARCHIVE OU NON (pas de filtre archive volontaire), avec ce telephone, est
     * desinscrit des SMS/WhatsApp. Sert a re-propager l'opt-out lors d'un re-import
     * email : un prospect qui s'etait desinscrit puis re-soumet ne doit jamais etre
     * recontacte automatiquement.
     */
    boolean existsByTenantIdAndTelephoneAndOptOutSmsTrue(Long tenantId, String telephone);

    /**
     * Stats sources : agrege les leads par source sur une periode. Retourne
     * [source, nbLeads, nbRdv]. nbRdv compte les leads ayant un RDV PRIS facturable :
     * un evenement immuable et horodate dans historique_statut (transition vers
     * RDV_PRIS de source 'IA' = RDV confirme+date par le prospect via l'IA). Meme
     * notion que PipelineStatsService (source unique), non-manipulable et immuable
     * (≠ snapshot conversation RDV_OBTENU qui pouvait changer apres coup). EXISTS
     * pour ne compter chaque lead qu'une fois.
     */
    @Query(value = """
            SELECT l.source AS source,
                   COUNT(l.id_lead) AS nb_leads,
                   COUNT(CASE WHEN EXISTS (
                       SELECT 1 FROM historique_statut h
                       WHERE h.lead_id = l.id_lead
                         AND h.statut_suivant = 'RDV_PRIS'
                         AND h.source = 'IA'
                   ) THEN 1 END) AS nb_rdv
            FROM lead l
            WHERE l.tenant_id = :tenantId
              AND l.cree_le >= :depuis
              AND l.source IS NOT NULL
            GROUP BY l.source
            ORDER BY nb_leads DESC
            """, nativeQuery = true)
    List<Object[]> statsParSourceParTenant(@Param("tenantId") Long tenantId,
                                            @Param("depuis") LocalDateTime depuis);

    /**
     * Variante cross-tenant pour le super admin. Le tenantId optionnel filtre,
     * sinon agrege sur toute la plateforme. nbRdv = meme definition canonique du
     * RDV pris facturable que statsParSourceParTenant (historique_statut, transition
     * RDV_PRIS de source 'IA').
     */
    @Query(value = """
            SELECT l.source AS source,
                   COUNT(l.id_lead) AS nb_leads,
                   COUNT(CASE WHEN EXISTS (
                       SELECT 1 FROM historique_statut h
                       WHERE h.lead_id = l.id_lead
                         AND h.statut_suivant = 'RDV_PRIS'
                         AND h.source = 'IA'
                   ) THEN 1 END) AS nb_rdv
            FROM lead l
            WHERE l.cree_le >= :depuis
              AND l.source IS NOT NULL
              AND (:tenantId IS NULL OR l.tenant_id = :tenantId)
            GROUP BY l.source
            ORDER BY nb_leads DESC
            """, nativeQuery = true)
    List<Object[]> statsParSourceGlobal(@Param("tenantId") Long tenantId,
                                         @Param("depuis") LocalDateTime depuis);

    @Query("""
            SELECT l.statut, COUNT(l) FROM Lead l
            WHERE l.tenantId = :tenantId
            AND l.archive = false
            GROUP BY l.statut
            """)
    List<Object[]> countByTenantIdGroupByStatut(@Param("tenantId") Long tenantId);

    @Query("""
            SELECT l.territoire, COUNT(l) FROM Lead l
            WHERE l.tenantId = :tenantId
            AND l.archive = false
            GROUP BY l.territoire
            """)
    List<Object[]> countByTenantIdGroupByTerritoire(@Param("tenantId") Long tenantId);

    @Query("""
            SELECT l.marque, COUNT(l) FROM Lead l
            WHERE l.tenantId = :tenantId
            AND l.archive = false
            GROUP BY l.marque
            """)
    List<Object[]> countByTenantIdGroupByMarque(@Param("tenantId") Long tenantId);

    @Query("""
            SELECT COUNT(l) FROM Lead l
            WHERE l.tenantId = :tenantId
            AND l.creeLe >= :depuis
            """)
    Long countByTenantIdDepuis(@Param("tenantId") Long tenantId, @Param("depuis") LocalDateTime depuis);

    @Query(value = """
            SELECT COUNT(l.id_lead) FROM lead l
            WHERE l.tenant_id = :tenantId
            AND l.archive = false
            AND l.cree_le >= :depuis
            AND l.id_lead NOT IN (
                SELECT DISTINCT c.lead_id FROM sms_conversation c
                WHERE c.tenant_id = :tenantId
            )
            """, nativeQuery = true)
    Long countNonContactesDepuis(@Param("tenantId") Long tenantId, @Param("depuis") LocalDateTime depuis);

    @Query(value = """
            SELECT DATE(l.cree_le) as jour, COUNT(*)
            FROM lead l
            WHERE l.tenant_id = :tenantId
            AND l.cree_le >= :depuis
            GROUP BY DATE(l.cree_le)
            ORDER BY jour
            """, nativeQuery = true)
    List<Object[]> countParJourDepuis(@Param("tenantId") Long tenantId, @Param("depuis") LocalDateTime depuis);

    @Query(value = """
            SELECT TO_CHAR(l.cree_le, 'YYYY-MM') as mois, COUNT(*)
            FROM lead l
            WHERE l.tenant_id = :tenantId
            AND l.cree_le >= :depuis
            GROUP BY TO_CHAR(l.cree_le, 'YYYY-MM')
            """, nativeQuery = true)
    List<Object[]> countParMoisDepuis(@Param("tenantId") Long tenantId, @Param("depuis") LocalDateTime depuis);

    @Query("""
            SELECT COUNT(l) FROM Lead l
            WHERE l.creeLe >= :depuis
            """)
    Long countGlobalDepuis(@Param("depuis") LocalDateTime depuis);

    /**
     * Met a jour UNIQUEMENT le cache de fiche de preparation IA (colonnes de confort, non
     * metier), via un UPDATE cible qui ne bump PAS le {@code @Version} du lead.
     *
     * <p><strong>Pourquoi</strong> : {@code genererFiche} tient le lead charge pendant l'appel
     * IA (~10s) puis le sauvait via {@code save(lead)}. Une mutation concurrente du meme lead
     * (webhook prospect -> statut/hotness, drag&drop Kanban) bumpait le {@code @Version} pendant
     * ces 10s et faisait echouer le save final sur conflit optimiste -> fiche perdue (meme
     * famille de bug que l'incident WhatsApp 2026-07-06). Un UPDATE cible n'entre plus en
     * conflit avec le traitement metier concurrent.</p>
     */
    @Modifying
    @Query("""
            UPDATE Lead l
            SET l.fichePreparation = :fiche, l.ficheGenereeLe = :genereeLe, l.ficheHash = :hash
            WHERE l.id = :leadId
            """)
    int updateCacheFiche(@Param("leadId") Long leadId,
                         @Param("fiche") String fiche,
                         @Param("genereeLe") LocalDateTime genereeLe,
                         @Param("hash") String hash);

    /**
     * Scheduler PremierMessageAutoJob (CROSS-TENANT) : leads armes dont l'echeance est
     * passee et encore au statut NOUVEAU (pas encore contactes). Execute hors requete
     * HTTP, donc le filtre Hibernate tenantFilter n'est pas actif -> balaye tous les tenants.
     */
    @Query("""
            SELECT l FROM Lead l
            WHERE l.premierMessageAutoPrevuLe IS NOT NULL
            AND l.premierMessageAutoPrevuLe <= :maintenant
            AND l.statut = fr.leadrevive.lead.entity.LeadStatutEnum.NOUVEAU
            AND l.archive = false
            ORDER BY l.premierMessageAutoPrevuLe ASC
            """)
    List<Lead> findEligiblesPremierMessageAuto(@Param("maintenant") LocalDateTime maintenant);

    /**
     * Dashboard "Messages auto a venir" : leads d'un tenant avec un envoi auto en attente,
     * encore au statut NOUVEAU, tries par echeance. Liste legere (id, nom, echeance).
     *
     * <p>Scope vendeur : {@code vendeurId} null (MANAGER) = tout le tenant ; non nul (VENDEUR)
     * = seulement SES leads. Sans ce filtre, ActionCenter (monte dans le dashboard VENDEUR)
     * exposait les prospects des autres vendeurs (IDOR / fuite PII).</p>
     */
    @Query("""
            SELECT l FROM Lead l
            WHERE l.tenantId = :tenantId
            AND l.premierMessageAutoPrevuLe IS NOT NULL
            AND l.statut = fr.leadrevive.lead.entity.LeadStatutEnum.NOUVEAU
            AND l.archive = false
            AND (:vendeurId IS NULL OR l.vendeurId = :vendeurId)
            ORDER BY l.premierMessageAutoPrevuLe ASC
            """)
    List<Lead> findEnvoisAutoAVenir(@Param("tenantId") Long tenantId, @Param("vendeurId") Long vendeurId);
}
