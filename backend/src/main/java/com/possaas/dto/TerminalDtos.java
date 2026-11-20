package com.possaas.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class TerminalDtos {

    private TerminalDtos() {
    }

    public record TerminalRequest(
            @NotNull UUID branchId,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 10) String dianPrefix,
            @NotNull @Min(1) Long rangeFrom,
            @NotNull @Min(1) Long rangeTo,
            @Size(max = 10) String creditNotePrefix,
            Boolean active
    ) {
    }

    public record TerminalResponse(
            UUID id,
            UUID branchId,
            String branchCode,
            String branchName,
            String name,
            String dianPrefix,
            long rangeFrom,
            long rangeTo,
            long lastSyncedSequence,
            String creditNotePrefix,
            long creditNoteLastSequence,
            boolean active,
            Instant updatedAt
    ) {
    }
}
