package fr.leadrevive.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifie que la migration V061 a bien cree le schema email-import attendu :
 * <ul>
 *   <li>Tables {@code tenant_email_config} et {@code inbound_email_log}</li>
 *   <li>Contrainte UNIQUE sur {@code tenant_email_config.email_address}</li>
 *   <li>Contrainte UNIQUE sur {@code tenant_email_config.tenant_id} (1 adresse / tenant)</li>
 *   <li>FK vers {@code tenant} et {@code lead}</li>
 *   <li>Defaut {@code statut = 'ACTIVE'} et {@code nb_emails_recus = 0}</li>
 * </ul>
 *
 * <p>Sert de filet de securite : si quelqu'un altere la migration V061 ou ajoute
 * une migration ulterieure qui casse une contrainte, ce test echoue.</p>
 */
class EmailImportSchemaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void tablesExistentApresMigration() {
        final Integer countTenantEmailConfig = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_name = 'tenant_email_config'
                """, Integer.class);
        final Integer countInboundEmailLog = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_name = 'inbound_email_log'
                """, Integer.class);
        assertThat(countTenantEmailConfig).isEqualTo(1);
        assertThat(countInboundEmailLog).isEqualTo(1);
    }

    @Test
    void contrainteUniqueSurEmailAddress() {
        // GIVEN : un tenant + une config existante
        nettoyer();
        final Long tenantA = creerTenant("Garage A");
        final Long tenantB = creerTenant("Garage B");
        jdbcTemplate.update(
                "INSERT INTO tenant_email_config (tenant_id, email_slug, email_address) VALUES (?, ?, ?)",
                tenantA, "garage-a", "duplique@leads.leadrive.fr");

        // WHEN / THEN : impossible d'inserer la meme adresse pour un autre tenant
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO tenant_email_config (tenant_id, email_slug, email_address) VALUES (?, ?, ?)",
                tenantB, "garage-b", "duplique@leads.leadrive.fr"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void contrainteUniqueSurTenantId() {
        // Un tenant ne peut avoir qu'UNE seule adresse email
        nettoyer();
        final Long tenantA = creerTenant("Garage A");
        jdbcTemplate.update(
                "INSERT INTO tenant_email_config (tenant_id, email_slug, email_address) VALUES (?, ?, ?)",
                tenantA, "garage-a", "adresse1@leads.leadrive.fr");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO tenant_email_config (tenant_id, email_slug, email_address) VALUES (?, ?, ?)",
                tenantA, "garage-a-bis", "adresse2@leads.leadrive.fr"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void defautStatutActifEtCompteurZero() {
        nettoyer();
        final Long tenantA = creerTenant("Garage Default");
        jdbcTemplate.update(
                "INSERT INTO tenant_email_config (tenant_id, email_slug, email_address) VALUES (?, ?, ?)",
                tenantA, "garage-default", "default@leads.leadrive.fr");

        final String statut = jdbcTemplate.queryForObject(
                "SELECT statut FROM tenant_email_config WHERE tenant_id = ?", String.class, tenantA);
        final Integer nb = jdbcTemplate.queryForObject(
                "SELECT nb_emails_recus FROM tenant_email_config WHERE tenant_id = ?", Integer.class, tenantA);
        assertThat(statut).isEqualTo("ACTIVE");
        assertThat(nb).isZero();
    }

    @Test
    void fkVersTenantBloqueSiTenantInexistant() {
        nettoyer();
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO tenant_email_config (tenant_id, email_slug, email_address) VALUES (999999, 'x', 'x@x.fr')"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void inboundEmailLog_supportJsonbResultatParsing() {
        nettoyer();
        final Long tenantA = creerTenant("Garage Json");
        jdbcTemplate.update("""
                INSERT INTO inbound_email_log (tenant_id, email_from, statut, resultat_parsing)
                VALUES (?, 'a@b.fr', 'LEAD_CREE', ?::jsonb)
                """, tenantA, "{\"nom\":\"Dupont\",\"confiance\":90}");

        // Verifie qu'on peut bien interroger le JSONB
        final String nom = jdbcTemplate.queryForObject("""
                SELECT resultat_parsing->>'nom' FROM inbound_email_log WHERE tenant_id = ?
                """, String.class, tenantA);
        assertThat(nom).isEqualTo("Dupont");
    }

    private void nettoyer() {
        jdbcTemplate.update("DELETE FROM inbound_email_log");
        jdbcTemplate.update("DELETE FROM tenant_email_config");
        jdbcTemplate.update("DELETE FROM lead");
        jdbcTemplate.update("DELETE FROM lead_import");
        jdbcTemplate.update("DELETE FROM utilisateur");
        jdbcTemplate.update("DELETE FROM tenant");
    }

    private Long creerTenant(final String nom) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO tenant (nom) VALUES (?) RETURNING id_tenant", Long.class, nom);
    }
}
