package com.possaas.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
// Not a @Component: registered explicitly in SecurityConfig.
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        boolean tenantSetHere = false;
        try {
            if (auth != null && auth.startsWith("Bearer ")) {
                String token = auth.substring(7).trim();
                if (!token.isEmpty() && SecurityContextHolder.getContext().getAuthentication() == null) {
                    try {
                        Claims claims = jwtService.parse(token);
                        AuthPrincipal principal = jwtService.toPrincipal(claims);
                        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + principal.role()));
                        var authentication = new UsernamePasswordAuthenticationToken(principal, token, authorities);
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        if (principal.tenantId() != null && TenantContext.get() == null) {
                            TenantContext.set(principal.tenantId());
                            tenantSetHere = true;
                        }
                    } catch (Exception ignored) {
                        // Token invalido: continuar sin autenticacion JWT (compatibilidad/401 manejado por seguridad)
                    }
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            if (tenantSetHere) {
                TenantContext.clear();
            }
        }
    }
}
