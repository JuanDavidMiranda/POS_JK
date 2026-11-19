package com.possaas.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Normalización y validación de valores provenientes de Excel para importación Colombia. */
public final class ExcelNormalizer {

    public static final int MAX_ROWS = 5_000;
    public static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    private static final DataFormatter FORMATTER = new DataFormatter(Locale.forLanguageTag("es-CO"));
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private ExcelNormalizer() {
    }

    public static String cellText(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        try {
            String value;
            if (cell.getCellType() == CellType.FORMULA) {
                CellType cached = cell.getCachedFormulaResultType();
                if (cached == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
                    value = numericAsPlainString(cell.getNumericCellValue());
                } else if (cached == CellType.STRING) {
                    value = cell.getStringCellValue();
                } else {
                    value = FORMATTER.formatCellValue(cell, evaluator);
                }
            } else if (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
                value = numericAsPlainString(cell.getNumericCellValue());
            } else {
                value = FORMATTER.formatCellValue(cell, evaluator);
            }
            return cleanSpaces(value);
        } catch (Exception ex) {
            return cleanSpaces(FORMATTER.formatCellValue(cell));
        }
    }

    public static String numericAsPlainString(double numeric) {
        BigDecimal decimal = BigDecimal.valueOf(numeric);
        String plain = decimal.stripTrailingZeros().toPlainString();
        if ("0.0".equals(plain) || "-0.0".equals(plain)) {
            return "0";
        }
        return plain;
    }

    public static String cleanSpaces(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\u00A0', ' ').trim();
        return MULTI_SPACE.matcher(normalized).replaceAll(" ");
    }

    public static String normalizeCode(String raw) {
        String value = cleanSpaces(raw);
        if (value.isEmpty()) {
            return "";
        }
        // Evitar notación científica residual si llega formateada.
        if (value.contains("E+") || value.contains("e+")) {
            try {
                value = new BigDecimal(value).toPlainString();
            } catch (NumberFormatException ignored) {
                // conservar valor original limpio
            }
        }
        return value;
    }

    public static String normalizeDocumentNumber(String documentType, String raw) {
        String value = normalizeCode(raw);
        if (value.isEmpty()) {
            return "";
        }
        String type = cleanSpaces(documentType).toUpperCase(Locale.ROOT);
        if ("NIT".equals(type)) {
            // Elimina dígito de verificación si viene pegado con guion o espacio.
            int dash = value.indexOf('-');
            if (dash > 0) {
                value = value.substring(0, dash).trim();
            } else {
                int space = value.indexOf(' ');
                if (space > 0 && space == value.length() - 2) {
                    value = value.substring(0, space).trim();
                }
            }
        }
        return value.replace(".", "").replace(",", "").trim();
    }

    public static String normalizeDocumentType(String raw) {
        String value = cleanSpaces(raw).toUpperCase(Locale.ROOT);
        return value;
    }

    public static BigDecimal parseColombianMoney(String raw, String fieldName, boolean required) {
        String value = cleanSpaces(raw)
                .replace("$", "")
                .replace("COP", "")
                .replace("cop", "")
                .replace("\u00A0", "")
                .replace(" ", "");
        if (value.isEmpty()) {
            if (required) {
                throw new IllegalArgumentException(fieldName + " es obligatorio");
            }
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        boolean hasComma = value.contains(",");
        boolean hasDot = value.contains(".");
        String normalized;
        if (hasComma && hasDot) {
            int lastComma = value.lastIndexOf(',');
            int lastDot = value.lastIndexOf('.');
            if (lastComma > lastDot) {
                // 12.500,50 -> 12500.50
                normalized = value.replace(".", "").replace(",", ".");
            } else {
                // 12,500.50 -> 12500.50
                normalized = value.replace(",", "");
            }
        } else if (hasComma) {
            // 12500,50 -> 12500.50
            normalized = value.replace(".", "").replace(",", ".");
        } else {
            // 12500 o 12500.50 (o 12.500 ambiguo; se interpreta como número plano/thousands si patrón de miles)
            if (hasDot && Pattern.matches("^\\d{1,3}(\\.\\d{3})+$", value)) {
                normalized = value.replace(".", "");
            } else {
                normalized = value;
            }
        }

        try {
            BigDecimal amount = new BigDecimal(normalized);
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException(fieldName + " no puede ser negativo");
            }
            return amount.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " tiene un formato numérico inválido: " + raw);
        }
    }

    public static BigDecimal parseQuantity(String raw, String fieldName, boolean required, int scale) {
        String value = cleanSpaces(raw).replace("\u00A0", "").replace(" ", "");
        if (value.isEmpty()) {
            if (required) {
                throw new IllegalArgumentException(fieldName + " es obligatorio");
            }
            return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);
        }
        boolean hasComma = value.contains(",");
        boolean hasDot = value.contains(".");
        String normalized;
        if (hasComma && hasDot) {
            if (value.lastIndexOf(',') > value.lastIndexOf('.')) {
                normalized = value.replace(".", "").replace(",", ".");
            } else {
                normalized = value.replace(",", "");
            }
        } else if (hasComma) {
            normalized = value.replace(".", "").replace(",", ".");
        } else if (hasDot && Pattern.matches("^\\d{1,3}(\\.\\d{3})+$", value)) {
            normalized = value.replace(".", "");
        } else {
            normalized = value;
        }
        try {
            BigDecimal qty = new BigDecimal(normalized);
            if (qty.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException(fieldName + " no puede ser negativo");
            }
            return qty.setScale(scale, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " tiene un formato numérico inválido: " + raw);
        }
    }

    public static String normalizeTaxType(String raw) {
        String value = stripAccents(cleanSpaces(raw)).toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            return "EXENTO";
        }
        return switch (value) {
            case "IVA" -> "IVA";
            case "INC", "IMPOCONSUMO", "IMPUESTO AL CONSUMO" -> "INC";
            case "EXENTO", "EXEMPT", "EXENTA", "EXCLUIDO", "EXCLUIDA" -> "EXENTO";
            default -> throw new IllegalArgumentException("Tipo de impuesto inválido: " + raw + " (use IVA, INC o EXENTO)");
        };
    }

    public static BigDecimal normalizeTaxRate(String raw, String taxType) {
        String value = cleanSpaces(raw).replace("%", "").replace(" ", "").replace(",", ".");
        BigDecimal rate;
        if (value.isEmpty()) {
            rate = "IVA".equals(taxType) ? new BigDecimal("19") : BigDecimal.ZERO;
            if (!"IVA".equals(taxType) && !"INC".equals(taxType)) {
                rate = BigDecimal.ZERO;
            }
        } else {
            try {
                rate = new BigDecimal(value);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Tarifa de impuesto inválida: " + raw);
            }
        }

        if (rate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("La tarifa de impuesto no puede ser negativa");
        }

        // Acepta 0,5,19 o 0.05/0.19
        if (rate.compareTo(BigDecimal.ONE) > 0) {
            if (rate.compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("Tarifa de impuesto fuera de rango: " + raw);
            }
            rate = rate.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        } else {
            rate = rate.setScale(4, RoundingMode.HALF_UP);
        }

        if ("EXENTO".equals(taxType)) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        if (rate.compareTo(BigDecimal.ZERO) != 0
                && rate.compareTo(new BigDecimal("0.0500")) != 0
                && rate.compareTo(new BigDecimal("0.1900")) != 0) {
            // permitir valor explícito pero dentro de rango razonable
            if (rate.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("Tarifa de impuesto no soportada: " + raw);
            }
        }
        return rate;
    }

    public static String normalizeEmail(String raw) {
        String value = cleanSpaces(raw);
        if (value.isEmpty()) {
            return null;
        }
        String email = value.toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("Correo electrónico inválido: " + raw);
        }
        return email;
    }

    public static boolean isRowEmpty(Row row, int lastColumn) {
        if (row == null) {
            return true;
        }
        for (int i = 0; i < lastColumn; i++) {
            String text = cleanSpaces(FORMATTER.formatCellValue(row.getCell(i)));
            if (!text.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static Map<String, String> rawMap(String[] headers, Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < headers.length; i++) {
            String header = headers[i] == null ? "col_" + (i + 1) : headers[i];
            Cell cell = row.getCell(i);
            String value = cell == null ? "" : cleanSpaces(formatter.formatCellValue(cell, evaluator));
            map.put(header, value);
        }
        return map;
    }

    public static String stripAccents(String input) {
        if (input == null) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "");
    }
}
