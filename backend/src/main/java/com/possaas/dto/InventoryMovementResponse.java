package com.possaas.dto;

import com.possaas.domain.MovementType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InventoryMovementResponse(
        UUID id,
        UUID branchId,
        UUID productId,
        String sku,
        String productName,
        MovementType movementType,
        BigDecimal quantity,
        BigDecimal previousStock,
        BigDecimal newStock,
        UUID referenceId,
        String notes,
        UUID createdBy,
        Instant createdAt
) {
}
