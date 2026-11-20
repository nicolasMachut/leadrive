package fr.leadrevive.common.tenant;

public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    public static Long getCurrentTenantId() {
        return CURRENT_TENANT_ID.get();
    }

    public static void setCurrentTenantId(final Long tenantId) {
        CURRENT_TENANT_ID.set(tenantId);
    }

    public static void clear() {
        CURRENT_TENANT_ID.remove();
    }
}
