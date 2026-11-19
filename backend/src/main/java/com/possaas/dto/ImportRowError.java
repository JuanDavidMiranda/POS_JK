package com.possaas.dto;

import java.util.Map;

public record ImportRowError(int rowNumber, String identifier, Map<String, String> rawValues, String errorMessage) {
}
