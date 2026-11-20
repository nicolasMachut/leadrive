package fr.leadrevive.outreach.service;

import fr.leadrevive.outreach.dto.ExtractionAnalyseDto;
import fr.leadrevive.outreach.dto.RepConversationAnalyseDto;
import fr.leadrevive.outreach.entity.SmsMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests de non-regression du fix optimistic-locking (incident prod 2026-07-06) :
 * l'application de l'analyse doit etre RETENTEE sur conflit optimiste concurrent
 * (ex. marquage lu par un manager pendant l'appel IA ~10s), puis echouer proprement
 * apres epuisement des tentatives.
 *
 * <p>La transaction courte reelle (TransactionTemplate REQUIRES_NEW) est remplacee ici par
 * un template qui execute directement le callback (pas de DB), et {@code appliquerAnalyse}
 * est stubbe via un spy pour piloter succes/conflit et compter les tentatives.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConversationAnalyseServiceRetryTest {

    @InjectMocks
    private ConversationAnalyseService service;

    private ConversationAnalyseService spied;

    @BeforeEach
    void setUp() {
        spied = spy(service);
        // TransactionTemplate qui execute directement le callback, sans vraie transaction :
        // permet de tester la logique de retry sans base ni transaction manager.
        final TransactionTemplate directTemplate = new TransactionTemplate() {
            @Override
            public <T> T execute(final TransactionCallback<T> action) {
                return action.doInTransaction(null);
            }
        };
        ReflectionTestUtils.setField(spied, "txTemplateRequiresNew", directTemplate);
    }

    @Test
    void appliquerAnalyseAvecRetry_conflitOptimistePuisSucces_rejoueEtAboutit() {
        // GIVEN : appliquerAnalyse echoue 1x (conflit optimiste) puis reussit
        final SmsMessage message = new SmsMessage();
        final ExtractionAnalyseDto extraction = new ExtractionAnalyseDto();
        final RepConversationAnalyseDto attendu = RepConversationAnalyseDto.builder().build();

        doThrow(new ObjectOptimisticLockingFailureException("SmsConversation", 24L))
                .doReturn(attendu)
                .when(spied).appliquerAnalyse("conv-1", message, extraction);

        // WHEN
        final RepConversationAnalyseDto resultat =
                spied.appliquerAnalyseAvecRetry("conv-1", message, extraction);

        // THEN : 2 appels (1 echec + 1 succes), resultat non null
        assertNotNull(resultat);
        assertSame(attendu, resultat);
        verify(spied, times(2)).appliquerAnalyse("conv-1", message, extraction);
    }

    @Test
    void appliquerAnalyseAvecRetry_conflitOptimistePersistant_propageApres3Tentatives() {
        // GIVEN : appliquerAnalyse echoue systematiquement
        final SmsMessage message = new SmsMessage();
        final ExtractionAnalyseDto extraction = new ExtractionAnalyseDto();

        doThrow(new ObjectOptimisticLockingFailureException("SmsConversation", 24L))
                .when(spied).appliquerAnalyse("conv-1", message, extraction);

        // WHEN / THEN : propage apres 3 tentatives (l'appelant decide quoi faire)
        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> spied.appliquerAnalyseAvecRetry("conv-1", message, extraction));
        verify(spied, times(3)).appliquerAnalyse("conv-1", message, extraction);
    }
}
