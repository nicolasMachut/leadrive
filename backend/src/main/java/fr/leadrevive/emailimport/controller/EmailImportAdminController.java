package fr.leadrevive.emailimport.controller;

import fr.leadrevive.common.admin.AdminService;
import fr.leadrevive.emailimport.dto.RepInboundEmailLogDto;
import fr.leadrevive.emailimport.dto.RepSourcesStatsDto;
import fr.leadrevive.emailimport.dto.RepTenantEmailConfigDto;
import fr.leadrevive.emailimport.entity.StatutInboundEmail;
import fr.leadrevive.emailimport.entity.TenantEmailConfig;
import fr.leadrevive.emailimport.service.EmailImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints super admin pour la gestion des adresses email d'import.
 *
 * <p>Operations cross-tenant : config par concession (creation retroactive,
 * desactivation), liste des emails recus (paginee, filtrable), detail d'un
 * email pour debug parsing.</p>
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class EmailImportAdminController {

    private final AdminService adminService;
    private final EmailImportService emailImportService;

    /**
     * Recupere la config email d'une concession (peut etre absente si tenant cree
     * avant l'epic 24).
     */
    @GetMapping("/concessions/{id}/email-config")
    public ResponseEntity<RepTenantEmailConfigDto> getEmailConfig(@PathVariable final Long id) {
        final RepTenantEmailConfigDto dto = emailImportService.getConfigAdmin(id);
        return dto != null ? ResponseEntity.ok(dto) : ResponseEntity.notFound().build();
    }

    /**
     * Cree retroactivement l'adresse email d'import pour une concession existante.
     * 409 si deja existante.
     */
    @PostMapping("/concessions/{id}/email-config")
    public ResponseEntity<RepTenantEmailConfigDto> creerEmailConfig(@PathVariable final Long id) {
        try {
            final TenantEmailConfig config = adminService.creerEmailConfigRetroactif(id);
            final RepTenantEmailConfigDto dto = emailImportService.getConfigAdmin(id);
            log.info("Email config creee retroactivement - tenantId: {}, email: {}",
                    id, config.getEmailAddress());
            return ResponseEntity.status(201).body(dto);
        } catch (final IllegalStateException e) {
            log.warn("Conflit creation email config - tenantId: {}, cause: {}", id, e.getMessage());
            return ResponseEntity.status(409).build();
        }
    }

    /** Desactive l'adresse email d'une concession (le webhook rejettera ensuite). */
    @DeleteMapping("/concessions/{id}/email-config")
    public ResponseEntity<Void> desactiverEmailConfig(@PathVariable final Long id) {
        adminService.desactiverEmailConfig(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Regenere l'adresse email d'une concession (nouveau slug + token).
     * L'ancienne adresse devient morte (webhook la rejettera). L'historique
     * inbound_email_log reste preserve (lie par tenant_id, pas par adresse).
     */
    @PostMapping("/concessions/{id}/email-config/regenerer")
    public ResponseEntity<RepTenantEmailConfigDto> regenererEmailConfig(@PathVariable final Long id) {
        try {
            adminService.regenererEmailConfig(id);
            final RepTenantEmailConfigDto dto = emailImportService.getConfigAdmin(id);
            return ResponseEntity.ok(dto);
        } catch (final IllegalStateException e) {
            log.warn("Regeneration email config impossible - tenantId: {}, cause: {}", id, e.getMessage());
            return ResponseEntity.status(409).build();
        }
    }

    /**
     * Liste paginee des emails recus (cross-tenant). Filtres optionnels :
     * {@code tenantId} et {@code statut}.
     */
    @GetMapping("/email-logs")
    public Page<RepInboundEmailLogDto> listerLogs(
            @RequestParam(required = false) final Long tenantId,
            @RequestParam(required = false) final StatutInboundEmail statut,
            final Pageable pageable) {
        return emailImportService.listerLogsAdmin(tenantId, statut, pageable);
    }

    /** Detail complet d'un email pour debug parsing (body + JSON IA). */
    @GetMapping("/email-logs/{id}")
    public ResponseEntity<RepInboundEmailLogDto> getLog(@PathVariable final Long id) {
        return emailImportService.getLogAdmin(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Stats par source de leads (cross-tenant, filtre optionnel par tenant).
     * Periode par defaut : 30 jours.
     */
    @GetMapping("/stats/sources")
    public RepSourcesStatsDto statsSources(
            @RequestParam(defaultValue = "30") final int periode,
            @RequestParam(required = false) final Long tenantId) {
        return emailImportService.statsSourcesAdmin(periode, tenantId);
    }
}
