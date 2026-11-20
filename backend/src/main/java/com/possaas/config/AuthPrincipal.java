package com.possaas.config;

import java.util.UUID;

public record AuthPrincipal(
        UUID userId,
        UUID tenantId,
        String email,
        String role,
        String fullName
) {
    public boolean isSuperAdmin() {
        return "SUPER_ADMIN".equalsIgnoreCase(role);
    }
}
