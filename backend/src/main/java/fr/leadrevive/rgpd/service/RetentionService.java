package fr.leadrevive.rgpd.service;

import fr.leadrevive.outreach.entity.SmsConversation;
import fr.leadrevive.outreach.entity.StatutConversationSmsEnum;
import fr.leadrevive.outreach.repository.ConversationAnalyseRepository;
import fr.leadrevive.outreach.repository.SmsConversationRepository;
import fr.leadrevive.outreach.repository.SmsMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Purge automatique des conversations terminees depuis plus de 18 mois.
 * Conformite RGPD : limitation de la duree de conservation (Art. 5.1.e).
 *
 * Sont considerees terminees : TERMINEE, STOP_DEMANDE, PROSPECT_REFUSE, ERREUR.
 * Cascade : analyses + messages sont supprimes avec la conversation.
 *
 * Le lead lui-meme n'est PAS purge ici (peut etre reactive plus tard) — il faut un parcours
 * RGPD explicite (RgpdService.supprimerDonneesProspect) pour cela.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RetentionService {

    private static final int MOIS_RETENTION = 18;

    private static final List<StatutConversationSmsEnum> STATUTS_FERMES = List.of(
            StatutConversationSmsEnum.TERMINEE,
            StatutConversationSmsEnum.STOP_DEMANDE,
            StatutConversationSmsEnum.PROSPECT_REFUSE,
            StatutConversationSmsEnum.ERREUR
    );

    private final SmsConversationRepository smsConversationRepository;
    private final SmsMessageRepository smsMessageRepository;
    private final ConversationAnalyseRepository conversationAnalyseRepository;

    /** Tourne tous les dimanches a 4h du matin (faible charge). */
    @Scheduled(cron = "0 0 4 * * SUN", zone = "Europe/Paris")
    @SchedulerLock(name = "purgerConversationsAnciennesRgpd", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
    public void purgerConversationsAnciennes() {
        final LocalDateTime seuil = LocalDateTime.now().minusMonths(MOIS_RETENTION);
        log.info("===== DEBUT Purge RGPD - seuil: {} (conversations fermees depuis plus de {} mois) =====",
                seuil, MOIS_RETENTION);

        // On itere sur tous les statuts fermes — pas de filtre tenant ici, c'est un job global.
        int conversationsPurgees = 0;
        int erreurs = 0;

        // Une seule requete SQL ciblee qui retourne uniquement les conversations a purger,
        // au lieu de charger toute la table en memoire (risque OOM en prod).
        final List<SmsConversation> aPurger = smsConversationRepository.findFermeesAvant(STATUTS_FERMES, seuil);

        // Fix bug-hunter #13 : @Transactional retire du scheduler. Sur un parc de plusieurs
        // milliers de conversations, une seule TX qui tient des heures saturait le pool
        // Hikari et empechait toute requete HTTP utilisateur. Chaque conversation est
        // maintenant purgee dans sa propre TX (REQUIRES_NEW).
        for (final SmsConversation conv : aPurger) {
            try {
                purgerConversation(conv);
                conversationsPurgees++;
            } catch (final Exception e) {
                erreurs++;
                log.error("Erreur purge RGPD - conversationId: {}, cause: {}",
                        conv.getConversationId(), e.getMessage(), e);
            }
        }

        // Log explicite "0 purgees" pour le monitoring : distingue "job a tourne et tout
        // etait deja a jour" d'un "le job n'a pas tourne" (alerte Sentry/cron miss).
        if (conversationsPurgees == 0 && erreurs == 0) {
            log.info("===== FIN Purge RGPD : AUCUNE conversation a purger (OK) =====");
        } else {
            log.info("===== FIN Purge RGPD : {} conversations purgees, {} erreurs =====",
                    conversationsPurgees, erreurs);
        }
    }

    /**
     * Purge isolee d'une conversation (cascade analyses + messages). TX dediee
     * pour eviter le hold de la connexion Hikari sur l'ensemble du run.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void purgerConversation(final SmsConversation conv) {
        final var analyses = conversationAnalyseRepository.findByConversationIdOrderByCreeLe(conv.getConversationId());
        analyses.forEach(conversationAnalyseRepository::delete);

        final var messages = smsMessageRepository.findByConversationIdOrderByOrdre(conv.getConversationId());
        messages.forEach(smsMessageRepository::delete);

        smsConversationRepository.delete(conv);
    }
}
