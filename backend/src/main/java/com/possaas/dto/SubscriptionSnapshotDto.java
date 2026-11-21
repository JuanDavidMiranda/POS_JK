package com.possaas.dto;

import java.time.Instant;

public record SubscriptionSnapshotDto(
        String planCode,
        String subscriptionStatus,
        Instant trialEndsAt,
        Instant subscriptionEndsAt,
        int electronicFoliosUsed,
        int electronicFoliosLimit,
        int electronicFoliosRemaining,
        boolean inGracePeriod,
        int graceDaysRemaining,
        boolean canSell
) {
}
