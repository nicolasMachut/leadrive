package fr.leadrevive.ai.controller;

import fr.leadrevive.ai.dto.RepAiLogDto;
import fr.leadrevive.ai.dto.RepAiStatsDto;
import fr.leadrevive.ai.dto.RepFichePreparationDto;
import fr.leadrevive.ai.dto.RepTenantAiConfigDto;
import fr.leadrevive.ai.dto.ReqTenantAiConfigDto;
import fr.leadrevive.ai.entity.AiLog;
import fr.leadrevive.ai.entity.TypeGenerationEnum;
import fr.leadrevive.ai.repository.AiLogRepository;
import fr.leadrevive.ai.service.AiCoutService;
import fr.leadrevive.ai.service.FichePreparationService;
import fr.leadrevive.ai.service.TenantAiConfigService;
import fr.leadrevive.common.ratelimit.RateLimitExceededException;
import fr.leadrevive.common.ratelimit.RateLimiter;
import fr.leadrevive.common.security.AuthService;
import fr.leadrevive.common.security.LeadAccessGuard;
import fr.leadrevive.lead.exception.LeadNotFoundException;
import fr.leadrevive.lead.repository.LeadRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AiController {

    private final FichePreparationService fichePreparationService;
    private final TenantAiConfigService tenantAiConfigService;
    private final AiCoutService aiCoutService;
    private final AiLogRepository aiLogRepository;
    private final LeadRepository leadRepository;
    private final RateLimiter rateLimiter;
    private final AuthService authService;
    private final LeadAccessGuard leadAccessGuard;

    /**
     * Retourne la fiche cachee si elle existe et est a jour. 204 si jamais generee ou perimee.
     */
    @GetMapping("/leads/{id}/fiche-preparation")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public ResponseEntity<RepFichePreparationDto> getFiche(@PathVariable("id") final Long leadId) {
        // SECURITE : VENDEUR ne peut lire la fiche que pour SES leads (404 anonyme sinon).
        leadAccessGuard.requireAccessibleLead(leadId);
        final Long tenantId = authService.getCurrentTenantId();
        final RepFichePreparationDto cached = fichePreparationService.getFicheSiExiste(leadId, tenantId);
        if (cached != null) {
            return ResponseEntity.ok(cached);
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Genere (ou regenere) la fiche. Persiste en cache sur le lead.
     */
    @PostMapping("/leads/{id}/fiche-preparation")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public RepFichePreparationDto genererFiche(@PathVariable("id") final Long leadId) {
        // SECURITE : VENDEUR ne peut generer la fiche que pour SES leads (404 anonyme sinon).
        leadAccessGuard.requireAccessibleLead(leadId);
        final Long tenantId = authService.getCurrentTenantId();
        final Long userId = authService.getCurrentUserId();
        if (!rateLimiter.tryAcquire("fiche-prepa:" + (userId != null ? userId : "anon"), 3)) {
            throw new RateLimitExceededException("Trop de generations de fiche en peu de temps. Patientez 1 minute.");
        }
        return fichePreparationService.genererFiche(leadId, tenantId);
    }

    @GetMapping("/leads/{id}/ai-logs")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public List<RepAiLogDto> getAiLogs(@PathVariable("id") final Long leadId) {
        // SECURITE : VENDEUR ne peut lire les logs IA que pour SES leads.
        leadAccessGuard.requireAccessibleLead(leadId);
        // On mappe vers un DTO de METADONNEES : ne JAMAIS renvoyer l'entite AiLog brute
        // (elle contient le prompt = system prompt interne + PII prospect, et la reponse brute).
        return aiLogRepository.findByLeadIdAndTypeGenerationOrderByCreeLe(leadId, TypeGenerationEnum.FICHE_PREPARATION)
                .stream()
                .map(log -> new RepAiLogDto(
                        log.getId(),
                        log.getLeadId(),
                        log.getTypeGeneration() != null ? log.getTypeGeneration().name() : null,
                        log.getModele(),
                        log.getTokensPrompt(),
                        log.getTokensReponse(),
                        log.getCoutEstime(),
                        log.getDureeMs(),
                        log.getStatut() != null ? log.getStatut().name() : null,
                        log.getErreur() != null,
                        log.getCreeLe()))
                .toList();
    }

    @GetMapping("/ai/config")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<RepTenantAiConfigDto> getConfig() {
        final Long tenantId = authService.getCurrentTenantId();
        final RepTenantAiConfigDto config = tenantAiConfigService.getConfigDto(tenantId);
        if (config == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(config);
    }

    @PutMapping("/ai/config")
    @PreAuthorize("hasRole('MANAGER')")
    public RepTenantAiConfigDto saveConfig(@RequestBody @Valid final ReqTenantAiConfigDto request) {
        final Long tenantId = authService.getCurrentTenantId();
        return tenantAiConfigService.saveConfig(tenantId, request);
    }

    @GetMapping("/ai/stats")
    @PreAuthorize("hasRole('MANAGER')")
    public RepAiStatsDto getStats() {
        final Long tenantId = authService.getCurrentTenantId();
        return aiCoutService.getStats(tenantId);
    }
}
