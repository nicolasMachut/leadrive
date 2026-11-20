package fr.leadrevive.outreach.service;

import fr.leadrevive.outreach.config.TwilioConfig;
import fr.leadrevive.outreach.dto.RepSelfSignupStatutDto;
import fr.leadrevive.outreach.dto.ReqMetaConnexionDto;
import fr.leadrevive.outreach.entity.StatutTemplateEnum;
import fr.leadrevive.outreach.entity.StatutWhatsappConfigEnum;
import fr.leadrevive.outreach.entity.TenantWhatsappConfig;
import fr.leadrevive.outreach.entity.TenantWhatsappTemplate;
import fr.leadrevive.outreach.integration.TwilioContentApiClient;
import fr.leadrevive.outreach.integration.TwilioSendersApiClient;
import fr.leadrevive.outreach.integration.TwilioSubAccountApiClient;
import fr.leadrevive.outreach.repository.TenantWhatsappConfigRepository;
import fr.leadrevive.outreach.repository.TenantWhatsappTemplateRepository;
import fr.leadrevive.outreach.template.WhatsAppTemplateLoader;
import fr.leadrevive.user.service.EmailService;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests d'integration multi-services pour le flow Self Sign-up WhatsApp
 * Business (epic 25, post-sunset LEGACY V064).
 *
 * <p>Verifie l'orchestration entre :
 * <ul>
 *   <li>{@link WhatsAppSelfSignupService} (orchestrateur)</li>
 *   <li>{@link TwilioSubAccountService} (creation sub-account)</li>
 *   <li>{@link WhatsAppTemplateService} (templates Meta)</li>
 *   <li>{@link TwilioWhatsAppService} (envoi messages)</li>
 *   <li>{@link WhatsAppSenderStatusJob} (polling Meta status)</li>
 * </ul>
 *
 * <p>Twilio en dry-run : aucun appel reseau.</p>
 */
class WhatsAppSelfSignupIntegrationTest {

    // ─── Repositories (mocks) ─────────────────────────────────────────────
    private TenantWhatsappConfigRepository configRepository;
    private TenantWhatsappTemplateRepository templateRepository;

    // ─── Clients externes (mocks) ─────────────────────────────────────────
    private TwilioSubAccountApiClient subAccountClient;
    private TwilioContentApiClient contentApiClient;
    private TwilioSendersApiClient sendersApiClient;
    private WhatsAppTemplateLoader templateLoader;
    private EmailService emailService;

    // ─── Config ────────────────────────────────────────────────────────────
    private TwilioConfig twilioConfig;

    // ─── Services sous test ────────────────────────────────────────────────
    private TwilioSubAccountService subAccountService;
    private WhatsAppTemplateService templateService;
    private WhatsAppSelfSignupService selfSignupService;
    private TwilioWhatsAppService whatsAppService;
    private WhatsAppSenderStatusJob statusJob;

    @BeforeEach
    void setUp() {
        configRepository = mock(TenantWhatsappConfigRepository.class);
        templateRepository = mock(TenantWhatsappTemplateRepository.class);
        subAccountClient = mock(TwilioSubAccountApiClient.class);
        contentApiClient = mock(TwilioContentApiClient.class);
        sendersApiClient = mock(TwilioSendersApiClient.class);
        templateLoader = mock(WhatsAppTemplateLoader.class);
        emailService = mock(EmailService.class);

        twilioConfig = mock(TwilioConfig.class);
        when(twilioConfig.isDryRun()).thenReturn(true);
        when(twilioConfig.getAssistantPrenom()).thenReturn("Nora");

        subAccountService = new TwilioSubAccountService(subAccountClient, configRepository, twilioConfig);
        templateService = new WhatsAppTemplateService(templateLoader, templateRepository, twilioConfig,
                contentApiClient, configRepository);
        selfSignupService = new WhatsAppSelfSignupService(configRepository, subAccountService, templateService,
                mock(fr.leadrevive.outreach.integration.MetaGraphApiClient.class),
                mock(fr.leadrevive.outreach.repository.SmsMessageRepository.class));

        final ObjectMapper objectMapper = JsonMapper.builder().build();
        whatsAppService = new TwilioWhatsAppService(twilioConfig, configRepository,
                templateRepository, templateLoader, objectMapper);

        statusJob = new WhatsAppSenderStatusJob(configRepository, twilioConfig, emailService,
                sendersApiClient, templateService);
    }

    // =====================================================================
    // FLOW COMPLET : demarrer → connecterMeta → statut → ACTIF
    // =====================================================================

    @Nested
    @DisplayName("Flow Self Sign-up complet (dry-run)")
    class FlowCompletDryRun {

        @Test
        @DisplayName("demarrer + connecterMeta + statut = progression coherente")
        void flow_complet_progression_coherente() {
            final Long tenantId = 42L;

            // ─── Phase 1 : demarrer ───────────────────────────────────────
            when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
            when(configRepository.save(any(TenantWhatsappConfig.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(templateLoader.tousLesTemplates()).thenReturn(List.of());

            selfSignupService.demarrer(tenantId);

            // Verifie : config creee avec sub-account dry-run
            final ArgumentCaptor<TenantWhatsappConfig> captorDemarrage =
                    ArgumentCaptor.forClass(TenantWhatsappConfig.class);
            verify(configRepository, times(3)).save(captorDemarrage.capture());
            final TenantWhatsappConfig apresCreation = captorDemarrage.getValue();
            assertThat(apresCreation.getTwilioSubAccountSid()).startsWith("AC_dryrun_");
            assertThat(apresCreation.getTwilioSubAuthToken()).startsWith("dryrun_token_");
            assertThat(apresCreation.getSubAccountIdempotencyKey()).isNotBlank();

            // ─── Phase 2 : connecterMeta ──────────────────────────────────
            when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.of(apresCreation));
            when(templateRepository.findAllByTenantIdAndStatut(anyLong(), any())).thenReturn(List.of());

            final ReqMetaConnexionDto metaReq = new ReqMetaConnexionDto(
                    "WABA_123456", "BIZ_789012", "+33612345678", null, null);
            final TenantWhatsappConfig apresConnexion = selfSignupService.connecterMeta(tenantId, metaReq);

            assertThat(apresConnexion.getWabaId()).isEqualTo("WABA_123456");
            assertThat(apresConnexion.getMetaBusinessId()).isEqualTo("BIZ_789012");
            assertThat(apresConnexion.getWhatsappFrom()).isEqualTo("+33612345678");
            assertThat(apresConnexion.getStatut()).isEqualTo(StatutWhatsappConfigEnum.EN_VALIDATION);

            // ─── Phase 3 : statut — EN_VALIDATION, pas encore pret ────────
            when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.of(apresConnexion));
            when(templateRepository.findAllByTenantId(tenantId)).thenReturn(List.of());

            final RepSelfSignupStatutDto statut = selfSignupService.statut(tenantId);

            assertThat(statut.subAccountCree()).isTrue();
            assertThat(statut.wabaId()).isEqualTo("WABA_123456");
            assertThat(statut.tousTemplatesApprouves()).isFalse();
            assertThat(statut.messageBlocage()).isNotNull();

            // ─── Phase 4 : tous les templates approuves → pret ────────────
            final TenantWhatsappTemplate tpl1 = new TenantWhatsappTemplate();
            tpl1.setTemplateName("leadrive_premier_contact");
            tpl1.setStatut(StatutTemplateEnum.APPROUVE);
            final TenantWhatsappTemplate tpl2 = new TenantWhatsappTemplate();
            tpl2.setTemplateName("leadrive_rappel_rdv");
            tpl2.setStatut(StatutTemplateEnum.APPROUVE);

            when(templateRepository.findAllByTenantId(tenantId)).thenReturn(List.of(tpl1, tpl2));

            final RepSelfSignupStatutDto statutFinal = selfSignupService.statut(tenantId);

            assertThat(statutFinal.tousTemplatesApprouves()).isTrue();
            assertThat(statutFinal.messageBlocage()).isNull();
            assertThat(statutFinal.messageGuidage()).contains("entièrement actif");
            assertThat(statutFinal.templates()).hasSize(2);
            assertThat(statutFinal.templates().get(0).statut()).isEqualTo("APPROUVE");
        }

        @Test
        @DisplayName("demarrer idempotent : 2e appel ne recree pas le sub-account")
        void demarrer_idempotent_pas_de_double_creation() {
            final Long tenantId = 42L;
            final TenantWhatsappConfig existante = configAvecSubAccount(tenantId);

            when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.of(existante));
            when(templateLoader.tousLesTemplates()).thenReturn(List.of());

            selfSignupService.demarrer(tenantId);

            // Pas de sauvegarde ni d'appel Twilio
            verify(configRepository, never()).save(any());
            verify(subAccountClient, never()).creerSubAccount(anyString());
        }
    }

    // =====================================================================
    // ENVOI MESSAGE VIA SUB-ACCOUNT
    // =====================================================================

    @Nested
    @DisplayName("Envoi WhatsApp via sub-account (dry-run)")
    class EnvoiSubAccount {

        @Test
        @DisplayName("envoyerWhatsApp en dry-run retourne un SID fictif")
        void envoyerWhatsApp_dryRun_retourne_fakeId() {
            final Long tenantId = 42L;
            final TenantWhatsappConfig config = configComplete(tenantId);

            when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.of(config));

            final String sid = whatsAppService.envoyerWhatsApp("+33612345678", "Bonjour !", tenantId);

            assertThat(sid).startsWith("dryrun-");
        }

        @Test
        @DisplayName("envoyerWhatsApp refuse un numero non E.164")
        void envoyerWhatsApp_numero_invalide_exception() {
            final Long tenantId = 42L;
            final TenantWhatsappConfig config = configComplete(tenantId);

            when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.of(config));

            assertThatThrownBy(() -> whatsAppService.envoyerWhatsApp("abc123", "test", tenantId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("E.164");
        }

        @Test
        @DisplayName("envoyerWhatsApp echoue si config pas ACTIF")
        void envoyerWhatsApp_config_non_active_exception() {
            final Long tenantId = 42L;
            final TenantWhatsappConfig config = configAvecSubAccount(tenantId);
            config.setStatut(StatutWhatsappConfigEnum.EN_VALIDATION); // Pas ACTIF

            when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.of(config));

            assertThatThrownBy(() -> whatsAppService.envoyerWhatsApp("+33612345678", "test", tenantId))
                    .isInstanceOf(fr.leadrevive.outreach.exception.WhatsAppNonConfigureException.class);
        }

        @Test
        @DisplayName("envoyerWhatsApp echoue si aucune config pour le tenant")
        void envoyerWhatsApp_sans_config_exception() {
            when(configRepository.findByTenantId(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> whatsAppService.envoyerWhatsApp("+33612345678", "test", 99L))
                    .isInstanceOf(fr.leadrevive.outreach.exception.WhatsAppNonConfigureException.class);
        }

        @Test
        @DisplayName("envoyerWhatsAppTemplate en dry-run retourne un SID fictif")
        void envoyerWhatsAppTemplate_dryRun_retourne_fakeId() {
            final Long tenantId = 42L;
            final TenantWhatsappConfig config = configComplete(tenantId);
            when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.of(config));
            final fr.leadrevive.outreach.entity.TenantWhatsappTemplate tpl = new fr.leadrevive.outreach.entity.TenantWhatsappTemplate();
            tpl.setTemplateSid("HX_tpl_xxx");
            when(templateRepository.findByTenantIdAndTemplateName(tenantId, "leadrive_premier_contact"))
                    .thenReturn(Optional.of(tpl));

            final String sid = whatsAppService.envoyerWhatsAppTemplate(
                    "+33612345678",
                    java.util.Map.of("1", "Jean", "2", "Nora"),
                    tenantId);

            assertThat(sid).startsWith("dryrun-tpl-");
        }

        @Test
        @DisplayName("envoyerRelanceTemplate en dry-run retourne un SID fictif")
        void envoyerRelanceTemplate_dryRun_retourne_fakeId() {
            final Long tenantId = 42L;
            final TenantWhatsappConfig config = configComplete(tenantId);
            when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.of(config));

            final String sid = whatsAppService.envoyerRelanceTemplate(
                    "+33612345678",
                    "HX_relance_xxx",
                    java.util.Map.of("1", "Jean", "2", "Nora"),
                    tenantId);

            assertThat(sid).startsWith("dryrun-relance-");
        }
    }

    // =====================================================================
    // STATUS JOB — POLLING META VIA SUB-ACCOUNT
    // =====================================================================

    @Nested
    @DisplayName("StatusJob polling avec sub-account credentials")
    class StatusJobPolling {

        @Test
        @DisplayName("config sans sub-account credentials = skip (pas de NPE)")
        void config_sans_credentials_skip() {
            final TenantWhatsappConfig config = new TenantWhatsappConfig();
            config.setTenantId(42L);
            config.setTwilioSenderSid("PN_xxx");
            // Pas de sub-account credentials

            statusJob.synchroniserUneConfig(config);

            verify(sendersApiClient, never()).recupererStatut(anyString(), anyString(), anyString());
            verify(configRepository, never()).save(any());
        }

        @Test
        @DisplayName("config avec sender dry-run = simulation, pas d'appel Twilio")
        void config_dryRun_simulation() {
            final TenantWhatsappConfig config = configComplete(42L);
            config.setTwilioSenderSid("dryrun-xxx");
            when(configRepository.save(any(TenantWhatsappConfig.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            statusJob.synchroniserUneConfig(config);

            verify(sendersApiClient, never()).recupererStatut(anyString(), anyString(), anyString());
            verify(configRepository).save(config);
            assertThat(config.getDateDerniereSynchroMeta()).isNotNull();
        }

        @Test
        @DisplayName("EN_VALIDATION + ONLINE + templates prets = transition vers ACTIF")
        void en_validation_online_templates_prets_transition_actif() {
            final Long tenantId = 42L;
            final TenantWhatsappConfig config = configComplete(tenantId);
            config.setStatut(StatutWhatsappConfigEnum.EN_VALIDATION);
            config.setTwilioSenderSid("PN_real_xxx");

            // Simule la reponse Twilio
            when(sendersApiClient.recupererStatut(
                    eq("PN_real_xxx"),
                    eq(config.getTwilioSubAccountSid()),
                    eq(config.getTwilioSubAuthToken())))
                    .thenReturn(new TwilioSendersApiClient.TwilioSenderResponse(
                            "PN_real_xxx", "ONLINE", "GREEN", "TIER_1K",
                            "Concession Dupont", null, "APPROVED"));

            // Templates tous prets
            when(templateRepository.findAllByTenantId(tenantId)).thenReturn(List.of(
                    templateApprouve("leadrive_premier_contact"),
                    templateApprouve("leadrive_rappel_rdv")));

            when(configRepository.save(any(TenantWhatsappConfig.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            statusJob.synchroniserUneConfig(config);

            assertThat(config.getStatut()).isEqualTo(StatutWhatsappConfigEnum.ACTIF);
            assertThat(config.getMetaStatus()).isEqualTo("ONLINE");
            assertThat(config.getQualityRating()).isEqualTo("GREEN");
            verify(emailService).notifierActivationWhatsApp(tenantId);
        }

        @Test
        @DisplayName("ACTIF + REJECTED = transition vers RESTREINT (compte restreint) + email manager")
        void actif_rejected_transition_restreint() {
            final Long tenantId = 42L;
            final TenantWhatsappConfig config = configComplete(tenantId);
            config.setStatut(StatutWhatsappConfigEnum.ACTIF);
            config.setTwilioSenderSid("PN_real_xxx");

            when(sendersApiClient.recupererStatut(anyString(), anyString(), anyString()))
                    .thenReturn(new TwilioSendersApiClient.TwilioSenderResponse(
                            "PN_real_xxx", "REJECTED", null, null,
                            "Concession Dupont", "Policy violation", "DECLINED"));

            when(configRepository.save(any(TenantWhatsappConfig.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            statusJob.synchroniserUneConfig(config);

            // Un compte qui etait ACTIF et dont le numero est refuse = RESTRICTION
            // (envoi bloque car statut != ACTIF), pas une erreur d'onboarding.
            assertThat(config.getStatut()).isEqualTo(StatutWhatsappConfigEnum.RESTREINT);
            assertThat(config.getMetaRejectReason()).isEqualTo("Policy violation");
            verify(emailService).notifierRejetMeta(eq(tenantId), eq("Policy violation"));
        }

        @Test
        @DisplayName("ERREUR + ONLINE + quality OK = retour ACTIF + email manager")
        void erreur_online_retour_actif() {
            final Long tenantId = 42L;
            final TenantWhatsappConfig config = configComplete(tenantId);
            config.setStatut(StatutWhatsappConfigEnum.ERREUR);
            config.setTwilioSenderSid("PN_real_xxx");

            when(sendersApiClient.recupererStatut(anyString(), anyString(), anyString()))
                    .thenReturn(new TwilioSendersApiClient.TwilioSenderResponse(
                            "PN_real_xxx", "ONLINE", "GREEN", "TIER_1K",
                            "Concession Dupont", null, "APPROVED"));

            when(configRepository.save(any(TenantWhatsappConfig.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            statusJob.synchroniserUneConfig(config);

            assertThat(config.getStatut()).isEqualTo(StatutWhatsappConfigEnum.ACTIF);
            verify(emailService).notifierActivationWhatsApp(tenantId);
        }

        @Test
        @DisplayName("quality GREEN → RED = alerte admin critique")
        void quality_green_vers_red_alerte_admin() {
            final Long tenantId = 42L;
            final TenantWhatsappConfig config = configComplete(tenantId);
            config.setStatut(StatutWhatsappConfigEnum.ACTIF);
            config.setTwilioSenderSid("PN_real_xxx");
            config.setQualityRating("GREEN");

            when(sendersApiClient.recupererStatut(anyString(), anyString(), anyString()))
                    .thenReturn(new TwilioSendersApiClient.TwilioSenderResponse(
                            "PN_real_xxx", "ONLINE", "RED", "TIER_250",
                            "Concession Dupont", null, "APPROVED"));

            when(configRepository.save(any(TenantWhatsappConfig.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            statusJob.synchroniserUneConfig(config);

            verify(emailService).alerterAdminQualityRatingRouge(tenantId);
        }
    }

    // =====================================================================
    // STATUT DTO — PLUS DE LEGACY
    // =====================================================================

    @Nested
    @DisplayName("Statut DTO post-sunset LEGACY")
    class StatutPostLegacy {

        @Test
        @DisplayName("aucune config = orientation SELF_SIGNUP, pas de migrationRecommandee")
        void aucune_config_self_signup_par_defaut() {
            when(configRepository.findByTenantId(99L)).thenReturn(Optional.empty());

            final RepSelfSignupStatutDto statut = selfSignupService.statut(99L);

            assertThat(statut.subAccountCree()).isFalse();
            assertThat(statut.messageBlocage()).contains("non encore configure");
        }

        @Test
        @DisplayName("template REJETE = message blocage specifique")
        void template_rejete_message_specifique() {
            final Long tenantId = 42L;
            final TenantWhatsappConfig config = configAvecSubAccount(tenantId);
            config.setWabaId("WABA_xxx");

            final TenantWhatsappTemplate rejete = new TenantWhatsappTemplate();
            rejete.setTemplateName("leadrive_premier_contact");
            rejete.setStatut(StatutTemplateEnum.REJETE);
            rejete.setMetaRejectReason("Trop de variables");
            rejete.setNbTentatives(2);

            when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.of(config));
            when(templateRepository.findAllByTenantId(tenantId)).thenReturn(List.of(rejete));

            final RepSelfSignupStatutDto statut = selfSignupService.statut(tenantId);

            assertThat(statut.messageBlocage()).contains("n'a pas pu être finalisée");
            assertThat(statut.templates()).hasSize(1);
            assertThat(statut.templates().get(0).statut()).isEqualTo("REJETE");
            assertThat(statut.templates().get(0).metaRejectReason()).isEqualTo("Trop de variables");
        }

        @Test
        @DisplayName("sub-account cree mais Meta non connecte = blocage specifique")
        void sub_cree_meta_non_connecte_blocage() {
            final Long tenantId = 42L;
            final TenantWhatsappConfig config = configAvecSubAccount(tenantId);
            // Pas de wabaId

            when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.of(config));
            when(templateRepository.findAllByTenantId(tenantId)).thenReturn(List.of());

            final RepSelfSignupStatutDto statut = selfSignupService.statut(tenantId);

            assertThat(statut.subAccountCree()).isTrue();
            assertThat(statut.wabaId()).isNull();
            assertThat(statut.messageBlocage()).contains("pas encore connecté");
            assertThat(statut.messageGuidage()).contains("Facebook");
        }
    }

    // =====================================================================
    // HELPERS
    // =====================================================================

    private static TenantWhatsappConfig configAvecSubAccount(final Long tenantId) {
        final TenantWhatsappConfig config = new TenantWhatsappConfig();
        config.setTenantId(tenantId);
        config.setTwilioSubAccountSid("AC_dryrun_" + tenantId);
        config.setTwilioSubAuthToken("dryrun_token_" + tenantId);
        config.setSubAccountIdempotencyKey("idem-" + tenantId);
        return config;
    }

    private static TenantWhatsappConfig configComplete(final Long tenantId) {
        final TenantWhatsappConfig config = configAvecSubAccount(tenantId);
        config.setWabaId("WABA_" + tenantId);
        config.setMetaBusinessId("BIZ_" + tenantId);
        config.setWhatsappFrom("+33612345678");
        config.setStatut(StatutWhatsappConfigEnum.ACTIF);
        return config;
    }

    private static TenantWhatsappTemplate templateApprouve(final String name) {
        final TenantWhatsappTemplate tpl = new TenantWhatsappTemplate();
        tpl.setTemplateName(name);
        tpl.setStatut(StatutTemplateEnum.APPROUVE);
        return tpl;
    }
}
