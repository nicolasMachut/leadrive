package fr.leadrevive.emailimport.repository;

import fr.leadrevive.emailimport.entity.TenantEmailConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TenantEmailConfigRepository extends JpaRepository<TenantEmailConfig, Long> {

    Optional<TenantEmailConfig> findByTenantId(Long tenantId);

    /**
     * Lookup global (cross-tenant) : utilise par le webhook SendGrid pour
     * router un email vers le bon tenant. La cle email_address est UNIQUE
     * au niveau plateforme. Appel sans TenantContext, le filtre Hibernate
     * doit etre desactive cote service.
     */
    Optional<TenantEmailConfig> findByEmailAddress(String emailAddress);

    boolean existsByEmailAddress(String emailAddress);

    /**
     * Heartbeat : configs ACTIVE ayant deja recu au moins un email et dont
     * la derniere reception est plus ancienne que {@code seuil}. Cross-tenant.
     */
    @Query("""
            SELECT c FROM TenantEmailConfig c
            WHERE c.statut = 'ACTIVE'
              AND c.nbEmailsRecus > 0
              AND c.dernierEmailRecuLe IS NOT NULL
              AND c.dernierEmailRecuLe < :seuil
            """)
    List<TenantEmailConfig> findHeartbeatPerdu(@Param("seuil") LocalDateTime seuil);
}
