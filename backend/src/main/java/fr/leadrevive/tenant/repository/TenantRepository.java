package fr.leadrevive.tenant.repository;

import fr.leadrevive.tenant.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByNom(String nom);

    Optional<Tenant> findByClerkOrganizationId(String clerkOrganizationId);
}
