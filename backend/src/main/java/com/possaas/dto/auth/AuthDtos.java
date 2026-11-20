package com.possaas.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public class AuthDtos {

    public record RegisterRequest(
            @NotBlank String nit,
            @NotBlank String businessName,
            @NotBlank String tradeName,
            String city,
            String phone,
            @NotBlank String adminName,
            @NotBlank @Email String adminEmail,
            @NotBlank @Size(min = 6, max = 100) String password
    ) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    public record AuthUser(
            UUID id,
            UUID tenantId,
            String fullName,
            String email,
            String role
    ) {}

    public record AuthTenant(
            UUID id,
            String nit,
            String businessName,
            String tradeName,
            String planCode,
            String subscriptionStatus,
            boolean onboardingCompleted,
            int onboardingStep,
            Instant trialEndsAt,
            Instant subscriptionEndsAt
    ) {}

    public record AuthResponse(
            String token,
            String tokenType,
            long expiresInSeconds,
            AuthUser user,
            AuthTenant tenant
    ) {}

    public record MeResponse(AuthUser user, AuthTenant tenant) {}
}
