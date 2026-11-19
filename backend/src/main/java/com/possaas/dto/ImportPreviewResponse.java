package com.possaas.dto;

import java.util.List;

public record ImportPreviewResponse(
        int totalRows,
        int validRowsCount,
        int invalidRowsCount,
        List<ImportRowPreview> previewSample,
        List<ImportRowError> errors
) {
}
