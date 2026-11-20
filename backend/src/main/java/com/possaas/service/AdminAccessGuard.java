package com.possaas.service;

import com.possaas.config.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Restringe operaciones administrativas a usuarios con rol ADMIN.
 * Soporta autenticación JWT (principal) y, para compatibilidad legacy/offline, headers X-User-Role/X-User-Id.
 */
@Component
public class AdminAccessGuard {

    public static final String ROLE_HEADER = "X-User-Role";
    public static final String USER_HEADER = "X-User-Id";

    public void requireAdmin() {
        currentAdminUserId();
    }

    public UUID currentAdminUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
            if ("ADMIN".equalsIgnoreCase(principal.role()) || "SUPER_ADMIN".equalsIgnoreCase(principal.role())) {
                return principal.userId();
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso restringido: se requiere rol ADMIN");
        }

        HttpServletRequest request = currentRequest();
        String role = header(request, ROLE_HEADER);
        if (role == null || !"ADMIN".equalsIgnoreCase(role.trim())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso restringido: se requiere rol ADMIN");
        }

        String rawUser = header(request, USER_HEADER);
        if (rawUser == null || rawUser.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Falta el identificador del usuario administrador");
        }
        try {
            return UUID.fromString(rawUser.trim());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-User-Id debe ser un UUID valido");
        }
    }

    private String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null ? null : value.trim();
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No hay contexto de peticion para validar rol ADMIN");
    }
}
