package fr.leadrevive.common.demo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DemandeDemoRepository extends JpaRepository<DemandeDemo, Long> {

    /**
     * Plafond defensif (500 demandes les plus anciennes). Endpoint admin
     * non pagine cote front. A 10 demandes/jour, 500 = 50 jours d'historique
     * affiches en une page. Au-dela il faudra paginer (changement UX).
     */
    List<DemandeDemo> findTop500ByOrderByCreeLe();

    /** Anti-doublon : un meme email ne peut avoir qu'une demande au statut NOUVEAU. */
    boolean existsByEmailAndStatut(String email, String statut);
}
