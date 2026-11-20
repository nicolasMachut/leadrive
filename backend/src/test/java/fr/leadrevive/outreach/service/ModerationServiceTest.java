package fr.leadrevive.outreach.service;

import fr.leadrevive.outreach.entity.MessageModerationLog;
import fr.leadrevive.outreach.repository.MessageModerationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests du ModerationService : court-circuit messages courts, parsing JSON robuste,
 * fail-open en cas d'erreur IA, log systematique pour audit Meta.
 */
@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {

    @Mock private ChatClient.Builder chatClientBuilder;
    @Mock private MessageModerationLogRepository moderationLogRepository;

    private ModerationService service;

    @BeforeEach
    void setUp() {
        service = new ModerationService(chatClientBuilder, moderationLogRepository, new ObjectMapper());
    }

    @Test
    void verifier_messageCourt_autoriseSansAppelIa() {
        // GIVEN : "ok" = 2 chars, sous le seuil de 5
        // WHEN
        final ModerationService.ResultatModeration r = service.verifier(1L, 42L, "uuid", "ok");
        // THEN : court-circuit immediat, pas d'appel ChatClient
        assertThat(r.autorise()).isTrue();
        verify(chatClientBuilder, never()).build();
        // Log quand meme cree pour audit Meta
        verify(moderationLogRepository).save(any());
    }

    @Test
    void verifier_messageNull_autorise() {
        final ModerationService.ResultatModeration r = service.verifier(1L, 42L, "uuid", null);
        assertThat(r.autorise()).isTrue();
        verify(chatClientBuilder, never()).build();
    }

    @Test
    void verifier_messageWhitespace_autorise() {
        // "   " = 3 chars apres trim => sous le seuil
        final ModerationService.ResultatModeration r = service.verifier(1L, 42L, "uuid", "   ");
        assertThat(r.autorise()).isTrue();
        verify(chatClientBuilder, never()).build();
    }

    @Test
    void verifier_iaPlante_failOpenAutorise() {
        // GIVEN : appel ChatClient lance une exception
        when(chatClientBuilder.build()).thenThrow(new RuntimeException("Anthropic 529 Overloaded"));

        // WHEN
        final ModerationService.ResultatModeration r = service.verifier(
                1L, 42L, "uuid", "Bonjour Jean, votre RDV est confirme pour demain 14h.");

        // THEN : fail-open obligatoire (Lieu vendeur ne doit pas etre bloque par panne IA)
        assertThat(r.autorise()).isTrue();
        assertThat(r.raison()).contains("Erreur IA");

        // Log systematique meme en cas d'erreur (audit Meta requis)
        final ArgumentCaptor<MessageModerationLog> captor = ArgumentCaptor.forClass(MessageModerationLog.class);
        verify(moderationLogRepository).save(captor.capture());
        assertThat(captor.getValue().getDecision()).isEqualTo("AUTORISE");
        assertThat(captor.getValue().getRaison()).contains("Erreur IA");
    }

    @Test
    void verifier_logToujoursPersisteAvecTenantEtUser() {
        // GIVEN : court-circuit (message court) pour eviter le mock ChatClient
        lenient().when(chatClientBuilder.build()).thenReturn(null);

        // WHEN
        service.verifier(99L, 7L, "conv-xyz", "ok");

        // THEN : tenantId / userId / conversationId presents dans le log audit Meta
        final ArgumentCaptor<MessageModerationLog> captor = ArgumentCaptor.forClass(MessageModerationLog.class);
        verify(moderationLogRepository).save(captor.capture());
        final MessageModerationLog logEntry = captor.getValue();
        assertThat(logEntry.getTenantId()).isEqualTo(99L);
        assertThat(logEntry.getUserId()).isEqualTo(7L);
        assertThat(logEntry.getConversationId()).isEqualTo("conv-xyz");
        assertThat(logEntry.getDecision()).isEqualTo("AUTORISE");
    }
}
