package com.possaas.config;

import java.util.Objects;
import java.util.UUID;

/** Contexto del tenant por petición. Alimentado por {@link TenantHeaderFilter}. */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID tenantId) {
        CURRENT_TENANT.set(Objects.requireNonNull(tenantId, "tenantId"));
    }

    public static UUID get() {
        return CURRENT_TENANT.get();
    }

    public static UUID require() {
        UUID tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new IllegalStateException("No hay tenant en el contexto de la petición");
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
