package fr.leadrevive.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sanity check des migrations Flyway.
 * Garantit que TOUTES les migrations s'appliquent proprement sur une base PostgreSQL vierge.
 * Detecte tres tot les regressions de schema (typo SQL, ordre incorrect, ref a une colonne supprimee, etc.).
 */
class FlywayMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Test
    void flyway_basePropre_appliqueToutesLesMigrations() {
        // GIVEN
        // Le contexte Spring + Flyway a deja tourne au demarrage du test.
        final MigrationInfo[] applied = flyway.info().applied();

        // WHEN
        final MigrationInfo[] pending = flyway.info().pending();

        // THEN
        assertAll(
                () -> assertNotNull(applied, "La liste des migrations appliquees doit exister"),
                () -> assertTrue(applied.length > 0, "Au moins une migration doit etre appliquee"),
                () -> assertTrue(pending.length == 0,
                        "Aucune migration ne doit rester en attente (en attente: " + Arrays.toString(pending) + ")")
        );
    }

    @Test
    void flyway_versionsSontStrictementCroissantes() {
        // GIVEN
        final MigrationInfo[] applied = flyway.info().applied();

        // WHEN / THEN
        for (int i = 1; i < applied.length; i++) {
            final String previous = applied[i - 1].getVersion().getVersion();
            final String current = applied[i].getVersion().getVersion();
            assertTrue(current.compareTo(previous) > 0,
                    "Versions Flyway non croissantes : " + previous + " >= " + current);
        }
    }
}
