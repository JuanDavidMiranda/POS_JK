package com.possaas.dto;

import java.time.Instant;
import java.util.UUID;

public record ImportJobResponse(
        UUID jobId,
        String entityType,
        String fileName,
        String status,
        int totalRows,
        int processedRows,
        int createdCount,
        int updatedCount,
        int rejectedCount,
        boolean hasRejectedFile,
        Instant createdAt,
        Instant finishedAt
) {
}
