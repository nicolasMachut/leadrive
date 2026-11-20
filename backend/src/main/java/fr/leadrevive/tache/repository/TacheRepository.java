package fr.leadrevive.tache.repository;

import fr.leadrevive.tache.entity.StatutTacheEnum;
import fr.leadrevive.tache.entity.Tache;
import fr.leadrevive.tache.entity.TypeTacheEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TacheRepository extends JpaRepository<Tache, Long> {

    List<Tache> findByTenantIdAndVendeurIdAndDateEcheanceBetweenOrderByDateEcheanceAsc(
            Long tenantId, Long vendeurId, LocalDateTime debut, LocalDateTime fin);

    List<Tache> findByTenantIdAndDateEcheanceBetweenOrderByDateEcheanceAsc(
            Long tenantId, LocalDateTime debut, LocalDateTime fin);

    List<Tache> findByTenantIdAndVendeurIdAndStatutAndDateEcheanceBeforeOrderByDateEcheanceAsc(
            Long tenantId, Long vendeurId, StatutTacheEnum statut, LocalDateTime avant);

    List<Tache> findByTenantIdAndStatutAndDateEcheanceBeforeOrderByDateEcheanceAsc(
            Long tenantId, StatutTacheEnum statut, LocalDateTime avant);

    List<Tache> findByLeadIdOrderByDateEcheanceDesc(Long leadId);

    /**
     * Toutes les taches d'un vendeur dont le statut fait partie de la liste.
     * Utilise pour la reassignation lors de la desactivation d'un vendeur :
     * on cherche les statuts "non termines" (A_FAIRE, EN_COURS) pour les
     * transferer au manager.
     */
    List<Tache> findByTenantIdAndVendeurIdAndStatutIn(Long tenantId, Long vendeurId,
                                                       java.util.Collection<StatutTacheEnum> statuts);

    boolean existsByLeadIdAndTypeTacheAndStatut(Long leadId, TypeTacheEnum typeTache, StatutTacheEnum statut);

    java.util.Optional<Tache> findFirstByLeadIdAndTypeTacheAndStatutOrderByCreeLe(
            Long leadId, TypeTacheEnum typeTache, StatutTacheEnum statut);

    List<Tache> findByLeadIdAndTypeTacheAndStatutOrderByCreeLe(
            Long leadId, TypeTacheEnum typeTache, StatutTacheEnum statut);

    /**
     * Charge en batch les taches RDV a venir pour une liste de leads. Utilise
     * par le Kanban pour trier la colonne RDV_PRIS par date du prochain RDV
     * (plus proche en haut) plutot que par date de prise. Evite un N+1.
     */
    List<Tache> findByLeadIdInAndTypeTacheAndStatutInAndDateEcheanceAfterOrderByDateEcheanceAsc(
            List<Long> leadIds, TypeTacheEnum typeTache, List<StatutTacheEnum> statuts, LocalDateTime apres);
}
