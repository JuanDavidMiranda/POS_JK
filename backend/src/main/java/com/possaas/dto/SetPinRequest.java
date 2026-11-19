package com.possaas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SetPinRequest(
        @NotBlank
        @Pattern(regexp = "\\d{4,6}", message = "El PIN debe tener entre 4 y 6 dígitos numéricos")
        String pin
) {
}
