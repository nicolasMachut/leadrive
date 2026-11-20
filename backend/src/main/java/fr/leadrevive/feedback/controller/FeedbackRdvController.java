package fr.leadrevive.feedback.controller;

import fr.leadrevive.common.security.AuthService;
import fr.leadrevive.feedback.dto.RepFeedbackRdvDto;
import fr.leadrevive.feedback.dto.RepImpactFeedbackDto;
import fr.leadrevive.feedback.dto.RepStatsPrecisionDto;
import fr.leadrevive.feedback.dto.ReqAbandonRdvDto;
import fr.leadrevive.feedback.dto.ReqAnnulerActionRdvDto;
import fr.leadrevive.feedback.dto.ReqFeedbackRdvDto;
import fr.leadrevive.feedback.service.FeedbackRdvService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackRdvController {

    private final FeedbackRdvService feedbackRdvService;
    private final AuthService authService;

    @PostMapping("/rdv")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public RepFeedbackRdvDto feedbackRdv(@RequestBody @Valid final ReqFeedbackRdvDto request) {
        final Long tenantId = authService.getCurrentTenantId();
        final Long vendeurId = authService.getCurrentUserId();
        return feedbackRdvService.enregistrerFeedback(request, tenantId, vendeurId);
    }

    @PostMapping("/abandon")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public ResponseEntity<Void> abandonRdv(@RequestBody @Valid final ReqAbandonRdvDto request) {
        final Long tenantId = authService.getCurrentTenantId();
        final Long vendeurId = authService.getCurrentUserId();
        feedbackRdvService.enregistrerAbandon(request, tenantId, vendeurId);
        return ResponseEntity.ok().build();
    }

    /** UNDO d'une action RDV (Effectue/Abandonner) : rouvre la tache + supprime le feedback. */
    @PostMapping("/rdv/annuler-action")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public ResponseEntity<Void> annulerActionRdv(@RequestBody @Valid final ReqAnnulerActionRdvDto request) {
        final Long tenantId = authService.getCurrentTenantId();
        final Long vendeurId = authService.getCurrentUserId();
        feedbackRdvService.annulerDerniereAction(request.leadId(), request.rdvId(), tenantId, vendeurId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/precision-ia")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public RepStatsPrecisionDto precisionIa() {
        final Long tenantId = authService.getCurrentTenantId();
        return feedbackRdvService.getStatsPrecision(tenantId);
    }

    @GetMapping("/impacts-en-attente")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public List<RepImpactFeedbackDto> impactsEnAttente() {
        final Long tenantId = authService.getCurrentTenantId();
        final Long vendeurId = authService.getCurrentUserId();
        return feedbackRdvService.getImpactsEnAttente(tenantId, vendeurId);
    }

    @PostMapping("/{id}/impact-vu")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public ResponseEntity<Void> marquerImpactVu(@PathVariable("id") final Long id) {
        final Long tenantId = authService.getCurrentTenantId();
        feedbackRdvService.marquerImpactVu(id, tenantId);
        return ResponseEntity.ok().build();
    }
}
