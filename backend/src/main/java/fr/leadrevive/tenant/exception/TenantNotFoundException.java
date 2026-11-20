package fr.leadrevive.tenant.exception;

import lombok.Getter;

@Getter
public class TenantNotFoundException extends RuntimeException {

    private final Long tenantId;

    public TenantNotFoundException(final Long tenantId) {
        super("Tenant non trouve - id: " + tenantId);
        this.tenantId = tenantId;
    }
}
