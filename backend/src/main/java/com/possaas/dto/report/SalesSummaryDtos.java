package com.possaas.dto.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class SalesSummaryDtos {

    private SalesSummaryDtos() {
    }

    public record PaymentBreakdown(
            String paymentMethod,
            BigDecimal grossTotal,
            long transactions
    ) {
    }

    public record DailyBreakdown(
            LocalDate date,
            BigDecimal grossSales,
            BigDecimal creditNotes,
            BigDecimal netSales,
            BigDecimal taxTotal,
            long transactions
    ) {
    }

    public record SalesSummaryResponse(
            BigDecimal grossSales,
            BigDecimal creditNotesTotal,
            BigDecimal netSales,
            BigDecimal taxTotal,
            long transactions,
            BigDecimal averageTicket,
            List<PaymentBreakdown> paymentBreakdown,
            List<DailyBreakdown> dailyBreakdown
    ) {
    }
}
