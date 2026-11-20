package com.possaas.dto;

import java.util.UUID;

public record TerminalConfigDto(
        UUID id,
        UUID branchId,
        String branchCode,
        String branchName,
        String name,
        String dianPrefix,
        long rangeFrom,
        long rangeTo,
        long lastSyncedSequence,
        long nextSequence,
        String creditNotePrefix,
        long creditNoteLastSequence,
        long creditNoteNextSequence
) {
}
