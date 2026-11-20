package fr.leadrevive.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base de tous les tests d'integration.
 * <p>
 * En local : demarre un PostgreSQL via Testcontainers, partage par tous les sous-tests
 * (static initializer, une seule fois pour toute la JVM).
 * En CI : si SPRING_DATASOURCE_URL est defini (service PostgreSQL du workflow GitHub Actions),
 * on utilise cette base directement, sans Testcontainers.
 * Flyway s'execute automatiquement au demarrage du contexte Spring.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    private static final String CI_URL = System.getenv("SPRING_DATASOURCE_URL");

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        if (CI_URL == null || CI_URL.isBlank()) {
            POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("leadrevive_test")
                    .withUsername("test")
                    .withPassword("test");
            POSTGRES.start();
        } else {
            POSTGRES = null;
        }
    }

    @DynamicPropertySource
    static void datasource(final DynamicPropertyRegistry registry) {
        if (POSTGRES != null) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        } else {
            registry.add("spring.datasource.url", () -> CI_URL);
            registry.add("spring.datasource.username", () -> System.getenv("SPRING_DATASOURCE_USERNAME"));
            registry.add("spring.datasource.password", () -> System.getenv("SPRING_DATASOURCE_PASSWORD"));
        }
    }
}
