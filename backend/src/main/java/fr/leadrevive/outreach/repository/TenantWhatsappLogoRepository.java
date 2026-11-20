package fr.leadrevive.outreach.repository;

import fr.leadrevive.outreach.entity.TenantWhatsappLogo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantWhatsappLogoRepository extends JpaRepository<TenantWhatsappLogo, Long> {
}
