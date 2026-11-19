package com.possaas.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CreditNoteItemResponse(
        UUID id,
        UUID productId,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal taxAmount,
        BigDecimal lineTotal
) {
}
