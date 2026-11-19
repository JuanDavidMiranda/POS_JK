package com.possaas.dto;

import java.time.Instant;
import java.util.UUID;

public record CustomerDto(
        UUID id,
        String documentType,
        String documentNumber,
        String fullName,
        String email,
        Instant updatedAt
) {
}
