package fr.leadrevive.outreach.controller;

import fr.leadrevive.common.security.AuthService;
import fr.leadrevive.outreach.dto.RepConversationAnalyseDto;
import fr.leadrevive.outreach.dto.RepConversationCompteursDto;
import fr.leadrevive.outreach.dto.RepConversationInboxDto;
import fr.leadrevive.outreach.dto.RepSmsConversationDto;
import fr.leadrevive.outreach.dto.ReqMessageDto;
import fr.leadrevive.outreach.entity.SmsConversation;
import fr.leadrevive.outreach.repository.SmsConversationRepository;
import fr.leadrevive.outreach.service.ConversationAnalyseService;
import fr.leadrevive.outreach.service.InboxNotificationService;
import fr.leadrevive.outreach.service.InboxService;
import fr.leadrevive.outreach.service.WhatsAppConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
@Slf4j
public class InboxController {

    private final InboxService inboxService;
    private final InboxNotificationService inboxNotificationService;
    private final WhatsAppConversationService whatsAppConversationService;
    private final ConversationAnalyseService conversationAnalyseService;
    private final SmsConversationRepository smsConversationRepository;
    private final AuthService authService;

    /**
     * Verifie que le vendeur connecte a bien acces a la conversation.
     * Un MANAGER peut acceder a toutes les conversations de son tenant.
     * Un VENDEUR ne peut acceder qu'a ses propres conversations.
     */
    private void verifierAccesConversation(final String conversationId, final Long tenantId) {
        final String role = authService.getCurrentRole();
        if (!"VENDEUR".equals(role)) {
            return;
        }
        final SmsConversation conversation = smsConversationRepository
                .findByConversationIdAndTenantId(conversationId, tenantId)
                .orElseThrow(() -> {
                    log.warn("Conversation introuvable - conversationId: {}, tenantId: {}", conversationId, tenantId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation introuvable");
                });
        final Long currentUserId = authService.getCurrentUserId();
        // SECURITE : si la conversation n'a pas de vendeur titulaire (cas non assignee
        // route par numero To sans userId), un VENDEUR ne doit PAS y acceder (seul le
        // MANAGER peut traiter). Inverser le sens du equals empeche le NPE si
        // conversation.userId est null. 404 anonyme pour ne pas leak l'oracle.
        if (conversation.getUserId() == null
                || currentUserId == null
                || !currentUserId.equals(conversation.getUserId())) {
            log.warn("Acces refuse a la conversation - conversationId: {}, userId: {}, proprietaireId: {}",
                    conversationId, currentUserId, conversation.getUserId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation introuvable");
        }
    }

    @GetMapping
    public List<RepConversationInboxDto> getConversations(
            @RequestParam(value = "statut", required = false) final String statut,
            @RequestParam(value = "modePilotage", required = false) final String modePilotage,
            @RequestParam(value = "nonLuSeulement", required = false) final Boolean nonLuSeulement,
            @RequestParam(value = "recherche", required = false) final String recherche,
            @RequestParam(value = "vendeurId", required = false) final Long vendeurId) {
        final Long tenantId = authService.getCurrentTenantId();
        final String role = authService.getCurrentRole();
        final Long effectiveVendeurId = "VENDEUR".equals(role) ? authService.getCurrentUserId() : vendeurId;
        return inboxService.getConversations(tenantId, effectiveVendeurId, statut, modePilotage, nonLuSeulement, recherche);
    }

    @GetMapping("/compteurs")
    public RepConversationCompteursDto getCompteurs() {
        final Long tenantId = authService.getCurrentTenantId();
        final String role = authService.getCurrentRole();
        final Long vendeurId = "VENDEUR".equals(role) ? authService.getCurrentUserId() : null;
        return inboxService.getCompteurs(tenantId, vendeurId);
    }

    @GetMapping("/{conversationId}")
    public RepSmsConversationDto getConversation(@PathVariable("conversationId") final String conversationId) {
        final Long tenantId = authService.getCurrentTenantId();
        verifierAccesConversation(conversationId, tenantId);
        return whatsAppConversationService.getConversation(conversationId, tenantId);
    }

    @GetMapping("/{conversationId}/analyse")
    public ResponseEntity<RepConversationAnalyseDto> getDerniereAnalyse(
            @PathVariable("conversationId") final String conversationId) {
        final Long tenantId = authService.getCurrentTenantId();
        verifierAccesConversation(conversationId, tenantId);
        final RepConversationAnalyseDto analyse = conversationAnalyseService.getDerniereAnalyse(tenantId, conversationId);
        return analyse != null ? ResponseEntity.ok(analyse) : ResponseEntity.noContent().build();
    }

    @PostMapping("/{conversationId}/reprendre")
    public ResponseEntity<Void> reprendre(@PathVariable("conversationId") final String conversationId) {
        final Long tenantId = authService.getCurrentTenantId();
        verifierAccesConversation(conversationId, tenantId);
        final Long userId = authService.getCurrentUserId();
        inboxService.reprendreMain(conversationId, tenantId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{conversationId}/rendre-ia")
    public ResponseEntity<Void> rendreIa(@PathVariable("conversationId") final String conversationId) {
        final Long tenantId = authService.getCurrentTenantId();
        verifierAccesConversation(conversationId, tenantId);
        inboxService.rendreIa(conversationId, tenantId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{conversationId}/message")
    public ResponseEntity<Void> envoyerMessage(@PathVariable("conversationId") final String conversationId,
                                                @RequestBody @Valid final ReqMessageDto request) {
        final Long tenantId = authService.getCurrentTenantId();
        verifierAccesConversation(conversationId, tenantId);
        final Long userId = authService.getCurrentUserId();
        inboxService.envoyerMessageManuel(conversationId, request.getContenu(), tenantId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{conversationId}/lire")
    public ResponseEntity<Void> marquerLue(@PathVariable("conversationId") final String conversationId) {
        final Long tenantId = authService.getCurrentTenantId();
        verifierAccesConversation(conversationId, tenantId);
        inboxService.marquerLue(conversationId, tenantId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{conversationId}/messages/{messageId}/retry")
    public ResponseEntity<Void> retryMessage(@PathVariable("conversationId") final String conversationId,
                                              @PathVariable("messageId") final Long messageId) {
        final Long tenantId = authService.getCurrentTenantId();
        verifierAccesConversation(conversationId, tenantId);
        inboxService.retryMessage(conversationId, messageId, tenantId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        // SECURITE : passer userId + role pour filtrer les events SSE par vendeur
        // titulaire de la conversation (empeche la fuite intra-tenant du contenu
        // des messages WhatsApp d'un vendeur a l'autre).
        final Long tenantId = authService.getCurrentTenantId();
        final Long userId = authService.getCurrentUserId();
        final String role = authService.getCurrentRole();
        // Diagnostic : log les ouvertures SSE pour tracer les 500 intermittents
        // observés (race condition entre l'auth Clerk et la sync mirror BDD).
        if (tenantId == null || userId == null) {
            log.warn("Ouverture stream SSE refusee - auth incomplete: tenantId={}, userId={}, role={}",
                    tenantId, userId, role);
        }
        try {
            return inboxNotificationService.enregistrerClient(tenantId, userId, role);
        } catch (final RuntimeException e) {
            log.error("Echec enregistrement client SSE - tenantId: {}, userId: {}, role: {}, cause: {}",
                    tenantId, userId, role, e.getMessage(), e);
            throw e;
        }
    }
}
