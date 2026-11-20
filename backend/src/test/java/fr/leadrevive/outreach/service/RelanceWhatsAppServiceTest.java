package fr.leadrevive.outreach.service;

import fr.leadrevive.ai.entity.TenantAiConfig;
import fr.leadrevive.ai.service.TenantAiConfigService;
import fr.leadrevive.fixtures.LeadFixture;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.outreach.entity.ModePilotageEnum;
import fr.leadrevive.outreach.entity.SmsConversation;
import fr.leadrevive.outreach.entity.SmsMessage;
import fr.leadrevive.outreach.entity.StatutConversationSmsEnum;
import fr.leadrevive.outreach.entity.TenantWhatsappTemplate;
import fr.leadrevive.outreach.repository.SmsConversationRepository;
import fr.leadrevive.outreach.repository.SmsMessageRepository;
import fr.leadrevive.outreach.repository.TenantWhatsappTemplateRepository;
import fr.leadrevive.tache.service.TacheIAService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelanceWhatsAppServiceTest {

    @Mock private SmsConversationRepository smsConversationRepository;
    @Mock private SmsMessageRepository smsMessageRepository;
    @Mock private TwilioWhatsAppService twilioWhatsAppService;
    @Mock private TenantWhatsappTemplateRepository tenantWhatsappTemplateRepository;
    @Mock private InboxNotificationService inboxNotificationService;
    @Mock private TacheIAService tacheIAService;
    @Mock private LeadRepository leadRepository;
    @Mock private TenantAiConfigService tenantAiConfigService;

    private RelanceWhatsAppService service;

    @BeforeEach
    void setUp() {
        service = new RelanceWhatsAppService(
                smsConversationRepository, smsMessageRepository, twilioWhatsAppService,
                tenantWhatsappTemplateRepository, inboxNotificationService, tacheIAService, leadRepository, tenantAiConfigService);
        ReflectionTestUtils.setField(service, "relanceEnabled", true);
        ReflectionTestUtils.setField(service, "maxRelances", 2);
        ReflectionTestUtils.setField(service, "delaiJ1Heures", 24);
        ReflectionTestUtils.setField(service, "delaiJ3Heures", 72);
    }

    @Test
    void traiterRelance_premiereRelance_envoieTemplateJ1() {
        final SmsConversation conv = conversationEligible(0);
        final Lead lead = leadAvecNom(1L);
        when(tenantWhatsappTemplateRepository.findByTenantIdAndTemplateName(1L, "leadrive_relance_j1"))
                .thenReturn(Optional.of(templateAvecSid("HX_J1")));
        when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));
        when(tenantAiConfigService.getConfig(1L)).thenReturn(configAvecConcession("Peugeot Lyon"));
        when(twilioWhatsAppService.getAssistantPrenom()).thenReturn("Nora");
        when(twilioWhatsAppService.envoyerRelanceTemplate(anyString(), eq("HX_J1"), any(), anyLong())).thenReturn("SM_123");
        when(smsMessageRepository.findProchainOrdre("conv-1")).thenReturn(0);

        service.traiterRelance(conv);

        verify(twilioWhatsAppService).envoyerRelanceTemplate(eq("+33612345678"), eq("HX_J1"), any(), eq(1L));
        assertThat(conv.getNombreRelances()).isEqualTo(1);
        assertThat(conv.getDerniereRelanceLe()).isNotNull();
    }

    @Test
    void traiterRelance_deuxiemeRelance_envoieTemplateJ3() {
        final SmsConversation conv = conversationEligible(1);
        final Lead lead = leadAvecNom(1L);
        when(tenantWhatsappTemplateRepository.findByTenantIdAndTemplateName(1L, "leadrive_relance_j3"))
                .thenReturn(Optional.of(templateAvecSid("HX_J3")));
        when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));
        when(tenantAiConfigService.getConfig(1L)).thenReturn(configAvecConcession("Peugeot Lyon"));
        when(twilioWhatsAppService.getAssistantPrenom()).thenReturn("Nora");
        when(twilioWhatsAppService.envoyerRelanceTemplate(anyString(), eq("HX_J3"), any(), anyLong())).thenReturn("SM_456");
        when(smsMessageRepository.findProchainOrdre("conv-1")).thenReturn(0);

        service.traiterRelance(conv);

        verify(twilioWhatsAppService).envoyerRelanceTemplate(eq("+33612345678"), eq("HX_J3"), any(), eq(1L));
        assertThat(conv.getNombreRelances()).isEqualTo(2);
        // Apres la 2eme relance (max=2), prochaine relance = null
        assertThat(conv.getProchaineRelanceLe()).isNull();
    }

    @Test
    void traiterRelance_maxAtteint_creeTacheEtArreteRelances() {
        final SmsConversation conv = conversationEligible(2);

        service.traiterRelance(conv);

        verify(twilioWhatsAppService, never()).envoyerRelanceTemplate(any(), any(), any(), any());
        verify(tacheIAService).onMaxRelancesAtteint(1L, 1L, 99L);
        assertThat(conv.getProchaineRelanceLe()).isNull();
    }

    @Test
    void traiterRelance_leadOptOut_annuleRelance() {
        final SmsConversation conv = conversationEligible(0);
        final Lead lead = leadAvecNom(1L);
        lead.setOptOutSms(true);
        when(tenantWhatsappTemplateRepository.findByTenantIdAndTemplateName(1L, "leadrive_relance_j1"))
                .thenReturn(Optional.of(templateAvecSid("HX_J1")));
        // construireVariables appelle leadRepository.findById, puis le code re-appelle pour opt-out
        when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));
        when(tenantAiConfigService.getConfig(1L)).thenReturn(configAvecConcession("Test"));
        when(twilioWhatsAppService.getAssistantPrenom()).thenReturn("Nora");

        service.traiterRelance(conv);

        verify(twilioWhatsAppService, never()).envoyerRelanceTemplate(any(), any(), any(), any());
        assertThat(conv.getProchaineRelanceLe()).isNull();
    }

    @Test
    void traiterRelance_templateNonConfigure_neRienFaire() {
        final SmsConversation conv = conversationEligible(0);
        when(tenantWhatsappTemplateRepository.findByTenantIdAndTemplateName(1L, "leadrive_relance_j1"))
                .thenReturn(Optional.empty());

        service.traiterRelance(conv);

        verify(twilioWhatsAppService, never()).envoyerRelanceTemplate(any(), any(), any(), any());
    }

    @Test
    void traiterRelance_sauvegardeLeBonMessage() {
        final SmsConversation conv = conversationEligible(0);
        final Lead lead = leadAvecNom(1L);
        lead.setNom("Dupont");
        lead.setMarque("Peugeot");
        lead.setModele("3008");
        when(tenantWhatsappTemplateRepository.findByTenantIdAndTemplateName(1L, "leadrive_relance_j1"))
                .thenReturn(Optional.of(templateAvecSid("HX_J1")));
        when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));
        when(tenantAiConfigService.getConfig(1L)).thenReturn(configAvecConcession("Auto Lyon"));
        when(twilioWhatsAppService.getAssistantPrenom()).thenReturn("Nora");
        when(twilioWhatsAppService.envoyerRelanceTemplate(anyString(), anyString(), any(), anyLong())).thenReturn("SM_789");
        when(smsMessageRepository.findProchainOrdre("conv-1")).thenReturn(0);

        service.traiterRelance(conv);

        final ArgumentCaptor<SmsMessage> captor = ArgumentCaptor.forClass(SmsMessage.class);
        verify(smsMessageRepository).save(captor.capture());
        final SmsMessage msg = captor.getValue();
        // Le template Meta-approuve ne contient PAS le prenom du prospect (volontaire) :
        // "Bonjour, c'est {{1}} de {{2}}. Je reviens vers vous au sujet de votre projet de {{3}}..."
        // {{1}} = assistant, {{2}} = concession, {{3}} = vehicule
        assertThat(msg.getContenu()).contains("Nora");
        assertThat(msg.getContenu()).contains("Auto Lyon");
        assertThat(msg.getContenu()).contains("Peugeot 3008");
        assertThat(msg.getConversationId()).isEqualTo("conv-1");
    }

    private SmsConversation conversationEligible(final int nombreRelances) {
        final SmsConversation conv = new SmsConversation();
        conv.setId(1L);
        conv.setTenantId(1L);
        conv.setLeadId(1L);
        conv.setUserId(99L);
        conv.setConversationId("conv-1");
        conv.setStatut(StatutConversationSmsEnum.EN_COURS);
        conv.setModePilotage(ModePilotageEnum.IA_AUTO);
        conv.setNumeroProspect("+33612345678");
        conv.setNombreMessages(1);
        conv.setDernierMessageLe(LocalDateTime.now().minusDays(2));
        conv.setNombreRelances(nombreRelances);
        conv.setProchaineRelanceLe(LocalDateTime.now().minusHours(1));
        return conv;
    }

    private Lead leadAvecNom(final Long id) {
        final Lead lead = LeadFixture.leadAvecTenant(1L);
        lead.setId(id);
        lead.setOptOutSms(false);
        return lead;
    }

    private TenantAiConfig configAvecConcession(final String nom) {
        final TenantAiConfig config = new TenantAiConfig();
        config.setNomConcession(nom);
        return config;
    }

    private TenantWhatsappTemplate templateAvecSid(final String sid) {
        final TenantWhatsappTemplate template = new TenantWhatsappTemplate();
        template.setTemplateSid(sid);
        return template;
    }
}
