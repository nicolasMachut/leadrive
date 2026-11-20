package fr.leadrevive.integration;

import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.entity.LeadStatutEnum;
import fr.leadrevive.lead.repository.LeadRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Filet de securite multi-tenant : verifie que le filtre Hibernate global {@code tenantFilter}
 * filtre AUTOMATIQUEMENT toutes les requetes JPA quand il est active.
 * <p>
 * Ce test garantit qu'un dev qui ajouterait demain un repo {@code findAll()} sans clause
 * tenantId ne provoquerait pas une fuite de donnees inter-tenants.
 */
@Transactional
class HibernateTenantFilterIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long tenantA;
    private Long tenantB;

    @BeforeEach
    void seed() {
        // GIVEN
        // Nettoyage : les autres tests d'integration peuvent laisser des leads en base.
        // Sans @Transactional + rollback automatique, on assure ici un etat propre.
        jdbcTemplate.update("DELETE FROM inbound_email_log");
        jdbcTemplate.update("DELETE FROM tenant_email_config");
        jdbcTemplate.update("DELETE FROM lead");
        jdbcTemplate.update("DELETE FROM lead_import");
        jdbcTemplate.update("DELETE FROM utilisateur");
        jdbcTemplate.update("DELETE FROM tenant");

        tenantA = jdbcTemplate.queryForObject(
                "INSERT INTO tenant (nom) VALUES ('Tenant A') RETURNING id_tenant", Long.class);
        tenantB = jdbcTemplate.queryForObject(
                "INSERT INTO tenant (nom) VALUES ('Tenant B') RETURNING id_tenant", Long.class);

        creerLead(tenantA, "Lead A1");
        creerLead(tenantA, "Lead A2");
        creerLead(tenantB, "Lead B1");
        creerLead(tenantB, "Lead B2");
        creerLead(tenantB, "Lead B3");
    }

    @AfterEach
    void cleanup() {
        // S'assure que le filtre est desactive entre les tests
        entityManager.unwrap(Session.class).disableFilter("tenantFilter");
    }

    private void creerLead(final Long tenantId, final String nom) {
        final Lead lead = new Lead();
        lead.setTenantId(tenantId);
        lead.setNom(nom);
        lead.setStatut(LeadStatutEnum.NOUVEAU);
        leadRepository.save(lead);
    }

    @Test
    void findAll_filtreActifSurTenantA_retourneUniquementLeadsDeA() {
        // GIVEN
        // Active le filtre Hibernate sur tenantA (ce que fait le TenantFilter en prod)
        entityManager.unwrap(Session.class)
                .enableFilter("tenantFilter")
                .setParameter("tenantId", tenantA);

        // WHEN
        // Methode standard JpaRepository SANS clause tenantId — c'est tout l'interet du filtre
        final List<Lead> tous = leadRepository.findAll();

        // THEN
        assertAll(
                () -> assertEquals(2, tous.size(),
                        "Filtre actif sur A : on doit voir uniquement les 2 leads de A"),
                () -> assertTrue(tous.stream().allMatch(l -> l.getTenantId().equals(tenantA)),
                        "Aucun lead du tenant B ne doit fuiter")
        );
    }

    @Test
    void findAll_filtreActifSurTenantB_retourneUniquementLeadsDeB() {
        // GIVEN
        entityManager.unwrap(Session.class)
                .enableFilter("tenantFilter")
                .setParameter("tenantId", tenantB);

        // WHEN
        final List<Lead> tous = leadRepository.findAll();

        // THEN
        assertEquals(3, tous.size(), "Filtre actif sur B : 3 leads attendus");
        assertTrue(tous.stream().allMatch(l -> l.getTenantId().equals(tenantB)));
    }

    @Test
    void findAll_sansFiltre_voitTousLesLeads_documenteLeRisque() {
        // GIVEN
        // Pas d'enableFilter — c'est le mode "danger" qui ne doit JAMAIS arriver en prod.
        // Ce test documente le comportement et garantit que le risque est reel
        // → renforce l'importance du TenantFilter interceptor.

        // WHEN
        final List<Lead> tous = leadRepository.findAll();

        // THEN
        assertEquals(5, tous.size(),
                "Sans le filtre Hibernate, findAll voit TOUS les tenants (= la raison d'etre du filtre)");
    }

    @Test
    void filtreActif_findById_BYPASSE_LeFiltre_documenteLaLimitation() {
        // GIVEN
        // Recupere un lead de B via JDBC pour avoir son id reel
        final Long idLeadB = jdbcTemplate.queryForObject(
                "SELECT id_lead FROM lead WHERE tenant_id = ? LIMIT 1",
                Long.class, tenantB);

        // Active le filtre sur tenantA
        entityManager.unwrap(Session.class)
                .enableFilter("tenantFilter")
                .setParameter("tenantId", tenantA);
        entityManager.clear();

        // WHEN
        // ATTENTION : findById() de JpaRepository appelle EntityManager.find(),
        // qui charge l'entite par cle primaire SANS appliquer les filtres Hibernate.
        // C'est un comportement documente d'Hibernate (Session.get / find bypass les filters).
        final var result = leadRepository.findById(idLeadB);

        // THEN
        // CE QUI EST CRITIQUE :
        // → JAMAIS utiliser findById() seul dans le code metier multi-tenant.
        // → TOUJOURS utiliser findByIdAndTenantId(id, tenantId) (pattern present partout dans le repo).
        // Ce test verrouille ce comportement pour qu'il soit visible si quelqu'un essaie
        // de "simplifier" en passant a findById().
        assertTrue(result.isPresent(),
                "Comportement Hibernate documente : findById bypass les filtres → utiliser findByIdAndTenantId");
    }
}
