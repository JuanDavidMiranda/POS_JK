package com.possaas.service;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class ImportTemplateService {

    public byte[] buildTemplate(String entityType) {
        String type = normalizeEntityType(entityType);
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if ("PRODUCTS".equals(type)) {
                buildProductsTemplate(workbook);
            } else {
                buildCustomersTemplate(workbook);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No fue posible generar la plantilla Excel", ex);
        }
    }

    public String fileNameFor(String entityType) {
        return "PRODUCTS".equals(normalizeEntityType(entityType))
                ? "plantilla_productos.xlsx"
                : "plantilla_clientes.xlsx";
    }

    public String normalizeEntityType(String entityType) {
        if (entityType == null) {
            throw new IllegalArgumentException("entityType es obligatorio");
        }
        String normalized = entityType.trim().toUpperCase();
        if (!"PRODUCTS".equals(normalized) && !"CUSTOMERS".equals(normalized)) {
            throw new IllegalArgumentException("entityType debe ser PRODUCTS o CUSTOMERS");
        }
        return normalized;
    }

    private void buildProductsTemplate(XSSFWorkbook workbook) {
        String[] headers = {
                "barcode*", "sku*", "nombre*", "precio_venta*", "tipo_impuesto",
                "tarifa_impuesto", "stock_inicial*", "stock_minimo"
        };
        String[] example = {
                "7702511000012", "ARR-001", "Arroz Diana 500g", "2800", "IVA", "0", "50", "10"
        };
        Sheet data = createDataSheet(workbook, "Datos", headers, example, new int[]{0, 1});

        List<String[]> instructions = List.of(
                new String[]{"barcode*", "Código de barras del producto. Obligatorio si no se identifica por SKU. Se lee como texto para conservar ceros a la izquierda."},
                new String[]{"sku*", "Código interno/SKU. Obligatorio. Llave de búsqueda cuando no hay código de barras."},
                new String[]{"nombre*", "Nombre comercial del producto."},
                new String[]{"precio_venta*", "Precio de venta. Acepta formato colombiano: 2800, 2.800, $ 2.800,00."},
                new String[]{"tipo_impuesto", "IVA, INC o EXENTO. Si se deja vacío se asume EXENTO."},
                new String[]{"tarifa_impuesto", "0, 5 o 19 (también 0.05/0.19). Si se deja vacío: 0 para EXENTO/INC según regla y 19 para IVA."},
                new String[]{"stock_inicial*", "Cantidad inicial. Para productos NUEVOS se crea saldo inicial y movimiento INITIAL_BALANCE en kardex. Para productos existentes NO se modifica el stock actual."},
                new String[]{"stock_minimo", "Umbral de alerta de inventario bajo. Si se deja vacío se asume 0."},
                new String[]{"Upsert", "Se actualiza por (tenant, barcode) o por (tenant, sku) si no hay barcode. En actualización: nombre, precio e impuestos; el stock vivo no se sobrescribe."},
                new String[]{"Límites", "Máximo 5.000 filas de datos y archivo de hasta 5 MB."}
        );
        createInstructionsSheet(workbook, instructions);
        autosize(data, headers.length);
    }

    private void buildCustomersTemplate(XSSFWorkbook workbook) {
        String[] headers = {
                "tipo_documento*", "numero_documento*", "nombre_completo*", "correo"
        };
        String[] example = {
                "CC", "1032456789", "María Fernanda Gómez Pérez", "maria.gomez@correo.com"
        };
        Sheet data = createDataSheet(workbook, "Datos", headers, example, new int[]{0, 1, 3});

        List<String[]> instructions = List.of(
                new String[]{"tipo_documento*", "Tipo de documento: CC, NIT, CE, TI, PP, etc."},
                new String[]{"numero_documento*", "Número de documento leído como texto (conserva ceros a la izquierda). Para NIT se acepta con dígito de verificación (se normaliza)."},
                new String[]{"nombre_completo*", "Nombre o razón social del cliente."},
                new String[]{"correo", "Correo electrónico opcional. Si se informa debe ser válido."},
                new String[]{"Upsert", "Se actualiza por (tenant, tipo_documento, numero_documento)."},
                new String[]{"Límites", "Máximo 5.000 filas de datos y archivo de hasta 5 MB."}
        );
        createInstructionsSheet(workbook, instructions);
        autosize(data, headers.length);
    }

    private Sheet createDataSheet(XSSFWorkbook workbook, String name, String[] headers, String[] example, int[] textColumns) {
        Sheet sheet = workbook.createSheet(name);
        CellStyle headerStyle = headerStyle(workbook);
        CellStyle exampleStyle = exampleStyle(workbook);
        CellStyle textStyle = textStyle(workbook);

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        Row exampleRow = sheet.createRow(1);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = exampleRow.createCell(i);
            cell.setCellValue(i < example.length ? example[i] : "");
            cell.setCellStyle(exampleStyle);
        }

        for (int col : textColumns) {
            sheet.setDefaultColumnStyle(col, textStyle);
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 1, 0, headers.length - 1));
        return sheet;
    }

    private void createInstructionsSheet(XSSFWorkbook workbook, List<String[]> rows) {
        Sheet sheet = workbook.createSheet("Instrucciones");
        CellStyle headerStyle = headerStyle(workbook);
        CellStyle textStyle = wrapStyle(workbook);

        Row header = sheet.createRow(0);
        Cell h1 = header.createCell(0);
        h1.setCellValue("Campo / Regla");
        h1.setCellStyle(headerStyle);
        Cell h2 = header.createCell(1);
        h2.setCellValue("Descripción");
        h2.setCellStyle(headerStyle);

        int r = 1;
        for (String[] rowValues : rows) {
            Row row = sheet.createRow(r++);
            Cell c1 = row.createCell(0);
            c1.setCellValue(rowValues[0]);
            c1.setCellStyle(textStyle);
            Cell c2 = row.createCell(1);
            c2.setCellValue(rowValues[1]);
            c2.setCellStyle(textStyle);
        }
        sheet.setColumnWidth(0, 28 * 256);
        sheet.setColumnWidth(1, 100 * 256);
    }

    private void autosize(Sheet sheet, int cols) {
        for (int i = 0; i < cols; i++) {
            sheet.autoSizeColumn(i);
            int width = Math.min(Math.max(sheet.getColumnWidth(i) + 512, 16 * 256), 40 * 256);
            sheet.setColumnWidth(i, width);
        }
    }

    private CellStyle headerStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle exampleStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        DataFormat fmt = workbook.createDataFormat();
        style.setDataFormat(fmt.getFormat("@"));
        return style;
    }

    private CellStyle textStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat fmt = workbook.createDataFormat();
        style.setDataFormat(fmt.getFormat("@"));
        return style;
    }

    private CellStyle wrapStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        return style;
    }
}
