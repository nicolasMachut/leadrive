package fr.leadrevive.ai.repository;

import fr.leadrevive.ai.entity.BriefingQuotidien;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface BriefingQuotidienRepository extends JpaRepository<BriefingQuotidien, Long> {

    Optional<BriefingQuotidien> findByTenantIdAndDateBriefingAndUserIdIsNull(Long tenantId, LocalDate dateBriefing);

    Optional<BriefingQuotidien> findByTenantIdAndDateBriefingAndUserId(Long tenantId, LocalDate dateBriefing, Long userId);

    void deleteByTenantIdAndDateBriefing(Long tenantId, LocalDate dateBriefing);

    void deleteByTenantIdAndDateBriefingAndUserIdIsNull(Long tenantId, LocalDate dateBriefing);

    void deleteByTenantIdAndDateBriefingAndUserId(Long tenantId, LocalDate dateBriefing, Long userId);
}
