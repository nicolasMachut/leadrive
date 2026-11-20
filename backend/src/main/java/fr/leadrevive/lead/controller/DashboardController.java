package fr.leadrevive.lead.controller;

import fr.leadrevive.common.security.AuthService;
import fr.leadrevive.lead.dto.RepDashboardAdminDto;
import fr.leadrevive.lead.dto.RepDashboardDto;
import fr.leadrevive.lead.dto.RepDashboardManagerDto;
import fr.leadrevive.lead.dto.RepDashboardVendeurDto;
import fr.leadrevive.lead.dto.RepStatsMoisDto;
import fr.leadrevive.lead.dto.RepStatsVendeurDto;
import fr.leadrevive.lead.service.DashboardStatsService;
import fr.leadrevive.lead.service.VenteExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardStatsService dashboardStatsService;
    private final VenteExportService venteExportService;
    private final AuthService authService;

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<RepDashboardDto> getStats(@RequestParam(value = "mois", required = false) final String mois) {
        final Long tenantId = authService.getCurrentTenantId();
        if (tenantId == null) return ResponseEntity.noContent().build();
        final YearMonth yearMonth = mois != null ? YearMonth.parse(mois) : YearMonth.now();
        return ResponseEntity.ok(dashboardStatsService.getStatsGlobales(tenantId, yearMonth));
    }

    @GetMapping("/vendeurs")
    @PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
    public List<RepStatsVendeurDto> getStatsVendeurs(@RequestParam(value = "mois", required = false) final String mois) {
        final Long tenantId = authService.getCurrentTenantId();
        final YearMonth yearMonth = mois != null ? YearMonth.parse(mois) : YearMonth.now();
        return dashboardStatsService.getStatsVendeurs(tenantId, yearMonth);
    }

    @GetMapping("/evolution")
    @PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
    public List<RepStatsMoisDto> getEvolution(@RequestParam(value = "mois", defaultValue = "6") final Integer mois) {
        final Long tenantId = authService.getCurrentTenantId();
        return dashboardStatsService.getStatsMois(tenantId, mois);
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('MANAGER')")
    public RepDashboardAdminDto getStatsAdmin() {
        return dashboardStatsService.getStatsAdmin(authService.getCurrentTenantId());
    }

    @GetMapping("/manager")
    @PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<RepDashboardManagerDto> getStatsManager() {
        final Long tenantId = authService.getCurrentTenantId();
        if (tenantId == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(dashboardStatsService.getStatsManager(tenantId));
    }

    @GetMapping("/vendeur")
    // MANAGER autorise pour pouvoir basculer en vue vendeur (petites concessions ou periode pilote)
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public RepDashboardVendeurDto getStatsVendeur() {
        final Long tenantId = authService.getCurrentTenantId();
        final Long userId = authService.getCurrentUserId();
        if (tenantId == null || userId == null) {
            return new RepDashboardVendeurDto();
        }
        return dashboardStatsService.getStatsVendeur(tenantId, userId);
    }

    /**
     * Export CSV des ventes confirmees pour la periode donnee (par defaut : annee en cours).
     * Reserve aux managers et super admins.
     */
    @GetMapping(value = "/ventes/export", produces = "text/csv;charset=UTF-8")
    @PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<String> exporterVentesCsv(
            @RequestParam(value = "debut", required = false) final String debutStr,
            @RequestParam(value = "fin", required = false) final String finStr) {
        final Long tenantId = authService.getCurrentTenantId();
        if (tenantId == null) return ResponseEntity.noContent().build();
        final LocalDate debut = debutStr != null ? LocalDate.parse(debutStr) : LocalDate.now().withDayOfYear(1);
        final LocalDate fin = finStr != null ? LocalDate.parse(finStr) : LocalDate.now();
        final String csv = venteExportService.exporterVentes(tenantId, debut, fin);
        final String filename = "ventes_" + debut + "_" + fin + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv);
    }
}
