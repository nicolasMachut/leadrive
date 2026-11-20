package fr.leadrevive.ai.controller;

import fr.leadrevive.ai.dto.RepBriefingDto;
import fr.leadrevive.ai.service.BriefingService;
import fr.leadrevive.common.ratelimit.RateLimitExceededException;
import fr.leadrevive.common.ratelimit.RateLimiter;
import fr.leadrevive.common.security.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/briefing")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
public class BriefingController {

    private final BriefingService briefingService;
    private final AuthService authService;
    private final RateLimiter rateLimiter;

    @GetMapping
    public ResponseEntity<RepBriefingDto> getBriefing() {
        final Long tenantId = authService.getCurrentTenantId();
        final Long userId = authService.getCurrentUserId();
        if (tenantId == null || userId == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(briefingService.getBriefingDuJour(tenantId, userId));
    }

    @PostMapping("/regenerer")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<RepBriefingDto> regenerer() {
        final Long tenantId = authService.getCurrentTenantId();
        final Long userId = authService.getCurrentUserId();
        if (tenantId == null || userId == null) {
            return ResponseEntity.noContent().build();
        }
        // Rate limit : 3 regenerations/jour/user (le briefing est deja cache 1x/jour)
        if (!rateLimiter.tryAcquire("briefing-regen:" + userId, 3)) {
            throw new RateLimitExceededException("Briefing deja regenere recemment. Le briefing est mis a jour une fois par jour.");
        }
        return ResponseEntity.ok(briefingService.regenerer(tenantId, userId));
    }
}
