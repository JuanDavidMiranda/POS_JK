package com.possaas.service;

import com.possaas.config.TenantContext;
import com.possaas.config.TenantFilterSupport;
import com.possaas.dto.report.SalesSummaryDtos;
import com.possaas.dto.report.TaxSummaryDtos;
import com.possaas.dto.report.TopProductDtos;
import com.possaas.repository.CreditNoteItemRepository;
import com.possaas.repository.CreditNoteRepository;
import com.possaas.repository.SaleItemRepository;
import com.possaas.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final ZoneId REPORT_ZONE = ZoneId.of("America/Bogota");

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final CreditNoteRepository creditNoteRepository;
    private final CreditNoteItemRepository creditNoteItemRepository;
    private final TenantFilterSupport tenantFilterSupport;

    @Transactional(readOnly = true)
    public SalesSummaryDtos.SalesSummaryResponse salesSummary(LocalDate from, LocalDate to, UUID branchId) {
        Range range = range(from, to);
        UUID tenantId = TenantContext.require();
        tenantFilterSupport.enable(tenantId);

        SaleRepository.SalesTotalsProjection totals = saleRepository.totals(tenantId, range.from(), range.to(), branchId);
        CreditNoteRepository.CreditNoteTotalsProjection cnTotals = creditNoteRepository.totals(tenantId, range.from(), range.to(), branchId);

        BigDecimal gross = nz(totals == null ? null : totals.getGrossSales());
        BigDecimal tax = nz(totals == null ? null : totals.getTaxTotal());
        long tx = totals == null || totals.getTransactions() == null ? 0L : totals.getTransactions();
        BigDecimal cn = nz(cnTotals == null ? null : cnTotals.getTotal());
        BigDecimal net = gross.subtract(cn).setScale(2, RoundingMode.HALF_UP);
        BigDecimal avg = tx > 0 ? gross.divide(BigDecimal.valueOf(tx), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        List<SalesSummaryDtos.PaymentBreakdown> payments = saleRepository.paymentBreakdown(tenantId, range.from(), range.to(), branchId)
                .stream()
                .map(p -> new SalesSummaryDtos.PaymentBreakdown(
                        p.getPaymentMethod(),
                        nz(p.getGrossTotal()),
                        p.getTransactions() == null ? 0L : p.getTransactions()))
                .toList();

        Map<LocalDate, BigDecimal> cnByDay = new HashMap<>();
        for (CreditNoteRepository.DailyCreditNoteProjection d : creditNoteRepository.dailyTotals(tenantId, range.from(), range.to(), branchId)) {
            if (d.getDate() != null) {
                cnByDay.put(d.getDate(), nz(d.getTotal()));
            }
        }

        List<SalesSummaryDtos.DailyBreakdown> daily = saleRepository.dailyBreakdown(tenantId, range.from(), range.to(), branchId)
                .stream()
                .map(d -> {
                    BigDecimal dayGross = nz(d.getGrossSales());
                    BigDecimal dayCn = cnByDay.getOrDefault(d.getDate(), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                    BigDecimal dayNet = dayGross.subtract(dayCn).setScale(2, RoundingMode.HALF_UP);
                    return new SalesSummaryDtos.DailyBreakdown(
                            d.getDate(),
                            dayGross,
                            dayCn,
                            dayNet,
                            nz(d.getTaxTotal()),
                            d.getTransactions() == null ? 0L : d.getTransactions());
                })
                .toList();

        return new SalesSummaryDtos.SalesSummaryResponse(
                gross, cn, net, tax, tx, avg, payments, daily);
    }

    @Transactional(readOnly = true)
    public TopProductDtos.TopProductsResponse topProducts(LocalDate from, LocalDate to, Integer limit, UUID branchId) {
        Range range = range(from, to);
        UUID tenantId = TenantContext.require();
        tenantFilterSupport.enable(tenantId);
        int lim = (limit == null || limit <= 0) ? 20 : Math.min(limit, 200);

        List<SaleItemRepository.TopProductProjection> rows = saleItemRepository.topProducts(tenantId, range.from(), range.to(), lim, branchId);
        BigDecimal totalRevenue = rows.stream().map(r -> nz(r.getRevenue())).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalUnits = rows.stream().map(r -> nz(r.getUnitsSold())).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(3, RoundingMode.HALF_UP);

        List<TopProductDtos.TopProductItem> items = rows.stream().map(r -> {
            BigDecimal revenue = nz(r.getRevenue());
            BigDecimal participation = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                    ? revenue.multiply(BigDecimal.valueOf(100)).divide(totalRevenue, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            return new TopProductDtos.TopProductItem(
                    r.getProductId(), r.getSku(), r.getName(), nz(r.getUnitsSold()), revenue, participation);
        }).toList();

        return new TopProductDtos.TopProductsResponse(totalRevenue, totalUnits, items);
    }

    @Transactional(readOnly = true)
    public TaxSummaryDtos.TaxSummaryResponse taxSummary(LocalDate from, LocalDate to, UUID branchId) {
        Range range = range(from, to);
        UUID tenantId = TenantContext.require();
        tenantFilterSupport.enable(tenantId);

        Map<String, TaxSummaryDtos.TaxRow> merged = new LinkedHashMap<>();

        for (SaleItemRepository.TaxBreakdownProjection r : saleItemRepository.taxBreakdown(tenantId, range.from(), range.to(), branchId)) {
            String key = key(r.getTaxType(), r.getTaxRate());
            merged.put(key, row(r.getTaxType(), r.getTaxRate(), nz(r.getTaxableBase()), nz(r.getTaxAmount()), nz(r.getTotal())));
        }
        for (CreditNoteItemRepository.CreditTaxBreakdownProjection r : creditNoteItemRepository.creditTaxBreakdown(tenantId, range.from(), range.to(), branchId)) {
            String key = key(r.getTaxType(), r.getTaxRate());
            TaxSummaryDtos.TaxRow existing = merged.get(key);
            BigDecimal base = nz(r.getTaxableBase()).negate();
            BigDecimal tax = nz(r.getTaxAmount()).negate();
            BigDecimal total = nz(r.getTotal()).negate();
            if (existing == null) {
                merged.put(key, row(r.getTaxType(), r.getTaxRate(), base, tax, total));
            } else {
                merged.put(key, row(r.getTaxType(), r.getTaxRate(),
                        existing.taxableBase().add(base).setScale(2, RoundingMode.HALF_UP),
                        existing.taxAmount().add(tax).setScale(2, RoundingMode.HALF_UP),
                        existing.total().add(total).setScale(2, RoundingMode.HALF_UP)));
            }
        }

        List<TaxSummaryDtos.TaxRow> rows = new ArrayList<>(merged.values());
        rows.sort(Comparator.comparing(TaxSummaryDtos.TaxRow::taxType, Comparator.nullsLast(String::compareTo))
                .thenComparing(TaxSummaryDtos.TaxRow::taxRate, Comparator.nullsLast(BigDecimal::compareTo)));

        BigDecimal baseTotal = rows.stream().map(TaxSummaryDtos.TaxRow::taxableBase).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxTotal = rows.stream().map(TaxSummaryDtos.TaxRow::taxAmount).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal grand = rows.stream().map(TaxSummaryDtos.TaxRow::total).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        return new TaxSummaryDtos.TaxSummaryResponse(baseTotal, taxTotal, grand, rows);
    }

    @Transactional(readOnly = true)
    public byte[] exportTaxSummary(LocalDate from, LocalDate to, UUID branchId) {
        TaxSummaryDtos.TaxSummaryResponse summary = taxSummary(from, to, branchId);
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Resumen Tributario");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            CreationHelper helper = workbook.getCreationHelper();
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd"));

            int r = 0;
            Row meta1 = sheet.createRow(r++);
            meta1.createCell(0).setCellValue("Informe Tributario");
            meta1.getCell(0).setCellStyle(headerStyle);
            Row meta2 = sheet.createRow(r++);
            meta2.createCell(0).setCellValue("Desde");
            Cell fromCell = meta2.createCell(1);
            fromCell.setCellValue(java.sql.Date.valueOf(from));
            fromCell.setCellStyle(dateStyle);
            meta2.createCell(2).setCellValue("Hasta");
            Cell toCell = meta2.createCell(3);
            toCell.setCellValue(java.sql.Date.valueOf(to));
            toCell.setCellStyle(dateStyle);
            meta2.createCell(4).setCellValue("Sede");
            meta2.createCell(5).setCellValue(branchId == null ? "Todas" : branchId.toString());
            r++; // blank

            Row header = sheet.createRow(r++);
            String[] cols = {"Tipo impuesto", "Tarifa", "Base gravable", "Impuesto", "Total"};
            for (int i = 0; i < cols.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(headerStyle);
            }

            for (TaxSummaryDtos.TaxRow row : summary.rows()) {
                Row rr = sheet.createRow(r++);
                rr.createCell(0).setCellValue(row.label());
                rr.createCell(1).setCellValue(row.taxRate() == null ? 0d : row.taxRate().doubleValue());
                rr.createCell(2).setCellValue(row.taxableBase() == null ? 0d : row.taxableBase().doubleValue());
                rr.createCell(3).setCellValue(row.taxAmount() == null ? 0d : row.taxAmount().doubleValue());
                rr.createCell(4).setCellValue(row.total() == null ? 0d : row.total().doubleValue());
            }

            r++;
            Row totalRow = sheet.createRow(r);
            Cell tl = totalRow.createCell(0);
            tl.setCellValue("TOTALES");
            tl.setCellStyle(headerStyle);
            totalRow.createCell(2).setCellValue(summary.taxableBaseTotal().doubleValue());
            totalRow.createCell(3).setCellValue(summary.taxAmountTotal().doubleValue());
            totalRow.createCell(4).setCellValue(summary.grandTotal().doubleValue());

            for (int i = 0; i < cols.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar el archivo de exportación", e);
        }
    }

    public String exportFileName(LocalDate from, LocalDate to) {
        DateTimeFormatter fmt = DateTimeFormatter.BASIC_ISO_DATE;
        return "informe-tributario_" + from.format(fmt) + "_" + to.format(fmt) + ".xlsx";
    }

    private Range range(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los parámetros 'from' y 'to' son obligatorios");
        }
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El rango de fechas es inválido");
        }
        Instant fromInstant = from.atStartOfDay(REPORT_ZONE).toInstant();
        Instant toExclusive = to.plusDays(1).atStartOfDay(REPORT_ZONE).toInstant();
        return new Range(fromInstant, toExclusive);
    }

    private String key(String taxType, BigDecimal rate) {
        return (taxType == null ? "" : taxType) + "|" + (rate == null ? "0" : rate.stripTrailingZeros().toPlainString());
    }

    private TaxSummaryDtos.TaxRow row(String taxType, BigDecimal rate, BigDecimal base, BigDecimal tax, BigDecimal total) {
        BigDecimal r = rate == null ? BigDecimal.ZERO : rate;
        String label = (taxType == null ? "IMPUESTO" : taxType) + " " + r.stripTrailingZeros().toPlainString() + "%";
        return new TaxSummaryDtos.TaxRow(label, taxType, r, base, tax, total);
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private record Range(Instant from, Instant to) {
    }
}
