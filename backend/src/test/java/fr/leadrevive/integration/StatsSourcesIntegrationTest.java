package fr.leadrevive.integration;

import fr.leadrevive.common.tenant.TenantContext;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'integration du tableau de bord des sources (epic 24).
 *
 * <p>Verifie :
 * <ul>
 *   <li>L'agregation correcte par source (nombre de leads + RDV)</li>
 *   <li>Le calcul du taux de conversion (= nbRdv / nbLeads * 100)</li>
 *   <li>L'isolation tenant : un manager ne voit pas les sources d'un autre tenant</li>
 *   <li>La detection RDV via l'evenement canonique {@code historique_statut} (transition IA vers RDV_PRIS)</li>
 *   <li>La periode glissante (parametre {@code periode})</li>
 * </ul>
 * </p>
 */
class StatsSourcesIntegrationTest extends AbstractIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    private MockMvc mockMvc;
    private Long tenantA;
    private Long tenantB;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        jdbcTemplate.update("DELETE FROM sms_message");
        jdbcTemplate.update("DELETE FROM sms_conversation");
        jdbcTemplate.update("DELETE FROM lead");
        jdbcTemplate.update("DELETE FROM lead_import");
        jdbcTemplate.update("DELETE FROM tenant_email_config");
        jdbcTemplate.update("DELETE FROM inbound_email_log");
        jdbcTemplate.update("DELETE FROM utilisateur");
        jdbcTemplate.update("DELETE FROM tenant");

        tenantA = jdbcTemplate.queryForObject(
                "INSERT INTO tenant (nom) VALUES ('Garage A') RETURNING id_tenant", Long.class);
        tenantB = jdbcTemplate.queryForObject(
                "INSERT INTO tenant (nom) VALUES ('Garage B') RETURNING id_tenant", Long.class);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        entityManager.unwrap(Session.class).disableFilter("tenantFilter");
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor jwtManager() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_MANAGER"));
    }

    // ===== GET /api/stats/sources (manager) =====

    @Test
    void statsSources_managerTenantA_aggregeUniquementSesLeads() throws Exception {
        TenantContext.setCurrentTenantId(tenantA);

        // GIVEN : 5 leads sur tenantA (3 LeBonCoin, 2 LaCentrale), 4 leads sur tenantB
        creerLead(tenantA, "LEBONCOIN");
        creerLead(tenantA, "LEBONCOIN");
        creerLead(tenantA, "LEBONCOIN");
        creerLead(tenantA, "LACENTRALE");
        creerLead(tenantA, "LACENTRALE");
        creerLead(tenantB, "LEBONCOIN");
        creerLead(tenantB, "LEBONCOIN");
        creerLead(tenantB, "AUTOSCOUT24");
        creerLead(tenantB, "AUTOSCOUT24");

        // WHEN / THEN : le manager A doit voir SES 5 leads, repartis sur 2 sources
        mockMvc.perform(get("/api/stats/sources").with(jwtManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLeads").value(5))
                .andExpect(jsonPath("$.sources.length()").value(2))
                .andExpect(jsonPath("$.sources[0].source").value("LEBONCOIN"))
                .andExpect(jsonPath("$.sources[0].nbLeads").value(3))
                .andExpect(jsonPath("$.sources[1].source").value("LACENTRALE"))
                .andExpect(jsonPath("$.sources[1].nbLeads").value(2));
    }

    @Test
    void statsSources_isolation_managerANePeutPasVoirLesSourcesDeTenantB() throws Exception {
        TenantContext.setCurrentTenantId(tenantA);

        // GIVEN : uniquement des leads sur tenantB
        creerLead(tenantB, "LEBONCOIN");
        creerLead(tenantB, "LEBONCOIN");
        creerLead(tenantB, "LACENTRALE");

        // WHEN / THEN : manager A voit 0 lead (les sources de B sont invisibles)
        mockMvc.perform(get("/api/stats/sources").with(jwtManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLeads").value(0))
                .andExpect(jsonPath("$.sources.length()").value(0));
    }

    @Test
    void statsSources_calculTauxConversion_compteRdvObtenu() throws Exception {
        TenantContext.setCurrentTenantId(tenantA);

        // GIVEN : 3 leads LeBonCoin, dont 1 avec un RDV pris (evenement canonique IA->RDV_PRIS)
        final Long leadAvecRdv = creerLeadRetourneId(tenantA, "LEBONCOIN");
        creerLead(tenantA, "LEBONCOIN");
        creerLead(tenantA, "LEBONCOIN");
        creerRdvPrisIa(tenantA, leadAvecRdv);

        // WHEN / THEN : taux = 1/3 = 33.3%
        mockMvc.perform(get("/api/stats/sources").with(jwtManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources[0].source").value("LEBONCOIN"))
                .andExpect(jsonPath("$.sources[0].nbLeads").value(3))
                .andExpect(jsonPath("$.sources[0].nbRdv").value(1))
                .andExpect(jsonPath("$.sources[0].tauxConversion").value(33.3));
    }

    @Test
    void statsSources_rdvPrisManuel_nEstPasCompte() throws Exception {
        TenantContext.setCurrentTenantId(tenantA);

        // GIVEN : 2 leads LeBonCoin. L'un a un RDV IA (facturable), l'autre un passage
        // MANUEL en RDV_PRIS (drag&drop) qui ne doit PAS compter (non-manipulable).
        final Long leadIa = creerLeadRetourneId(tenantA, "LEBONCOIN");
        final Long leadManuel = creerLeadRetourneId(tenantA, "LEBONCOIN");
        creerRdvPrisIa(tenantA, leadIa);
        creerRdvPrisManuel(tenantA, leadManuel);

        // WHEN / THEN : seul le RDV IA compte → nbRdv = 1 (pas 2), taux = 1/2 = 50%
        mockMvc.perform(get("/api/stats/sources").with(jwtManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources[0].source").value("LEBONCOIN"))
                .andExpect(jsonPath("$.sources[0].nbLeads").value(2))
                .andExpect(jsonPath("$.sources[0].nbRdv").value(1))
                .andExpect(jsonPath("$.sources[0].tauxConversion").value(50.0));
    }

    @Test
    void statsSources_sansLeads_retourneListeVide() throws Exception {
        TenantContext.setCurrentTenantId(tenantA);

        mockMvc.perform(get("/api/stats/sources").with(jwtManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLeads").value(0))
                .andExpect(jsonPath("$.sources.length()").value(0));
    }

    @Test
    void statsSources_leadsSansSource_neSontPasComptes() throws Exception {
        TenantContext.setCurrentTenantId(tenantA);

        // GIVEN : 2 leads avec source + 3 leads SANS source (saisie manuelle)
        creerLead(tenantA, "LEBONCOIN");
        creerLead(tenantA, "LEBONCOIN");
        creerLead(tenantA, null);
        creerLead(tenantA, null);
        creerLead(tenantA, null);

        // WHEN / THEN : seuls les leads avec source sont aggreges
        mockMvc.perform(get("/api/stats/sources").with(jwtManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLeads").value(2));
    }

    @Test
    void statsSources_periodeCourte_excluLesLeadsAnciens() throws Exception {
        TenantContext.setCurrentTenantId(tenantA);

        // GIVEN : 2 leads recents (aujourd'hui) + 1 lead vieux de 60 jours
        creerLead(tenantA, "LEBONCOIN");
        creerLead(tenantA, "LEBONCOIN");
        creerLeadAvecJoursDecalage(tenantA, "LACENTRALE", 60);

        // Sanity check : verifier que le lead vieux a bien sa cree_le ancienne
        final java.sql.Timestamp creeLeVieux = jdbcTemplate.queryForObject(
                "SELECT cree_le FROM lead WHERE source = 'LACENTRALE'", java.sql.Timestamp.class);
        assertThat(creeLeVieux).isNotNull();
        assertThat(creeLeVieux.toLocalDateTime())
                .isBefore(java.time.LocalDateTime.now().minusDays(30));

        // WHEN / THEN : periode=30j exclu le lead vieux
        mockMvc.perform(get("/api/stats/sources")
                        .param("periode", "30")
                        .with(jwtManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLeads").value(2));

        // Le TenantFilter clear() le ThreadLocal apres chaque requete (anti-fuite
        // pool Tomcat). On doit re-injecter la context entre les deux appels.
        TenantContext.setCurrentTenantId(tenantA);

        // Periode=90j inclut tous les leads
        mockMvc.perform(get("/api/stats/sources")
                        .param("periode", "90")
                        .with(jwtManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLeads").value(3));
    }

    @Test
    void statsSources_sansJWT_retourne401() throws Exception {
        mockMvc.perform(get("/api/stats/sources"))
                .andExpect(status().isUnauthorized());
    }

    // ===== Helpers =====

    private void creerLead(final Long tenantId, final String source) {
        jdbcTemplate.update("""
                INSERT INTO lead (tenant_id, nom, source, statut, archive, client_irrite,
                                  mode_import, cree_le, modifie_le)
                VALUES (?, 'Lead Test', ?, 'NOUVEAU', false, false, 'IMPORT_CSV', NOW(), NOW())
                """, tenantId, source);
    }

    private Long creerLeadRetourneId(final Long tenantId, final String source) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO lead (tenant_id, nom, source, statut, archive, client_irrite,
                                  mode_import, cree_le, modifie_le)
                VALUES (?, 'Lead Test', ?, 'NOUVEAU', false, false, 'IMPORT_CSV', NOW(), NOW())
                RETURNING id_lead
                """, Long.class, tenantId, source);
    }

    private void creerLeadAvecJoursDecalage(final Long tenantId, final String source, final int joursAvant) {
        final java.sql.Timestamp date = java.sql.Timestamp.valueOf(
                java.time.LocalDateTime.now().minusDays(joursAvant));
        jdbcTemplate.update("""
                INSERT INTO lead (tenant_id, nom, source, statut, archive, client_irrite,
                                  mode_import, cree_le, modifie_le)
                VALUES (?, 'Lead Vieux', ?, 'NOUVEAU', false, false, 'IMPORT_CSV', ?, ?)
                """, tenantId, source, date, date);
    }

    /** Cree un RDV pris FACTURABLE : l'evenement canonique = transition immuable vers
     *  RDV_PRIS de source 'IA' dans historique_statut (cf. CLAUDE.md). */
    private void creerRdvPrisIa(final Long tenantId, final Long leadId) {
        jdbcTemplate.update("""
                INSERT INTO historique_statut (tenant_id, lead_id, statut_precedent, statut_suivant, source, cree_le)
                VALUES (?, ?, 'CONTACTE', 'RDV_PRIS', 'IA', NOW())
                """, tenantId, leadId);
    }

    /** Transition MANUELLE vers RDV_PRIS (drag&drop Kanban / dropdown) : NON facturable,
     *  ne doit JAMAIS etre comptee comme un RDV pris. */
    private void creerRdvPrisManuel(final Long tenantId, final Long leadId) {
        jdbcTemplate.update("""
                INSERT INTO historique_statut (tenant_id, lead_id, statut_precedent, statut_suivant, source, cree_le)
                VALUES (?, ?, 'CONTACTE', 'RDV_PRIS', 'DRAG_DROP', NOW())
                """, tenantId, leadId);
    }
}
