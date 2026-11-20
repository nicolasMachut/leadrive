package fr.leadrevive.tenant.service;

import fr.leadrevive.tenant.dto.RepTenantDto;
import fr.leadrevive.tenant.dto.ReqCreateTenantDto;
import fr.leadrevive.tenant.dto.ReqUpdateTenantDto;
import fr.leadrevive.tenant.entity.Tenant;
import fr.leadrevive.tenant.exception.TenantNotFoundException;
import fr.leadrevive.tenant.mapper.TenantMapper;
import fr.leadrevive.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantMapper tenantMapper;
    private final TenantBuilder tenantBuilder;

    @Transactional(readOnly = true)
    public RepTenantDto getTenant(final Long id) {
        log.info("Recuperation du tenant - idTenant: {}", id);
        final Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException(id));
        log.info("Tenant recupere - idTenant: {}", id);
        return tenantMapper.toDto(tenant);
    }

    @Transactional(readOnly = true)
    public List<RepTenantDto> getAllTenants() {
        log.info("Recuperation de tous les tenants");
        final List<Tenant> tenants = tenantRepository.findAll();
        log.info("Tenants recuperes - nombre: {}", tenants.size());
        return tenants.stream()
                .map(tenantMapper::toDto)
                .toList();
    }

    @Transactional
    public RepTenantDto createTenant(final ReqCreateTenantDto request) {
        log.info("Creation d'un tenant - nom: {}", request.getNom());
        final Tenant entity = tenantBuilder.buildFromRequest(request);
        final Tenant saved = tenantRepository.save(entity);
        log.info("Tenant cree - idTenant: {}", saved.getId());
        return tenantMapper.toDto(saved);
    }

    @Transactional
    public RepTenantDto updateTenant(final Long id, final ReqUpdateTenantDto request) {
        log.info("Mise a jour du tenant - idTenant: {}", id);
        final Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException(id));

        if (request.getNom() != null) {
            tenant.setNom(request.getNom());
        }
        if (request.getAdresse() != null) {
            tenant.setAdresse(request.getAdresse());
        }
        if (request.getTelephone() != null) {
            tenant.setTelephone(request.getTelephone());
        }
        if (request.getEmailContact() != null) {
            tenant.setEmailContact(request.getEmailContact());
        }
        if (request.getTimezone() != null) {
            tenant.setTimezone(request.getTimezone());
        }

        final Tenant saved = tenantRepository.save(tenant);
        log.info("Tenant mis a jour - idTenant: {}", id);
        return tenantMapper.toDto(saved);
    }
}
