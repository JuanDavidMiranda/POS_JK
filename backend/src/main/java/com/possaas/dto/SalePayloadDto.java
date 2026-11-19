package com.possaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SalePayloadDto(
        @NotNull UUID id,
        @NotNull UUID terminalId,
        UUID customerId,
        @NotBlank String documentType,
        @NotBlank String dianPrefix,
        @NotNull Long sequenceNumber,
        @NotBlank String fullDocumentNumber,
        String dianStatus,
        @NotNull @DecimalMin("0") BigDecimal subtotal,
        @NotNull @DecimalMin("0") BigDecimal taxTotal,
        @NotNull @DecimalMin("0") BigDecimal grandTotal,
        @NotBlank String paymentMethod,
        @NotNull Instant soldAt,
        @Valid @NotEmpty List<SaleItemPayloadDto> items
) {
}
