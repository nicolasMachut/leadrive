package fr.leadrevive.integration;

import fr.leadrevive.emailimport.entity.InboundEmailLog;
import fr.leadrevive.emailimport.entity.StatutInboundEmail;
import fr.leadrevive.emailimport.entity.TenantEmailConfig;
import fr.leadrevive.emailimport.repository.InboundEmailLogRepository;
import fr.leadrevive.emailimport.repository.TenantEmailConfigRepository;
import fr.leadrevive.emailimport.service.InboundEmailParsingService;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.entity.ModeImport;
import fr.leadrevive.lead.repository.LeadRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'integration du webhook SendGrid Inbound Parse (epic 24).
 *
 * <p>Verifie le flow bout-en-bout : POST multipart → routage tenant → parsing IA
 * (mocke) → dedup → persistance lead + log. Le {@link InboundEmailParsingService}
 * est mocke pour ne pas appeler Anthropic, tout le reste (BDD reelle, securite,
 * filtre Hibernate) tourne en conditions reelles via Testcontainers.</p>
 *
 * <p>Le webhook est public ({@code permitAll} dans SecurityConfig). On verifie
 * qu'il accepte les POST sans JWT et qu'il repond toujours 200 (sinon SendGrid
 * retry).</p>
 */
class SendGridWebhookIntegrationTest extends AbstractIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantEmailConfigRepository tenantEmailConfigRepository;
    @Autowired private InboundEmailLogRepository inboundEmailLogRepository;
    @Autowired private LeadRepository leadRepository;
    @Autowired private EntityManager entityManager;

    @MockitoBean
    private InboundEmailParsingService parsingService;

    private MockMvc mockMvc;
    private Long tenantId;
    private String emailAddressActive;
    private String emailAddressDesactivee;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        // Nettoyage : respecter l'ordre des FK
        jdbcTemplate.update("DELETE FROM inbound_email_log");
        jdbcTemplate.update("DELETE FROM tenant_email_config");
        jdbcTemplate.update("DELETE FROM lead");
        jdbcTemplate.update("DELETE FROM lead_import");
        jdbcTemplate.update("DELETE FROM utilisateur");
        jdbcTemplate.update("DELETE FROM tenant");

        tenantId = jdbcTemplate.queryForObject(
                "INSERT INTO tenant (nom) VALUES ('Garage Test') RETURNING id_tenant", Long.class);
        final Long tenantBId = jdbcTemplate.queryForObject(
                "INSERT INTO tenant (nom) VALUES ('Garage Autre') RETURNING id_tenant", Long.class);

        emailAddressActive = "garage-test-abc1@leads.leadrive.fr";
        emailAddressDesactivee = "garage-autre-xyz2@leads.leadrive.fr";
        jdbcTemplate.update(
                "INSERT INTO tenant_email_config (tenant_id, email_slug, email_address, statut) VALUES (?, ?, ?, ?)",
                tenantId, "garage-test", emailAddressActive, "ACTIVE");
        jdbcTemplate.update(
                "INSERT INTO tenant_email_config (tenant_id, email_slug, email_address, statut) VALUES (?, ?, ?, ?)",
                tenantBId, "garage-autre", emailAddressDesactivee, "DESACTIVE");
    }

    @AfterEach
    void cleanup() {
        entityManager.unwrap(Session.class).disableFilter("tenantFilter");
    }

    // ===== Routage tenant =====

    @Test
    void inbound_adresseInconnue_retourne200SansLog() throws Exception {
        // GIVEN
        // Pas de mock parsingService : on verifie que le service n'est jamais appele.

        // WHEN / THEN
        mockMvc.perform(post("/api/webhook/sendgrid/inbound")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("to", "inconnu@leads.leadrive.fr")
                        .param("from", "prospect@example.com")
                        .param("subject", "Demande info")
                        .param("text", "Bonjour je suis interesse"))
                .andExpect(status().isOk());

        // Le parser ne doit jamais etre appele car l'adresse n'existe pas
        verify(parsingService, never()).parser(anyString(), anyString(), anyLong());
        // Aucun log inbound cree
        assertThat(countLogs()).isZero();
    }

    @Test
    void inbound_adresseDesactivee_retourne200SansLog() throws Exception {
        // WHEN / THEN
        mockMvc.perform(post("/api/webhook/sendgrid/inbound")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("to", emailAddressDesactivee)
                        .param("from", "prospect@example.com")
                        .param("subject", "Demande")
                        .param("text", "Body"))
                .andExpect(status().isOk());

        verify(parsingService, never()).parser(anyString(), anyString(), anyLong());
        assertThat(countLogs()).isZero();
    }

    @Test
    void inbound_payloadVide_retourne200() throws Exception {
        // GIVEN : aucun champ "to" - SendGrid pourrait envoyer un payload incomplet
        mockMvc.perform(post("/api/webhook/sendgrid/inbound")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                // On repond TOUJOURS 200 (sinon SendGrid retry)
                .andExpect(status().isOk());

        verify(parsingService, never()).parser(anyString(), anyString(), anyLong());
    }

    @Test
    void inbound_bodyVide_retourne200SansLog() throws Exception {
        mockMvc.perform(post("/api/webhook/sendgrid/inbound")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("to", emailAddressActive)
                        .param("from", "prospect@example.com")
                        .param("subject", "Sujet"))
                .andExpect(status().isOk());

        verify(parsingService, never()).parser(anyString(), anyString(), anyLong());
        assertThat(countLogs()).isZero();
    }

    // ===== Flow nominal : parsing OK → creation lead =====

    @Test
    void inbound_parsingOk_creeLeadEtLog() throws Exception {
        // GIVEN : IA renvoie un prospect complet
        final InboundEmailParsingService.ResultatParsing parsingOk = parsingOk(
                "Dupont", "Jean", "+33612345678", "jean@dupont.fr",
                "Peugeot", "3008", "LEBONCOIN", 90);
        when(parsingService.parser(anyString(), anyString(), anyLong())).thenReturn(parsingOk);

        // WHEN
        mockMvc.perform(post("/api/webhook/sendgrid/inbound")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("to", emailAddressActive)
                        .param("from", "noreply@leboncoin.fr")
                        .param("subject", "Nouveau message LeBonCoin")
                        .param("text", "Jean Dupont (+33612345678) est interesse par votre Peugeot 3008"))
                .andExpect(status().isOk());

        // THEN : un lead a ete cree
        assertCrossTenant(() -> {
            final List<Lead> leads = leadRepository.findByTenantId(tenantId);
            assertThat(leads).hasSize(1);
            final Lead lead = leads.get(0);
            assertThat(lead.getTelephone()).isEqualTo("+33612345678");
            assertThat(lead.getEmail()).isEqualTo("jean@dupont.fr");
            assertThat(lead.getSource()).isEqualTo("LEBONCOIN");
            assertThat(lead.getModeImport()).isEqualTo(ModeImport.EMAIL_AUTO);
            assertThat(lead.getConsentementContact()).isTrue();
            assertThat(lead.getConsentementSource()).isEqualTo("REDIRECT_EMAIL");
            return null;
        });

        // Un log inbound a ete persiste avec le statut LEAD_CREE
        final List<InboundEmailLog> logs = trouverLogsPourTenant(tenantId);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getStatut()).isEqualTo(StatutInboundEmail.LEAD_CREE);
        assertThat(logs.get(0).getSourceDetectee()).isEqualTo("LEBONCOIN");
        assertThat(logs.get(0).getLeadId()).isNotNull();

        // Le compteur de la config a ete incremente
        final TenantEmailConfig config = chercherConfigParAdresse(emailAddressActive);
        assertThat(config.getNbEmailsRecus()).isEqualTo(1);
        assertThat(config.getDernierEmailRecuLe()).isNotNull();
    }

    @Test
    void inbound_parsingOk_dejaUnLeadAvecMemeTelephone_enrichitAuLieuDeCreerDoublon() throws Exception {
        // GIVEN : un lead recent existe pour ce tenant avec le meme telephone
        creerLeadExistant(tenantId, "Jean Dupont", "+33612345678", "vieux@dupont.fr", "LACENTRALE");

        final InboundEmailParsingService.ResultatParsing parsingOk = parsingOk(
                "Dupont", "Jean", "+33612345678", "jean@dupont.fr",
                "Peugeot", "3008", "LEBONCOIN", 90);
        when(parsingService.parser(anyString(), anyString(), anyLong())).thenReturn(parsingOk);

        // WHEN
        mockMvc.perform(post("/api/webhook/sendgrid/inbound")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("to", emailAddressActive)
                        .param("from", "noreply@leboncoin.fr")
                        .param("subject", "Sujet")
                        .param("text", "Body"))
                .andExpect(status().isOk());

        // THEN : toujours 1 seul lead (pas de doublon cree)
        assertCrossTenant(() -> {
            assertThat(leadRepository.findByTenantId(tenantId)).hasSize(1);
            return null;
        });

        // Le log a le statut LEAD_DEDUPLIQUE
        final List<InboundEmailLog> logs = trouverLogsPourTenant(tenantId);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getStatut()).isEqualTo(StatutInboundEmail.LEAD_DEDUPLIQUE);
    }

    @Test
    void inbound_parsingOkSansTelephone_creeLeadIncomplet() throws Exception {
        // GIVEN
        final InboundEmailParsingService.ResultatParsing parsingSansTel = parsingOk(
                "Dupont", "Jean", null, "jean@dupont.fr",
                null, null, "AUTRE", 70);
        when(parsingService.parser(anyString(), anyString(), anyLong())).thenReturn(parsingSansTel);

        // WHEN
        mockMvc.perform(post("/api/webhook/sendgrid/inbound")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("to", emailAddressActive)
                        .param("from", "prospect@example.com")
                        .param("subject", "Sujet")
                        .param("text", "Body sans numero"))
                .andExpect(status().isOk());

        // THEN : lead cree mais marque LEAD_INCOMPLET pour signaler au manager
        final List<InboundEmailLog> logs = trouverLogsPourTenant(tenantId);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getStatut()).isEqualTo(StatutInboundEmail.LEAD_INCOMPLET);

        assertCrossTenant(() -> {
            final List<Lead> leads = leadRepository.findByTenantId(tenantId);
            assertThat(leads).hasSize(1);
            assertThat(leads.get(0).getTelephone()).isNull();
            return null;
        });
    }

    // ===== Cas de rejet =====

    @Test
    void inbound_parsingRejetSpam_logueRejetSansCreerLead() throws Exception {
        // GIVEN : l'IA detecte une newsletter
        when(parsingService.parser(anyString(), anyString(), anyLong()))
                .thenReturn(InboundEmailParsingService.ResultatParsing
                        .rejetSpam("Newsletter hebdomadaire", "{\"rejet\":true}", 80L));

        // WHEN
        mockMvc.perform(post("/api/webhook/sendgrid/inbound")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("to", emailAddressActive)
                        .param("from", "newsletter@leboncoin.fr")
                        .param("subject", "Votre selection hebdo")
                        .param("text", "Les meilleures annonces de la semaine..."))
                .andExpect(status().isOk());

        // THEN : aucun lead cree, log REJETE_SPAM
        assertCrossTenant(() -> {
            assertThat(leadRepository.findByTenantId(tenantId)).isEmpty();
            return null;
        });
        final List<InboundEmailLog> logs = trouverLogsPourTenant(tenantId);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getStatut()).isEqualTo(StatutInboundEmail.REJETE_SPAM);
        assertThat(logs.get(0).getRaisonRejet()).contains("Newsletter");

        // Compteur incremente meme pour spam (= preuve qu'un email est arrive)
        final TenantEmailConfig config = chercherConfigParAdresse(emailAddressActive);
        assertThat(config.getNbEmailsRecus()).isEqualTo(1);
    }

    @Test
    void inbound_parsingEchec_logueEchecSansCreerLead() throws Exception {
        // GIVEN
        when(parsingService.parser(anyString(), anyString(), anyLong()))
                .thenReturn(InboundEmailParsingService.ResultatParsing.echec("JSON IA invalide"));

        // WHEN
        mockMvc.perform(post("/api/webhook/sendgrid/inbound")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("to", emailAddressActive)
                        .param("from", "prospect@example.com")
                        .param("subject", "Sujet")
                        .param("text", "Body"))
                .andExpect(status().isOk());

        // THEN
        assertCrossTenant(() -> {
            assertThat(leadRepository.findByTenantId(tenantId)).isEmpty();
            return null;
        });
        final List<InboundEmailLog> logs = trouverLogsPourTenant(tenantId);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getStatut()).isEqualTo(StatutInboundEmail.PARSE_ECHEC);
    }

    // ===== HTML / format =====

    @Test
    void inbound_htmlUniquementSansText_extraitTextePuisParse() throws Exception {
        // GIVEN : email HTML pur (pas de version texte fournie par SendGrid)
        when(parsingService.parser(anyString(), anyString(), anyLong()))
                .thenReturn(parsingOk("Test", "Marie", "+33611111111", "marie@x.fr",
                        null, null, "AUTRE", 80));

        final String html = "<html><body><p>Bonjour, je suis <b>Marie Test</b> "
                + "(<a href=\"tel:+33611111111\">+33 6 11 11 11 11</a>).</p>"
                + "<script>alert('xss')</script></body></html>";

        // WHEN
        mockMvc.perform(post("/api/webhook/sendgrid/inbound")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("to", emailAddressActive)
                        .param("from", "prospect@example.com")
                        .param("subject", "Demande info")
                        .param("html", html))
                .andExpect(status().isOk());

        // THEN : un lead a bien ete cree (= le HTML a ete strippe en texte exploitable)
        assertCrossTenant(() -> {
            assertThat(leadRepository.findByTenantId(tenantId)).hasSize(1);
            return null;
        });
    }

    @Test
    void inbound_adresseAvecFormatNomComplet_extraitBienLAdresse() throws Exception {
        // GIVEN : SendGrid envoie "Nom <addr@domaine>" dans certains cas
        when(parsingService.parser(anyString(), anyString(), anyLong()))
                .thenReturn(parsingOk("X", "Y", "+33600000000", null,
                        null, null, "AUTRE", 80));

        mockMvc.perform(post("/api/webhook/sendgrid/inbound")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("to", "Garage Test <" + emailAddressActive + ">")
                        .param("from", "Prospect Inconnu <prospect@example.com>")
                        .param("subject", "Sujet")
                        .param("text", "Body"))
                .andExpect(status().isOk());

        // THEN : routage a bien fonctionne malgre le format avec brackets
        final List<InboundEmailLog> logs = trouverLogsPourTenant(tenantId);
        assertThat(logs).hasSize(1);
    }

    // ===== Securite : webhook public =====

    @Test
    void inbound_publicSansJWT_estAcceptee() throws Exception {
        // Le webhook est dans /api/webhook/** qui est permitAll dans SecurityConfig.
        // Pas de jwt() sur la requete : ce test echouerait avec 401 si la config bougeait.
        mockMvc.perform(post("/api/webhook/sendgrid/inbound")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("to", "x@leads.leadrive.fr"))
                .andExpect(status().isOk());
    }

    // ===== Helpers =====

    private InboundEmailParsingService.ResultatParsing parsingOk(final String nom, final String prenom,
                                                                  final String telephone, final String email,
                                                                  final String marque, final String modele,
                                                                  final String source, final int confiance) {
        return new InboundEmailParsingService.ResultatParsing(
                InboundEmailParsingService.StatutResultat.OK,
                nom, prenom, telephone, email,
                marque, modele,
                marque != null && modele != null ? marque + " " + modele : null,
                source, confiance, new HashMap<>(),
                "{\"json\":\"ok\"}", null, 150L, false);
    }

    private void creerLeadExistant(final Long tenantId, final String nom, final String telephone,
                                    final String email, final String source) {
        jdbcTemplate.update("""
                INSERT INTO lead (tenant_id, nom, telephone, email, source, statut, archive, client_irrite,
                                  mode_import, cree_le, modifie_le)
                VALUES (?, ?, ?, ?, ?, 'NOUVEAU', false, false, 'EMAIL_AUTO', NOW(), NOW())
                """, tenantId, nom, telephone, email, source);
    }

    private long countLogs() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inbound_email_log", Long.class);
    }

    private List<InboundEmailLog> trouverLogsPourTenant(final Long tenantId) {
        return assertCrossTenant(() -> inboundEmailLogRepository.findAll().stream()
                .filter(l -> l.getTenantId().equals(tenantId))
                .toList());
    }

    private TenantEmailConfig chercherConfigParAdresse(final String emailAddress) {
        return assertCrossTenant(() -> {
            final Optional<TenantEmailConfig> opt = tenantEmailConfigRepository.findByEmailAddress(emailAddress);
            assertThat(opt).isPresent();
            return opt.get();
        });
    }

    /**
     * Execute une lecture en mode cross-tenant (filtre Hibernate desactive).
     * Necessaire pour verifier l'etat reel de la BDD apres un webhook qui tourne
     * lui-meme sans TenantContext.
     */
    private <T> T assertCrossTenant(final java.util.function.Supplier<T> bloc) {
        final Session session = entityManager.unwrap(Session.class);
        session.disableFilter("tenantFilter");
        try {
            return bloc.get();
        } finally {
            // Pas de tenantId courant a restaurer en environnement test
        }
    }
}
