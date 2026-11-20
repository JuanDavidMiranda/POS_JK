package com.possaas.service;

import java.math.BigDecimal;
import java.util.Map;

public final class BulkImportModels {

    private BulkImportModels() {
    }

    public record ProductRow(
            int rowNumber,
            Map<String, String> rawValues,
            String barcode,
            String sku,
            String name,
            BigDecimal salePrice,
            String taxType,
            BigDecimal taxRate,
            BigDecimal initialStock,
            BigDecimal minStock,
            String branchCode
    ) {
        public String identifier() {
            if (barcode != null && !barcode.isBlank()) {
                return barcode;
            }
            return sku == null ? "" : sku;
        }
    }

    public record CustomerRow(
            int rowNumber,
            Map<String, String> rawValues,
            String documentType,
            String documentNumber,
            String fullName,
            String email
    ) {
        public String identifier() {
            return (documentType == null ? "" : documentType) + " " + (documentNumber == null ? "" : documentNumber);
        }
    }

    public record ExcelRowResult(int rowNumber, String identifier, Map<String, String> rawValues, Object data, String error) {
        public static ExcelRowResult ok(int rowNumber, String identifier, Map<String, String> rawValues, Object data) {
            return new ExcelRowResult(rowNumber, identifier, rawValues, data, null);
        }

        public static ExcelRowResult err(int rowNumber, String identifier, Map<String, String> rawValues, String error) {
            return new ExcelRowResult(rowNumber, identifier, rawValues, null, error);
        }
    }

    public record BatchOutcome(int created, int updated) {
    }

    public record RejectedFile(String fileName, byte[] data) {
    }
}
