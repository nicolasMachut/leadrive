package fr.leadrevive.outreach.service;

import fr.leadrevive.outreach.entity.DirectionSmsEnum;
import fr.leadrevive.outreach.entity.SmsConversation;
import fr.leadrevive.outreach.entity.SmsMessage;
import fr.leadrevive.outreach.entity.StatutSmsEnum;
import fr.leadrevive.outreach.repository.SmsMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persiste le message entrant d'un prospect dans une transaction PROPRE, isolee
 * du traitement IA qui suit.
 *
 * <p><strong>Pourquoi un bean separe ?</strong> {@code traiterReponseProspect} est
 * une longue {@code @Transactional} (sauvegarde entrant + analyse IA + reponse
 * sortante, 2-5s). Une notification SSE {@code notifierApresCommit} enregistree
 * dans cette transaction ne partirait qu'a son commit final : le message du
 * prospect n'apparaitrait donc qu'au moment ou l'IA repond. En commitant l'entrant
 * dans une transaction {@code REQUIRES_NEW} distincte (qui necessite un appel
 * inter-bean pour que le proxy AOP s'active), la donnee est durable des le retour
 * de cette methode : l'appelant peut alors notifier l'inbox IMMEDIATEMENT (SSE
 * synchrone, pas via afterCommit), et le message s'affiche des sa reception.</p>
 *
 * <p><strong>Note :</strong> la notification n'est volontairement PAS faite ici via
 * {@code notifierApresCommitPourVendeur}. Un afterCommit enregistre dans cette
 * transaction interne ne se declenchait pas de facon fiable (la notification SSE de
 * l'entrant ne partait pas, alors que la reponse IA - notifiee par la transaction
 * englobante - s'affichait). L'appelant notifie donc directement apres le retour.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InboxEntrantWriter {

    private final SmsMessageRepository smsMessageRepository;

    /**
     * Sauvegarde le message entrant (statut RECU) dans une transaction propre qui
     * commit immediatement. L'appelant doit notifier l'inbox APRES le retour (la
     * donnee est alors deja committee -> SSE instantane et coherent).
     *
     * @return le message persiste (detache une fois cette transaction terminee ;
     *         l'appelant ne doit en lire que les champs, pas le re-persister).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SmsMessage persister(final SmsConversation conversation,
                                final String contenu, final int ordre) {
        final SmsMessage msg = new SmsMessage();
        msg.setTenantId(conversation.getTenantId());
        msg.setConversationId(conversation.getConversationId());
        msg.setDirection(DirectionSmsEnum.ENTRANT);
        msg.setContenu(contenu);
        msg.setStatut(StatutSmsEnum.RECU);
        msg.setExpediteur(null);
        msg.setOrdre(ordre);
        return smsMessageRepository.save(msg);
    }
}
