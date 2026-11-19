package com.possaas.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class CashShiftDtos {

    private CashShiftDtos() {
    }

    public record OpenRequest(
            UUID terminalId,
            @NotNull @DecimalMin("0") BigDecimal openingAmount,
            String notes
    ) {
    }

    public record CloseRequest(
            UUID authorizedByUserId,
            @NotNull @DecimalMin("0") BigDecimal countedCashAmount,
            String notes
    ) {
    }

    public record CashShiftResponse(
            UUID id,
            UUID terminalId,
            UUID openedBy,
            UUID closedBy,
            UUID authorizedByUserId,
            String status,
            BigDecimal openingAmount,
            BigDecimal expectedCashAmount,
            BigDecimal countedCashAmount,
            BigDecimal differenceAmount,
            Instant openedAt,
            Instant closedAt,
            String notes
    ) {
    }
}
