package fr.leadrevive.ai.repository;

import fr.leadrevive.ai.entity.TenantAiConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantAiConfigRepository extends JpaRepository<TenantAiConfig, Long> {

    Optional<TenantAiConfig> findByTenantId(Long tenantId);
}
