package fr.leadrevive.stats.controller;

import fr.leadrevive.common.security.AuthService;
import fr.leadrevive.stats.dto.RepPipelineStatsDto;
import fr.leadrevive.stats.service.PipelineStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final PipelineStatsService pipelineStatsService;
    private final AuthService authService;

    @GetMapping("/pipeline")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public RepPipelineStatsDto getPipelineStats(
            @RequestParam(value = "periode", defaultValue = "30j") final String periode,
            @RequestParam(value = "vendeurId", required = false) final Long vendeurId) {
        final Long tenantId = authService.getCurrentTenantId();
        // SECURITE : un VENDEUR ne peut consulter QUE ses propres stats.
        // Sans ce check, vendeur A pouvait taper ?vendeurId=B et voir les performances
        // de son collegue (perf vendeur, taux de RDV, etc.).
        final Long vendeurEffectif = "VENDEUR".equals(authService.getCurrentRole())
                ? authService.getCurrentUserId() : vendeurId;
        return pipelineStatsService.getStats(tenantId, vendeurEffectif, periode);
    }
}
