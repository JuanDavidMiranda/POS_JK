package com.possaas.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SyncPushResponse(
        UUID batchId,
        Instant processedAt,
        List<SyncEventResultDto> results
) {
}
