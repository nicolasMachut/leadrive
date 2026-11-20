package fr.leadrevive.emailimport.service;

import fr.leadrevive.emailimport.repository.InboundEmailLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Purge RGPD des logs d'emails entrants.
 *
 * <p>Le contenu d'un email (PII : nom, téléphone, email, message du prospect) ne doit
 * pas être conservé indéfiniment, en particulier pour les emails rejetés (spam / parse
 * échec) qui ne créent aucun lead, donc auxquels aucun droit à l'oubli ne peut se
 * rattacher autrement. Rétention : 30 jours.</p>
 *
 * <p>@SchedulerLock : exécution unique même en multi-instance Railway.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InboundEmailLogPurgeJob {

    private static final int RETENTION_JOURS = 30;

    private final InboundEmailLogRepository inboundEmailLogRepository;

    @Scheduled(cron = "0 30 3 * * *")
    @SchedulerLock(name = "purgeInboundEmailLog", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    @Transactional
    public void purger() {
        final LocalDateTime limite = LocalDateTime.now().minusDays(RETENTION_JOURS);
        final int supprimes = inboundEmailLogRepository.purgerAvant(limite);
        log.info("Purge RGPD inbound_email_log - retention: {}j, lignes supprimees: {}",
                RETENTION_JOURS, supprimes);
    }
}
