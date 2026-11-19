package com.possaas.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SyncPullResponse(
        Instant serverTime,
        UUID batchId,
        UUID tenantId,
        TerminalConfigDto terminal,
        List<ProductDto> products,
        List<CustomerDto> customers
) {
}
