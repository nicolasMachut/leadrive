package fr.leadrevive.outreach.controller;

import fr.leadrevive.common.ratelimit.RateLimitExceededException;
import fr.leadrevive.common.ratelimit.RateLimiter;
import fr.leadrevive.common.security.AuthService;
import fr.leadrevive.common.security.LeadAccessGuard;
import fr.leadrevive.outreach.dto.RepSmsConversationDto;
import fr.leadrevive.outreach.dto.ReqWhatsAppDto;
import fr.leadrevive.outreach.repository.SmsConversationRepository;
import fr.leadrevive.outreach.service.WhatsAppConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
public class WhatsAppController {

    private final WhatsAppConversationService whatsAppConversationService;
    private final SmsConversationRepository smsConversationRepository;
    private final RateLimiter rateLimiter;
    private final AuthService authService;
    private final LeadAccessGuard leadAccessGuard;

    @PostMapping("/leads/{leadId}/whatsapp")
    public ResponseEntity<RepSmsConversationDto> demarrerWhatsApp(@PathVariable("leadId") final Long leadId,
                                                                    @RequestBody @Valid final ReqWhatsAppDto request) {
        // SECURITE : VENDEUR ne peut demarrer une conversation que pour SES leads (404 anonyme sinon).
        leadAccessGuard.requireAccessibleLead(leadId);
        final Long tenantId = authService.getCurrentTenantId();
        final Long userId = authService.getCurrentUserId();
        // Rate limit : 5 envois WhatsApp/min/user. Protege contre le spam involontaire.
        if (!rateLimiter.tryAcquire("whatsapp-send:" + (userId != null ? userId : "anon"), 5)) {
            throw new RateLimitExceededException("Trop d'envois WhatsApp en peu de temps. Patientez 1 minute.");
        }
        final RepSmsConversationDto result = whatsAppConversationService.demarrerConversation(
                leadId, request.getNumeroDestinataire(), tenantId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/whatsapp/{conversationId}")
    public RepSmsConversationDto getConversation(@PathVariable("conversationId") final String conversationId) {
        final Long tenantId = authService.getCurrentTenantId();
        // SECURITE : VENDEUR ne peut lire que les conversations de SES leads.
        // On recupere d'abord le leadId via le repo (scoped tenant), puis on delegue
        // au LeadAccessGuard pour le 404 anonyme si le lead n'est pas accessible.
        smsConversationRepository.findByConversationIdAndTenantId(conversationId, tenantId)
                .ifPresent(conv -> {
                    if (conv.getLeadId() != null) {
                        leadAccessGuard.requireAccessibleLead(conv.getLeadId());
                    }
                });
        return whatsAppConversationService.getConversation(conversationId, tenantId);
    }

    @GetMapping("/leads/{leadId}/whatsapp")
    public List<RepSmsConversationDto> getConversationsLead(@PathVariable("leadId") final Long leadId) {
        // SECURITE : VENDEUR ne peut lire que les conversations de SES leads (404 anonyme sinon).
        leadAccessGuard.requireAccessibleLead(leadId);
        final Long tenantId = authService.getCurrentTenantId();
        return whatsAppConversationService.getConversationsLead(leadId, tenantId);
    }
}
