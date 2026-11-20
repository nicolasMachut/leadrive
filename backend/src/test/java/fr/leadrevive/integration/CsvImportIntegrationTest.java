package fr.leadrevive.integration;

import fr.leadrevive.csvimport.dto.ImportAnalyseRep;
import fr.leadrevive.csvimport.dto.RepImportResultDto;
import fr.leadrevive.csvimport.exception.CsvParsingException;
import fr.leadrevive.csvimport.service.CsvImportService;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.entity.LeadStatutEnum;
import fr.leadrevive.lead.repository.LeadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test bout-en-bout du flux d'import CSV : upload → cache → validate → bulk insert.
 * Verifie l'integration reelle entre CsvImportService, ValidationService, DeduplicationService
 * et la BDD (vraie persistance des Leads).
 */
class CsvImportIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CsvImportService service;

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long tenantId;
    private Long userId;

    @BeforeEach
    void seed() {
        // Nettoyage complet entre tests pour eviter doublons cumulatifs
        jdbcTemplate.update("DELETE FROM inbound_email_log");
        jdbcTemplate.update("DELETE FROM tenant_email_config");
        jdbcTemplate.update("DELETE FROM lead");
        jdbcTemplate.update("DELETE FROM lead_import");
        jdbcTemplate.update("DELETE FROM utilisateur");
        jdbcTemplate.update("DELETE FROM tenant");

        tenantId = jdbcTemplate.queryForObject(
                "INSERT INTO tenant (nom) VALUES ('Tenant Import') RETURNING id_tenant", Long.class);
        userId = jdbcTemplate.queryForObject(
                """
                INSERT INTO utilisateur (tenant_id, email, nom, prenom, role, est_actif, statut)
                VALUES (?, 'admin@test.fr', 'Admin', 'Test', 'MANAGER', true, 'ACTIF')
                RETURNING id_utilisateur
                """, Long.class, tenantId);
    }

    private MockMultipartFile csvDe(final String contenu) {
        return new MockMultipartFile("file", "leads.csv", "text/csv",
                contenu.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void importCsv_3lignesValides_persisteLes3LeadsEnBdd() {
        final String csv = """
                Nom du compte,Téléphone mobile,Contact : Adresse e-mail,Marque,Modèle
                Dupont Jean,0612345678,jean@dupont.fr,Peugeot,308
                Martin Marie,0623456789,marie@martin.fr,Renault,Clio
                Bernard Paul,0634567890,paul@bernard.fr,Citroen,C3
                """;

        final ImportAnalyseRep upload = service.upload(csvDe(csv), tenantId, userId);
        final RepImportResultDto resultat = service.validate(upload.getImportId(), tenantId);

        final List<Lead> leads = leadRepository.findByTenantId(tenantId);
        assertAll(
                () -> assertEquals(3, resultat.getNombreImportees()),
                () -> assertEquals(0, resultat.getNombreErreurs()),
                () -> assertEquals(0, resultat.getNombreDoublons()),
                () -> assertEquals(3, leads.size()),
                () -> assertTrue(leads.stream().allMatch(l -> l.getStatut() == LeadStatutEnum.NOUVEAU)),
                () -> assertTrue(leads.stream().allMatch(l -> "CSV".equals(l.getSource()))),
                () -> assertTrue(leads.stream().allMatch(l -> l.getTenantId().equals(tenantId))),
                () -> assertTrue(leads.stream().allMatch(l -> l.getImportId().equals(upload.getImportId())))
        );
    }

    @Test
    void importCsv_doublonDansLeMemeFichier_estDetecte() {
        final String csv = """
                Nom du compte,Téléphone mobile,Contact : Adresse e-mail,Marque,Modèle
                Dupont Jean,0612345678,jean@dupont.fr,Peugeot,308
                Bernard Paul,0634567890,paul@bernard.fr,Citroen,C3
                Bernard Paul Bis,0634567890,paul@bernard.fr,Citroen,C3
                """;

        final ImportAnalyseRep upload = service.upload(csvDe(csv), tenantId, userId);
        final RepImportResultDto resultat = service.validate(upload.getImportId(), tenantId);

        assertAll(
                () -> assertEquals(2, resultat.getNombreImportees(), "1 doublon doit etre rejete"),
                () -> assertEquals(1, resultat.getNombreDoublons()),
                () -> assertEquals(2, leadRepository.findByTenantId(tenantId).size())
        );
    }

    @Test
    void importCsv_lignesInvalides_sontComptesEnErreur() {
        final String csv = """
                Nom du compte,Téléphone mobile,Contact : Adresse e-mail,Marque,Modèle
                ,0612345678,jean@dupont.fr,Peugeot,308
                Martin Marie,0623456789,marie@martin.fr,Renault,Clio
                """;

        final ImportAnalyseRep upload = service.upload(csvDe(csv), tenantId, userId);
        final RepImportResultDto resultat = service.validate(upload.getImportId(), tenantId);

        assertAll(
                () -> assertEquals(1, resultat.getNombreImportees()),
                () -> assertEquals(1, resultat.getNombreErreurs()),
                () -> assertEquals(1, leadRepository.findByTenantId(tenantId).size())
        );
    }

    @Test
    void importCsv_isolationTenant_unTenantNeVoitPasLeImportDelautre() {
        final Long autreTenantId = jdbcTemplate.queryForObject(
                "INSERT INTO tenant (nom) VALUES ('Autre Tenant') RETURNING id_tenant", Long.class);

        final String csv = """
                Nom du compte,Téléphone mobile,Contact : Adresse e-mail,Marque,Modèle
                Dupont Jean,0612345678,jean@dupont.fr,Peugeot,308
                """;
        final ImportAnalyseRep upload = service.upload(csvDe(csv), tenantId, userId);
        service.validate(upload.getImportId(), tenantId);

        final List<Lead> leadsAutreTenant = leadRepository.findByTenantId(autreTenantId);
        final List<Lead> leadsTenantImporteur = leadRepository.findByTenantId(tenantId);

        assertAll(
                () -> assertTrue(leadsAutreTenant.isEmpty(),
                        "L'autre tenant ne doit voir aucun lead importe"),
                () -> assertEquals(1, leadsTenantImporteur.size())
        );
    }

    @Test
    void importCsv_assignVendeur_attribueLeVendeurAuxLeadsImportes() {
        final Long vendeurId = jdbcTemplate.queryForObject(
                """
                INSERT INTO utilisateur (tenant_id, email, nom, prenom, role, est_actif, statut)
                VALUES (?, 'vendeur@test.fr', 'Vendeur', 'Test', 'VENDEUR', true, 'ACTIF')
                RETURNING id_utilisateur
                """, Long.class, tenantId);

        final String csv = """
                Nom du compte,Téléphone mobile,Contact : Adresse e-mail,Marque,Modèle
                Dupont Jean,0612345678,jean@dupont.fr,Peugeot,308
                """;

        final ImportAnalyseRep upload = service.upload(csvDe(csv), tenantId, userId);
        service.validate(upload.getImportId(), tenantId, vendeurId);

        final List<Lead> leads = leadRepository.findByTenantId(tenantId);
        assertEquals(1, leads.size());
        assertEquals(vendeurId, leads.get(0).getVendeurId());
    }

    @Test
    void importCsv_scoringCsvIgnore_rawDataPersiste() {
        final String csv = """
                Nom du compte,Téléphone mobile,Contact : Adresse e-mail,Marque,Modèle,Scoring-Score Temps Reel
                Dupont Jean,0612345678,jean@dupont.fr,Peugeot,308,0.85
                """;

        final ImportAnalyseRep upload = service.upload(csvDe(csv), tenantId, userId);
        service.validate(upload.getImportId(), tenantId);

        final List<Lead> leads = leadRepository.findByTenantId(tenantId);
        assertEquals(1, leads.size());
        final Lead lead = leads.get(0);
        assertAll(
                () -> assertNull(lead.getScoring(), "Le scoring CSV ne doit plus etre importe"),
                () -> assertNotNull(lead.getRawData(), "rawData JSON doit etre persiste")
        );
    }
}
