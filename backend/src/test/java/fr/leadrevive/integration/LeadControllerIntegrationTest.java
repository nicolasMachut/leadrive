package fr.leadrevive.integration;

import tools.jackson.databind.ObjectMapper;
import fr.leadrevive.common.security.ClerkJwtConverter;
import fr.leadrevive.common.tenant.TenantContext;
import fr.leadrevive.lead.dto.ReqUpdateLeadDto;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.entity.LeadStatutEnum;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.user.entity.RoleEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice vertical : LeadController + Service + Repository + securite + multi-tenant.
 * Verifie le bon comportement bout-en-bout d'un CRUD type, en s'assurant que :
 * - la securite role-based fonctionne
 * - l'isolation multi-tenant est respectee meme via les endpoints REST (IDOR via path param)
 * - les operations modifient bien la BDD reelle
 */
class LeadControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private Long tenantId;
    private Long autreTenantId;
    private Long leadIdTenantA;
    private Long leadIdTenantAutre;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        // Nettoyage (respecter l'ordre des FK : lead → lead_import → utilisateur → tenant)
        jdbcTemplate.update("DELETE FROM inbound_email_log");
        jdbcTemplate.update("DELETE FROM tenant_email_config");
        jdbcTemplate.update("DELETE FROM lead");
        jdbcTemplate.update("DELETE FROM lead_import");
        jdbcTemplate.update("DELETE FROM utilisateur");
        jdbcTemplate.update("DELETE FROM tenant");

        tenantId = jdbcTemplate.queryForObject(
                "INSERT INTO tenant (nom) VALUES ('Concession A') RETURNING id_tenant", Long.class);
        autreTenantId = jdbcTemplate.queryForObject(
                "INSERT INTO tenant (nom) VALUES ('Concession B') RETURNING id_tenant", Long.class);

        leadIdTenantA = creerLead(tenantId, "Dupont Jean", "jean@dupont.fr", "0612345678", LeadStatutEnum.NOUVEAU);
        creerLead(tenantId, "Martin Marie", "marie@martin.fr", "0623456789", LeadStatutEnum.EN_COURS);
        leadIdTenantAutre = creerLead(autreTenantId, "Concurrent", "concurrent@b.fr", "0699999999", LeadStatutEnum.NOUVEAU);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    private Long creerLead(final Long tenantId, final String nom, final String email,
                            final String tel, final LeadStatutEnum statut) {
        final Lead lead = new Lead();
        lead.setTenantId(tenantId);
        lead.setNom(nom);
        lead.setEmail(email);
        lead.setTelephone(tel);
        lead.setStatut(statut);
        return leadRepository.save(lead).getId();
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtManager() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_MANAGER"));
    }

    /**
     * JWT vendeur porteur des claims lus par {@link fr.leadrevive.common.security.AuthService}
     * ({@code ClerkAuthDetails} : userId + role). Indispensable pour exercer le controle
     * d'ownership du {@link fr.leadrevive.common.security.LeadAccessGuard} : le simple
     * {@code jwt().authorities(ROLE_VENDEUR)} ne pose pas de details, donc le guard croit
     * l'utilisateur non-vendeur et laisse tout passer.
     */
    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtVendeur(final Long userId) {
        final Jwt jwtToken = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("clerk-user-" + userId)
                .issuedAt(Instant.EPOCH)
                .expiresAt(Instant.EPOCH.plusSeconds(3600))
                .build();
        final JwtAuthenticationToken auth = new JwtAuthenticationToken(
                jwtToken, List.of(new SimpleGrantedAuthority("ROLE_VENDEUR")));
        auth.setDetails(new ClerkJwtConverter.ClerkAuthDetails(
                userId, tenantId, RoleEnum.VENDEUR, "clerk-user-" + userId, "clerk-org"));
        return authentication(auth);
    }

    /**
     * Cree un vendeur reel (contrainte FK lead.vendeur_id -> utilisateur) et renvoie son id.
     */
    private Long creerVendeur(final Long tenantId, final String email) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO utilisateur (tenant_id, email, nom, prenom, role) "
                        + "VALUES (?, ?, 'Vendeur', 'Test', 'VENDEUR') RETURNING id_utilisateur",
                Long.class, tenantId, email);
    }

    // ===== GET /api/leads (liste) =====

    @Test
    void getLeads_managerDuTenant_retourneSesLeadsUniquement() throws Exception {
        // GIVEN
        TenantContext.setCurrentTenantId(tenantId);

        // WHEN / THEN
        // 2 leads pour le tenant A, 1 pour le tenant B → on doit voir 2
        mockMvc.perform(get("/api/leads").with(jwtManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void getLeads_avecRecherche_filtreSurLeNom() throws Exception {
        // GIVEN
        TenantContext.setCurrentTenantId(tenantId);

        // WHEN / THEN
        mockMvc.perform(get("/api/leads").param("recherche", "dupont").with(jwtManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nom").value("Dupont Jean"));
    }

    // ===== GET /api/leads/{id} =====

    @Test
    void getLead_leadDuTenantCourant_retourne200() throws Exception {
        // GIVEN
        TenantContext.setCurrentTenantId(tenantId);

        // WHEN / THEN
        mockMvc.perform(get("/api/leads/" + leadIdTenantA).with(jwtManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Dupont Jean"))
                .andExpect(jsonPath("$.email").value("jean@dupont.fr"));
    }

    @Test
    void getLead_idDunLeadAutreTenant_retourne404_IDOR_bloque() throws Exception {
        // GIVEN
        // L'utilisateur appartient au tenant A, mais demande un lead du tenant B
        TenantContext.setCurrentTenantId(tenantId);

        // WHEN / THEN
        mockMvc.perform(get("/api/leads/" + leadIdTenantAutre).with(jwtManager()))
                .andExpect(status().isNotFound());
    }

    // ===== PUT /api/leads/{id} =====

    @Test
    void changeStatut_viaPatch_persisteEnBdd() throws Exception {
        // GIVEN : le statut se change désormais via PATCH /{id}/statut (deplacerLead),
        // seul chemin qui trace historique_statut. Le PUT ne mute plus le statut.
        TenantContext.setCurrentTenantId(tenantId);

        // WHEN
        mockMvc.perform(patch("/api/leads/" + leadIdTenantA + "/statut")
                        .with(jwtManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statut\":\"CONTACTE\"}"))
                .andExpect(status().isOk());

        // THEN : le statut est bien persiste
        final Optional<Lead> reloaded = leadRepository.findByIdAndTenantId(leadIdTenantA, tenantId);
        assertTrue(reloaded.isPresent());
        assertEquals(LeadStatutEnum.CONTACTE, reloaded.get().getStatut());
    }

    @Test
    void updateLead_putAvecChangementStatut_estRejete() throws Exception {
        // GIVEN : un PUT qui tente de CHANGER le statut doit etre refuse (400) pour forcer
        // le passage par PATCH /statut (tracage historique). Le statut ne doit pas bouger.
        TenantContext.setCurrentTenantId(tenantId);
        final LeadStatutEnum statutAvant = leadRepository.findByIdAndTenantId(leadIdTenantA, tenantId)
                .orElseThrow().getStatut();
        final ReqUpdateLeadDto request = ReqUpdateLeadDto.builder()
                .statut(statutAvant == LeadStatutEnum.CONTACTE ? "RDV_PRIS" : "CONTACTE")
                .build();

        // WHEN / THEN
        mockMvc.perform(put("/api/leads/" + leadIdTenantA)
                        .with(jwtManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        final Optional<Lead> reloaded = leadRepository.findByIdAndTenantId(leadIdTenantA, tenantId);
        assertTrue(reloaded.isPresent());
        assertEquals(statutAvant, reloaded.get().getStatut());
    }

    @Test
    void updateLead_avecStatutInvalide_retourne400() throws Exception {
        // GIVEN
        TenantContext.setCurrentTenantId(tenantId);
        final ReqUpdateLeadDto request = ReqUpdateLeadDto.builder()
                .statut("STATUT_BIDON")
                .build();

        // WHEN / THEN
        mockMvc.perform(put("/api/leads/" + leadIdTenantA)
                        .with(jwtManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateLead_idDunLeadAutreTenant_retourne404_IDOR_bloque() throws Exception {
        // GIVEN
        TenantContext.setCurrentTenantId(tenantId);
        final ReqUpdateLeadDto request = ReqUpdateLeadDto.builder()
                .statut("CONTACTE")
                .build();

        // WHEN / THEN
        mockMvc.perform(put("/api/leads/" + leadIdTenantAutre)
                        .with(jwtManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());

        // Verifie que le lead du tenant B n'a PAS ete modifie
        final Lead leadAutreTenant = jdbcTemplate.queryForObject(
                "SELECT * FROM lead WHERE id_lead = ?",
                (rs, rowNum) -> {
                    final Lead l = new Lead();
                    l.setStatut(LeadStatutEnum.valueOf(rs.getString("statut")));
                    return l;
                }, leadIdTenantAutre);
        assertEquals(LeadStatutEnum.NOUVEAU, leadAutreTenant.getStatut(),
                "Le statut du lead de l'autre tenant ne doit JAMAIS changer");
    }

    @Test
    void deplacerStatut_idDunLeadAutreTenant_retourne404_IDOR_bloque() throws Exception {
        // GIVEN : le déplacement Kanban (PATCH /{id}/statut) passe désormais par
        // LeadAccessGuard. Un lead d'un autre tenant doit renvoyer 404 anonyme et
        // ne JAMAIS changer de statut.
        TenantContext.setCurrentTenantId(tenantId);

        // WHEN / THEN
        mockMvc.perform(patch("/api/leads/" + leadIdTenantAutre + "/statut")
                        .with(jwtManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statut\":\"CONTACTE\"}"))
                .andExpect(status().isNotFound());

        final Lead leadAutreTenant = jdbcTemplate.queryForObject(
                "SELECT * FROM lead WHERE id_lead = ?",
                (rs, rowNum) -> {
                    final Lead l = new Lead();
                    l.setStatut(LeadStatutEnum.valueOf(rs.getString("statut")));
                    return l;
                }, leadIdTenantAutre);
        assertEquals(LeadStatutEnum.NOUVEAU, leadAutreTenant.getStatut(),
                "Le statut du lead de l'autre tenant ne doit JAMAIS changer via le Kanban");
    }

    @Test
    void getHistoriqueStatuts_idDunLeadAutreTenant_retourne404_IDOR_bloque() throws Exception {
        TenantContext.setCurrentTenantId(tenantId);
        mockMvc.perform(get("/api/leads/" + leadIdTenantAutre + "/historique-statuts").with(jwtManager()))
                .andExpect(status().isNotFound());
    }

    // ===== DELETE /api/leads/{id} =====

    @Test
    void deleteLead_managerDuTenant_archiveEnBdd() throws Exception {
        // GIVEN
        TenantContext.setCurrentTenantId(tenantId);

        // WHEN
        mockMvc.perform(delete("/api/leads/" + leadIdTenantA).with(jwtManager()))
                .andExpect(status().isNoContent());

        // THEN — le lead est archivé, pas supprimé
        final var lead = leadRepository.findByIdAndTenantId(leadIdTenantA, tenantId);
        assertTrue(lead.isPresent(), "Le lead doit toujours exister en base");
        assertTrue(lead.get().getArchive(), "Le lead doit être marqué comme archivé");
    }

    @Test
    void deleteLead_vendeur_leadNonAssigne_retourne404_ownershipBloque() throws Exception {
        // GIVEN : un vendeur reel ; leadIdTenantA n'est assigné à personne
        TenantContext.setCurrentTenantId(tenantId);
        final Long vendeurId = creerVendeur(tenantId, "vendeur-delete@example.com");

        // WHEN / THEN : le guard d'ownership renvoie 404 (anonyme, pas 403) sur un lead non possédé
        mockMvc.perform(delete("/api/leads/" + leadIdTenantA).with(jwtVendeur(vendeurId)))
                .andExpect(status().isNotFound());

        // Le lead n'a pas été archivé
        final Optional<Lead> lead = leadRepository.findByIdAndTenantId(leadIdTenantA, tenantId);
        assertTrue(lead.isPresent() && !lead.get().getArchive(),
                "Le lead ne doit pas être archivé par un vendeur non propriétaire");
    }

    @Test
    void deleteLead_vendeur_sonPropreLead_archiveEnBdd() throws Exception {
        // GIVEN : un vendeur reel proprietaire du lead
        TenantContext.setCurrentTenantId(tenantId);
        final Long vendeurId = creerVendeur(tenantId, "vendeur-owner@example.com");
        final Lead sien = leadRepository.findByIdAndTenantId(leadIdTenantA, tenantId).orElseThrow();
        sien.setVendeurId(vendeurId);
        leadRepository.save(sien);

        // WHEN : le vendeur archive SON lead
        mockMvc.perform(delete("/api/leads/" + leadIdTenantA).with(jwtVendeur(vendeurId)))
                .andExpect(status().isNoContent());

        // THEN : le lead est archivé, pas supprimé
        final Optional<Lead> lead = leadRepository.findByIdAndTenantId(leadIdTenantA, tenantId);
        assertTrue(lead.isPresent() && lead.get().getArchive(),
                "Le vendeur doit pouvoir archiver son propre lead");
    }

    @Test
    void deleteLead_idAutreTenant_retourne404_IDOR_bloque() throws Exception {
        // GIVEN
        TenantContext.setCurrentTenantId(tenantId);

        // WHEN / THEN
        mockMvc.perform(delete("/api/leads/" + leadIdTenantAutre).with(jwtManager()))
                .andExpect(status().isNotFound());

        // Le lead de l'autre tenant existe toujours
        assertAll(
                () -> assertTrue(leadRepository.findByIdAndTenantId(leadIdTenantAutre, autreTenantId).isPresent(),
                        "Le lead de l'autre tenant doit toujours exister")
        );
    }
}
