package com.possaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SyncEventDto(
        @NotNull UUID idempotencyKey,
        @NotBlank String entityType,
        @Valid @NotNull SalePayloadDto payload
) {
}
