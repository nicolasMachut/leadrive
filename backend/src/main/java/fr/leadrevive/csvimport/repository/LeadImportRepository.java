package fr.leadrevive.csvimport.repository;

import fr.leadrevive.csvimport.entity.LeadImport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeadImportRepository extends JpaRepository<LeadImport, Long> {

    /**
     * Plafond defensif (200 imports les plus recents) pour empecher l'historique
     * d'exploser apres 1-2 ans d'utilisation : 1 import/jour x 365 = 365 lignes,
     * la liste est rendue en RAM cote front sans pagination. 200 couvre largement
     * les besoins d'audit; au-dela il faudra paginer.
     */
    List<LeadImport> findTop200ByTenantIdOrderByDateImportDesc(Long tenantId);

    Optional<LeadImport> findByIdAndTenantId(Long id, Long tenantId);
}
