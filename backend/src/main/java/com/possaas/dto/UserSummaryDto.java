package com.possaas.dto;

import java.time.Instant;
import java.util.UUID;

public record UserSummaryDto(
        UUID id,
        String username,
        String fullName,
        String role,
        boolean active,
        boolean hasSupervisorPin,
        Instant updatedAt
) {
}