package com.possaas.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record SecurityPolicyDto(
        boolean requirePinForDiscounts,
        BigDecimal maxCashierDiscountPercent,
        boolean requirePinForVoidItem,
        boolean blindCashClose,
        Instant updatedAt
) {
}
