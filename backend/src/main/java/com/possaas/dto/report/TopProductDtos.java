package com.possaas.dto.report;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class TopProductDtos {

    private TopProductDtos() {
    }

    public record TopProductItem(
            UUID productId,
            String sku,
            String name,
            BigDecimal unitsSold,
            BigDecimal revenue,
            BigDecimal participationPercent
    ) {
    }

    public record TopProductsResponse(
            BigDecimal totalRevenue,
            BigDecimal totalUnits,
            List<TopProductItem> items
    ) {
    }
}
