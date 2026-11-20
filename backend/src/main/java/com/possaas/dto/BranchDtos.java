package com.possaas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class BranchDtos {

    private BranchDtos() {
    }

    public record BranchRequest(
            @NotBlank @Size(max = 30) String code,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 255) String address,
            @Size(max = 120) String city,
            @Size(max = 40) String phone,
            Boolean active
    ) {
    }

    public record BranchResponse(
            UUID id,
            String code,
            String name,
            String address,
            String city,
            String phone,
            boolean main,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
