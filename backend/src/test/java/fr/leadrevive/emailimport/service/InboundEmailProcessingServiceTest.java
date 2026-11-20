package fr.leadrevive.emailimport.service;

import fr.leadrevive.emailimport.config.EmailImportConfig;
import fr.leadrevive.emailimport.entity.InboundEmailLog;
import fr.leadrevive.emailimport.entity.StatutInboundEmail;
import fr.leadrevive.emailimport.entity.TenantEmailConfig;
import fr.leadrevive.emailimport.repository.InboundEmailLogRepository;
import fr.leadrevive.emailimport.repository.TenantEmailConfigRepository;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.entity.LeadStatutEnum;
import fr.leadrevive.lead.entity.ModeImport;
import fr.leadrevive.lead.repository.LeadRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de l'orchestrateur d'import email (epic 24).
 *
 * <p>Couvre les chemins critiques : adresse inconnue / inactive, rate limit,
 * rejet spam, parsing OK + creation lead, parsing OK + dedup.</p>
 */
@ExtendWith(MockitoExtension.class)
class InboundEmailProcessingServiceTest {

    @Mock private TenantEmailConfigRepository tenantEmailConfigRepository;
    @Mock private InboundEmailLogRepository inboundEmailLogRepository;
    @Mock private LeadRepository leadRepository;
    @Mock private InboundEmailParsingService parsingService;
    @Mock private EmailDeduplicationService deduplicationService;
    @Mock private EntityManager entityManager;
    @Mock private Session hibernateSession;
    @Mock private fr.leadrevive.common.realtime.SseConnectionService sseConnectionService;
    @Mock private fr.leadrevive.ai.service.TenantAiConfigService tenantAiConfigService;

    private EmailImportConfig config;
    private InboundEmailProcessingService service;

    @BeforeEach
    void setUp() {
        config = new EmailImportConfig();
        config.setDeduplicationFenetreHeures(48);
        config.setMaxEmailsParHeure(50);
        config.setMaxBodyChars(50_000);

        when(entityManager.unwrap(Session.class)).thenReturn(hibernateSession);

        service = new InboundEmailProcessingService(
                tenantEmailConfigRepository,
                inboundEmailLogRepository,
                leadRepository,
                parsingService,
                deduplicationService,
                config,
                entityManager,
                sseConnectionService,
                tenantAiConfigService);
    }

    @Test
    void traite_adresseInconnue_retourneAdresseInconnueSansSauvegarderRien() {
        when(tenantEmailConfigRepository.findByEmailAddress(anyString()))
                .thenReturn(Optional.empty());

        final InboundEmailProcessingService.ResultatTraitement res =
                service.traiter("inconnu@leads.leadrive.fr", "from@x.com", "Sujet", "Body");

        assertThat(res.statut()).isEqualTo(InboundEmailProcessingService.StatutTraitement.ADRESSE_INCONNUE);
        verify(inboundEmailLogRepository, never()).save(any());
    }

    @Test
    void traite_adresseInactive_retourneAdresseInactive() {
        final TenantEmailConfig configTenant = new TenantEmailConfig();
        configTenant.setTenantId(1L);
        configTenant.setStatut("DESACTIVE");
        when(tenantEmailConfigRepository.findByEmailAddress(anyString()))
                .thenReturn(Optional.of(configTenant));

        final InboundEmailProcessingService.ResultatTraitement res =
                service.traiter("test@leads.leadrive.fr", "from@x.com", "Sujet", "Body");

        assertThat(res.statut()).isEqualTo(InboundEmailProcessingService.StatutTraitement.ADRESSE_INACTIVE);
        verify(inboundEmailLogRepository, never()).save(any());
    }

    @Test
    void traite_rateLimitDepasse_retourneRateLimitEtLogue() {
        final TenantEmailConfig configTenant = new TenantEmailConfig();
        configTenant.setTenantId(1L);
        configTenant.setStatut("ACTIVE");
        when(tenantEmailConfigRepository.findByEmailAddress(anyString()))
                .thenReturn(Optional.of(configTenant));
        when(inboundEmailLogRepository.countByTenantIdSince(eq(1L), any())).thenReturn(50L);

        final InboundEmailProcessingService.ResultatTraitement res =
                service.traiter("test@leads.leadrive.fr", "from@x.com", "Sujet", "Body");

        assertThat(res.statut()).isEqualTo(InboundEmailProcessingService.StatutTraitement.RATE_LIMIT);
        final ArgumentCaptor<InboundEmailLog> captor = ArgumentCaptor.forClass(InboundEmailLog.class);
        verify(inboundEmailLogRepository).save(captor.capture());
        assertThat(captor.getValue().getStatut()).isEqualTo(StatutInboundEmail.REJETE_RATE_LIMIT);
    }

    @Test
    void traite_rejetSpam_logueRejetEtIncremementeCompteur() {
        final TenantEmailConfig configTenant = new TenantEmailConfig();
        configTenant.setTenantId(1L);
        configTenant.setStatut("ACTIVE");
        configTenant.setNbEmailsRecus(5);
        when(tenantEmailConfigRepository.findByEmailAddress(anyString()))
                .thenReturn(Optional.of(configTenant));
        when(inboundEmailLogRepository.countByTenantIdSince(eq(1L), any())).thenReturn(0L);

        final InboundEmailParsingService.ResultatParsing resultat =
                InboundEmailParsingService.ResultatParsing.rejetSpam("Newsletter detectee", "{}", 100L);
        when(parsingService.parser(anyString(), anyString(), eq(1L))).thenReturn(resultat);

        final InboundEmailProcessingService.ResultatTraitement res =
                service.traiter("test@leads.leadrive.fr", "newsletter@x.com", "Newsletter hebdo", "Body promo");

        assertThat(res.statut()).isEqualTo(InboundEmailProcessingService.StatutTraitement.REJET_SPAM);
        final ArgumentCaptor<InboundEmailLog> captor = ArgumentCaptor.forClass(InboundEmailLog.class);
        verify(inboundEmailLogRepository).save(captor.capture());
        assertThat(captor.getValue().getStatut()).isEqualTo(StatutInboundEmail.REJETE_SPAM);
        // Compteur incremente meme sur spam
        verify(tenantEmailConfigRepository).save(configTenant);
        assertThat(configTenant.getNbEmailsRecus()).isEqualTo(6);
    }

    @Test
    void traite_parsingOkSansDedup_creeLead() {
        final TenantEmailConfig configTenant = new TenantEmailConfig();
        configTenant.setTenantId(1L);
        configTenant.setStatut("ACTIVE");
        configTenant.setNbEmailsRecus(0);
        when(tenantEmailConfigRepository.findByEmailAddress(anyString()))
                .thenReturn(Optional.of(configTenant));
        when(inboundEmailLogRepository.countByTenantIdSince(eq(1L), any())).thenReturn(0L);

        final InboundEmailParsingService.ResultatParsing resultat = new InboundEmailParsingService.ResultatParsing(
                InboundEmailParsingService.StatutResultat.OK,
                "Dupont", "Jean", "+33612345678", "jean@x.com",
                "Peugeot", "3008", "Peugeot 3008",
                "LEBONCOIN", 90, new HashMap<>(),
                "{json}", null, 150L, false);
        when(parsingService.parser(anyString(), anyString(), eq(1L))).thenReturn(resultat);
        when(deduplicationService.chercherDoublon(eq(1L), eq("+33612345678"), eq("jean@x.com")))
                .thenReturn(Optional.empty());

        final Lead leadSauvegarde = new Lead();
        leadSauvegarde.setId(123L);
        when(leadRepository.save(any(Lead.class))).thenReturn(leadSauvegarde);

        final InboundEmailProcessingService.ResultatTraitement res =
                service.traiter("test@leads.leadrive.fr", "from@x.com", "Sujet", "Body");

        assertThat(res.statut()).isEqualTo(InboundEmailProcessingService.StatutTraitement.SUCCES);
        assertThat(res.leadId()).isEqualTo(123L);
        // Verifie les champs critiques du lead cree
        final ArgumentCaptor<Lead> leadCaptor = ArgumentCaptor.forClass(Lead.class);
        verify(leadRepository).save(leadCaptor.capture());
        final Lead leadCree = leadCaptor.getValue();
        assertThat(leadCree.getTenantId()).isEqualTo(1L);
        assertThat(leadCree.getTelephone()).isEqualTo("+33612345678");
        assertThat(leadCree.getEmail()).isEqualTo("jean@x.com");
        assertThat(leadCree.getModeImport()).isEqualTo(ModeImport.EMAIL_AUTO);
        assertThat(leadCree.getStatut()).isEqualTo(LeadStatutEnum.NOUVEAU);
        assertThat(leadCree.getSource()).isEqualTo("LEBONCOIN");
        // Consentement RGPD acquis via redirection
        assertThat(leadCree.getConsentementContact()).isTrue();
        assertThat(leadCree.getConsentementSource()).isEqualTo("REDIRECT_EMAIL");
    }

    @Test
    void traite_parsingOkAvecDedup_enrichitLeadExistant() {
        final TenantEmailConfig configTenant = new TenantEmailConfig();
        configTenant.setTenantId(1L);
        configTenant.setStatut("ACTIVE");
        configTenant.setNbEmailsRecus(0);
        when(tenantEmailConfigRepository.findByEmailAddress(anyString()))
                .thenReturn(Optional.of(configTenant));
        when(inboundEmailLogRepository.countByTenantIdSince(eq(1L), any())).thenReturn(0L);

        final InboundEmailParsingService.ResultatParsing resultat = new InboundEmailParsingService.ResultatParsing(
                InboundEmailParsingService.StatutResultat.OK,
                "Dupont", "Jean", "+33612345678", "jean@x.com",
                "Peugeot", null, null,
                "LACENTRALE", 85, new HashMap<>(),
                "{json}", null, 150L, false);
        when(parsingService.parser(anyString(), anyString(), eq(1L))).thenReturn(resultat);

        final Lead leadExistant = new Lead();
        leadExistant.setId(42L);
        leadExistant.setTenantId(1L);
        leadExistant.setSource("LEBONCOIN");
        when(deduplicationService.chercherDoublon(eq(1L), eq("+33612345678"), eq("jean@x.com")))
                .thenReturn(Optional.of(leadExistant));
        when(leadRepository.save(any(Lead.class))).thenReturn(leadExistant);

        final InboundEmailProcessingService.ResultatTraitement res =
                service.traiter("test@leads.leadrive.fr", "from@x.com", "Sujet", "Body");

        assertThat(res.statut()).isEqualTo(InboundEmailProcessingService.StatutTraitement.SUCCES);
        assertThat(res.statutLog()).isEqualTo(StatutInboundEmail.LEAD_DEDUPLIQUE);
        verify(leadRepository).save(leadExistant);
    }

    @Test
    void traite_nouveauLeadNumeroDejaOptOut_heriteOptOutEtNArmePasEnvoiAuto() {
        final TenantEmailConfig configTenant = new TenantEmailConfig();
        configTenant.setTenantId(1L);
        configTenant.setStatut("ACTIVE");
        configTenant.setNbEmailsRecus(0);
        when(tenantEmailConfigRepository.findByEmailAddress(anyString()))
                .thenReturn(Optional.of(configTenant));
        when(inboundEmailLogRepository.countByTenantIdSince(eq(1L), any())).thenReturn(0L);

        final InboundEmailParsingService.ResultatParsing resultat = new InboundEmailParsingService.ResultatParsing(
                InboundEmailParsingService.StatutResultat.OK,
                "Dupont", "Jean", "+33612345678", "jean@x.com",
                "Peugeot", "3008", "Peugeot 3008",
                "LEBONCOIN", 90, new HashMap<>(),
                "{json}", null, 150L, false);
        when(parsingService.parser(anyString(), anyString(), eq(1L))).thenReturn(resultat);
        when(deduplicationService.chercherDoublon(eq(1L), eq("+33612345678"), eq("jean@x.com")))
                .thenReturn(Optional.empty());
        // Un lead precedent du meme numero (potentiellement archive) etait opt-out.
        when(leadRepository.existsByTenantIdAndTelephoneAndOptOutSmsTrue(eq(1L), eq("+33612345678")))
                .thenReturn(true);

        final Lead leadSauvegarde = new Lead();
        leadSauvegarde.setId(321L);
        when(leadRepository.save(any(Lead.class))).thenReturn(leadSauvegarde);

        final InboundEmailProcessingService.ResultatTraitement res =
                service.traiter("test@leads.leadrive.fr", "from@x.com", "Sujet", "Body");

        assertThat(res.statut()).isEqualTo(InboundEmailProcessingService.StatutTraitement.SUCCES);
        // Le lead cree herite de l'opt-out (RGPD : desinscription preservee par numero).
        final ArgumentCaptor<Lead> leadCaptor = ArgumentCaptor.forClass(Lead.class);
        verify(leadRepository).save(leadCaptor.capture());
        assertThat(leadCaptor.getValue().getOptOutSms()).isTrue();
        // Aucun envoi auto arme -> la config IA n'est jamais consultee (branche court-circuitee).
        verify(tenantAiConfigService, never()).getConfig(any());
    }

    @Test
    void traite_parsingOkSansTelephone_creeLeadIncomplet() {
        final TenantEmailConfig configTenant = new TenantEmailConfig();
        configTenant.setTenantId(1L);
        configTenant.setStatut("ACTIVE");
        configTenant.setNbEmailsRecus(0);
        when(tenantEmailConfigRepository.findByEmailAddress(anyString()))
                .thenReturn(Optional.of(configTenant));
        when(inboundEmailLogRepository.countByTenantIdSince(eq(1L), any())).thenReturn(0L);

        final InboundEmailParsingService.ResultatParsing resultat = new InboundEmailParsingService.ResultatParsing(
                InboundEmailParsingService.StatutResultat.OK,
                "Dupont", "Jean", null, "jean@x.com",
                null, null, null, "AUTRE", 70, new HashMap<>(),
                "{json}", null, 150L, false);
        when(parsingService.parser(anyString(), anyString(), eq(1L))).thenReturn(resultat);
        when(deduplicationService.chercherDoublon(eq(1L), eq(null), eq("jean@x.com")))
                .thenReturn(Optional.empty());

        final Lead leadSauvegarde = new Lead();
        leadSauvegarde.setId(99L);
        when(leadRepository.save(any(Lead.class))).thenReturn(leadSauvegarde);

        final InboundEmailProcessingService.ResultatTraitement res =
                service.traiter("test@leads.leadrive.fr", "from@x.com", "Sujet", "Body");

        assertThat(res.statut()).isEqualTo(InboundEmailProcessingService.StatutTraitement.SUCCES);
        assertThat(res.statutLog()).isEqualTo(StatutInboundEmail.LEAD_INCOMPLET);
    }
}
