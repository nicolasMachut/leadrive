package fr.leadrevive.csvimport.repository;

import fr.leadrevive.csvimport.entity.MappingTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MappingTemplateRepository extends JpaRepository<MappingTemplate, Long> {

    List<MappingTemplate> findByTenantIdOrderByDerniereUtilisationDesc(Long tenantId);
}
