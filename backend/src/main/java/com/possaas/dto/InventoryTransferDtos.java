package com.possaas.dto;

import com.possaas.domain.InventoryTransferStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class InventoryTransferDtos {

    private InventoryTransferDtos() {
    }

    public record TransferItemRequest(
            @NotNull UUID productId,
            @NotNull @DecimalMin(value = "0.001", inclusive = true) BigDecimal quantity
    ) {
    }

    public record CreateTransferRequest(
            @NotNull UUID sourceBranchId,
            @NotNull UUID destinationBranchId,
            @Size(max = 500) String notes,
            UUID createdBy,
            @Valid @NotEmpty List<TransferItemRequest> items
    ) {
    }

    public record ReceiveTransferRequest(
            UUID receivedBy
    ) {
    }

    public record CancelTransferRequest(
            @Size(max = 500) String reason
    ) {
    }

    public record TransferItemResponse(
            UUID id,
            UUID productId,
            String sku,
            String productName,
            BigDecimal quantity
    ) {
    }

    public record TransferResponse(
            UUID id,
            String transferNumber,
            UUID sourceBranchId,
            String sourceBranchName,
            UUID destinationBranchId,
            String destinationBranchName,
            InventoryTransferStatus status,
            String notes,
            UUID createdBy,
            UUID receivedBy,
            Instant createdAt,
            Instant updatedAt,
            Instant dispatchedAt,
            Instant receivedAt,
            Instant cancelledAt,
            List<TransferItemResponse> items
    ) {
    }

    public record TransferSummaryResponse(
            UUID id,
            String transferNumber,
            UUID sourceBranchId,
            String sourceBranchName,
            UUID destinationBranchId,
            String destinationBranchName,
            InventoryTransferStatus status,
            int itemCount,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
