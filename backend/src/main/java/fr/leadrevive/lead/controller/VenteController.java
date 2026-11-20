package fr.leadrevive.lead.controller;

import fr.leadrevive.common.security.AuthService;
import fr.leadrevive.common.security.LeadAccessGuard;
import fr.leadrevive.lead.dto.RepRdvEnAttenteDto;
import fr.leadrevive.lead.dto.RepVenteDto;
import fr.leadrevive.lead.dto.ReqVenteDto;
import fr.leadrevive.lead.dto.ReqVenteRapideDto;
import fr.leadrevive.lead.service.DashboardStatsService;
import fr.leadrevive.lead.service.VenteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class VenteController {

    private final VenteService venteService;
    private final DashboardStatsService dashboardStatsService;
    private final AuthService authService;
    private final LeadAccessGuard leadAccessGuard;

    @PostMapping("/leads/{leadId}/vente")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public ResponseEntity<RepVenteDto> confirmerVente(@PathVariable("leadId") final Long leadId,
                                                       @RequestBody @Valid final ReqVenteDto request) {
        // SECURITE : VENDEUR ne peut confirmer une vente que sur SES leads (404 anonyme sinon).
        leadAccessGuard.requireAccessibleLead(leadId);
        final Long tenantId = authService.getCurrentTenantId();
        final Long userId = authService.getCurrentUserId();
        final RepVenteDto result = venteService.confirmerVente(leadId, tenantId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/leads/{leadId}/vente")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public RepVenteDto getVenteLead(@PathVariable("leadId") final Long leadId) {
        // SECURITE : VENDEUR ne peut lire la vente que de SES leads.
        leadAccessGuard.requireAccessibleLead(leadId);
        final Long tenantId = authService.getCurrentTenantId();
        return venteService.getVenteLead(leadId, tenantId);
    }

    @GetMapping("/ventes")
    @PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
    public List<RepVenteDto> listerVentes(@RequestParam(value = "mois", required = false) final String mois) {
        final Long tenantId = authService.getCurrentTenantId();
        final YearMonth yearMonth = mois != null ? YearMonth.parse(mois) : YearMonth.now();
        return venteService.listerVentes(tenantId, yearMonth);
    }

    @DeleteMapping("/ventes/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> supprimerVente(@PathVariable("id") final Long id) {
        final Long tenantId = authService.getCurrentTenantId();
        venteService.supprimerVente(id, tenantId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/ventes/confirmer-rapide")
    @PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<RepVenteDto> confirmerRapide(@RequestBody @Valid final ReqVenteRapideDto request) {
        final Long tenantId = authService.getCurrentTenantId();
        final Long userId = authService.getCurrentUserId();
        final RepVenteDto result = venteService.confirmerRapide(request, tenantId, userId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/ventes/en-attente")
    @PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
    public List<RepRdvEnAttenteDto> getRdvsEnAttente(
            @RequestParam(value = "joursSansConfirmation", defaultValue = "3") final Integer jours) {
        return dashboardStatsService.listerRdvEnAttente(authService.getCurrentTenantId(), jours);
    }
}
