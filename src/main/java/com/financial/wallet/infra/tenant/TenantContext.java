package com.financial.wallet.infra.tenant;

import java.util.UUID;

/**
 * Holds the current tenant ID in a ThreadLocal for the duration of a request.
 * Must be cleared in a finally block after each request to prevent leakage
 * between thread-pool reuses.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }

    public static boolean hasTenant() {
        return CURRENT_TENANT.get() != null;
    }
}
