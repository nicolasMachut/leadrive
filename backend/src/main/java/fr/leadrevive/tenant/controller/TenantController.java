package fr.leadrevive.tenant.controller;

import fr.leadrevive.common.security.AuthService;
import fr.leadrevive.tenant.dto.RepTenantDto;
import fr.leadrevive.tenant.dto.ReqCreateTenantDto;
import fr.leadrevive.tenant.dto.ReqUpdateTenantDto;
import fr.leadrevive.tenant.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;
    private final AuthService authService;

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public RepTenantDto getTenant(@PathVariable("id") final Long id) {
        final Long currentTenantId = authService.getCurrentTenantId();
        if (!currentTenantId.equals(id)) {
            throw new RuntimeException("Acces refuse");
        }
        return tenantService.getTenant(id);
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<RepTenantDto> getAllTenants() {
        return tenantService.getAllTenants();
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<RepTenantDto> createTenant(@RequestBody @Valid final ReqCreateTenantDto request) {
        final RepTenantDto created = tenantService.createTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public RepTenantDto updateTenant(@PathVariable("id") final Long id,
                                     @RequestBody @Valid final ReqUpdateTenantDto request) {
        // SECURITE : un manager ne peut modifier QUE sa propre concession.
        // Sans ce check, un manager du tenant A peut PUT /api/tenants/&lt;tenant_B&gt;
        // (Tenant n'a pas de @Filter Hibernate, donc findById passerait).
        final Long currentTenantId = authService.getCurrentTenantId();
        if (currentTenantId == null || !currentTenantId.equals(id)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Acces refuse");
        }
        return tenantService.updateTenant(id, request);
    }
}
