package com.possaas.dto;

import java.util.UUID;

public record SyncEventResultDto(
        UUID idempotencyKey,
        String status,
        UUID saleId,
        String fullDocumentNumber,
        String message
) {

    public static SyncEventResultDto processed(UUID key, UUID saleId, String documentNumber) {
        return new SyncEventResultDto(key, "PROCESSED", saleId, documentNumber, "Venta registrada");
    }

    public static SyncEventResultDto duplicate(UUID key) {
        return new SyncEventResultDto(key, "DUPLICATE_IGNORED", null, null,
                "Idempotency key ya procesada: no se duplicó la venta ni se descontó stock");
    }

    public static SyncEventResultDto rejected(UUID key, String reason) {
        return new SyncEventResultDto(key, "REJECTED_CONFLICT", null, null, reason);
    }
}
