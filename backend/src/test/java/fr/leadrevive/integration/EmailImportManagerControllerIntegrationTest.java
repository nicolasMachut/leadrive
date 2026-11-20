package fr.leadrevive.integration;

import fr.leadrevive.common.tenant.TenantContext;
import fr.leadrevive.emailimport.entity.StatutInboundEmail;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'integration des endpoints manager email import (epic 24).
 *
 * <p>Verifie que le manager voit uniquement la config et les logs de son
 * propre tenant (filtre Hibernate {@code tenantFilter} actif).</p>
 */
class EmailImportManagerControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    private MockMvc mockMvc;
    private Long tenantA;
    private Long tenantB;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        jdbcTemplate.update("DELETE FROM inbound_email_log");
        jdbcTemplate.update("DELETE FROM tenant_email_config");
        jdbcTemplate.update("DELETE FROM lead");
        jdbcTemplate.update("DELETE FROM lead_import");
        jdbcTemplate.update("DELETE FROM utilisateur");
        jdbcTemplate.update("DELETE FROM tenant");

        tenantA = jdbcTemplate.queryForObject(
                "INSERT INTO tenant (nom) VALUES ('Garage A') RETURNING id_tenant", Long.class);
        tenantB = jdbcTemplate.queryForObject(
                "INSERT INTO tenant (nom) VALUES ('Garage B') RETURNING id_tenant", Long.class);

        // Config email pour les deux tenants
        jdbcTemplate.update(
                "INSERT INTO tenant_email_config (tenant_id, email_slug, email_address, statut, nb_emails_recus) "
                        + "VALUES (?, ?, ?, ?, ?)",
                tenantA, "garage-a", "garage-a-aaa1@leads.leadrive.fr", "ACTIVE", 5);
        jdbcTemplate.update(
                "INSERT INTO tenant_email_config (tenant_id, email_slug, email_address, statut, nb_emails_recus) "
                        + "VALUES (?, ?, ?, ?, ?)",
                tenantB, "garage-b", "garage-b-bbb1@leads.leadrive.fr", "ACTIVE", 99);

        // Logs : 3 pour A, 2 pour B
        insererLog(tenantA, StatutInboundEmail.LEAD_CREE, "LEBONCOIN");
        insererLog(tenantA, StatutInboundEmail.LEAD_CREE, "LACENTRALE");
        insererLog(tenantA, StatutInboundEmail.REJETE_SPAM, null);
        insererLog(tenantB, StatutInboundEmail.LEAD_CREE, "LEBONCOIN");
        insererLog(tenantB, StatutInboundEmail.PARSE_ECHEC, null);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        entityManager.unwrap(Session.class).disableFilter("tenantFilter");
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor jwtManager() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_MANAGER"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor jwtVendeur() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_VENDEUR"));
    }

    // ===== GET /api/parametres/email-import =====

    @Test
    void getConfig_managerDuTenantA_retourneSaConfig() throws Exception {
        TenantContext.setCurrentTenantId(tenantA);

        mockMvc.perform(get("/api/parametres/email-import").with(jwtManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailAddress").value("garage-a-aaa1@leads.leadrive.fr"))
                .andExpect(jsonPath("$.nbEmailsRecus").value(5))
                .andExpect(jsonPath("$.tenantId").value(tenantA));
    }

    @Test
    void getConfig_managerDuTenantB_retourneSaConfig_pasCelleDeA() throws Exception {
        TenantContext.setCurrentTenantId(tenantB);

        mockMvc.perform(get("/api/parametres/email-import").with(jwtManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailAddress").value("garage-b-bbb1@leads.leadrive.fr"))
                .andExpect(jsonPath("$.nbEmailsRecus").value(99))
                .andExpect(jsonPath("$.tenantId").value(tenantB));
    }

    @Test
    void getConfig_managerDunTenantSansConfig_retourne404() throws Exception {
        // GIVEN : nouveau tenant sans config email
        final Long tenantSansConfig = jdbcTemplate.queryForObject(
                "INSERT INTO tenant (nom) VALUES ('Sans Config') RETURNING id_tenant", Long.class);
        TenantContext.setCurrentTenantId(tenantSansConfig);

        mockMvc.perform(get("/api/parametres/email-import").with(jwtManager()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getConfig_vendeurInterdit_retourne403() throws Exception {
        TenantContext.setCurrentTenantId(tenantA);
        mockMvc.perform(get("/api/parametres/email-import").with(jwtVendeur()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getConfig_sansJWT_retourne401() throws Exception {
        mockMvc.perform(get("/api/parametres/email-import"))
                .andExpect(status().isUnauthorized());
    }

    // ===== GET /api/parametres/email-import/logs =====

    @Test
    void listerLogs_managerDuTenantA_voitSeulementSes3Logs() throws Exception {
        TenantContext.setCurrentTenantId(tenantA);

        mockMvc.perform(get("/api/parametres/email-import/logs").with(jwtManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void listerLogs_managerDuTenantB_voitSeulementSes2Logs_IsolationStricte() throws Exception {
        TenantContext.setCurrentTenantId(tenantB);

        mockMvc.perform(get("/api/parametres/email-import/logs").with(jwtManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void listerLogs_vendeurInterdit_retourne403() throws Exception {
        TenantContext.setCurrentTenantId(tenantA);
        mockMvc.perform(get("/api/parametres/email-import/logs").with(jwtVendeur()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listerLogs_sansJWT_retourne401() throws Exception {
        mockMvc.perform(get("/api/parametres/email-import/logs"))
                .andExpect(status().isUnauthorized());
    }

    private void insererLog(final Long tenantId, final StatutInboundEmail statut, final String source) {
        jdbcTemplate.update("""
                INSERT INTO inbound_email_log (tenant_id, email_from, email_subject, statut, source_detectee)
                VALUES (?, 'from@test.com', 'Sujet', ?, ?)
                """, tenantId, statut.name(), source);
    }
}
