package com.possaas.dto;

import java.util.UUID;

public record SupervisorPinDto(
        UUID userId,
        String fullName,
        String pinHash
) {
}
