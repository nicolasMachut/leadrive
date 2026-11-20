package fr.leadrevive.ai.controller;

import fr.leadrevive.ai.dto.RepAideChatDto;
import fr.leadrevive.ai.dto.ReqAideChatDto;
import fr.leadrevive.ai.service.AideChatService;
import fr.leadrevive.common.ratelimit.RateLimitExceededException;
import fr.leadrevive.common.ratelimit.RateLimiter;
import fr.leadrevive.common.security.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/aide")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class AideChatController {

    private final AideChatService aideChatService;
    private final AuthService authService;
    private final RateLimiter rateLimiter;

    @PostMapping("/chat")
    public RepAideChatDto chat(@RequestBody @Valid final ReqAideChatDto request) {
        final Long tenantId = authService.getCurrentTenantId();
        final Long userId = authService.getCurrentUserId();
        final String role = authService.getCurrentRole();

        // Rate limit : 10 questions/min/user.
        final String rlKey = "aide-chat:" + (userId != null ? userId : "anon");
        if (!rateLimiter.tryAcquire(rlKey, 10)) {
            throw new RateLimitExceededException("Trop de questions a l'aide en peu de temps. Patientez quelques instants.");
        }

        log.info("Question aide chat - role: {}, route: {}", role, request.getRouteCourante());
        final String reponse = aideChatService.repondre(request, tenantId, role);
        return RepAideChatDto.builder().reponse(reponse).build();
    }
}
