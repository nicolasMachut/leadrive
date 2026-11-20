package fr.leadrevive.integration;

import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.entity.LeadStatutEnum;
import fr.leadrevive.lead.repository.LeadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vraie verification de l'isolation multi-tenant au niveau du repository.
 * <p>
 * On insere des leads pour 2 tenants, puis on s'assure que les requetes
 * "tenant-aware" ne fuitent jamais les donnees d'un autre tenant.
 * Ce test attrape les regressions ou un dev oublierait `tenantId` dans une nouvelle methode repo.
 */
@Transactional
class LeadRepositoryIsolationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long tenantA;
    private Long tenantB;

    @BeforeEach
    void seed() {
        // GIVEN
        // Cree 2 tenants distincts
        tenantA = jdbcTemplate.queryForObject(
                "INSERT INTO tenant (nom) VALUES ('Tenant A') RETURNING id_tenant", Long.class);
        tenantB = jdbcTemplate.queryForObject(
                "INSERT INTO tenant (nom) VALUES ('Tenant B') RETURNING id_tenant", Long.class);

        // 2 leads pour A, 1 pour B
        creerLead(tenantA, "Alice A");
        creerLead(tenantA, "Bob A");
        creerLead(tenantB, "Charlie B");
    }

    private void creerLead(final Long tenantId, final String nom) {
        creerLead(tenantId, nom, false);
    }

    private Lead creerLead(final Long tenantId, final String nom, final boolean archive) {
        final Lead lead = new Lead();
        lead.setTenantId(tenantId);
        lead.setNom(nom);
        lead.setStatut(LeadStatutEnum.NOUVEAU);
        lead.setArchive(archive);
        return leadRepository.save(lead);
    }

    /**
     * Cree un vrai vendeur en base et renvoie son id.
     * Necessaire car {@code lead.vendeur_id} porte une contrainte FK vers {@code utilisateur} :
     * un id fictif provoque une {@code DataIntegrityViolationException} au flush.
     */
    private Long creerVendeur(final Long tenantId, final String email) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO utilisateur (tenant_id, email, nom, prenom, role) "
                        + "VALUES (?, ?, 'Vendeur', 'Test', 'VENDEUR') RETURNING id_utilisateur",
                Long.class, tenantId, email);
    }

    @Test
    void findByTenantId_renvoieUniquementLesLeadsDuTenant() {
        // WHEN
        final List<Lead> leadsA = leadRepository.findByTenantId(tenantA);
        final List<Lead> leadsB = leadRepository.findByTenantId(tenantB);

        // THEN
        assertAll(
                () -> assertEquals(2, leadsA.size(), "Tenant A doit avoir 2 leads"),
                () -> assertEquals(1, leadsB.size(), "Tenant B doit avoir 1 lead"),
                () -> assertTrue(leadsA.stream().allMatch(l -> l.getTenantId().equals(tenantA))),
                () -> assertTrue(leadsB.stream().allMatch(l -> l.getTenantId().equals(tenantB)))
        );
    }

    @Test
    void findByIdAndTenantId_idValideMaisAutreTenant_retourneVide() {
        // GIVEN
        // Recupere un lead de A
        final Lead leadA = leadRepository.findByTenantId(tenantA).get(0);

        // WHEN
        // Tente d'y acceder via le tenant B (IDOR)
        final Optional<Lead> result = leadRepository.findByIdAndTenantId(leadA.getId(), tenantB);

        // THEN
        assertTrue(result.isEmpty(), "IDOR : un autre tenant ne doit pas pouvoir lire ce lead");
    }

    @Test
    void findByIdAndTenantId_memeTenant_retourneLeLead() {
        // GIVEN
        final Lead leadA = leadRepository.findByTenantId(tenantA).get(0);

        // WHEN
        final Optional<Lead> result = leadRepository.findByIdAndTenantId(leadA.getId(), tenantA);

        // THEN
        assertTrue(result.isPresent(), "Le tenant proprietaire doit pouvoir lire son lead");
        assertEquals(leadA.getId(), result.get().getId());
    }

    @Test
    void findByTenantIdAndEmail_chercheEmailDansAutreTenant_retourneVide() {
        // GIVEN
        final Lead leadA = leadRepository.findByTenantId(tenantA).get(0);
        leadA.setEmail("partage@example.com");
        leadRepository.save(leadA);

        // WHEN
        // Email existe mais pas dans le tenant B
        final Optional<Lead> result = leadRepository.findByTenantIdAndEmail(tenantB, "partage@example.com");

        // THEN
        assertTrue(result.isEmpty());
    }

    @Test
    void findByTenantIdWithFilters_archiveFalse_neRenvoieQueLesLeadsActifs() {
        // GIVEN : sur tenant B, 1 lead actif (Charlie B du seed) + 1 lead archivé
        creerLead(tenantB, "Archivé B", true);

        // WHEN : bascule "Actifs" (archive = false)
        final Page<Lead> actifs = leadRepository.findByTenantIdWithFilters(
                tenantB, false, null, null, null, null, null, null, PageRequest.of(0, 20));

        // THEN : seul le lead actif remonte, jamais l'archivé
        assertAll(
                () -> assertEquals(1, actifs.getTotalElements(), "Mode Actifs : seuls les leads non archivés"),
                () -> assertTrue(actifs.getContent().stream().noneMatch(l -> Boolean.TRUE.equals(l.getArchive())),
                        "Aucun lead archivé ne doit apparaître en mode Actifs")
        );
    }

    @Test
    void findByTenantIdWithFilters_archiveTrue_neRenvoieQueLesLeadsArchives() {
        // GIVEN : sur tenant B, 1 lead actif (seed) + 2 leads archivés
        creerLead(tenantB, "Archivé B1", true);
        creerLead(tenantB, "Archivé B2", true);

        // WHEN : bascule "Archives" (archive = true)
        final Page<Lead> archives = leadRepository.findByTenantIdWithFilters(
                tenantB, true, null, null, null, null, null, null, PageRequest.of(0, 20));

        // THEN : seuls les 2 leads archivés remontent, jamais l'actif
        assertAll(
                () -> assertEquals(2, archives.getTotalElements(), "Mode Archives : seuls les leads archivés"),
                () -> assertTrue(archives.getContent().stream().allMatch(l -> Boolean.TRUE.equals(l.getArchive())),
                        "Tous les leads du mode Archives doivent être archivés")
        );
    }

    @Test
    void findByTenantIdAndVendeurId_respecteLeFiltreArchive() {
        // GIVEN : un vendeur (reel, contrainte FK) avec 1 lead actif et 1 lead archivé sur tenant A
        final Long vendeurId = creerVendeur(tenantA, "vendeur-filtre-archive@example.com");
        final Lead actif = creerLead(tenantA, "Actif vendeur", false);
        actif.setVendeurId(vendeurId);
        leadRepository.save(actif);
        final Lead archive = creerLead(tenantA, "Archivé vendeur", true);
        archive.setVendeurId(vendeurId);
        leadRepository.save(archive);

        // WHEN / THEN : Actifs ne remonte que l'actif, Archives que l'archivé
        final Page<Lead> actifs = leadRepository.findByTenantIdAndVendeurId(
                tenantA, vendeurId, false, null, null, PageRequest.of(0, 20));
        final Page<Lead> archives = leadRepository.findByTenantIdAndVendeurId(
                tenantA, vendeurId, true, null, null, PageRequest.of(0, 20));

        assertAll(
                () -> assertEquals(1, actifs.getTotalElements(), "Vendeur, mode Actifs : 1 lead"),
                () -> assertTrue(actifs.getContent().stream().noneMatch(l -> Boolean.TRUE.equals(l.getArchive()))),
                () -> assertEquals(1, archives.getTotalElements(), "Vendeur, mode Archives : 1 lead"),
                () -> assertTrue(archives.getContent().stream().allMatch(l -> Boolean.TRUE.equals(l.getArchive())))
        );
    }

    @Test
    void findEnvoisAutoAVenir_scopeVendeur_neFuitePasLesLeadsDesAutres() {
        // GIVEN : 2 vendeurs sur tenant A, chacun 1 lead NOUVEAU avec envoi auto arme
        final Long vendeur1 = creerVendeur(tenantA, "vendeur1-envoi-auto@example.com");
        final Long vendeur2 = creerVendeur(tenantA, "vendeur2-envoi-auto@example.com");
        final Lead leadV1 = creerLead(tenantA, "Lead vendeur 1", false);
        leadV1.setVendeurId(vendeur1);
        leadV1.setPremierMessageAutoPrevuLe(java.time.LocalDateTime.now().plusHours(1));
        leadRepository.save(leadV1);
        final Lead leadV2 = creerLead(tenantA, "Lead vendeur 2", false);
        leadV2.setVendeurId(vendeur2);
        leadV2.setPremierMessageAutoPrevuLe(java.time.LocalDateTime.now().plusHours(2));
        leadRepository.save(leadV2);

        // WHEN
        final List<Lead> pourVendeur1 = leadRepository.findEnvoisAutoAVenir(tenantA, vendeur1);
        final List<Lead> pourManager = leadRepository.findEnvoisAutoAVenir(tenantA, null);

        // THEN : le vendeur 1 ne voit QUE son lead ; le manager (null) voit les deux
        assertAll(
                () -> assertEquals(1, pourVendeur1.size(), "VENDEUR : seulement ses envois auto"),
                () -> assertEquals(vendeur1, pourVendeur1.get(0).getVendeurId()),
                () -> assertEquals(2, pourManager.size(), "MANAGER (null) : tout le tenant")
        );
    }

    @Test
    void existsByTenantIdAndTelephoneAndArchiveFalseAndIdNot_detecteLaCollisionActive() {
        // GIVEN : un lead ACTIF avec un telephone + un lead ARCHIVE avec le meme numero
        final Lead actif = creerLead(tenantA, "Actif tel", false);
        actif.setTelephone("+33612345678");
        leadRepository.save(actif);
        final Lead archive = creerLead(tenantA, "Archive tel", true);
        archive.setTelephone("+33612345678");
        leadRepository.save(archive);

        // WHEN / THEN : en excluant l'archive lui-meme, on detecte bien l'actif (collision) ;
        // et l'actif ne se detecte pas lui-meme (IdNot). Scope tenant respecte.
        assertAll(
                () -> assertTrue(leadRepository.existsByTenantIdAndTelephoneAndArchiveFalseAndIdNot(
                        tenantA, "+33612345678", archive.getId()), "Collision active detectee"),
                () -> assertTrue(!leadRepository.existsByTenantIdAndTelephoneAndArchiveFalseAndIdNot(
                        tenantA, "+33612345678", actif.getId()), "Un lead ne se detecte pas lui-meme"),
                () -> assertTrue(!leadRepository.existsByTenantIdAndTelephoneAndArchiveFalseAndIdNot(
                        tenantB, "+33612345678", archive.getId()), "Scope tenant respecte")
        );
    }

    @Test
    void countByTenantIdGroupByStatut_compteUniquementLesLeadsDuTenant() {
        // WHEN
        final List<Object[]> stats = leadRepository.countByTenantIdGroupByStatut(tenantA);

        // THEN
        // 2 leads NOUVEAU pour A, 0 pour B (pas dans le resultat)
        final long total = stats.stream()
                .mapToLong(row -> ((Number) row[1]).longValue())
                .sum();
        assertEquals(2L, total, "Le count groupe par statut doit etre tenant-scoped");
    }
}
