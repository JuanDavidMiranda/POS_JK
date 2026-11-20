package com.possaas.dto;

import com.possaas.domain.MovementType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryMovementRequest(
        UUID branchId,
        @NotNull UUID productId,
        @NotNull MovementType movementType,
        @NotNull @DecimalMin(value = "0.001", inclusive = true) BigDecimal quantity,
        @NotBlank @Size(max = 255) String notes,
        UUID createdBy
) {
}
