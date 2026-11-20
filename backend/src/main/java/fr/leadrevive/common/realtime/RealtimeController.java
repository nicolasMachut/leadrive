package fr.leadrevive.common.realtime;

import fr.leadrevive.common.security.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Endpoint SSE : flux d'events temps reel scope au tenant courant.
 *
 * <p>Usage cote frontend : {@code new EventSource("/api/realtime/stream?token=<jwt>")}.
 * L'API native EventSource ne permet pas de headers custom — le JWT est passe
 * en query param et transforme en header Authorization par
 * {@link fr.leadrevive.common.security.BearerTokenQueryParamFilter}.</p>
 */
@RestController
@RequestMapping("/api/realtime")
@RequiredArgsConstructor
@Slf4j
public class RealtimeController {

    private final SseConnectionService sseConnectionService;
    private final AuthService authService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    public SseEmitter stream() {
        final Long tenantId = authService.getCurrentTenantId();
        if (tenantId == null) {
            log.warn("Stream SSE refuse - pas de tenantId resolu");
            throw new IllegalStateException("tenantId non resolu");
        }
        log.info("Ouverture stream SSE - tenantId: {}", tenantId);
        return sseConnectionService.creerEmitter(tenantId);
    }
}
