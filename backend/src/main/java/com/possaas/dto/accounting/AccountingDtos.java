package com.possaas.dto.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class AccountingDtos {
    private AccountingDtos() {}

    public record AccountingConfigDto(
            String targetSoftware,
            String salesIncomeAccount,
            String vatGeneratedAccount,
            String incGeneratedAccount,
            String cashAccount,
            String bankTransferAccount,
            String returnsAccount
    ) {}

    public record AccountingConfigRequest(
            String targetSoftware,
            String salesIncomeAccount,
            String vatGeneratedAccount,
            String incGeneratedAccount,
            String cashAccount,
            String bankTransferAccount,
            String returnsAccount
    ) {}

    public record AccountingRow(
            LocalDate date,
            String documentType,
            String documentNumber,
            String thirdParty,
            String concept,
            String accountCode,
            String accountName,
            BigDecimal debit,
            BigDecimal credit,
            String paymentMethod
    ) {}

    public record AccountingPreviewResponse(
            String format,
            LocalDate from,
            LocalDate to,
            List<AccountingRow> rows,
            int totalRows
    ) {}
}
