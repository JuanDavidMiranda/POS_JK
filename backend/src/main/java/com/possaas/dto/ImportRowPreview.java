package com.possaas.dto;

import java.util.Map;

public record ImportRowPreview(int rowNumber, String identifier, Map<String, String> values) {
}
