package fr.leadrevive.outreach.service;

import fr.leadrevive.outreach.dto.RepAdminWhatsappActivationDto;
import fr.leadrevive.outreach.entity.StatutTemplateEnum;
import fr.leadrevive.outreach.entity.StatutWhatsappConfigEnum;
import fr.leadrevive.outreach.entity.TenantWhatsappConfig;
import fr.leadrevive.outreach.entity.TenantWhatsappTemplate;
import fr.leadrevive.outreach.repository.SmsMessageRepository;
import fr.leadrevive.outreach.repository.TenantWhatsappConfigRepository;
import fr.leadrevive.outreach.repository.TenantWhatsappTemplateRepository;
import java.time.LocalDateTime;
import fr.leadrevive.tenant.entity.Tenant;
import fr.leadrevive.tenant.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests de la logique d'etape / blocage de la console admin d'activation
 * WhatsApp (epic 25). Couvre le calcul d'avancement et les raisons de blocage,
 * vraie source de verite reutilisee par l'UI SAV.
 */
@ExtendWith(MockitoExtension.class)
class AdminWhatsappActivationServiceTest {

    @Mock private TenantWhatsappConfigRepository configRepository;
    @Mock private TenantWhatsappTemplateRepository templateRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private WhatsAppTemplateService templateService;
    @Mock private WhatsAppSenderProvisioningService senderProvisioningService;
    @Mock private SmsMessageRepository smsMessageRepository;
    @Mock private EntityManager entityManager;
    @Mock private Session session;
    @Mock private Filter filter;

    private AdminWhatsappActivationService service;

    @BeforeEach
    void setUp() {
        service = new AdminWhatsappActivationService(
                configRepository, templateRepository, tenantRepository, templateService,
                senderProvisioningService, smsMessageRepository);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        lenient().when(entityManager.unwrap(Session.class)).thenReturn(session);
        lenient().when(session.enableFilter("tenantFilter")).thenReturn(filter);
        lenient().when(filter.setParameter("tenantId", -1L)).thenReturn(filter);
        lenient().when(smsMessageRepository.countEchecsParTenantDepuis(any())).thenReturn(List.of());

        final Tenant tenant = new Tenant();
        ReflectionTestUtils.setField(tenant, "id", 1L);
        tenant.setNom("Garage Test");
        lenient().when(tenantRepository.findAll()).thenReturn(List.of(tenant));
    }

    private TenantWhatsappConfig config(final StatutWhatsappConfigEnum statut) {
        final TenantWhatsappConfig c = new TenantWhatsappConfig();
        c.setTenantId(1L);
        c.setStatut(statut);
        return c;
    }

    private TenantWhatsappTemplate modele(final StatutTemplateEnum statut, final String nom) {
        final TenantWhatsappTemplate t = new TenantWhatsappTemplate();
        t.setTenantId(1L);
        t.setTemplateName(nom);
        t.setStatut(statut);
        return t;
    }

    @Test
    void config_active_etape6_nonBloquee() {
        final TenantWhatsappConfig c = config(StatutWhatsappConfigEnum.ACTIF);
        c.setTwilioSubAccountSid("AC_sub");
        c.setWabaId("WABA_1");
        c.setMetaStatus("ONLINE");
        when(configRepository.findAll()).thenReturn(List.of(c));
        when(templateRepository.findAll()).thenReturn(List.of(
                modele(StatutTemplateEnum.APPROUVE, "leadrive_premier_contact")));

        final RepAdminWhatsappActivationDto dto = service.listerActivation().get(0);

        assertThat(dto.etape()).isEqualTo(6);
        assertThat(dto.bloque()).isFalse();
        assertThat(dto.nomEtablissement()).isEqualTo("Garage Test");
    }

    @Test
    void config_restreinte_estBloquee_avecRaison() {
        final TenantWhatsappConfig c = config(StatutWhatsappConfigEnum.RESTREINT);
        c.setTwilioSubAccountSid("AC_sub");
        c.setWabaId("WABA_1");
        c.setMetaRejectReason("[63042] compte restreint");
        when(configRepository.findAll()).thenReturn(List.of(c));
        when(templateRepository.findAll()).thenReturn(List.of(
                modele(StatutTemplateEnum.APPROUVE, "leadrive_premier_contact")));

        final RepAdminWhatsappActivationDto dto = service.listerActivation().get(0);

        assertThat(dto.statut()).isEqualTo("RESTREINT");
        assertThat(dto.bloque()).isTrue();
        assertThat(dto.raisonBlocage()).contains("restreint");
    }

    @Test
    void rollupEchecs_exposeNombreEtDerniereDate() {
        final LocalDateTime dernier = LocalDateTime.of(2026, 6, 24, 10, 0);
        final TenantWhatsappConfig c = config(StatutWhatsappConfigEnum.ACTIF);
        c.setTwilioSubAccountSid("AC_sub");
        c.setWabaId("WABA_1");
        c.setMetaStatus("ONLINE");
        when(configRepository.findAll()).thenReturn(List.of(c));
        when(templateRepository.findAll()).thenReturn(List.of(
                modele(StatutTemplateEnum.APPROUVE, "leadrive_premier_contact")));
        when(smsMessageRepository.countEchecsParTenantDepuis(any()))
                .thenReturn(List.<Object[]>of(new Object[]{1L, 3L, dernier}));

        final RepAdminWhatsappActivationDto dto = service.listerActivation().get(0);

        assertThat(dto.nbEchecsRecents()).isEqualTo(3L);
        assertThat(dto.dernierEchecLe()).isEqualTo(dernier);
    }

    @Test
    void config_sansSubAccount_etape0() {
        when(configRepository.findAll()).thenReturn(List.of(config(StatutWhatsappConfigEnum.DESACTIVE)));
        when(templateRepository.findAll()).thenReturn(List.of());

        final RepAdminWhatsappActivationDto dto = service.listerActivation().get(0);

        assertThat(dto.etape()).isZero();
        assertThat(dto.bloque()).isFalse();
    }

    @Test
    void config_modelesApprouvesMaisOffline_etape4() {
        final TenantWhatsappConfig c = config(StatutWhatsappConfigEnum.EN_VALIDATION);
        c.setTwilioSubAccountSid("AC_sub");
        c.setWabaId("WABA_1");
        c.setMetaStatus("OFFLINE");
        when(configRepository.findAll()).thenReturn(List.of(c));
        when(templateRepository.findAll()).thenReturn(List.of(
                modele(StatutTemplateEnum.APPROUVE, "leadrive_premier_contact"),
                modele(StatutTemplateEnum.APPROUVE, "leadrive_rappel_rdv")));

        final RepAdminWhatsappActivationDto dto = service.listerActivation().get(0);

        assertThat(dto.etape()).isEqualTo(4);
        assertThat(dto.bloque()).isFalse();
    }

    @Test
    void config_avecModeleRejete_estBloquee_avecRaison() {
        final TenantWhatsappConfig c = config(StatutWhatsappConfigEnum.EN_VALIDATION);
        c.setTwilioSubAccountSid("AC_sub");
        c.setWabaId("WABA_1");
        final TenantWhatsappTemplate rejete = modele(StatutTemplateEnum.REJETE, "leadrive_relance_j1");
        rejete.setMetaRejectReason("Trop de variables");
        when(configRepository.findAll()).thenReturn(List.of(c));
        when(templateRepository.findAll()).thenReturn(List.of(
                modele(StatutTemplateEnum.APPROUVE, "leadrive_premier_contact"), rejete));

        final RepAdminWhatsappActivationDto dto = service.listerActivation().get(0);

        assertThat(dto.bloque()).isTrue();
        assertThat(dto.raisonBlocage()).contains("leadrive_relance_j1").contains("Trop de variables");
    }

    @Test
    void config_enErreur_estBloquee() {
        final TenantWhatsappConfig c = config(StatutWhatsappConfigEnum.ERREUR);
        c.setTwilioSubAccountSid("AC_sub");
        c.setWabaId("WABA_1");
        c.setMetaRejectReason("Sender introuvable");
        when(configRepository.findAll()).thenReturn(List.of(c));
        when(templateRepository.findAll()).thenReturn(List.of());

        final RepAdminWhatsappActivationDto dto = service.listerActivation().get(0);

        assertThat(dto.bloque()).isTrue();
        assertThat(dto.raisonBlocage()).isEqualTo("Sender introuvable");
    }

    @Test
    void resoumettre_forceLaRecreationViaResoumettreTous() {
        // Le bouton admin force la recreation complete (resoumettreTous), pas
        // le soumettreTous classique qui ignore les SOUMIS/APPROUVE.
        when(templateService.resoumettreTous(1L))
                .thenReturn(new WhatsAppTemplateService.ResultatSoumission(2, 0, 2));

        final WhatsAppTemplateService.ResultatSoumission res = service.resoumettreModeles(1L);

        assertThat(res.soumis()).isEqualTo(2);
    }
}
