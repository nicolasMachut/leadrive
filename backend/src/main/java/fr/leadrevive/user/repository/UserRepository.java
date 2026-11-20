package fr.leadrevive.user.repository;

import fr.leadrevive.user.entity.RoleEnum;
import fr.leadrevive.user.entity.StatutUtilisateurEnum;
import fr.leadrevive.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByClerkUserId(String clerkUserId);

    List<User> findByTenantId(Long tenantId);

    @Query("""
            SELECT u FROM User u
            WHERE u.tenantId = :tenantId
            AND u.role = :role
            AND u.actif = true
            """)
    List<User> findByTenantIdAndRole(@Param("tenantId") Long tenantId,
                                      @Param("role") RoleEnum role);

    List<User> findAllByRoleNot(RoleEnum role);

    long countByStatut(StatutUtilisateurEnum statut);

    long countByTenantId(Long tenantId);

    @Query("""
            SELECT u FROM User u
            WHERE (:tenantId IS NULL OR u.tenantId = :tenantId)
            AND (:role IS NULL OR u.role = :role)
            AND u.role <> 'SUPER_ADMIN'
            """)
    List<User> rechercherUtilisateurs(@Param("tenantId") Long tenantId,
                                       @Param("role") RoleEnum role);
}
