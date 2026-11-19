package com.possaas.dto.report;

import java.math.BigDecimal;
import java.util.List;

public final class TaxSummaryDtos {

    private TaxSummaryDtos() {
    }

    public record TaxRow(
            String label,
            String taxType,
            BigDecimal taxRate,
            BigDecimal taxableBase,
            BigDecimal taxAmount,
            BigDecimal total
    ) {
    }

    public record TaxSummaryResponse(
            BigDecimal taxableBaseTotal,
            BigDecimal taxAmountTotal,
            BigDecimal grandTotal,
            List<TaxRow> rows
    ) {
    }
}
