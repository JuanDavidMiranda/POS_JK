package com.possaas.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
// Not a @Component: registered explicitly in SecurityConfig after JWT filter.

import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Extrae el tenant del header X-Tenant-Id para compatibilidad con clientes legacy (sync/offline).
 * Las rutas públicas, de autenticación y de super-admin no requieren tenant en header; si llega,
 * se ignora para no activar filtros de tenant de cliente. Si ya existe contexto (p.ej. JWT), no se sobreescribe.
 */
public class TenantHeaderFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Tenant-Id";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/public/")
                || path.startsWith("/api/v1/auth/")
                || path.startsWith("/api/v1/super-admin/")
                || path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (TenantContext.get() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawTenant = request.getHeader(TENANT_HEADER);
        if (rawTenant == null || rawTenant.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Falta el header " + TENANT_HEADER);
            return;
        }

        try {
            TenantContext.set(UUID.fromString(rawTenant.trim()));
        } catch (IllegalArgumentException ex) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, TENANT_HEADER + " debe ser un UUID valido");
            return;
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
