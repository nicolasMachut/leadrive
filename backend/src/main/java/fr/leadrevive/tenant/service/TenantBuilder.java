package fr.leadrevive.tenant.service;

import fr.leadrevive.tenant.dto.ReqCreateTenantDto;
import fr.leadrevive.tenant.entity.Tenant;
import fr.leadrevive.tenant.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TenantBuilder {

    private final TenantMapper tenantMapper;

    public Tenant buildFromRequest(final ReqCreateTenantDto request) {
        log.info("Construction du tenant depuis la requete - nom: {}", request.getNom());
        final Tenant tenant = tenantMapper.toEntity(request);
        return tenant;
    }
}
