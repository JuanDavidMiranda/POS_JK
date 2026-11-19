package com.possaas.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/** Extrae el tenant del header X-Tenant-Id y lo deja disponible en el hilo de la petición. */
@Component
public class TenantHeaderFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Tenant-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
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
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, TENANT_HEADER + " debe ser un UUID válido");
            return;
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
