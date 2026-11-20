package fr.leadrevive.integration;

import fr.leadrevive.outreach.config.TwilioConfig;
import fr.leadrevive.outreach.service.WhatsAppConversationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'integration du webhook Twilio WhatsApp.
 * Verifie le comportement de bout en bout avec MockMvc + securite Spring,
 * incluant la validation de signature et le rejet des payloads invalides.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class WebhookControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TwilioConfig twilioConfig;

    @MockitoBean
    private WhatsAppConversationService whatsAppConversationService;

    private MockMvc mockMvc;
    private boolean dryRunInitial;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        dryRunInitial = twilioConfig.isDryRun();
    }

    @AfterEach
    void restore() {
        twilioConfig.setDryRun(dryRunInitial);
    }

    @Test
    void webhook_dryRunActif_payloadValide_appelleService() throws Exception {
        // GIVEN
        twilioConfig.setDryRun(true);

        // WHEN / THEN
        mockMvc.perform(post("/api/webhook/twilio/whatsapp")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("From", "whatsapp:+33612345678")
                        .param("Body", "Bonjour, oui je suis interesse")
                        .param("MessageSid", "SM_test123"))
                .andExpect(status().isOk())
                .andExpect(content().string("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response></Response>"));

        verify(whatsAppConversationService).traiterReponseProspect(
                eq("+33612345678"), eq("Bonjour, oui je suis interesse"), isNull(), isNull());
    }

    @Test
    void webhook_dryRunActif_avecOriginalRepliedMessageSid_propageAuService() throws Exception {
        // GIVEN
        twilioConfig.setDryRun(true);

        // WHEN / THEN
        mockMvc.perform(post("/api/webhook/twilio/whatsapp")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("From", "whatsapp:+33612345678")
                        .param("Body", "Oui d'accord")
                        .param("MessageSid", "SM_reply")
                        .param("OriginalRepliedMessageSid", "SM_original_xyz"))
                .andExpect(status().isOk());

        verify(whatsAppConversationService).traiterReponseProspect(
                eq("+33612345678"), eq("Oui d'accord"), eq("SM_original_xyz"), isNull());
    }

    @Test
    void webhook_dryRunActif_bodyVide_retourne400EtNAppellePasService() throws Exception {
        // GIVEN
        twilioConfig.setDryRun(true);

        // WHEN / THEN
        mockMvc.perform(post("/api/webhook/twilio/whatsapp")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("From", "whatsapp:+33612345678")
                        .param("Body", "")
                        .param("MessageSid", "SM_test_empty"))
                .andExpect(status().isBadRequest());

        verify(whatsAppConversationService, never()).traiterReponseProspect(any(), any(), any(), any());
    }

    @Test
    void webhook_dryRunOff_signatureManquante_retourne403() throws Exception {
        // GIVEN
        twilioConfig.setDryRun(false);

        // WHEN / THEN
        mockMvc.perform(post("/api/webhook/twilio/whatsapp")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("From", "whatsapp:+33612345678")
                        .param("Body", "Bonjour")
                        .param("MessageSid", "SM_no_sig"))
                .andExpect(status().isForbidden());

        verify(whatsAppConversationService, never()).traiterReponseProspect(any(), any(), any(), any());
    }

    @Test
    void webhook_dryRunOff_signatureBidon_retourne403() throws Exception {
        // GIVEN
        twilioConfig.setDryRun(false);

        // WHEN / THEN
        mockMvc.perform(post("/api/webhook/twilio/whatsapp")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header("X-Twilio-Signature", "signature-clairement-invalide")
                        .param("From", "whatsapp:+33612345678")
                        .param("Body", "Bonjour")
                        .param("MessageSid", "SM_bad_sig"))
                .andExpect(status().isForbidden());

        verify(whatsAppConversationService, never()).traiterReponseProspect(any(), any(), any(), any());
    }
}
