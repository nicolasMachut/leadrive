package fr.leadrevive.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base de tous les tests d'integration.
 * <p>
 * Demarre un PostgreSQL via Testcontainers, partage par tous les sous-tests.
 * Le container est demarre une seule fois (static initializer) pour toute la JVM
 * et ne s'arrete que via le shutdown hook JVM. Cela evite de redemarrer Postgres
 * entre chaque classe de test (gain temps tres important).
 * Flyway s'execute automatiquement au demarrage du contexte Spring.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("leadrevive_test")
                .withUsername("test")
                .withPassword("test");
        POSTGRES.start();
    }
}
