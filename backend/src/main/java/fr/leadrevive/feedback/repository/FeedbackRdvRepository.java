package fr.leadrevive.feedback.repository;

import fr.leadrevive.feedback.entity.FeedbackRdv;
import fr.leadrevive.feedback.entity.QualiteQualificationEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FeedbackRdvRepository extends JpaRepository<FeedbackRdv, Long> {

    List<FeedbackRdv> findByTenantId(Long tenantId);

    Optional<FeedbackRdv> findByRdvIdAndTenantId(Long rdvId, Long tenantId);

    /** Supprime le(s) feedback(s) lie(s) a un RDV (undo d'une action Effectue/Abandonner). */
    long deleteByRdvIdAndTenantId(Long rdvId, Long tenantId);

    /**
     * Detection des doublons : un meme lead ne devrait avoir qu'UN seul feedback RDV
     * cloturant. Si le vendeur reclique sur le slide-up apres avoir deja note, on doit
     * refuser et lui afficher le 1er feedback (modifiable si besoin).
     */
    Optional<FeedbackRdv> findFirstByLeadIdAndTenantIdAndQualiteAppelIsNotNullOrderByCreeLe(
            Long leadId, Long tenantId);

    /**
     * Anti-doublon d'abandon : un meme lead ne devrait avoir qu'UN seul feedback
     * d'abandon (raison renseignee). Sert a rendre {@code enregistrerAbandon}
     * idempotent (double-clic, deux onglets, retry reseau) et a ne pas gonfler
     * les stats d'abandon avec des doublons.
     */
    Optional<FeedbackRdv> findFirstByLeadIdAndTenantIdAndRaisonAbandonIsNotNullOrderByCreeLe(
            Long leadId, Long tenantId);

    long countByTenantIdAndQualiteAppelIn(Long tenantId, List<QualiteQualificationEnum> qualites);

    long countByTenantIdAndQualiteAppelIsNotNull(Long tenantId);

    long countByTenantIdAndQualiteAppel(Long tenantId, QualiteQualificationEnum qualite);

    long countByTenantIdAndSuitePrevue(Long tenantId, fr.leadrevive.feedback.entity.SuitePrevueEnum suite);

    long countByTenantIdAndSuitePrevueIsNotNull(Long tenantId);

    @Query("""
            SELECT f FROM FeedbackRdv f
            WHERE f.tenantId = :tenantId
              AND f.vendeurId = :vendeurId
              AND f.qualiteAppel = fr.leadrevive.feedback.entity.QualiteQualificationEnum.RATE
              AND f.impactNotifie = false
              AND f.creeLe <= :seuilDate
            ORDER BY f.creeLe ASC
            """)
    List<FeedbackRdv> findImpactsEnAttente(
            @Param("tenantId") Long tenantId,
            @Param("vendeurId") Long vendeurId,
            @Param("seuilDate") LocalDateTime seuilDate);
}
