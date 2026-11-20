package fr.leadrevive.common.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Configuration ShedLock pour serialiser l'execution des @Scheduled entre replicas.
 *
 * <p>Sans ce verrou, le scale Railway (2 replicas, rolling deploy) ferait executer
 * les jobs deux fois - notamment le scheduler de relance WhatsApp = 2x le cout
 * Twilio + double message au prospect (catastrophique cote marque).
 *
 * <p>defaultLockAtMostFor : duree max d'un lock si le replica crash. 10 min est
 * suffisant pour la plupart des jobs (relance WhatsApp tourne en 1-3 min).
 * defaultLockAtLeastFor : evite qu'un job tres rapide soit re-execute immediatement
 * apres release (utile pour les jobs qui finissent en 50 ms).
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M", defaultLockAtLeastFor = "PT30S")
public class SchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(final DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build()
        );
    }
}
