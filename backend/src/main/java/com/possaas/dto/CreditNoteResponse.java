package com.possaas.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreditNoteResponse(
        UUID id,
        UUID saleId,
        UUID branchId,
        UUID terminalId,
        String dianPrefix,
        long sequenceNumber,
        String fullDocumentNumber,
        String reasonCode,
        String reasonDescription,
        String dianStatus,
        String cude,
        BigDecimal subtotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal,
        Instant createdAt,
        String saleStatus,
        List<CreditNoteItemResponse> items
) {
}
