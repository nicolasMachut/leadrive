package fr.leadrevive.feedback.service;

import fr.leadrevive.common.security.AuthService;
import fr.leadrevive.feedback.dto.ReqAbandonRdvDto;
import fr.leadrevive.feedback.dto.ReqFeedbackRdvDto;
import fr.leadrevive.feedback.entity.FeedbackRdv;
import fr.leadrevive.feedback.entity.RaisonAbandonEnum;
import fr.leadrevive.feedback.repository.FeedbackRdvRepository;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.tache.service.TacheIAService;
import fr.leadrevive.tache.service.TacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Couvre le chemin d'abandon RDV rendu atomique + sécurisé (juillet 2026) :
 * annulation de la tâche dans la même transaction, contrôle d'ownership du lead,
 * et idempotence (anti-doublon).
 */
@ExtendWith(MockitoExtension.class)
class FeedbackRdvServiceTest {

    @Mock
    private FeedbackRdvRepository feedbackRdvRepository;
    @Mock
    private LeadRepository leadRepository;
    @Mock
    private TacheIAService tacheIAService;
    @Mock
    private TacheService tacheService;
    @Mock
    private AuthService authService;

    private FeedbackRdvService service;

    private static final Long TENANT = 1L;
    private static final Long VENDEUR = 10L;
    private static final Long LEAD = 42L;
    private static final Long RDV = 100L;

    @BeforeEach
    void setUp() {
        service = new FeedbackRdvService(feedbackRdvRepository, leadRepository,
                tacheIAService, tacheService, authService);
    }

    private Lead leadOwnedBy(final Long vendeurId) {
        final Lead lead = new Lead();
        lead.setId(LEAD);
        lead.setTenantId(TENANT);
        lead.setVendeurId(vendeurId);
        return lead;
    }

    @Test
    void enregistrerAbandon_ok_enregistreMotifEtAnnuleLaTacheAtomiquement() {
        when(authService.getCurrentRole()).thenReturn("MANAGER");
        when(leadRepository.findByIdAndTenantId(LEAD, TENANT)).thenReturn(Optional.of(leadOwnedBy(VENDEUR)));
        when(feedbackRdvRepository.findFirstByLeadIdAndTenantIdAndRaisonAbandonIsNotNullOrderByCreeLe(LEAD, TENANT))
                .thenReturn(Optional.empty());

        service.enregistrerAbandon(new ReqAbandonRdvDto(LEAD, RDV, "NO_SHOW"), TENANT, VENDEUR);

        verify(feedbackRdvRepository).save(any(FeedbackRdv.class));
        // Annulation de la tâche RDV DANS la même transaction (plus de 2e appel front),
        // via annulerRdvDeLead qui garantit que le rdvId appartient bien au leadId.
        verify(tacheService).annulerRdvDeLead(RDV, TENANT, LEAD);
    }

    @Test
    void enregistrerAbandon_sansRdvId_enregistreMotifSansToucherAucuneTache() {
        when(authService.getCurrentRole()).thenReturn("MANAGER");
        when(leadRepository.findByIdAndTenantId(LEAD, TENANT)).thenReturn(Optional.of(leadOwnedBy(VENDEUR)));
        when(feedbackRdvRepository.findFirstByLeadIdAndTenantIdAndRaisonAbandonIsNotNullOrderByCreeLe(LEAD, TENANT))
                .thenReturn(Optional.empty());

        service.enregistrerAbandon(new ReqAbandonRdvDto(LEAD, null, "PROSPECT_ANNULE"), TENANT, VENDEUR);

        verify(feedbackRdvRepository).save(any(FeedbackRdv.class));
        verify(tacheService, never()).annulerRdvDeLead(any(), any(), any());
    }

    @Test
    void enregistrerAbandon_leadDunAutreVendeur_404EtRien() {
        when(authService.getCurrentRole()).thenReturn("VENDEUR");
        // Lead appartient au vendeur 99, l'appelant est le vendeur 10.
        when(leadRepository.findByIdAndTenantId(LEAD, TENANT)).thenReturn(Optional.of(leadOwnedBy(99L)));

        assertThatThrownBy(() ->
                service.enregistrerAbandon(new ReqAbandonRdvDto(LEAD, RDV, "NO_SHOW"), TENANT, VENDEUR))
                .isInstanceOf(ResponseStatusException.class);

        verify(feedbackRdvRepository, never()).save(any());
        verify(tacheService, never()).annulerRdvDeLead(any(), any(), any());
    }

    @Test
    void enregistrerAbandon_leadIntrouvableDansLeTenant_404() {
        // Pas de stub getCurrentRole ici : l'exception part dès findByIdAndTenantId
        // (lead absent), AVANT le check de rôle. Le stubber serait "unnecessary".
        when(leadRepository.findByIdAndTenantId(LEAD, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.enregistrerAbandon(new ReqAbandonRdvDto(LEAD, RDV, "NO_SHOW"), TENANT, VENDEUR))
                .isInstanceOf(ResponseStatusException.class);

        verify(feedbackRdvRepository, never()).save(any());
        verify(tacheService, never()).annulerRdvDeLead(any(), any(), any());
    }

    @Test
    void enregistrerAbandon_doublon_noOpIdempotent() {
        final FeedbackRdv existant = new FeedbackRdv();
        existant.setRaisonAbandon(RaisonAbandonEnum.NO_SHOW);
        when(authService.getCurrentRole()).thenReturn("MANAGER");
        when(leadRepository.findByIdAndTenantId(LEAD, TENANT)).thenReturn(Optional.of(leadOwnedBy(VENDEUR)));
        when(feedbackRdvRepository.findFirstByLeadIdAndTenantIdAndRaisonAbandonIsNotNullOrderByCreeLe(LEAD, TENANT))
                .thenReturn(Optional.of(existant));

        service.enregistrerAbandon(new ReqAbandonRdvDto(LEAD, RDV, "NO_SHOW"), TENANT, VENDEUR);

        // Idempotent : ni nouveau motif, ni ré-annulation.
        verify(feedbackRdvRepository, never()).save(any());
        verify(tacheService, never()).annulerRdvDeLead(eq(RDV), any(), any());
    }

    // --- enregistrerFeedback : même garde d'ownership, doit être couverte aussi ---

    @Test
    void enregistrerFeedback_leadDunAutreVendeur_404EtRien() {
        when(authService.getCurrentRole()).thenReturn("VENDEUR");
        when(leadRepository.findByIdAndTenantId(LEAD, TENANT)).thenReturn(Optional.of(leadOwnedBy(99L)));

        assertThatThrownBy(() ->
                service.enregistrerFeedback(new ReqFeedbackRdvDto(LEAD, RDV, "CHAUD", "DEVIS"), TENANT, VENDEUR))
                .isInstanceOf(ResponseStatusException.class);

        verify(feedbackRdvRepository, never()).save(any());
    }

    @Test
    void enregistrerFeedback_doublon_illegalState() {
        when(authService.getCurrentRole()).thenReturn("MANAGER");
        when(leadRepository.findByIdAndTenantId(LEAD, TENANT)).thenReturn(Optional.of(leadOwnedBy(VENDEUR)));
        when(feedbackRdvRepository.findFirstByLeadIdAndTenantIdAndQualiteAppelIsNotNullOrderByCreeLe(LEAD, TENANT))
                .thenReturn(Optional.of(new FeedbackRdv()));

        assertThatThrownBy(() ->
                service.enregistrerFeedback(new ReqFeedbackRdvDto(LEAD, RDV, "CHAUD", "DEVIS"), TENANT, VENDEUR))
                .isInstanceOf(IllegalStateException.class);

        verify(feedbackRdvRepository, never()).save(any());
    }

    // --- annulerDerniereAction (undo Effectue/Abandonner) ---

    @Test
    void annulerDerniereAction_ok_supprimeFeedbackEtRouvreLaTache() {
        when(authService.getCurrentRole()).thenReturn("MANAGER");
        when(leadRepository.findByIdAndTenantId(LEAD, TENANT)).thenReturn(Optional.of(leadOwnedBy(VENDEUR)));

        service.annulerDerniereAction(LEAD, RDV, TENANT, VENDEUR);

        verify(feedbackRdvRepository).deleteByRdvIdAndTenantId(RDV, TENANT);
        // Passe désormais par rouvrirTacheDeLead (valide rdvId ∈ leadId avant mutation).
        verify(tacheService).rouvrirTacheDeLead(RDV, TENANT, LEAD);
    }

    @Test
    void annulerDerniereAction_leadDunAutreVendeur_404EtRien() {
        when(authService.getCurrentRole()).thenReturn("VENDEUR");
        when(leadRepository.findByIdAndTenantId(LEAD, TENANT)).thenReturn(Optional.of(leadOwnedBy(99L)));

        assertThatThrownBy(() ->
                service.annulerDerniereAction(LEAD, RDV, TENANT, VENDEUR))
                .isInstanceOf(ResponseStatusException.class);

        verify(feedbackRdvRepository, never()).deleteByRdvIdAndTenantId(any(), any());
        verify(tacheService, never()).rouvrirTacheDeLead(any(), any(), any());
    }
}
