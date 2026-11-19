package com.possaas.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreditNoteRequest(
        @NotNull UUID id,
        @NotNull UUID saleId,
        @NotNull UUID terminalId,
        @NotBlank String reasonCode,
        @NotBlank String reasonDescription,
        @Valid @NotNull List<CreditNoteItemRequest> items
) {
}
