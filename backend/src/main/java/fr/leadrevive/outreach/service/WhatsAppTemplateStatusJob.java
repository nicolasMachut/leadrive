package fr.leadrevive.outreach.service;

import fr.leadrevive.outreach.config.TwilioConfig;
import fr.leadrevive.outreach.entity.StatutTemplateEnum;
import fr.leadrevive.outreach.entity.TenantWhatsappTemplate;
import fr.leadrevive.outreach.repository.TenantWhatsappTemplateRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.hibernate.Session;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Job de polling Meta des templates WhatsApp (epic 25).
 *
 * <p>Toutes les 5 minutes, interroge Twilio Content API pour chaque template
 * en statut SOUMIS et met a jour le statut local (APPROUVE / REJETE +
 * raison).</p>
 *
 * <p><strong>Cross-tenant</strong> : le job s'execute sans TenantContext.
 * Le filtre Hibernate {@code tenantFilter} est desactive pour pouvoir lister
 * les templates de tous les tenants en une passe. Le polling lui-meme se fait
 * tenant par tenant via {@link WhatsAppTemplateService#raffraichirStatuts(Long)},
 * qui resout les credentials sub-account du tenant.</p>
 *
 * <p><strong>Dry-run</strong> : court-circuite des le boot (les templates
 * dry-run sont auto-APPROUVE a la soumission, rien a poller).</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WhatsAppTemplateStatusJob {

    private final TenantWhatsappTemplateRepository templateRepository;
    private final WhatsAppTemplateService templateService;
    private final TwilioConfig twilioConfig;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Polling toutes les 5 minutes. Premier run 60s apres demarrage pour
     * laisser le boot finir et ne pas saturer l'API au cold start.
     */
    @Scheduled(initialDelay = 60_000L, fixedDelay = 300_000L)
    @SchedulerLock(name = "synchroniserStatutsTemplatesMeta", lockAtMostFor = "PT4M", lockAtLeastFor = "PT30S")
    public void synchroniserStatutsTemplates() {
        if (twilioConfig.isDryRun()) {
            return;
        }
        final Session session = entityManager.unwrap(Session.class);
        session.disableFilter("tenantFilter");
        try {
            // Fix bug-hunter #5 : query SQL ciblee au lieu d'un findAll().stream().filter()
            // qui chargeait toute la table en RAM (OOM possible avec parc concessions).
            final List<TenantWhatsappTemplate> soumis =
                    templateRepository.findAllByStatut(StatutTemplateEnum.SOUMIS);
            if (soumis.isEmpty()) {
                return;
            }
            // Groupe par tenant pour une passe par tenant (un seul fetch des
            // credentials sub-account par boucle).
            final Set<Long> tenantIds = new HashSet<>();
            for (final TenantWhatsappTemplate t : soumis) {
                tenantIds.add(t.getTenantId());
            }
            log.info("Polling templates Meta - tenants: {}, templates: {}",
                    tenantIds.size(), soumis.size());
            for (final Long tenantId : tenantIds) {
                try {
                    templateService.raffraichirStatuts(tenantId);
                } catch (final Exception ex) {
                    log.warn("Polling templates en echec - tenantId: {}, cause: {}",
                            tenantId, ex.getMessage());
                }
            }
        } finally {
            // Re-activation systematique du filtre pour ne pas polluer la
            // session si elle est reutilisee.
            try {
                session.enableFilter("tenantFilter").setParameter("tenantId", -1L);
            } catch (final Exception ignore) {
                // Pas critique si echec - la session sera close en fin de tx.
            }
        }
    }

    /**
     * Polling accelere (60s) cible sur les templates fraichement soumis (moins de
     * 30 min). En categorie UTILITY, Meta auto-approuve en quelques minutes : ce
     * job detecte l'approbation vite pour activer l'envoi rapidement. Le job 5 min
     * ci-dessus reste le filet de securite pour les templates plus anciens.
     */
    @Scheduled(initialDelay = 30_000L, fixedDelay = 60_000L)
    @SchedulerLock(name = "synchroniserStatutsTemplatesMetaRapide",
            lockAtMostFor = "PT50S", lockAtLeastFor = "PT10S")
    public void synchroniserStatutsTemplatesRapide() {
        if (twilioConfig.isDryRun()) {
            return;
        }
        final Session session = entityManager.unwrap(Session.class);
        session.disableFilter("tenantFilter");
        try {
            final LocalDateTime seuil = LocalDateTime.now().minusMinutes(30);
            final List<TenantWhatsappTemplate> recents = templateRepository
                    .findAllByStatutAndSoumisLeAfter(StatutTemplateEnum.SOUMIS, seuil);
            if (recents.isEmpty()) {
                return;
            }
            final Set<Long> tenantIds = new HashSet<>();
            for (final TenantWhatsappTemplate t : recents) {
                tenantIds.add(t.getTenantId());
            }
            log.info("Polling rapide templates Meta - tenants: {}, templates: {}",
                    tenantIds.size(), recents.size());
            for (final Long tenantId : tenantIds) {
                try {
                    templateService.raffraichirStatuts(tenantId);
                } catch (final Exception ex) {
                    log.warn("Polling rapide en echec - tenantId: {}, cause: {}",
                            tenantId, ex.getMessage());
                }
            }
        } finally {
            try {
                session.enableFilter("tenantFilter").setParameter("tenantId", -1L);
            } catch (final Exception ignore) {
                // Pas critique si echec - la session sera close en fin de tx.
            }
        }
    }
}
