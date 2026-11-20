package fr.leadrevive.outreach.service;

import fr.leadrevive.outreach.repository.WebhookIdempotenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Gere l'idempotence des webhooks externes (Twilio principalement) via une table
 * partagee. Remplace l'ancien cache Caffeine local, inutilisable en multi-instance
 * Railway (les retries Twilio peuvent atterrir sur une replica differente).
 *
 * <p>Le pattern : a la reception d'un webhook, on tente un INSERT en ON CONFLICT
 * DO NOTHING sur la table {@code webhook_idempotence}. Si l'INSERT retourne 1, on
 * traite le message. S'il retourne 0, c'est un retry deja traite, on ack et on
 * passe.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookIdempotenceService {

    private static final int RETENTION_HEURES = 24;

    private final WebhookIdempotenceRepository repository;

    /**
     * Tente d'enregistrer le message comme traite. Retourne {@code true} si c'est
     * une nouvelle entree (-> il faut traiter le webhook), {@code false} si c'est
     * un retry deja traite (-> ignorer).
     *
     * <p>REQUIRES_NEW : on ne veut pas que cette ecriture soit annulee si la
     * transaction du traitement metier echoue (sinon, un re-retry Twilio ne serait
     * pas detecte comme idempotent).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean enregistrerSiNouveau(final String messageSid, final String source) {
        if (messageSid == null || messageSid.isBlank()) {
            // Pas de SID => pas d'idempotence possible, on laisse passer.
            return true;
        }
        final int rowsInserted = repository.insertSiNouveau(messageSid, source, LocalDateTime.now());
        if (rowsInserted == 0) {
            log.info("Webhook deja traite (idempotence DB) - sid: {}, source: {}", messageSid, source);
            return false;
        }
        return true;
    }

    /**
     * Retire le marqueur d'idempotence d'un SID quand le traitement metier a echoue.
     *
     * <p>Le marqueur est pose EN AMONT du traitement (protection contre le traitement
     * concurrent du meme SID sur 2 replicas Railway). Si le traitement metier echoue
     * ensuite, on doit retirer ce marqueur : sinon le retry Twilio serait considere
     * "deja traite" et le prospect resterait sans reponse (incident prod 2026-07-06,
     * cause aggravante). REQUIRES_NEW : la suppression doit commiter meme si la
     * transaction appelante est en rollback.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void supprimerMarqueur(final String messageSid) {
        if (messageSid == null || messageSid.isBlank()) {
            return;
        }
        final int supprimes = repository.supprimerParSid(messageSid);
        if (supprimes > 0) {
            log.warn("Marqueur idempotence retire apres echec traitement - sid: {} (retry Twilio autorise)", messageSid);
        }
    }

    /**
     * Purge les entrees expirees toutes les heures. La retention de 24h couvre les
     * retries Twilio (max ~90s) avec une marge confortable pour le debug.
     */
    @Scheduled(fixedDelay = 3_600_000L)
    @SchedulerLock(name = "purgerWebhookIdempotence", lockAtMostFor = "PT5M", lockAtLeastFor = "PT1M")
    @Transactional
    public void purger() {
        final LocalDateTime seuil = LocalDateTime.now().minusHours(RETENTION_HEURES);
        final int supprimes = repository.purgerAvant(seuil);
        if (supprimes > 0) {
            log.info("Purge webhook_idempotence - lignes supprimees: {}", supprimes);
        }
    }
}
