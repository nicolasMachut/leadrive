package fr.leadrevive.outreach.service;

import fr.leadrevive.fixtures.SmsConversationFixture;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.outreach.entity.ModePilotageEnum;
import fr.leadrevive.outreach.entity.SmsConversation;
import fr.leadrevive.outreach.entity.StatutWhatsappConfigEnum;
import fr.leadrevive.outreach.entity.TenantWhatsappConfig;
import fr.leadrevive.outreach.exception.WhatsAppNonConfigureException;
import fr.leadrevive.outreach.repository.SmsConversationRepository;
import fr.leadrevive.outreach.repository.SmsMessageRepository;
import fr.leadrevive.outreach.repository.TenantWhatsappConfigRepository;
import fr.leadrevive.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de la securite multi-tenant d'InboxService.
 */
@ExtendWith(MockitoExtension.class)
class InboxServiceIdorTest {

    @Mock private SmsConversationRepository smsConversationRepository;
    @Mock private SmsMessageRepository smsMessageRepository;
    @Mock private LeadRepository leadRepository;
    @Mock private UserRepository userRepository;
    @Mock private TwilioWhatsAppService twilioWhatsAppService;
    @Mock private InboxNotificationService inboxNotificationService;
    @Mock private ModerationService moderationService;
    @Mock private TenantWhatsappConfigRepository tenantWhatsappConfigRepository;

    @InjectMocks
    private InboxService service;

    @Test
    void reprendreMain_conversationAutreTenant_lanceExceptionPasDeSave() {
        // GIVEN
        // Conversation appartient au tenant 1, mais user appelle avec son tenant 999
        when(smsConversationRepository.findByConversationIdAndTenantId("uuid-abc", 999L))
                .thenReturn(Optional.empty());

        // WHEN / THEN
        assertThrows(IllegalArgumentException.class,
                () -> service.reprendreMain("uuid-abc", 999L, 42L));
        verify(smsConversationRepository, never()).save(any());
    }

    @Test
    void reprendreMain_memeTenant_modifieLeMode() {
        // GIVEN
        final SmsConversation conv = SmsConversationFixture.conversationAvecMode(
                "uuid-abc", 1L, ModePilotageEnum.IA_AUTO);
        when(smsConversationRepository.findByConversationIdAndTenantId("uuid-abc", 1L))
                .thenReturn(Optional.of(conv));

        // WHEN
        service.reprendreMain("uuid-abc", 1L, 42L);

        // THEN
        assertAll(
                () -> assertEquals(ModePilotageEnum.HUMAIN, conv.getModePilotage()),
                () -> assertEquals(42L, conv.getRepriseParUserId()),
                () -> verify(smsConversationRepository).save(conv)
        );
    }

    /**
     * Garde-fou opt-out : on ne doit pas pouvoir reprendre la main sur une conversation
     * dont le prospect a explicitement demande l'arret (STOP_DEMANDE). Sinon, le vendeur
     * pourrait envoyer un message manuel et violer l'opt-out RGPD.
     */
    @Test
    void reprendreMain_conversationStopDemande_lanceIllegalState() {
        // GIVEN
        final SmsConversation conv = SmsConversationFixture.conversationAvecMode(
                "uuid-abc", 1L, ModePilotageEnum.IA_AUTO);
        conv.setStatut(fr.leadrevive.outreach.entity.StatutConversationSmsEnum.STOP_DEMANDE);
        when(smsConversationRepository.findByConversationIdAndTenantId("uuid-abc", 1L))
                .thenReturn(Optional.of(conv));

        // WHEN / THEN
        assertThatThrownBy(() -> service.reprendreMain("uuid-abc", 1L, 42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminée");
        verify(smsConversationRepository, never()).save(any());
    }

    @Test
    void rendreIa_conversationAutreTenant_lanceException() {
        // GIVEN
        when(smsConversationRepository.findByConversationIdAndTenantId("uuid-abc", 999L))
                .thenReturn(Optional.empty());

        // WHEN / THEN
        assertThrows(IllegalArgumentException.class,
                () -> service.rendreIa("uuid-abc", 999L));
        verify(smsConversationRepository, never()).save(any());
    }

    @Test
    void marquerLue_conversationAutreTenant_lanceException() {
        // GIVEN : l'UPDATE cible ne touche aucune ligne (mauvais tenant) -> IDOR bloque
        when(smsConversationRepository.updateDerniereLecture(eq("uuid-abc"), eq(999L), any()))
                .thenReturn(0);

        // WHEN / THEN
        assertThrows(IllegalArgumentException.class,
                () -> service.marquerLue("uuid-abc", 999L));
        verify(smsConversationRepository, never()).save(any());
    }

    @Test
    void marquerLue_memeTenant_updateCibleSansSave() {
        // GIVEN : l'UPDATE cible touche 1 ligne (conversation du bon tenant)
        when(smsConversationRepository.updateDerniereLecture(eq("uuid-abc"), eq(1L), any()))
                .thenReturn(1);

        // WHEN
        service.marquerLue("uuid-abc", 1L);

        // THEN : UPDATE cible uniquement, JAMAIS de load+save (qui bumperait @Version et
        // entrerait en conflit optimiste avec le traitement IA concurrent du webhook).
        verify(smsConversationRepository).updateDerniereLecture(eq("uuid-abc"), eq(1L), any());
        verify(smsConversationRepository, never()).findByConversationIdAndTenantId(any(), any());
        verify(smsConversationRepository, never()).save(any());
    }

    @Test
    void envoyerMessageManuel_conversationAutreTenant_lanceExceptionPasDeMessageEnvoye() {
        // GIVEN
        when(smsConversationRepository.findByConversationIdAndTenantId("uuid-abc", 999L))
                .thenReturn(Optional.empty());

        // WHEN / THEN
        assertThrows(IllegalArgumentException.class,
                () -> service.envoyerMessageManuel("uuid-abc", "Hello", 999L, 42L));
        // Aucun appel Twilio (sinon on aurait spamme pour le compte d'un autre tenant)
        verify(twilioWhatsAppService, never()).envoyerWhatsApp(any(), any(), any());
        verify(smsMessageRepository, never()).save(any());
    }

    @Test
    void envoyerMessageManuel_modePasHumain_lanceIllegalState() {
        // GIVEN
        final SmsConversation conv = SmsConversationFixture.conversationAvecMode(
                "uuid-abc", 1L, ModePilotageEnum.IA_AUTO);
        when(smsConversationRepository.findByConversationIdAndTenantId("uuid-abc", 1L))
                .thenReturn(Optional.of(conv));

        // WHEN / THEN
        assertThatThrownBy(() -> service.envoyerMessageManuel("uuid-abc", "Hello", 1L, 42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("manuel");
        verify(twilioWhatsAppService, never()).envoyerWhatsApp(any(), any(), any());
    }

    /**
     * Bug live remonte : le vendeur peut reprendre la main + cliquer "envoyer" alors
     * que WhatsApp Business n'est pas configure. Resultat actuel : message stocke en
     * statut ECHEC, retour 200 OK silencieux, l'utilisateur ne sait pas pourquoi rien ne part.
     *
     * Fix : on leve WhatsAppNonConfigureException AVANT d'enregistrer quoi que ce soit
     * pour que le GlobalExceptionHandler retourne 422 avec un message clair.
     */
    @Test
    void envoyerMessageManuel_whatsappPasConfigure_lanceExceptionSansEnregistrer() {
        // GIVEN : conversation en mode HUMAIN, lead avec consentement, MAIS pas de config WhatsApp.
        final SmsConversation conv = SmsConversationFixture.conversationAvecMode(
                "uuid-abc", 1L, ModePilotageEnum.HUMAIN);
        when(smsConversationRepository.findByConversationIdAndTenantId("uuid-abc", 1L))
                .thenReturn(Optional.of(conv));
        when(tenantWhatsappConfigRepository.findByTenantId(1L)).thenReturn(Optional.empty());

        // WHEN / THEN
        assertThatThrownBy(() -> service.envoyerMessageManuel("uuid-abc", "Hello", 1L, 42L))
                .isInstanceOf(WhatsAppNonConfigureException.class);

        // Aucun appel Twilio, aucun message ECHEC fantome cree, pas d'appel moderation
        verify(twilioWhatsAppService, never()).envoyerWhatsApp(any(), any(), any());
        verify(smsMessageRepository, never()).save(any());
        verify(moderationService, never()).verifier(any(), any(), any(), any());
        verify(leadRepository, never()).findById(any());
    }

    @Test
    void envoyerMessageManuel_whatsappConfigPasActive_lanceException() {
        // GIVEN : config WhatsApp existe mais en statut DESACTIVE (pas ACTIF)
        final SmsConversation conv = SmsConversationFixture.conversationAvecMode(
                "uuid-abc", 1L, ModePilotageEnum.HUMAIN);
        when(smsConversationRepository.findByConversationIdAndTenantId("uuid-abc", 1L))
                .thenReturn(Optional.of(conv));
        final TenantWhatsappConfig cfg = new TenantWhatsappConfig();
        cfg.setTenantId(1L);
        cfg.setStatut(StatutWhatsappConfigEnum.DESACTIVE);
        when(tenantWhatsappConfigRepository.findByTenantId(1L)).thenReturn(Optional.of(cfg));

        // WHEN / THEN
        assertThatThrownBy(() -> service.envoyerMessageManuel("uuid-abc", "Hello", 1L, 42L))
                .isInstanceOf(WhatsAppNonConfigureException.class);

        verify(twilioWhatsAppService, never()).envoyerWhatsApp(any(), any(), any());
        verify(smsMessageRepository, never()).save(any());
    }

    /**
     * Verification du chemin nominal : conversation HUMAIN + config WhatsApp ACTIF
     * + lead avec consentement RGPD => l'envoi va jusqu'a la moderation.
     */
    @Test
    void envoyerMessageManuel_configurationOk_appelleModeration() {
        // GIVEN
        final SmsConversation conv = SmsConversationFixture.conversationAvecMode(
                "uuid-abc", 1L, ModePilotageEnum.HUMAIN);
        conv.setLeadId(10L);
        when(smsConversationRepository.findByConversationIdAndTenantId("uuid-abc", 1L))
                .thenReturn(Optional.of(conv));
        final TenantWhatsappConfig cfg = new TenantWhatsappConfig();
        cfg.setTenantId(1L);
        cfg.setStatut(StatutWhatsappConfigEnum.ACTIF);
        when(tenantWhatsappConfigRepository.findByTenantId(1L)).thenReturn(Optional.of(cfg));
        final Lead lead = new Lead();
        lead.setId(10L);
        lead.setConsentementContact(true);
        when(leadRepository.findById(10L)).thenReturn(Optional.of(lead));
        // Fenetre WhatsApp 24h : prospect a ecrit recemment -> envoi libre autorise.
        when(smsMessageRepository.findDernierMessageEntrantLe("uuid-abc"))
                .thenReturn(Optional.of(java.time.LocalDateTime.now().minusMinutes(5)));
        when(moderationService.verifier(any(), any(), any(), any()))
                .thenReturn(new fr.leadrevive.outreach.service.ModerationService.ResultatModeration(true, null));

        // WHEN
        service.envoyerMessageManuel("uuid-abc", "Bonjour", 1L, 42L);

        // THEN
        verify(moderationService).verifier(eq(1L), eq(42L), eq("uuid-abc"), eq("Bonjour"));
        verify(smsMessageRepository).save(any());
    }

    /**
     * IDOR retry : on ne doit pas pouvoir reessayer un message qui n'appartient pas a
     * CETTE conversation ET a CE tenant. Sinon un vendeur du tenant A pourrait renvoyer
     * le contenu d'un message du tenant B vers le prospect de A (fuite + envoi Twilio).
     */
    /** Construit un message SORTANT en ECHEC, template ou libre selon contentSid. */
    private static fr.leadrevive.outreach.entity.SmsMessage messageEnEchec(
            final Long id, final String conversationId, final Long tenantId,
            final String contentSid, final String contentVariables) {
        final fr.leadrevive.outreach.entity.SmsMessage m = new fr.leadrevive.outreach.entity.SmsMessage();
        m.setId(id);
        m.setConversationId(conversationId);
        m.setTenantId(tenantId);
        m.setDirection(fr.leadrevive.outreach.entity.DirectionSmsEnum.SORTANT);
        m.setContenu("Bonjour Jean, c'est l'équipe.");
        m.setStatut(fr.leadrevive.outreach.entity.StatutSmsEnum.ECHEC);
        m.setErreur("[63016] hors fenetre");
        m.setContentSid(contentSid);
        m.setContentVariables(contentVariables);
        m.setOrdre(0);
        return m;
    }

    private SmsConversation conversationActiveAvecConsentement() {
        final SmsConversation conv = SmsConversationFixture.conversationAvecMode(
                "uuid-abc", 1L, ModePilotageEnum.HUMAIN);
        conv.setLeadId(10L);
        conv.setNumeroProspect("+33612345678");
        when(smsConversationRepository.findByConversationIdAndTenantId("uuid-abc", 1L))
                .thenReturn(Optional.of(conv));
        final TenantWhatsappConfig cfg = new TenantWhatsappConfig();
        cfg.setTenantId(1L);
        cfg.setStatut(StatutWhatsappConfigEnum.ACTIF);
        when(tenantWhatsappConfigRepository.findByTenantId(1L)).thenReturn(Optional.of(cfg));
        final Lead lead = new Lead();
        lead.setId(10L);
        lead.setConsentementContact(true);
        when(leadRepository.findById(10L)).thenReturn(Optional.of(lead));
        return conv;
    }

    /**
     * Retry d'un message TEMPLATE (premier contact / relance) : doit REJOUER le template
     * via son Content SID (autorise hors fenetre 24h), PAS renvoyer le texte en message
     * libre (qui echouerait 63016). Le motif d'echec perime est efface.
     */
    @Test
    void retryMessage_template_rejoueLeTemplateEtEffaceLeMotif() {
        // GIVEN
        conversationActiveAvecConsentement();
        final fr.leadrevive.outreach.entity.SmsMessage msg =
                messageEnEchec(55L, "uuid-abc", 1L, "HX123", "{\"1\":\"Jean\"}");
        when(smsMessageRepository.findByIdAndConversationIdAndTenantId(55L, "uuid-abc", 1L))
                .thenReturn(Optional.of(msg));
        when(twilioWhatsAppService.deserialiserContentVariables(any()))
                .thenReturn(java.util.Map.of("1", "Jean"));
        when(twilioWhatsAppService.envoyerRelanceTemplate(eq("+33612345678"), eq("HX123"), any(), eq(1L)))
                .thenReturn("SM-new");

        // WHEN
        service.retryMessage("uuid-abc", 55L, 1L);

        // THEN : template rejoue, pas d'envoi libre, pas de check 24h, motif efface.
        verify(twilioWhatsAppService).envoyerRelanceTemplate(eq("+33612345678"), eq("HX123"), any(), eq(1L));
        verify(twilioWhatsAppService, never()).envoyerWhatsApp(any(), any(), any());
        verify(smsMessageRepository, never()).findDernierMessageEntrantLe(any());
        assertAll(
                () -> assertEquals(fr.leadrevive.outreach.entity.StatutSmsEnum.ENVOYE, msg.getStatut()),
                () -> assertEquals("SM-new", msg.getBrevoMessageId()),
                () -> org.junit.jupiter.api.Assertions.assertNull(msg.getErreur())
        );
        verify(smsMessageRepository).save(msg);
    }

    /**
     * Retry d'un message LIBRE dans la fenetre 24h : autorise, part via envoyerWhatsApp.
     */
    @Test
    void retryMessage_libreDansFenetre24h_renvoie() {
        // GIVEN
        conversationActiveAvecConsentement();
        final fr.leadrevive.outreach.entity.SmsMessage msg =
                messageEnEchec(56L, "uuid-abc", 1L, null, null);
        when(smsMessageRepository.findByIdAndConversationIdAndTenantId(56L, "uuid-abc", 1L))
                .thenReturn(Optional.of(msg));
        when(smsMessageRepository.findDernierMessageEntrantLe("uuid-abc"))
                .thenReturn(Optional.of(java.time.LocalDateTime.now().minusMinutes(10)));
        when(twilioWhatsAppService.envoyerWhatsApp(eq("+33612345678"), any(), eq(1L)))
                .thenReturn("SM-libre");

        // WHEN
        service.retryMessage("uuid-abc", 56L, 1L);

        // THEN
        verify(twilioWhatsAppService).envoyerWhatsApp(eq("+33612345678"), any(), eq(1L));
        verify(twilioWhatsAppService, never()).envoyerRelanceTemplate(any(), any(), any(), any());
        assertEquals(fr.leadrevive.outreach.entity.StatutSmsEnum.ENVOYE, msg.getStatut());
        verify(smsMessageRepository).save(msg);
    }

    /**
     * Retry d'un message LIBRE hors fenetre 24h : bloque AVANT Twilio avec message clair
     * (et non un rejet Twilio opaque).
     */
    @Test
    void retryMessage_libreHorsFenetre24h_bloqueSansEnvoi() {
        // GIVEN
        conversationActiveAvecConsentement();
        final fr.leadrevive.outreach.entity.SmsMessage msg =
                messageEnEchec(57L, "uuid-abc", 1L, null, null);
        when(smsMessageRepository.findByIdAndConversationIdAndTenantId(57L, "uuid-abc", 1L))
                .thenReturn(Optional.of(msg));
        when(smsMessageRepository.findDernierMessageEntrantLe("uuid-abc"))
                .thenReturn(Optional.of(java.time.LocalDateTime.now().minusHours(25)));

        // WHEN / THEN
        assertThatThrownBy(() -> service.retryMessage("uuid-abc", 57L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("24h");
        verify(twilioWhatsAppService, never()).envoyerWhatsApp(any(), any(), any());
        verify(twilioWhatsAppService, never()).envoyerRelanceTemplate(any(), any(), any(), any());
        verify(smsMessageRepository, never()).save(any());
    }

    /**
     * Retry d'un template alors que WhatsApp n'est plus actif (restreint/desactive) :
     * on bloque, meme garde-fou que l'envoi initial.
     */
    @Test
    void retryMessage_whatsappPasActif_lanceException() {
        // GIVEN
        final SmsConversation conv = SmsConversationFixture.conversationAvecMode(
                "uuid-abc", 1L, ModePilotageEnum.HUMAIN);
        when(smsConversationRepository.findByConversationIdAndTenantId("uuid-abc", 1L))
                .thenReturn(Optional.of(conv));
        final fr.leadrevive.outreach.entity.SmsMessage msg =
                messageEnEchec(58L, "uuid-abc", 1L, "HX123", "{}");
        when(smsMessageRepository.findByIdAndConversationIdAndTenantId(58L, "uuid-abc", 1L))
                .thenReturn(Optional.of(msg));
        when(tenantWhatsappConfigRepository.findByTenantId(1L)).thenReturn(Optional.empty());

        // WHEN / THEN
        assertThatThrownBy(() -> service.retryMessage("uuid-abc", 58L, 1L))
                .isInstanceOf(WhatsAppNonConfigureException.class);
        verify(twilioWhatsAppService, never()).envoyerRelanceTemplate(any(), any(), any(), any());
        verify(smsMessageRepository, never()).save(any());
    }

    /** Retry d'un message qui n'est PAS en echec : refus. */
    @Test
    void retryMessage_messagePasEnEchec_lanceIllegalState() {
        // GIVEN
        final SmsConversation conv = SmsConversationFixture.conversationAvecMode(
                "uuid-abc", 1L, ModePilotageEnum.HUMAIN);
        when(smsConversationRepository.findByConversationIdAndTenantId("uuid-abc", 1L))
                .thenReturn(Optional.of(conv));
        final fr.leadrevive.outreach.entity.SmsMessage msg =
                messageEnEchec(59L, "uuid-abc", 1L, null, null);
        msg.setStatut(fr.leadrevive.outreach.entity.StatutSmsEnum.ENVOYE);
        when(smsMessageRepository.findByIdAndConversationIdAndTenantId(59L, "uuid-abc", 1L))
                .thenReturn(Optional.of(msg));

        // WHEN / THEN
        assertThatThrownBy(() -> service.retryMessage("uuid-abc", 59L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("échec");
        verify(twilioWhatsAppService, never()).envoyerWhatsApp(any(), any(), any());
        verify(twilioWhatsAppService, never()).envoyerRelanceTemplate(any(), any(), any(), any());
    }

    @Test
    void retryMessage_messageHorsConversationOuTenant_lanceExceptionPasDeTwilio() {
        // GIVEN : la conversation existe pour le tenant 1, mais le message demande
        // n'est pas rattache a cette conversation/tenant (scope vide).
        final SmsConversation conv = SmsConversationFixture.conversationAvecMode(
                "uuid-abc", 1L, ModePilotageEnum.HUMAIN);
        when(smsConversationRepository.findByConversationIdAndTenantId("uuid-abc", 1L))
                .thenReturn(Optional.of(conv));
        when(smsMessageRepository.findByIdAndConversationIdAndTenantId(77L, "uuid-abc", 1L))
                .thenReturn(Optional.empty());

        // WHEN / THEN
        assertThrows(IllegalArgumentException.class,
                () -> service.retryMessage("uuid-abc", 77L, 1L));
        verify(twilioWhatsAppService, never()).envoyerWhatsApp(any(), any(), any());
        verify(smsMessageRepository, never()).save(any());
    }

    /**
     * Fenetre WhatsApp 24h : si le prospect n'a pas ecrit depuis plus de 24h, l'envoi
     * libre (message de session) est interdit par Meta. On bloque en amont avec un
     * message clair plutot que de laisser Twilio echouer en silence.
     */
    @Test
    void envoyerMessageManuel_prospectInactifPlusDe24h_bloqueSansEnvoi() {
        // GIVEN : conversation HUMAIN, config ACTIF, consentement OK, mais dernier
        // message entrant date de plus de 24h.
        final SmsConversation conv = SmsConversationFixture.conversationAvecMode(
                "uuid-abc", 1L, ModePilotageEnum.HUMAIN);
        conv.setLeadId(10L);
        when(smsConversationRepository.findByConversationIdAndTenantId("uuid-abc", 1L))
                .thenReturn(Optional.of(conv));
        final TenantWhatsappConfig cfg = new TenantWhatsappConfig();
        cfg.setTenantId(1L);
        cfg.setStatut(StatutWhatsappConfigEnum.ACTIF);
        when(tenantWhatsappConfigRepository.findByTenantId(1L)).thenReturn(Optional.of(cfg));
        final Lead lead = new Lead();
        lead.setId(10L);
        lead.setConsentementContact(true);
        when(leadRepository.findById(10L)).thenReturn(Optional.of(lead));
        when(smsMessageRepository.findDernierMessageEntrantLe("uuid-abc"))
                .thenReturn(Optional.of(java.time.LocalDateTime.now().minusHours(25)));

        // WHEN / THEN
        assertThatThrownBy(() -> service.envoyerMessageManuel("uuid-abc", "Toujours intéressé ?", 1L, 42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("24h");
        verify(twilioWhatsAppService, never()).envoyerWhatsApp(any(), any(), any());
        verify(smsMessageRepository, never()).save(any());
        verify(moderationService, never()).verifier(any(), any(), any(), any());
    }
}
