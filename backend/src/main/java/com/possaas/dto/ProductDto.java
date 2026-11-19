package com.possaas.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductDto(
        UUID id,
        String barcode,
        String sku,
        String name,
        BigDecimal salePrice,
        String taxType,
        BigDecimal taxRate,
        BigDecimal currentStock,
        Instant updatedAt
) {
}
