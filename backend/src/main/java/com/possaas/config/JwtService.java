package com.possaas.config;

import com.possaas.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationSeconds;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-seconds:43200}") long expirationSeconds) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            // pad to minimum length for HS256
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            for (int i = bytes.length; i < 32; i++) padded[i] = (byte) i;
            bytes = padded;
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expirationSeconds = expirationSeconds;
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(expirationSeconds);
        var builder = Jwts.builder()
                .subject(user.getId().toString())
                .claim("role", user.getRole())
                .claim("name", user.getFullName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp));
        if (user.getTenantId() != null) {
            builder.claim("tenantId", user.getTenantId().toString());
        }
        if (user.getEmail() != null) {
            builder.claim("email", user.getEmail());
        }
        return builder.signWith(key).compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public AuthPrincipal toPrincipal(Claims claims) {
        UUID userId = UUID.fromString(claims.getSubject());
        String tenant = claims.get("tenantId", String.class);
        UUID tenantId = (tenant == null || tenant.isBlank()) ? null : UUID.fromString(tenant);
        return new AuthPrincipal(
                userId,
                tenantId,
                claims.get("email", String.class),
                claims.get("role", String.class),
                claims.get("name", String.class)
        );
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
