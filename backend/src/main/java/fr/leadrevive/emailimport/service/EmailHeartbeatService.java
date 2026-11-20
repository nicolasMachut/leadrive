package fr.leadrevive.emailimport.service;

import fr.leadrevive.common.util.PiiMasker;
import fr.leadrevive.emailimport.config.EmailImportConfig;
import fr.leadrevive.emailimport.entity.TenantEmailConfig;
import fr.leadrevive.emailimport.repository.TenantEmailConfigRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.hibernate.Session;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Job @Scheduled quotidien qui detecte les configs email "endormies" :
 * concessions ayant deja recu au moins 1 email, dont la derniere reception
 * date de plus de X jours (configurable via {@code leadrevive.email-import.heartbeat-alerte-jours}).
 *
 * <p>Indicateur d'une redirection cassee cote client (changement de provider,
 * filtre supprime, etc.). Pour l'instant : simple log warning. Une notification
 * email super admin pourra etre ajoutee plus tard si necessaire.</p>
 *
 * <p>Cross-tenant : tourne sans TenantContext, le filtre Hibernate
 * {@code tenantFilter} est desactive pour voir toutes les configs.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailHeartbeatService {

    private final TenantEmailConfigRepository tenantEmailConfigRepository;
    private final EmailImportConfig emailImportConfig;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Tous les jours a 8h (heure de Paris cf. spring.jackson.time-zone).
     */
    @Scheduled(cron = "0 0 8 * * *")
    @SchedulerLock(name = "verifierHeartbeatEmail", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void verifierHeartbeat() {
        final int seuilJours = emailImportConfig.getHeartbeatAlerteJours();
        log.info("Verification heartbeat email - seuil: {} jours", seuilJours);

        // Fix bug-hunter #12 : disableFilter sans try/finally laissait le filtre desactive
        // sur la Session si elle est reutilisee dans le pool (fuite cross-tenant garantie).
        final Session session = entityManager.unwrap(Session.class);
        session.disableFilter("tenantFilter");
        try {
            final LocalDateTime seuil = LocalDateTime.now().minusDays(seuilJours);
            final List<TenantEmailConfig> configsEndormies =
                    tenantEmailConfigRepository.findHeartbeatPerdu(seuil);

            if (configsEndormies.isEmpty()) {
                log.info("Heartbeat email OK - aucune config endormie");
                return;
            }

            log.warn("Heartbeat email - {} config(s) endormie(s) detectee(s)", configsEndormies.size());
            for (final TenantEmailConfig config : configsEndormies) {
                final long joursSansEmail = config.getDernierEmailRecuLe() != null
                        ? java.time.Duration.between(config.getDernierEmailRecuLe(), LocalDateTime.now()).toDays()
                        : -1;
                log.warn("Redirection email probablement cassee - tenantId: {}, email: {}, dernier email: il y a {} jours",
                        config.getTenantId(),
                        PiiMasker.email(config.getEmailAddress()),
                        joursSansEmail);
            }
        } finally {
            // Re-active le filtre pour ne pas polluer la session si reutilisee. Tenant
            // sentinelle -1L : aucun tenant ne match, securise par defaut.
            try {
                session.enableFilter("tenantFilter").setParameter("tenantId", -1L);
            } catch (final Exception ignore) {
                // Pas critique si la session est deja close.
            }
        }
    }
}
