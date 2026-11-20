package com.possaas.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record LowStockProductDto(
        UUID id,
        UUID branchId,
        String sku,
        String name,
        BigDecimal currentStock,
        BigDecimal minStock
) {
}
