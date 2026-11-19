package com.possaas.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SecurityPolicyUpdateRequest(
        @NotNull Boolean requirePinForDiscounts,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal maxCashierDiscountPercent,
        @NotNull Boolean requirePinForVoidItem,
        @NotNull Boolean blindCashClose
) {
}
