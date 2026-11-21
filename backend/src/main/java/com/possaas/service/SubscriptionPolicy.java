package com.possaas.service;

import com.possaas.domain.Tenant;
import com.possaas.dto.SubscriptionSnapshotDto;

import java.time.Duration;
import java.time.Instant;

public final class SubscriptionPolicy {
    public static final int GRACE_DAYS = 5;

    private SubscriptionPolicy() {}

    public static boolean isPastDue(Tenant tenant) {
        return "PAST_DUE".equalsIgnoreCase(tenant.getSubscriptionStatus());
    }

    public static boolean isSuspended(Tenant tenant) {
        return "SUSPENDED".equalsIgnoreCase(tenant.getSubscriptionStatus());
    }

    public static boolean isActive(Tenant tenant) {
        return "ACTIVE".equalsIgnoreCase(tenant.getSubscriptionStatus());
    }

    public static int graceDaysRemaining(Tenant tenant) {
        if (!isPastDue(tenant)) return 0;
        Instant due = tenant.getSubscriptionEndsAt() != null ? tenant.getSubscriptionEndsAt() : tenant.getTrialEndsAt();
        if (due == null) return GRACE_DAYS;
        long daysPast = Math.max(0, Duration.between(due, Instant.now()).toDays());
        return (int) Math.max(0, GRACE_DAYS - daysPast);
    }

    public static boolean inGracePeriod(Tenant tenant) {
        return isPastDue(tenant) && graceDaysRemaining(tenant) >= 0;
    }

    public static boolean canSell(Tenant tenant) {
        return !isSuspended(tenant);
    }

    public static SubscriptionSnapshotDto snapshot(Tenant tenant) {
        int limit = tenant.getElectronicFoliosLimit() == null ? 0 : tenant.getElectronicFoliosLimit();
        int used = tenant.getElectronicFoliosUsed() == null ? 0 : tenant.getElectronicFoliosUsed();
        int remaining = Math.max(0, limit - used);
        Instant end = tenant.getSubscriptionEndsAt() != null ? tenant.getSubscriptionEndsAt() : tenant.getTrialEndsAt();
        long daysRemaining = end == null ? 0 : Math.max(0, Duration.between(Instant.now(), end).toDays());
        boolean grace = inGracePeriod(tenant);
        return new SubscriptionSnapshotDto(
                tenant.getPlanCode(),
                tenant.getSubscriptionStatus(),
                tenant.getTrialEndsAt(),
                tenant.getSubscriptionEndsAt(),
                used,
                limit,
                remaining,
                grace,
                grace ? graceDaysRemaining(tenant) : 0,
                canSell(tenant)
        );
    }
}
