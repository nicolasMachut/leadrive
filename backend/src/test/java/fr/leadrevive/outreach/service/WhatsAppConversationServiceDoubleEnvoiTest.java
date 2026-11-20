package fr.leadrevive.outreach.service;

import fr.leadrevive.ai.repository.AiLogRepository;
import fr.leadrevive.ai.service.AiCoutService;
import fr.leadrevive.ai.service.AiModelResolver;
import fr.leadrevive.outreach.entity.SmsConversation;
import fr.leadrevive.outreach.entity.StatutSmsEnum;
import fr.leadrevive.outreach.repository.SmsConversationRepository;
import fr.leadrevive.outreach.repository.SmsMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Non-regression anti double-envoi (point residuel du fix optimistic-locking) :
 * une fois la reponse ENVOYEE au prospect (action externe irreversible), un echec du
 * bookkeeping post-envoi (ex. conflit optimiste sur la conversation dont la version a
 * bouge pendant la generation IA de 3-8s) ne doit JAMAIS remonter d'exception. Sinon
 * elle atteindrait le WebhookController -> 500 -> retry Twilio -> 2e message au prospect.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WhatsAppConversationServiceDoubleEnvoiTest {

    @Mock private SmsConversationRepository smsConversationRepository;
    @Mock private SmsMessageRepository smsMessageRepository;
    @Mock private InboxNotificationService inboxNotificationService;
    @Mock private AiLogRepository aiLogRepository;
    @Mock private AiCoutService aiCoutService;
    @Mock private AiModelResolver aiModelResolver;

    @InjectMocks
    private WhatsAppConversationService service;

    private SmsConversation conversation() {
        final SmsConversation conv = new SmsConversation();
        conv.setConversationId("conv-1");
        conv.setTenantId(1L);
        conv.setUserId(42L);
        conv.setLeadId(7L);
        return conv;
    }

    @Test
    void persisterReponseSortante_conflitOptimisteSurBookkeeping_aucuneExceptionRemontee() {
        // GIVEN : le message sortant est bien persiste (INSERT), mais l'UPDATE de bookkeeping
        // leve un conflit optimiste (version conversation bumpee par une ecriture concurrente).
        when(smsConversationRepository.updateBookkeepingApresEnvoi(eq("conv-1"), anyInt(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException("SmsConversation", 24L));

        // WHEN / THEN : AUCUNE exception ne remonte -> le webhook renverra 200 -> pas de retry
        // Twilio -> pas de double-envoi au prospect.
        assertDoesNotThrow(() -> service.persisterReponseSortanteBestEffort(
                conversation(), "Bonjour, avec plaisir !", StatutSmsEnum.ENVOYE, "SM-sid", 5, "ok"));

        // Le message sortant a quand meme ete tente en persistance (best-effort).
        verify(smsMessageRepository).save(any());
    }

    @Test
    void persisterReponseSortante_echecPersistanceMessage_aucuneExceptionRemontee() {
        // GIVEN : meme l'INSERT du message sortant echoue (contrainte, DB indispo transitoire).
        when(smsMessageRepository.save(any()))
                .thenThrow(new RuntimeException("DB indisponible"));

        // WHEN / THEN : le prospect a deja recu sa reponse, on n'echoue pas le webhook.
        assertDoesNotThrow(() -> service.persisterReponseSortanteBestEffort(
                conversation(), "Reponse", StatutSmsEnum.ENVOYE, "SM-sid", 5, "ok"));
    }
}
