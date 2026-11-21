package com.possaas.dto.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class BillingDtos {
    private BillingDtos() {}

    public record PlanDto(
            String code,
            String name,
            BigDecimal monthlyPriceCop,
            BigDecimal annualPriceCop,
            int maxBranches,
            int maxTerminals,
            int includedFoliosPerMonth,
            boolean active
    ) {}

    public record FolioPackageDto(
            String code,
            String name,
            int foliosCount,
            BigDecimal priceCop
    ) {}

    public record PaymentTransactionDto(
            UUID id,
            String referenceCode,
            String gatewayTransactionId,
            String paymentType,
            String itemCode,
            BigDecimal amountCop,
            String status,
            String paymentMethodType,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record CurrentSubscriptionDto(
            String planCode,
            String planName,
            String subscriptionStatus,
            Instant trialEndsAt,
            Instant subscriptionEndsAt,
            int electronicFoliosUsed,
            int electronicFoliosLimit,
            int electronicFoliosRemaining,
            long daysRemaining,
            boolean inGracePeriod,
            long graceDaysRemaining
    ) {}

    public record OverviewResponse(
            CurrentSubscriptionDto subscription,
            List<PlanDto> plans,
            List<FolioPackageDto> folioPackages,
            List<PaymentTransactionDto> recentTransactions,
            boolean wompiSandbox,
            String wompiPublicKey
    ) {}

    public record CheckoutIntentRequest(
            String paymentType, // SUBSCRIPTION_MONTHLY, SUBSCRIPTION_ANNUAL, FOLIO_TOPUP
            String itemCode     // plan code or folio package code
    ) {}

    public record CheckoutIntentResponse(
            UUID transactionId,
            String publicKey,
            String reference,
            long amountInCents,
            String currency,
            String integritySignature,
            String redirectUrl,
            boolean sandbox,
            String itemName,
            String paymentType,
            String itemCode
    ) {}

    public record SandboxSimulateRequest(
            String referenceCode,
            boolean approve
    ) {}
}
