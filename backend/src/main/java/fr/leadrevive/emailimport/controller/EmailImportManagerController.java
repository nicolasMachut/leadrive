package fr.leadrevive.emailimport.controller;

import fr.leadrevive.emailimport.dto.RepInboundEmailLogDto;
import fr.leadrevive.emailimport.dto.RepTenantEmailConfigDto;
import fr.leadrevive.emailimport.service.EmailImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints manager pour consulter sa configuration d'import email.
 *
 * <p>Le manager voit uniquement la config + les logs de son propre tenant
 * (filtre Hibernate {@code tenantFilter} actif).</p>
 */
@RestController
@RequestMapping("/api/parametres/email-import")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
public class EmailImportManagerController {

    private final EmailImportService emailImportService;

    /**
     * Recupere la config email du tenant courant. 404 si pas encore d'adresse
     * (concessions creees avant l'epic 24 sans creation retroactive).
     */
    @GetMapping
    public ResponseEntity<RepTenantEmailConfigDto> getConfig() {
        final RepTenantEmailConfigDto dto = emailImportService.getConfigPourTenantCourant();
        return dto != null ? ResponseEntity.ok(dto) : ResponseEntity.notFound().build();
    }

    /**
     * Liste paginee des emails recus par le tenant courant.
     */
    @GetMapping("/logs")
    public Page<RepInboundEmailLogDto> listerLogs(final Pageable pageable) {
        return emailImportService.listerLogsManager(pageable);
    }
}
