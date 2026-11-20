package fr.leadrevive.integration;

import fr.leadrevive.common.security.ClerkJwtConverter;
import fr.leadrevive.common.security.ClerkJwtConverter.ClerkAuthDetails;
import fr.leadrevive.common.tenant.TenantContext;
import fr.leadrevive.user.entity.RoleEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'integration de l'auth Clerk JWT.
 * Verifie le comportement de bout en bout :
 * - 401 sans token sur endpoint protege
 * - le ClerkJwtConverter resout correctement userId/tenantId/role depuis le JWT
 * - les @PreAuthorize fonctionnent avec les bonnes authorities
 */
class ClerkJwtAuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ClerkJwtConverter clerkJwtConverter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private Long tenantId;
    private Long userId;
    private String clerkUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        // Nettoyage (respecter l'ordre des FK : inbound_email_log → tenant_email_config →
        // lead → lead_import → utilisateur → tenant)
        jdbcTemplate.update("DELETE FROM inbound_email_log");
        jdbcTemplate.update("DELETE FROM tenant_email_config");
        jdbcTemplate.update("DELETE FROM lead");
        jdbcTemplate.update("DELETE FROM lead_import");
        jdbcTemplate.update("DELETE FROM utilisateur");
        jdbcTemplate.update("DELETE FROM tenant");

        tenantId = jdbcTemplate.queryForObject(
                "INSERT INTO tenant (nom) VALUES ('Concession Auth Test') RETURNING id_tenant", Long.class);

        clerkUserId = "user_clerk_" + System.currentTimeMillis();
        userId = jdbcTemplate.queryForObject(
                """
                INSERT INTO utilisateur (tenant_id, clerk_user_id, email, nom, prenom, role, est_actif, statut)
                VALUES (?, ?, 'manager@test.fr', 'Manager', 'Test', 'MANAGER', true, 'ACTIF')
                RETURNING id_utilisateur
                """, Long.class, tenantId, clerkUserId);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    // ===== Endpoint protege sans auth → 401 =====

    @Test
    void getLeads_sansAuthentification_retourne401() throws Exception {
        // GIVEN / WHEN / THEN
        mockMvc.perform(get("/api/leads"))
                .andExpect(status().isUnauthorized());
    }

    // ===== Endpoint protege avec JWT mocke + bonne authority → 200 =====

    @Test
    void getLeads_jwtAvecRoleManager_retourne200() throws Exception {
        // GIVEN
        // Le post-processor jwt() bypass le ClerkJwtConverter et injecte un token
        // pre-cuit : on doit donc seeder TenantContext manuellement pour qu'AuthService trouve un tenant.
        TenantContext.setCurrentTenantId(tenantId);

        // WHEN / THEN
        mockMvc.perform(get("/api/leads")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_MANAGER"))))
                .andExpect(status().isOk());
    }

    @Test
    void getLeads_jwtAvecRoleVendeur_retourne200() throws Exception {
        // GIVEN
        TenantContext.setCurrentTenantId(tenantId);

        // WHEN / THEN
        mockMvc.perform(get("/api/leads")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_VENDEUR"))))
                .andExpect(status().isOk());
    }

    // ===== @PreAuthorize bloque les mauvais roles =====

    @Test
    void getStats_jwtAvecRoleVendeur_retourne403() throws Exception {
        // GIVEN
        // /api/leads/stats requiert MANAGER ou SUPER_ADMIN
        TenantContext.setCurrentTenantId(tenantId);

        // WHEN / THEN
        mockMvc.perform(get("/api/leads/stats")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_VENDEUR"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteLead_jwtAvecRoleVendeur_leadInexistant_retourne404() throws Exception {
        // GIVEN
        // DELETE /api/leads/{id} est desormais ouvert au VENDEUR au niveau du role
        // (hasAnyRole MANAGER, VENDEUR) ; l'ownership est controlee par LeadAccessGuard,
        // qui renvoie 404 (anonyme) sur un lead inexistant ou non possede, jamais 403.
        TenantContext.setCurrentTenantId(tenantId);

        // WHEN / THEN
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/leads/999999")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_VENDEUR"))))
                .andExpect(status().isNotFound());
    }

    // ===== ClerkJwtConverter — resolution depuis le JWT =====

    @Test
    void clerkJwtConverter_orgRoleManager_extraitTenantIdEtRoleManager() {
        // GIVEN
        final Jwt jwt = jwtBuilder(clerkUserId)
                .claim("org_role", "org:manager")
                .build();

        // WHEN
        final AbstractAuthenticationToken token = clerkJwtConverter.convert(jwt);

        // THEN
        final ClerkAuthDetails details = (ClerkAuthDetails) token.getDetails();
        assertAll(
                () -> assertNotNull(details),
                () -> assertEquals(userId, details.userId()),
                () -> assertEquals(tenantId, details.tenantId()),
                () -> assertEquals(RoleEnum.MANAGER, details.role()),
                () -> assertEquals(tenantId, TenantContext.getCurrentTenantId(),
                        "Le converter doit avoir initialise TenantContext")
        );
    }

    @Test
    void clerkJwtConverter_publicMetadataIsSuperAdmin_extraitRoleSuperAdmin() {
        // GIVEN
        final Jwt jwt = jwtBuilder(clerkUserId)
                .claim("public_metadata", Map.of("isSuperAdmin", true))
                .build();

        // WHEN
        final AbstractAuthenticationToken token = clerkJwtConverter.convert(jwt);

        // THEN
        final ClerkAuthDetails details = (ClerkAuthDetails) token.getDetails();
        assertAll(
                () -> assertEquals(RoleEnum.SUPER_ADMIN, details.role()),
                // SUPER_ADMIN n'est pas rattache a un tenant
                () -> assertNull(details.tenantId(), "Un SUPER_ADMIN n'est pas rattache a un tenant")
        );
    }

    @Test
    void clerkJwtConverter_userInconnuSansEmail_throwDisabledException() {
        // GIVEN : JWT pour un user qui n'existe pas en BDD (mirror supprime,
        // tenant desactive, ou clerkUserId obsolete apres suppression d'org).
        final Jwt jwt = jwtBuilder("user_inexistant_xyz")
                .claim("org_role", "org:vendeur")
                .build();

        // WHEN / THEN : la conversion doit refuser l'auth (-> 401 cote frontend
        // qui declenche signOut + redirect /login). Avant ce fix, on retournait
        // un token avec userId=null/tenantId=null et le user naviguait dans une
        // coquille vide (404 / listes vides partout).
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.security.authentication.DisabledException.class,
                () -> clerkJwtConverter.convert(jwt)
        );
    }

    @Test
    void clerkJwtConverter_userTrouveParEmail_lieLeClerkUserIdEnBdd() {
        // GIVEN
        // Cree un user SANS clerk_user_id (cas premier login post-invitation)
        final String emailInvite = "invite@test.fr";
        jdbcTemplate.update("""
                INSERT INTO utilisateur (tenant_id, email, nom, prenom, role, est_actif, statut)
                VALUES (?, ?, 'Invite', 'Test', 'VENDEUR', true, 'INVITE')
                """, tenantId, emailInvite);

        final String nouveauClerkUserId = "user_clerk_premier_login";
        final Jwt jwt = jwtBuilder(nouveauClerkUserId)
                .claim("org_role", "org:vendeur")
                .claim("email", emailInvite)
                .build();

        // WHEN
        clerkJwtConverter.convert(jwt);

        // THEN
        // L'utilisateur invite a maintenant son clerk_user_id et statut ACTIF
        final Map<String, Object> userBdd = jdbcTemplate.queryForMap(
                "SELECT clerk_user_id, statut FROM utilisateur WHERE email = ?", emailInvite);
        assertAll(
                () -> assertEquals(nouveauClerkUserId, userBdd.get("clerk_user_id")),
                () -> assertEquals("ACTIF", userBdd.get("statut"))
        );
    }

    private Jwt.Builder jwtBuilder(final String subject) {
        final Map<String, Object> headers = new HashMap<>();
        headers.put("alg", "RS256");
        return Jwt.withTokenValue("token-test")
                .headers(h -> h.putAll(headers))
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));
    }
}
