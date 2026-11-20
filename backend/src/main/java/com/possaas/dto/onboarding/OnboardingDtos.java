package com.possaas.dto.onboarding;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class OnboardingDtos {

    public record TerminalInfo(
            UUID id,
            String name,
            String dianPrefix,
            Long rangeFrom,
            Long rangeTo,
            String creditNotePrefix,
            String dianResolutionNumber,
            LocalDate dianResolutionDate,
            LocalDate dianValidFrom,
            LocalDate dianValidTo,
            String dianTechnicalKey
    ) {}

    public record TenantFiscalInfo(
            UUID id,
            String nit,
            String businessName,
            String tradeName,
            String taxRegime,
            String fiscalResponsibilities,
            String city,
            String address,
            String phone,
            String email,
            String receiptFooterMessage,
            String planCode,
            String subscriptionStatus,
            Instant trialEndsAt
    ) {}

    public record OnboardingStatusResponse(
            boolean onboardingCompleted,
            int onboardingStep,
            TenantFiscalInfo tenant,
            TerminalInfo mainTerminal
    ) {}

    public record FiscalStepRequest(
            @NotBlank String taxRegime,
            @NotBlank String fiscalResponsibilities,
            String city,
            String address,
            String phone,
            String receiptFooterMessage
    ) {}

    public record DianStepRequest(
            @NotBlank String dianPrefix,
            String dianResolutionNumber,
            LocalDate dianResolutionDate,
            LocalDate dianValidFrom,
            LocalDate dianValidTo,
            @NotNull Long rangeFrom,
            @NotNull Long rangeTo,
            String dianTechnicalKey,
            Boolean verify
    ) {}

    public record DianStepResponse(
            boolean verified,
            String verificationMode,
            String verificationMessage,
            OnboardingStatusResponse status
    ) {}

    public record SeedCatalogRequest(
            String action, // "SKIP" or "SEED"
            String niche   // MINIMERCADO, FERRETERIA, DROGUERIA, MISCELANEA
    ) {}

    public record SeedCatalogResponse(
            String action,
            int productsCreated,
            String message,
            OnboardingStatusResponse status
    ) {}
}
