package fr.leadrevive.outreach.service;

import fr.leadrevive.ai.entity.TenantAiConfig;
import fr.leadrevive.ai.service.TenantAiConfigService;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.entity.LeadStatutEnum;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.outreach.exception.WhatsAppNonConfigureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires du scheduler d'envoi automatique du premier message WhatsApp.
 *
 * <p>Couvre : envoi dans la plage / report hors plage / config OFF / lead deja CONTACTE
 * (filtre par la requete) / expiration >24h / isolation des erreurs par lead.</p>
 */
@ExtendWith(MockitoExtension.class)
class PremierMessageAutoJobTest {

    @Mock private LeadRepository leadRepository;
    @Mock private TenantAiConfigService tenantAiConfigService;
    @Mock private WhatsAppConversationService whatsAppConversationService;

    private PremierMessageAutoJob job;

    @BeforeEach
    void setUp() {
        job = new PremierMessageAutoJob(leadRepository, tenantAiConfigService, whatsAppConversationService);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Lead leadArme(final Long id, final LocalDateTime prevuLe) {
        final Lead lead = new Lead();
        lead.setId(id);
        lead.setTenantId(1L);
        lead.setNom("Jean Dupont");
        lead.setTelephone("+33612345678");
        lead.setStatut(LeadStatutEnum.NOUVEAU);
        lead.setPremierMessageAutoPrevuLe(prevuLe);
        return lead;
    }

    private TenantAiConfig configActive(final int debut, final int fin) {
        final TenantAiConfig config = new TenantAiConfig();
        config.setTenantId(1L);
        config.setPremierMessageAutoActif(true);
        config.setPremierMessageAutoDelaiMinutes(5);
        config.setPremierMessageAutoFenetreDebutHeure(debut);
        config.setPremierMessageAutoFenetreFinHeure(fin);
        return config;
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    void envoi_dansLaPlage_appelleDemarrerConversation() {
        final Lead lead = leadArme(10L, LocalDateTime.now().minusMinutes(1));
        when(leadRepository.findEligiblesPremierMessageAuto(any())).thenReturn(List.of(lead));
        // Plage 0h-24h (fin exclusive, 24 = minuit) : couvre toute heure de test.
        when(tenantAiConfigService.getConfig(1L)).thenReturn(configActive(0, 24));

        job.envoyerPremiersMessagesAuto();

        verify(whatsAppConversationService)
                .demarrerConversation(eq(10L), eq("+33612345678"), eq(1L), isNull());
        // Pas de desarmement manuel : demarrerConversation efface le flag lui-meme.
        verify(leadRepository, never()).save(any(Lead.class));
    }

    @Test
    void horsPlage_reporte_sansEnvoyerNiDesarmer() {
        final int heure = LocalDateTime.now().getHour();
        // Fenetre valide (debut<fin) ne contenant PAS l'heure courante, quel que soit le moment
        // d'execution du test :
        //  - si heure < 22 : fenetre [heure+1, heure+2] -> heure < debut (hors plage).
        //  - sinon (22 ou 23) : fenetre [0, heure] -> heure >= fin (hors plage).
        final int debut;
        final int fin;
        if (heure < 22) {
            debut = heure + 1;
            fin = heure + 2;
        } else {
            debut = 0;
            fin = heure;
        }
        final Lead lead = leadArme(11L, LocalDateTime.now().minusMinutes(1));
        when(leadRepository.findEligiblesPremierMessageAuto(any())).thenReturn(List.of(lead));
        when(tenantAiConfigService.getConfig(1L)).thenReturn(configActive(debut, fin));

        job.envoyerPremiersMessagesAuto();

        verify(whatsAppConversationService, never())
                .demarrerConversation(anyLong(), anyString(), anyLong(), any());
        // Hors plage : on NE desarme PAS (sera repris a l'ouverture de la plage).
        verify(leadRepository, never()).save(any(Lead.class));
    }

    @Test
    void configOff_desarmeSansEnvoyer() {
        final Lead lead = leadArme(12L, LocalDateTime.now().minusMinutes(1));
        when(leadRepository.findEligiblesPremierMessageAuto(any())).thenReturn(List.of(lead));
        final TenantAiConfig off = configActive(0, 24);
        off.setPremierMessageAutoActif(false);
        when(tenantAiConfigService.getConfig(1L)).thenReturn(off);

        job.envoyerPremiersMessagesAuto();

        verify(whatsAppConversationService, never())
                .demarrerConversation(anyLong(), anyString(), anyLong(), any());
        // Config coupee : desarmement propre du flag.
        verify(leadRepository).save(lead);
        assertThat(lead.getPremierMessageAutoPrevuLe()).isNull();
    }

    @Test
    void configNull_desarmeSansEnvoyer() {
        final Lead lead = leadArme(13L, LocalDateTime.now().minusMinutes(1));
        when(leadRepository.findEligiblesPremierMessageAuto(any())).thenReturn(List.of(lead));
        when(tenantAiConfigService.getConfig(1L)).thenReturn(null);

        job.envoyerPremiersMessagesAuto();

        verify(whatsAppConversationService, never())
                .demarrerConversation(anyLong(), anyString(), anyLong(), any());
        verify(leadRepository).save(lead);
        assertThat(lead.getPremierMessageAutoPrevuLe()).isNull();
    }

    @Test
    void expire_plusDe24h_desarmeSansEnvoyer() {
        final Lead lead = leadArme(14L, LocalDateTime.now().minusHours(25));
        when(leadRepository.findEligiblesPremierMessageAuto(any())).thenReturn(List.of(lead));
        when(tenantAiConfigService.getConfig(1L)).thenReturn(configActive(0, 24));

        job.envoyerPremiersMessagesAuto();

        verify(whatsAppConversationService, never())
                .demarrerConversation(anyLong(), anyString(), anyLong(), any());
        verify(leadRepository).save(lead);
        assertThat(lead.getPremierMessageAutoPrevuLe()).isNull();
    }

    @Test
    void gardeFouPermanent_optOut_desarme() {
        final Lead lead = leadArme(15L, LocalDateTime.now().minusMinutes(1));
        when(leadRepository.findEligiblesPremierMessageAuto(any())).thenReturn(List.of(lead));
        when(tenantAiConfigService.getConfig(1L)).thenReturn(configActive(0, 24));
        when(whatsAppConversationService.demarrerConversation(eq(15L), anyString(), eq(1L), isNull()))
                .thenThrow(new IllegalStateException("Ce lead a demande a ne plus recevoir de WhatsApp (opt-out)"));

        job.envoyerPremiersMessagesAuto();

        // Garde-fou permanent : on desarme (jamais de retry).
        verify(leadRepository).save(lead);
        assertThat(lead.getPremierMessageAutoPrevuLe()).isNull();
    }

    @Test
    void erreurTransitoire_whatsAppNonConfigure_laisseLeFlagPourRetry() {
        final Lead lead = leadArme(16L, LocalDateTime.now().minusMinutes(1));
        when(leadRepository.findEligiblesPremierMessageAuto(any())).thenReturn(List.of(lead));
        when(tenantAiConfigService.getConfig(1L)).thenReturn(configActive(0, 24));
        // demarrerConversation encapsule les erreurs Twilio/WhatsApp dans une RuntimeException.
        when(whatsAppConversationService.demarrerConversation(eq(16L), anyString(), eq(1L), isNull()))
                .thenThrow(new RuntimeException("Echec envoi WhatsApp : service indisponible"));

        job.envoyerPremiersMessagesAuto();

        // Transitoire : on ne desarme PAS (retry au prochain tick).
        verify(leadRepository, never()).save(any(Lead.class));
        assertThat(lead.getPremierMessageAutoPrevuLe()).isNotNull();
    }

    @Test
    void uneErreurSurUnLead_neCassePasLeTraitementDesAutres() {
        final Lead leadKo = leadArme(17L, LocalDateTime.now().minusMinutes(1));
        final Lead leadOk = leadArme(18L, LocalDateTime.now().minusMinutes(1));
        when(leadRepository.findEligiblesPremierMessageAuto(any())).thenReturn(List.of(leadKo, leadOk));
        when(tenantAiConfigService.getConfig(1L)).thenReturn(configActive(0, 24));
        when(whatsAppConversationService.demarrerConversation(eq(17L), anyString(), eq(1L), isNull()))
                .thenThrow(new RuntimeException("boom"));

        job.envoyerPremiersMessagesAuto();

        // Le second lead est bien traite malgre l'echec du premier.
        verify(whatsAppConversationService).demarrerConversation(eq(18L), anyString(), eq(1L), isNull());
    }

    @Test
    void aucunLeadEligible_neFaitRien() {
        when(leadRepository.findEligiblesPremierMessageAuto(any())).thenReturn(List.of());

        job.envoyerPremiersMessagesAuto();

        verify(tenantAiConfigService, never()).getConfig(anyLong());
        verify(whatsAppConversationService, never())
                .demarrerConversation(anyLong(), anyString(), anyLong(), any());
    }
}
