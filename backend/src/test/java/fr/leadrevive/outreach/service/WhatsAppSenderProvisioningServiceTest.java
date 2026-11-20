package fr.leadrevive.outreach.service;

import fr.leadrevive.outreach.config.TwilioConfig;
import fr.leadrevive.outreach.entity.StatutWhatsappConfigEnum;
import fr.leadrevive.outreach.entity.TenantWhatsappConfig;
import fr.leadrevive.outreach.exception.WhatsAppOnboardingException;
import fr.leadrevive.outreach.integration.TwilioSendersApiClient;
import fr.leadrevive.outreach.repository.TenantWhatsappConfigRepository;
import fr.leadrevive.tenant.entity.Tenant;
import fr.leadrevive.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de la création du WhatsApp Sender chez Twilio (étape manquante du flow,
 * rétablie juin 2026). Couvre la création réelle, les pré-requis manquants et
 * le mode dry-run.
 */
@ExtendWith(MockitoExtension.class)
class WhatsAppSenderProvisioningServiceTest {

    @Mock private TenantWhatsappConfigRepository configRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private TwilioSendersApiClient sendersApiClient;
    @Mock private TwilioConfig twilioConfig;
    @Mock private WhatsAppTemplateService templateService;

    private WhatsAppSenderProvisioningService service;

    @BeforeEach
    void setUp() {
        service = new WhatsAppSenderProvisioningService(
                configRepository, tenantRepository, sendersApiClient, twilioConfig, templateService);
        ReflectionTestUtils.setField(service, "backendUrl", "https://api.test");
        lenient().when(configRepository.save(any(TenantWhatsappConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private TenantWhatsappConfig configComplete() {
        final TenantWhatsappConfig c = new TenantWhatsappConfig();
        c.setTenantId(5L);
        c.setTwilioSubAccountSid("AC_sub");
        c.setTwilioSubAuthToken("token");
        c.setWabaId("WABA_1");
        c.setWhatsappFrom("+33611111111");
        c.setStatut(StatutWhatsappConfigEnum.ERREUR);
        return c;
    }

    @Test
    void creation_reelle_stockeLeSidEtReengageLaValidation() {
        final TenantWhatsappConfig config = configComplete();
        when(configRepository.findByTenantId(5L)).thenReturn(Optional.of(config));
        when(twilioConfig.isDryRun()).thenReturn(false);
        final Tenant tenant = new Tenant();
        tenant.setNom("Garage Test");
        when(tenantRepository.findById(5L)).thenReturn(Optional.of(tenant));
        when(sendersApiClient.creerSender(anyString(), anyString(), anyString(), any(), any(), any(), anyString(), anyString()))
                .thenReturn(new TwilioSendersApiClient.SenderCreationResponse("XEnew123", "CREATING"));

        final TenantWhatsappConfig result = service.provisionner(5L);

        assertThat(result.getTwilioSenderSid()).isEqualTo("XEnew123");
        assertThat(result.getMetaStatus()).isEqualTo("CREATING");
        assertThat(result.getStatut()).isEqualTo(StatutWhatsappConfigEnum.EN_VALIDATION);

        // profile.name + les 3 webhooks (callback entrants, fallback entrants, status).
        verify(sendersApiClient).creerSender(eq("+33611111111"), eq("WABA_1"), eq("Garage Test"),
                eq("https://api.test/api/webhook/twilio/whatsapp"),
                eq("https://api.test/api/webhook/twilio/whatsapp"),
                eq("https://api.test/api/webhook/twilio/status"),
                eq("AC_sub"), eq("token"));
    }

    @Test
    void creation_avecNomImpose_utiliseEtPersisteCeNom() {
        final TenantWhatsappConfig config = configComplete();
        when(configRepository.findByTenantId(5L)).thenReturn(Optional.of(config));
        when(twilioConfig.isDryRun()).thenReturn(false);
        when(sendersApiClient.creerSender(anyString(), anyString(), anyString(), any(), any(), any(), anyString(), anyString()))
                .thenReturn(new TwilioSendersApiClient.SenderCreationResponse("XEnew123", "CREATING"));

        final TenantWhatsappConfig result = service.provisionner(5L, "Garage Pinson");

        // Le nom imposé devient le profile.name envoyé ET le nom affiché stocké.
        assertThat(result.getDisplayName()).isEqualTo("Garage Pinson");
        verify(sendersApiClient).creerSender(eq("+33611111111"), eq("WABA_1"), eq("Garage Pinson"),
                any(), any(), any(), eq("AC_sub"), eq("token"));
    }

    @Test
    void suppression_supprimeCoteTwilioEtVideLeSid() {
        final TenantWhatsappConfig config = configComplete();
        config.setTwilioSenderSid("XEold");
        when(configRepository.findByTenantId(5L)).thenReturn(Optional.of(config));
        when(twilioConfig.isDryRun()).thenReturn(false);

        final TenantWhatsappConfig result = service.supprimerSender(5L);

        verify(sendersApiClient).supprimerSender("XEold", "AC_sub", "token");
        assertThat(result.getTwilioSenderSid()).isNull();
        assertThat(result.getMetaStatus()).isNull();
    }

    @Test
    void suppression_sansSender_estRefusee() {
        final TenantWhatsappConfig config = configComplete();
        config.setTwilioSenderSid(null);
        when(configRepository.findByTenantId(5L)).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> service.supprimerSender(5L))
                .isInstanceOf(WhatsAppOnboardingException.class)
                .satisfies(e -> assertThat(((WhatsAppOnboardingException) e).getCodeErreur()).isEqualTo("SENDER_ABSENT"));
        verify(sendersApiClient, never()).supprimerSender(anyString(), anyString(), anyString());
    }

    @Test
    void wabaAbsent_estRefuse() {
        final TenantWhatsappConfig config = configComplete();
        config.setWabaId(null);
        when(configRepository.findByTenantId(5L)).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> service.provisionner(5L))
                .isInstanceOf(WhatsAppOnboardingException.class)
                .satisfies(e -> assertThat(((WhatsAppOnboardingException) e).getCodeErreur()).isEqualTo("WABA_ABSENT"));
    }

    @Test
    void numeroAbsent_estRefuse() {
        final TenantWhatsappConfig config = configComplete();
        config.setWhatsappFrom(null);
        when(configRepository.findByTenantId(5L)).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> service.provisionner(5L))
                .isInstanceOf(WhatsAppOnboardingException.class)
                .satisfies(e -> assertThat(((WhatsAppOnboardingException) e).getCodeErreur()).isEqualTo("NUMERO_ABSENT"));
    }

    @Test
    void sousCompteAbsent_estRefuse() {
        final TenantWhatsappConfig config = configComplete();
        config.setTwilioSubAccountSid(null);
        when(configRepository.findByTenantId(5L)).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> service.provisionner(5L))
                .isInstanceOf(WhatsAppOnboardingException.class)
                .satisfies(e -> assertThat(((WhatsAppOnboardingException) e).getCodeErreur()).isEqualTo("SUB_ACCOUNT_ABSENT"));
    }

    @Test
    void dryRun_simuleUnSenderSansAppelTwilio() {
        final TenantWhatsappConfig config = configComplete();
        config.setDisplayName("Garage Test"); // évite le lookup tenant
        when(configRepository.findByTenantId(5L)).thenReturn(Optional.of(config));
        when(twilioConfig.isDryRun()).thenReturn(true);

        final TenantWhatsappConfig result = service.provisionner(5L);

        assertThat(result.getTwilioSenderSid()).isEqualTo("dryrun-5");
        assertThat(result.getMetaStatus()).isEqualTo("ONLINE");
        assertThat(result.getStatut()).isEqualTo(StatutWhatsappConfigEnum.EN_VALIDATION);
    }

    @Test
    void deconnexion_supprimeLeSenderEtReinitialiseLaConnexion() {
        final TenantWhatsappConfig config = configComplete();
        config.setTwilioSenderSid("XEold");
        config.setDisplayName("Pinson Automobiles");
        config.setStatut(StatutWhatsappConfigEnum.ACTIF);
        when(configRepository.findByTenantId(5L)).thenReturn(Optional.of(config));
        when(twilioConfig.isDryRun()).thenReturn(false);

        final TenantWhatsappConfig result = service.deconnecter(5L);

        verify(sendersApiClient).supprimerSender("XEold", "AC_sub", "token");
        // Connexion réinitialisée...
        assertThat(result.getWabaId()).isNull();
        assertThat(result.getWhatsappFrom()).isNull();
        assertThat(result.getTwilioSenderSid()).isNull();
        assertThat(result.getDisplayName()).isNull();
        assertThat(result.getStatut()).isEqualTo(StatutWhatsappConfigEnum.DESACTIVE);
        // ... mais le sous-compte Twilio est conservé (réutilisé à la reconnexion).
        assertThat(result.getTwilioSubAccountSid()).isEqualTo("AC_sub");
        // ... et les templates sont réinitialisés (approbations liées au WABA).
        verify(templateService).reinitialiserTemplates(5L);
    }
}
