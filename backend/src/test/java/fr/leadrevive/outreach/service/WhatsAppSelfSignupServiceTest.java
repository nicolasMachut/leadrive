package fr.leadrevive.outreach.service;

import fr.leadrevive.outreach.config.TwilioConfig;
import fr.leadrevive.outreach.crypto.EncryptedStringConverter;
import fr.leadrevive.outreach.dto.RepSelfSignupStatutDto;
import fr.leadrevive.outreach.dto.ReqMetaConnexionDto;
import fr.leadrevive.outreach.entity.StatutTemplateEnum;
import fr.leadrevive.outreach.entity.StatutWhatsappConfigEnum;
import fr.leadrevive.outreach.entity.TenantWhatsappConfig;
import fr.leadrevive.outreach.entity.TenantWhatsappTemplate;
import fr.leadrevive.outreach.integration.TwilioContentApiClient;
import fr.leadrevive.outreach.integration.TwilioSubAccountApiClient;
import fr.leadrevive.outreach.repository.TenantWhatsappConfigRepository;
import fr.leadrevive.outreach.repository.TenantWhatsappTemplateRepository;
import fr.leadrevive.outreach.template.WhatsAppTemplateDefinition;
import fr.leadrevive.outreach.template.WhatsAppTemplateLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires du service orchestrateur Self Sign-up (epic 25).
 *
 * <p>Verifie : le demarrage idempotent, la connexion Meta, et la composition
 * des differents etats du statut UI (SELF_SIGNUP non demarre, en cours,
 * bloque, complet).</p>
 *
 * <p>Tous les appels Twilio sont en dry-run (pas de reseau).</p>
 */
class WhatsAppSelfSignupServiceTest {

    private TenantWhatsappConfigRepository configRepository;
    private TenantWhatsappTemplateRepository templateRepository;
    private TwilioSubAccountApiClient subAccountClient;
    private WhatsAppTemplateLoader templateLoader;
    private TwilioConfig twilioConfig;
    private TwilioContentApiClient contentApiClient;
    private fr.leadrevive.outreach.repository.SmsMessageRepository smsMessageRepository;

    private TwilioSubAccountService subAccountService;
    private WhatsAppTemplateService templateService;
    private WhatsAppSelfSignupService service;

    @BeforeEach
    void setUp() {
        // EncryptedStringConverter a besoin d'un SecretCryptoService statique.
        // En tests unitaires (hors Spring context) on s'assure que les setters
        // d'auth_token ne tombent pas dans le converter — ici les entites sont
        // mockees, le converter n'est jamais appele.
        configRepository = mock(TenantWhatsappConfigRepository.class);
        templateRepository = mock(TenantWhatsappTemplateRepository.class);
        subAccountClient = mock(TwilioSubAccountApiClient.class);
        templateLoader = mock(WhatsAppTemplateLoader.class);
        twilioConfig = mock(TwilioConfig.class);
        when(twilioConfig.isDryRun()).thenReturn(true);
        contentApiClient = mock(TwilioContentApiClient.class);
        smsMessageRepository = mock(fr.leadrevive.outreach.repository.SmsMessageRepository.class);

        subAccountService = new TwilioSubAccountService(subAccountClient, configRepository, twilioConfig);
        templateService = new WhatsAppTemplateService(templateLoader, templateRepository, twilioConfig,
                contentApiClient, configRepository);
        service = new WhatsAppSelfSignupService(configRepository, subAccountService, templateService,
                mock(fr.leadrevive.outreach.integration.MetaGraphApiClient.class), smsMessageRepository);
    }

    @Test
    void demarrer_cree_le_sub_account_en_dry_run_sans_appel_reseau() {
        final Long tenantId = 42L;
        when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(configRepository.save(any(TenantWhatsappConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(templateLoader.tousLesTemplates()).thenReturn(List.of());

        service.demarrer(tenantId);

        // Aucun appel reseau Twilio.
        verify(subAccountClient, times(0)).creerSubAccount(anyString());
        verify(subAccountClient, times(0)).rechercherParFriendlyName(anyString());

        // La config a ete sauvegardee avec un SID dry-run.
        final ArgumentCaptor<TenantWhatsappConfig> captor = ArgumentCaptor.forClass(TenantWhatsappConfig.class);
        verify(configRepository, times(3)).save(captor.capture());
        // 1) creation initiale 2) cle idempotence 3) sub-account simule
        final TenantWhatsappConfig finale = captor.getValue();
        assertThat(finale.getTwilioSubAccountSid()).startsWith("AC_dryrun_");
        assertThat(finale.getSubAccountIdempotencyKey()).isNotBlank();
    }

    @Test
    void demarrer_est_idempotent_si_sub_account_deja_present() {
        final Long tenantId = 42L;
        final TenantWhatsappConfig existante = new TenantWhatsappConfig();
        existante.setTenantId(tenantId);
        existante.setTwilioSubAccountSid("AC_existant");
        when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.of(existante));
        when(templateLoader.tousLesTemplates()).thenReturn(List.of());

        service.demarrer(tenantId);

        // Aucune nouvelle sauvegarde du sub-account.
        verify(configRepository, times(0)).save(any(TenantWhatsappConfig.class));
    }

    @Test
    void connecterMeta_persiste_les_identifiants_et_lance_la_soumission() {
        final Long tenantId = 42L;
        final TenantWhatsappConfig existante = new TenantWhatsappConfig();
        existante.setTenantId(tenantId);
        existante.setTwilioSubAccountSid("AC_existant");
        when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.of(existante));
        when(configRepository.save(any(TenantWhatsappConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(templateRepository.findAllByTenantIdAndStatut(anyLong(), any())).thenReturn(List.of());

        final ReqMetaConnexionDto req = new ReqMetaConnexionDto(
                "1234567890123456",
                "9876543210123456",
                "+33612345678",
                null,
                null
        );
        final TenantWhatsappConfig finale = service.connecterMeta(tenantId, req);

        assertThat(finale.getWabaId()).isEqualTo("1234567890123456");
        assertThat(finale.getMetaBusinessId()).isEqualTo("9876543210123456");
        assertThat(finale.getWhatsappFrom()).isEqualTo("+33612345678");
    }

    @Test
    void connecterMeta_echoue_si_flow_non_demarre() {
        final Long tenantId = 42L;
        when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.connecterMeta(tenantId,
                new ReqMetaConnexionDto("waba", "biz", null, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("doit etre demarre");
    }

    @Test
    void statut_SELF_SIGNUP_incomplet_bloque_l_envoi() {
        final Long tenantId = 42L;
        final TenantWhatsappConfig config = new TenantWhatsappConfig();
        config.setTenantId(tenantId);
        when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.of(config));
        when(templateRepository.findAllByTenantId(tenantId)).thenReturn(List.of());

        final RepSelfSignupStatutDto statut = service.statut(tenantId);

        assertThat(statut.subAccountCree()).isFalse();
        assertThat(statut.tousTemplatesApprouves()).isFalse();
        assertThat(statut.messageBlocage()).contains("Activation incomplète");
    }

    @Test
    void statut_SELF_SIGNUP_complet_n_a_aucun_blocage() {
        final Long tenantId = 42L;
        final TenantWhatsappConfig config = new TenantWhatsappConfig();
        config.setTenantId(tenantId);
        config.setTwilioSubAccountSid("AC_xxx");
        config.setWabaId("WABA_xxx");
        config.setWhatsappFrom("+33612345678");

        final TenantWhatsappTemplate t1 = new TenantWhatsappTemplate();
        t1.setTemplateName("leadrive_premier_contact");
        t1.setStatut(StatutTemplateEnum.APPROUVE);

        when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.of(config));
        when(templateRepository.findAllByTenantId(tenantId)).thenReturn(List.of(t1));

        final RepSelfSignupStatutDto statut = service.statut(tenantId);

        assertThat(statut.subAccountCree()).isTrue();
        assertThat(statut.wabaId()).isEqualTo("WABA_xxx");
        assertThat(statut.tousTemplatesApprouves()).isTrue();
        assertThat(statut.messageBlocage()).isNull();
        assertThat(statut.messageGuidage()).contains("entièrement actif");
    }

    @Test
    void statut_sans_config_oriente_directement_vers_Self_Sign_up() {
        final Long tenantId = 999L;
        when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());

        final RepSelfSignupStatutDto statut = service.statut(tenantId);

        assertThat(statut.messageBlocage()).contains("non encore configure");
    }

    @Test
    void statut_remonte_le_motif_d_echec_sender_si_compte_connecte_mais_sender_absent() {
        // Compte connecte (WABA) mais sender non cree + motif persiste (ex: numero
        // deja enregistre sur WhatsApp) : on doit afficher le motif actionnable,
        // pas "en cours de finalisation de notre cote".
        final Long tenantId = 42L;
        final TenantWhatsappConfig config = new TenantWhatsappConfig();
        config.setTenantId(tenantId);
        config.setTwilioSubAccountSid("AC_xxx");
        config.setWabaId("WABA_xxx");
        config.setWhatsappFrom("+33612345678");
        // Pas de twilioSenderSid -> sender manquant.
        config.setMetaRejectReason("Ce numero est deja enregistre sur WhatsApp. Supprimez d'abord...");

        when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.of(config));
        when(templateRepository.findAllByTenantId(tenantId)).thenReturn(List.of());

        final RepSelfSignupStatutDto statut = service.statut(tenantId);

        assertThat(statut.messageBlocage()).contains("deja enregistre sur WhatsApp");
        assertThat(statut.messageGuidage()).contains("deja enregistre sur WhatsApp");
    }

    @Test
    void statut_ignore_un_motif_residuel_si_le_sender_existe() {
        // Sender present : un metaRejectReason eventuel concerne le polling Meta du
        // sender, pas un echec de creation -> on ne doit PAS le remonter comme blocage sender.
        final Long tenantId = 42L;
        final TenantWhatsappConfig config = new TenantWhatsappConfig();
        config.setTenantId(tenantId);
        config.setTwilioSubAccountSid("AC_xxx");
        config.setWabaId("WABA_xxx");
        config.setWhatsappFrom("+33612345678");
        config.setTwilioSenderSid("XE_existant");
        config.setMetaRejectReason("motif residuel");

        final TenantWhatsappTemplate t1 = new TenantWhatsappTemplate();
        t1.setTemplateName("leadrive_premier_contact");
        t1.setStatut(StatutTemplateEnum.APPROUVE);

        when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.of(config));
        when(templateRepository.findAllByTenantId(tenantId)).thenReturn(List.of(t1));

        final RepSelfSignupStatutDto statut = service.statut(tenantId);

        assertThat(statut.messageBlocage()).isNull();
    }

    @Test
    void statut_signale_numero_en_echec_quand_compte_actif_et_envois_casses() {
        // Compte ACTIF mais des envois recents echouent avec un code "numero indisponible"
        // (63007) -> signal numeroEnEchec true (≠ restriction Meta).
        final Long tenantId = 42L;
        final TenantWhatsappConfig config = new TenantWhatsappConfig();
        config.setTenantId(tenantId);
        config.setTwilioSubAccountSid("AC_xxx");
        config.setWabaId("WABA_xxx");
        config.setWhatsappFrom("+33612345678");
        config.setTwilioSenderSid("XE_ok");
        config.setStatut(StatutWhatsappConfigEnum.ACTIF);

        final TenantWhatsappTemplate t1 = new TenantWhatsappTemplate();
        t1.setTemplateName("leadrive_premier_contact");
        t1.setStatut(StatutTemplateEnum.APPROUVE);

        when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.of(config));
        when(templateRepository.findAllByTenantId(tenantId)).thenReturn(List.of(t1));
        // Echec "numero indisponible" recent, sans envoi reussi depuis -> signal actif.
        when(smsMessageRepository.findEchecsAvecDateDepuis(anyLong(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{"[63007] Channel not found", java.time.LocalDateTime.now()}));
        when(smsMessageRepository.findDerniereDateEnvoiReussi(anyLong(), any()))
                .thenReturn(null);

        final RepSelfSignupStatutDto statut = service.statut(tenantId);

        assertThat(statut.numeroEnEchec()).isTrue();
        assertThat(statut.messageEnvoi()).isNotNull();
    }

    @Test
    void statut_n_alerte_plus_numero_en_echec_apres_un_envoi_reussi() {
        // Un echec "numero indisponible" ancien MAIS un envoi a reussi depuis :
        // le numero refonctionne -> le signal doit s'eteindre (etat courant, pas
        // simple fenetre glissante).
        final Long tenantId = 42L;
        final TenantWhatsappConfig config = new TenantWhatsappConfig();
        config.setTenantId(tenantId);
        config.setTwilioSubAccountSid("AC_xxx");
        config.setWabaId("WABA_xxx");
        config.setWhatsappFrom("+33612345678");
        config.setTwilioSenderSid("XE_ok");
        config.setStatut(StatutWhatsappConfigEnum.ACTIF);

        final TenantWhatsappTemplate t1 = new TenantWhatsappTemplate();
        t1.setTemplateName("leadrive_premier_contact");
        t1.setStatut(StatutTemplateEnum.APPROUVE);

        when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.of(config));
        when(templateRepository.findAllByTenantId(tenantId)).thenReturn(List.of(t1));
        when(smsMessageRepository.findEchecsAvecDateDepuis(anyLong(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{"[63007] Channel not found",
                        java.time.LocalDateTime.now().minusHours(3)}));
        when(smsMessageRepository.findDerniereDateEnvoiReussi(anyLong(), any()))
                .thenReturn(java.time.LocalDateTime.now().minusHours(1));

        final RepSelfSignupStatutDto statut = service.statut(tenantId);

        assertThat(statut.numeroEnEchec()).isFalse();
        assertThat(statut.messageEnvoi()).isNull();
    }

    @Test
    void statut_ne_signale_pas_numero_en_echec_pour_un_echec_par_message() {
        // Un echec "hors fenetre 24h" (63016) est par-message, PAS un numero casse :
        // ne doit pas declencher le signal numeroEnEchec.
        final Long tenantId = 42L;
        final TenantWhatsappConfig config = new TenantWhatsappConfig();
        config.setTenantId(tenantId);
        config.setTwilioSubAccountSid("AC_xxx");
        config.setWabaId("WABA_xxx");
        config.setWhatsappFrom("+33612345678");
        config.setTwilioSenderSid("XE_ok");
        config.setStatut(StatutWhatsappConfigEnum.ACTIF);

        final TenantWhatsappTemplate t1 = new TenantWhatsappTemplate();
        t1.setTemplateName("leadrive_premier_contact");
        t1.setStatut(StatutTemplateEnum.APPROUVE);

        when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.of(config));
        when(templateRepository.findAllByTenantId(tenantId)).thenReturn(List.of(t1));
        when(smsMessageRepository.findEchecsAvecDateDepuis(anyLong(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{"[63016] Out of session window", java.time.LocalDateTime.now()}));
        when(smsMessageRepository.findDerniereDateEnvoiReussi(anyLong(), any()))
                .thenReturn(null);

        final RepSelfSignupStatutDto statut = service.statut(tenantId);

        assertThat(statut.numeroEnEchec()).isFalse();
        assertThat(statut.messageEnvoi()).isNull();
    }

    // ─── Tests de la conversion template -> DTO (couvre indirectement la liste) ──

    @Test
    void statut_inclut_les_templates_avec_leur_raison_de_rejet() {
        final Long tenantId = 42L;
        final TenantWhatsappConfig config = new TenantWhatsappConfig();
        config.setTwilioSubAccountSid("AC_xxx");
        config.setWabaId("WABA_xxx");
        final TenantWhatsappTemplate rejete = new TenantWhatsappTemplate();
        rejete.setTemplateName("leadrive_rappel_rdv");
        rejete.setStatut(StatutTemplateEnum.REJETE);
        rejete.setMetaRejectReason("Trop de variables");
        rejete.setNbTentatives(2);

        when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.of(config));
        when(templateRepository.findAllByTenantId(tenantId)).thenReturn(List.of(rejete));

        final RepSelfSignupStatutDto statut = service.statut(tenantId);

        assertThat(statut.templates()).hasSize(1);
        assertThat(statut.templates().get(0).statut()).isEqualTo("REJETE");
        assertThat(statut.templates().get(0).metaRejectReason()).isEqualTo("Trop de variables");
        assertThat(statut.messageBlocage()).contains("n'a pas pu être finalisée");
    }

    /** Garde-fou : la dependance non utilisee dans certains tests doit etre supprimee. */
    @SuppressWarnings("unused")
    private void silenceUnusedFieldWarning() {
        new WhatsAppTemplateDefinition.Variable("", "");
        new EncryptedStringConverter();
        ReflectionTestUtils.invokeMethod(this, "setUp");
    }
}
