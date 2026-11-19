package com.possaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SaleItemPayloadDto(
        @NotNull UUID id,
        @NotNull UUID productId,
        @NotNull @DecimalMin(value = "0.001") BigDecimal quantity,
        @NotNull @DecimalMin("0") BigDecimal unitPrice,
        @NotNull @DecimalMin("0") BigDecimal taxRate,
        @NotNull @DecimalMin("0") BigDecimal taxAmount,
        @NotNull @DecimalMin("0") BigDecimal lineTotal
) {
}
