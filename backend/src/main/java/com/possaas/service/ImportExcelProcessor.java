package com.possaas.service;

import com.possaas.dto.ImportPreviewResponse;
import com.possaas.dto.ImportRowError;
import com.possaas.dto.ImportRowPreview;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ImportExcelProcessor {

    private static final String[] PRODUCT_HEADERS = {
            "barcode*", "sku*", "nombre*", "precio_venta*", "tipo_impuesto", "tarifa_impuesto", "stock_inicial*", "stock_minimo", "sede_codigo"
    };
    private static final String[] CUSTOMER_HEADERS = {
            "tipo_documento*", "numero_documento*", "nombre_completo*", "correo"
    };

    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe adjuntar un archivo .xlsx");
        }
        if (file.getSize() > ExcelNormalizer.MAX_FILE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo supera el tamaño máximo de 5 MB");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se permiten archivos .xlsx");
        }
    }

    public String safeFileName(String original, String entityType) {
        if (original == null || original.isBlank()) {
            return (entityType == null ? "import" : entityType.toLowerCase()) + "_import.xlsx";
        }
        return original.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    public List<BulkImportModels.ExcelRowResult> parseAndValidate(String entityType, byte[] fileBytes) {
        try (InputStream in = new ByteArrayInputStream(fileBytes)) {
            return parseAndValidate(entityType, in);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No fue posible leer el archivo Excel: " + ex.getMessage());
        }
    }

    public List<BulkImportModels.ExcelRowResult> parseAndValidate(String entityType, InputStream in) throws IOException {
        return "PRODUCTS".equals(entityType) ? parseAndValidateProducts(in) : parseAndValidateCustomers(in);
    }

    public List<BulkImportModels.ExcelRowResult> parseAndValidateProducts(InputStream in) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null) {
                return List.of();
            }
            DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("es-CO"));
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            List<BulkImportModels.ExcelRowResult> results = new ArrayList<>();
            Set<String> seenBarcodes = new java.util.HashSet<>();
            Set<String> seenSkus = new java.util.HashSet<>();

            int last = sheet.getLastRowNum();
            int dataRows = 0;
            for (int i = 1; i <= last; i++) {
                Row row = sheet.getRow(i);
                if (ExcelNormalizer.isRowEmpty(row, PRODUCT_HEADERS.length)) {
                    continue;
                }
                dataRows++;
                if (dataRows > ExcelNormalizer.MAX_ROWS) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "El archivo supera el máximo de " + ExcelNormalizer.MAX_ROWS + " filas");
                }
                Map<String, String> raw = rawMap(PRODUCT_HEADERS, row, formatter, evaluator);
                int rowNumber = i + 1;
                try {
                    BulkImportModels.ProductRow parsed = parseProductRow(row, rowNumber, evaluator, raw);
                    if (parsed.barcode() != null && !seenBarcodes.add(parsed.barcode())) {
                        results.add(BulkImportModels.ExcelRowResult.err(rowNumber, parsed.identifier(), raw,
                                "Código de barras duplicado dentro del archivo: " + parsed.barcode()));
                        continue;
                    }
                    if (!seenSkus.add(parsed.sku())) {
                        results.add(BulkImportModels.ExcelRowResult.err(rowNumber, parsed.identifier(), raw,
                                "SKU duplicado dentro del archivo: " + parsed.sku()));
                        continue;
                    }
                    results.add(BulkImportModels.ExcelRowResult.ok(rowNumber, parsed.identifier(), raw, parsed));
                } catch (Exception ex) {
                    String id = identifierFromRaw(raw, "barcode*", "sku*");
                    results.add(BulkImportModels.ExcelRowResult.err(rowNumber, id, raw, safeMessage(ex)));
                }
            }
            return results;
        }
    }

    public List<BulkImportModels.ExcelRowResult> parseAndValidateCustomers(InputStream in) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null) {
                return List.of();
            }
            DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("es-CO"));
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            List<BulkImportModels.ExcelRowResult> results = new ArrayList<>();
            Set<String> seenDocs = new java.util.HashSet<>();

            int last = sheet.getLastRowNum();
            int dataRows = 0;
            for (int i = 1; i <= last; i++) {
                Row row = sheet.getRow(i);
                if (ExcelNormalizer.isRowEmpty(row, CUSTOMER_HEADERS.length)) {
                    continue;
                }
                dataRows++;
                if (dataRows > ExcelNormalizer.MAX_ROWS) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "El archivo supera el máximo de " + ExcelNormalizer.MAX_ROWS + " filas");
                }
                Map<String, String> raw = rawMap(CUSTOMER_HEADERS, row, formatter, evaluator);
                int rowNumber = i + 1;
                try {
                    BulkImportModels.CustomerRow parsed = parseCustomerRow(row, rowNumber, evaluator, raw);
                    String key = parsed.documentType() + "|" + parsed.documentNumber();
                    if (!seenDocs.add(key)) {
                        results.add(BulkImportModels.ExcelRowResult.err(rowNumber, parsed.identifier(), raw,
                                "Cliente duplicado dentro del archivo: " + parsed.identifier()));
                        continue;
                    }
                    results.add(BulkImportModels.ExcelRowResult.ok(rowNumber, parsed.identifier(), raw, parsed));
                } catch (Exception ex) {
                    String id = identifierFromRaw(raw, "tipo_documento*", "numero_documento*");
                    results.add(BulkImportModels.ExcelRowResult.err(rowNumber, id, raw, safeMessage(ex)));
                }
            }
            return results;
        }
    }

    public ImportPreviewResponse toPreview(List<BulkImportModels.ExcelRowResult> results) {
        List<ImportRowPreview> valid = new ArrayList<>();
        List<ImportRowError> errors = new ArrayList<>();
        for (BulkImportModels.ExcelRowResult r : results) {
            if (r.error() == null) {
                valid.add(new ImportRowPreview(r.rowNumber(), r.identifier(), previewValues(r.data())));
            } else {
                errors.add(new ImportRowError(r.rowNumber(), r.identifier(), r.rawValues(), r.error()));
            }
        }
        List<ImportRowPreview> sample = valid.size() > 20 ? new ArrayList<>(valid.subList(0, 20)) : valid;
        return new ImportPreviewResponse(results.size(), valid.size(), errors.size(), sample, errors);
    }

    public byte[] buildRejectedWorkbook(String entityType, List<BulkImportModels.ExcelRowResult> rejected) {
        String[] headers = "PRODUCTS".equals(entityType) ? PRODUCT_HEADERS : CUSTOMER_HEADERS;
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Rechazados");
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            CellStyle errorStyle = workbook.createCellStyle();
            Font errFont = workbook.createFont();
            errFont.setColor(IndexedColors.WHITE.getIndex());
            errFont.setBold(true);
            errorStyle.setFont(errFont);
            errorStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
            errorStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row hr = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = hr.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }
            Cell ec = hr.createCell(headers.length);
            ec.setCellValue("MOTIVO_ERROR");
            ec.setCellStyle(errorStyle);

            int r = 1;
            for (BulkImportModels.ExcelRowResult row : rejected) {
                Row rr = sheet.createRow(r++);
                for (int i = 0; i < headers.length; i++) {
                    String val = row.rawValues() == null ? "" : row.rawValues().getOrDefault(headers[i], "");
                    rr.createCell(i).setCellValue(val == null ? "" : val);
                }
                Cell reason = rr.createCell(headers.length);
                reason.setCellValue(row.error() == null ? "" : row.error());
                reason.setCellStyle(errorStyle);
            }
            for (int i = 0; i <= headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No fue posible generar el archivo de rechazados", ex);
        }
    }

    private BulkImportModels.ProductRow parseProductRow(Row row, int rowNumber, FormulaEvaluator evaluator, Map<String, String> raw) {
        String barcode = ExcelNormalizer.normalizeCode(ExcelNormalizer.cellText(row == null ? null : row.getCell(0), evaluator));
        String sku = ExcelNormalizer.normalizeCode(ExcelNormalizer.cellText(row == null ? null : row.getCell(1), evaluator));
        String name = ExcelNormalizer.cleanSpaces(ExcelNormalizer.cellText(row == null ? null : row.getCell(2), evaluator));
        String priceRaw = ExcelNormalizer.cellText(row == null ? null : row.getCell(3), evaluator);
        String taxTypeRaw = ExcelNormalizer.cellText(row == null ? null : row.getCell(4), evaluator);
        String taxRateRaw = ExcelNormalizer.cellText(row == null ? null : row.getCell(5), evaluator);
        String initialStockRaw = ExcelNormalizer.cellText(row == null ? null : row.getCell(6), evaluator);
        String minStockRaw = ExcelNormalizer.cellText(row == null ? null : row.getCell(7), evaluator);
        String branchCodeRaw = ExcelNormalizer.cellText(row == null ? null : row.getCell(8), evaluator);

        if (barcode.isBlank() && sku.isBlank()) {
            throw new IllegalArgumentException("Debe informar barcode o sku");
        }
        if (sku.isBlank()) {
            sku = barcode;
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("El nombre es obligatorio");
        }
        if (priceRaw.isBlank()) {
            throw new IllegalArgumentException("El precio de venta es obligatorio");
        }
        if (initialStockRaw.isBlank()) {
            throw new IllegalArgumentException("El stock inicial es obligatorio");
        }

        String taxType = ExcelNormalizer.normalizeTaxType(taxTypeRaw);
        BigDecimal taxRate = ExcelNormalizer.normalizeTaxRate(taxRateRaw, taxType);
        BigDecimal salePrice = ExcelNormalizer.parseColombianMoney(priceRaw, "Precio de venta", true);
        BigDecimal initialStock = ExcelNormalizer.parseQuantity(initialStockRaw, "Stock inicial", true, 3);
        BigDecimal minStock = ExcelNormalizer.parseQuantity(minStockRaw, "Stock mínimo", false, 3);
        String branchCode = branchCodeRaw == null ? null : branchCodeRaw.trim().toUpperCase();
        if (branchCode != null && branchCode.isBlank()) {
            branchCode = null;
        }

        return new BulkImportModels.ProductRow(rowNumber, raw, barcode.isBlank() ? null : barcode, sku, name,
                salePrice, taxType, taxRate, initialStock, minStock, branchCode);
    }

    private BulkImportModels.CustomerRow parseCustomerRow(Row row, int rowNumber, FormulaEvaluator evaluator, Map<String, String> raw) {
        String docType = ExcelNormalizer.normalizeDocumentType(ExcelNormalizer.cellText(row == null ? null : row.getCell(0), evaluator));
        String docNumberRaw = ExcelNormalizer.cellText(row == null ? null : row.getCell(1), evaluator);
        String name = ExcelNormalizer.cleanSpaces(ExcelNormalizer.cellText(row == null ? null : row.getCell(2), evaluator));
        String emailRaw = ExcelNormalizer.cellText(row == null ? null : row.getCell(3), evaluator);

        if (docType.isBlank()) {
            throw new IllegalArgumentException("El tipo de documento es obligatorio");
        }
        String docNumber = ExcelNormalizer.normalizeDocumentNumber(docType, docNumberRaw);
        if (docNumber.isBlank()) {
            throw new IllegalArgumentException("El número de documento es obligatorio");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("El nombre completo es obligatorio");
        }
        String email = ExcelNormalizer.normalizeEmail(emailRaw);

        return new BulkImportModels.CustomerRow(rowNumber, raw, docType, docNumber, name, email);
    }

    private Map<String, String> rawMap(String[] headers, Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row == null ? null : row.getCell(i);
            map.put(headers[i], cell == null ? "" : ExcelNormalizer.cleanSpaces(formatter.formatCellValue(cell, evaluator)));
        }
        return map;
    }

    private Map<String, String> previewValues(Object data) {
        if (data instanceof BulkImportModels.ProductRow p) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("barcode", p.barcode());
            m.put("sku", p.sku());
            m.put("nombre", p.name());
            m.put("precio_venta", p.salePrice() == null ? "" : p.salePrice().toPlainString());
            m.put("tipo_impuesto", p.taxType());
            m.put("tarifa_impuesto", p.taxRate() == null ? "" : p.taxRate().toPlainString());
            m.put("stock_inicial", p.initialStock() == null ? "" : p.initialStock().toPlainString());
            m.put("stock_minimo", p.minStock() == null ? "" : p.minStock().toPlainString());
            m.put("sede_codigo", p.branchCode() == null ? "" : p.branchCode());
            return m;
        }
        if (data instanceof BulkImportModels.CustomerRow c) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("tipo_documento", c.documentType());
            m.put("numero_documento", c.documentNumber());
            m.put("nombre_completo", c.fullName());
            m.put("correo", c.email());
            return m;
        }
        return Map.of();
    }

    private String identifierFromRaw(Map<String, String> raw, String a, String b) {
        String va = raw.getOrDefault(a, "");
        String vb = raw.getOrDefault(b, "");
        String id = (va + " " + vb).trim();
        return id.isEmpty() ? "-" : id;
    }

    private String safeMessage(Exception ex) {
        String msg = ex.getMessage();
        if (msg == null || msg.isBlank()) {
            return "Fila inválida";
        }
        return msg;
    }
}
