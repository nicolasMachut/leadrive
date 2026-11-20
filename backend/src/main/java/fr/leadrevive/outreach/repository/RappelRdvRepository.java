package fr.leadrevive.outreach.repository;

import fr.leadrevive.outreach.entity.RappelRdv;
import fr.leadrevive.outreach.entity.TypeRappelEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RappelRdvRepository extends JpaRepository<RappelRdv, Long> {

    List<RappelRdv> findByEnvoyeFalseAndEnvoiPrevuAtBefore(LocalDateTime maintenant);

    List<RappelRdv> findByLeadIdAndEnvoyeFalse(Long leadId);

    /**
     * Compte les rappels prevus dans une fenetre temporelle (filtre par tenant via Hibernate).
     * Utilise pour le calcul des impacts d'un changement de numero WhatsApp.
     */
    long countByEnvoyeFalseAndEnvoiPrevuAtBetween(LocalDateTime debut, LocalDateTime fin);

    Optional<RappelRdv> findFirstByTacheIdAndTypeRappelAndEnvoyeFalseOrderByIdAsc(Long tacheId, TypeRappelEnum typeRappel);

    void deleteByTacheId(Long tacheId);
}
