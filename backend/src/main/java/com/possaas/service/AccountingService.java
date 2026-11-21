package com.possaas.service;

import com.possaas.config.TenantContext;
import com.possaas.config.TenantFilterSupport;
import com.possaas.domain.CreditNote;
import com.possaas.domain.CreditNoteItem;
import com.possaas.domain.Sale;
import com.possaas.domain.SaleItem;
import com.possaas.domain.Tenant;
import com.possaas.domain.TenantAccountingConfig;
import com.possaas.dto.accounting.AccountingDtos;
import com.possaas.repository.CreditNoteItemRepository;
import com.possaas.repository.CreditNoteRepository;
import com.possaas.repository.SaleItemRepository;
import com.possaas.repository.SaleRepository;
import com.possaas.repository.TenantAccountingConfigRepository;
import com.possaas.repository.TenantRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountingService {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private static final Set<String> FORMATS = Set.of("SIIGO", "ALEGRA", "WORLD_OFFICE", "GENERIC_PUC");

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final CreditNoteRepository creditNoteRepository;
    private final CreditNoteItemRepository creditNoteItemRepository;
    private final TenantRepository tenantRepository;
    private final TenantAccountingConfigRepository configRepository;
    private final TenantFilterSupport tenantFilterSupport;

    @Transactional
    public AccountingDtos.AccountingConfigDto getConfig() {
        UUID tenantId = TenantContext.require();
        TenantAccountingConfig cfg = configRepository.findById(tenantId).orElseGet(() -> createDefault(tenantId));
        return toDto(cfg);
    }

    @Transactional
    public AccountingDtos.AccountingConfigDto updateConfig(AccountingDtos.AccountingConfigRequest request) {
        UUID tenantId = TenantContext.require();
        TenantAccountingConfig cfg = configRepository.findById(tenantId).orElseGet(() -> createDefault(tenantId));
        String format = normalizeFormat(request.targetSoftware());
        cfg.setTargetSoftware(format);
        cfg.setSalesIncomeAccount(orDefault(request.salesIncomeAccount(), "413505"));
        cfg.setVatGeneratedAccount(orDefault(request.vatGeneratedAccount(), "240805"));
        cfg.setIncGeneratedAccount(orDefault(request.incGeneratedAccount(), "249505"));
        cfg.setCashAccount(orDefault(request.cashAccount(), "110505"));
        cfg.setBankTransferAccount(orDefault(request.bankTransferAccount(), "111005"));
        cfg.setReturnsAccount(orDefault(request.returnsAccount(), "417505"));
        cfg.setUpdatedAt(Instant.now());
        configRepository.save(cfg);
        return toDto(cfg);
    }

    @Transactional(readOnly = true)
    public AccountingDtos.AccountingPreviewResponse preview(LocalDate from, LocalDate to, String format) {
        validateRange(from, to);
        String fmt = normalizeFormat(format);
        List<AccountingDtos.AccountingRow> rows = buildRows(from, to, fmt);
        List<AccountingDtos.AccountingRow> first = rows.size() > 10 ? rows.subList(0, 10) : rows;
        return new AccountingDtos.AccountingPreviewResponse(fmt, from, to, new ArrayList<>(first), rows.size());
    }

    @Transactional(readOnly = true)
    public byte[] exportXlsx(LocalDate from, LocalDate to, String format) {
        validateRange(from, to);
        String fmt = normalizeFormat(format);
        List<AccountingDtos.AccountingRow> rows = buildRows(from, to, fmt);
        return buildWorkbook(rows, from, to, fmt);
    }

    public String filename(LocalDate from, LocalDate to, String format) {
        String fmt = normalizeFormat(format);
        return "export-contable-" + fmt.toLowerCase(Locale.ROOT) + "-" + from.format(FILE_DATE) + "-" + to.format(FILE_DATE) + ".xlsx";
    }

    private List<AccountingDtos.AccountingRow> buildRows(LocalDate from, LocalDate to, String format) {
        UUID tenantId = TenantContext.require();
        tenantFilterSupport.enable(tenantId);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant no encontrado"));
        TenantAccountingConfig cfg = configRepository.findById(tenantId).orElseGet(() -> defaultConfig(tenantId));
        String thirdParty = tenant.getNit() == null ? "CLIENTE" : tenant.getNit();
        Instant fromI = from.atStartOfDay(ZONE).toInstant();
        Instant toI = to.plusDays(1).atStartOfDay(ZONE).toInstant();

        List<Sale> sales = saleRepository.findByTenantIdAndSoldAtBetween(tenantId, fromI, toI);
        sales.sort(Comparator.comparing(Sale::getSoldAt, Comparator.nullsLast(Comparator.naturalOrder())));

        List<CreditNote> cns = creditNoteRepository.findAll().stream()
                .filter(cn -> tenantId.equals(cn.getTenantId()))
                .filter(cn -> cn.getCreatedAt() != null && !cn.getCreatedAt().isBefore(fromI) && cn.getCreatedAt().isBefore(toI))
                .filter(cn -> cn.getDianStatus() == null || !"REJECTED".equalsIgnoreCase(cn.getDianStatus()))
                .sorted(Comparator.comparing(CreditNote::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        Map<UUID, Sale> salesById = new HashMap<>();
        for (Sale s : sales) salesById.put(s.getId(), s);

        List<AccountingDtos.AccountingRow> rows = new ArrayList<>();
        for (Sale sale : sales) {
            rows.addAll(rowsForSale(sale, cfg, thirdParty));
        }
        for (CreditNote cn : cns) {
            rows.addAll(rowsForCreditNote(cn, cfg, thirdParty, salesById.get(cn.getSaleId())));
        }
        return rows;
    }

    private List<AccountingDtos.AccountingRow> rowsForSale(Sale sale, TenantAccountingConfig cfg, String thirdParty) {
        List<AccountingDtos.AccountingRow> out = new ArrayList<>();
        LocalDate date = toLocalDate(sale.getSoldAt() != null ? sale.getSoldAt() : Instant.now());
        String doc = sale.getFullDocumentNumber() != null ? sale.getFullDocumentNumber() : "";
        String concept = "Venta " + doc;
        String pm = sale.getPaymentMethod() == null ? "EFECTIVO" : sale.getPaymentMethod().toUpperCase(Locale.ROOT);
        String debitAccount = ("TRANSFERENCIA".equals(pm) || "TARJETA".equals(pm)) ? cfg.getBankTransferAccount() : cfg.getCashAccount();
        String debitName = ("TRANSFERENCIA".equals(pm) || "TARJETA".equals(pm)) ? "Bancos" : "Caja";

        BigDecimal grand = nz(sale.getGrandTotal());
        BigDecimal tax = nz(sale.getTaxTotal());
        BigDecimal subtotal = nz(sale.getSubtotal());

        // compute INC vs IVA split from items if possible
        BigDecimal inc = BigDecimal.ZERO;
        BigDecimal iva = tax;
        try {
            List<SaleItem> items = saleItemRepository.findByTenantIdAndSaleId(sale.getTenantId(), sale.getId());
            BigDecimal incSum = BigDecimal.ZERO;
            BigDecimal ivaSum = BigDecimal.ZERO;
            boolean any = false;
            for (SaleItem it : items) {
                if (it.getTaxRate() != null && it.getTaxRate().compareTo(BigDecimal.ZERO) > 0) {
                    any = true;
                    // heuristic: INC rates like 8% common, IVA 19%/5%; if rate == 0.08 treat as INC else IVA
                    if (it.getTaxRate().compareTo(new BigDecimal("0.08")) == 0) {
                        incSum = incSum.add(nz(it.getTaxAmount()));
                    } else {
                        ivaSum = ivaSum.add(nz(it.getTaxAmount()));
                    }
                }
            }
            if (any) { inc = incSum.setScale(2, RoundingMode.HALF_UP); iva = ivaSum.setScale(2, RoundingMode.HALF_UP); }
        } catch (Exception ignored) { }

        // Debit cash/bank
        out.add(row(date, "FV", doc, thirdParty, concept, debitAccount, debitName, grand, BigDecimal.ZERO, pm));
        // Credit income
        out.add(row(date, "FV", doc, thirdParty, concept, cfg.getSalesIncomeAccount(), "Ingresos operacionales", BigDecimal.ZERO, subtotal, pm));
        if (iva.compareTo(BigDecimal.ZERO) > 0) {
            out.add(row(date, "FV", doc, thirdParty, concept, cfg.getVatGeneratedAccount(), "IVA generado", BigDecimal.ZERO, iva, pm));
        }
        if (inc.compareTo(BigDecimal.ZERO) > 0) {
            out.add(row(date, "FV", doc, thirdParty, concept, cfg.getIncGeneratedAccount(), "INC generado", BigDecimal.ZERO, inc, pm));
        }
        // balance adjustment if rounding drift
        BigDecimal credits = subtotal.add(iva).add(inc);
        BigDecimal diff = grand.subtract(credits).setScale(2, RoundingMode.HALF_UP);
        if (diff.abs().compareTo(new BigDecimal("0.05")) > 0) {
            // force balance by adjusting income
            out.set(1, row(date, "FV", doc, thirdParty, concept, cfg.getSalesIncomeAccount(), "Ingresos operacionales", BigDecimal.ZERO, subtotal.add(diff), pm));
        }
        return out;
    }

    private List<AccountingDtos.AccountingRow> rowsForCreditNote(CreditNote cn, TenantAccountingConfig cfg, String thirdParty, Sale sale) {
        List<AccountingDtos.AccountingRow> out = new ArrayList<>();
        LocalDate date = toLocalDate(cn.getCreatedAt());
        String doc = cn.getFullDocumentNumber() != null ? cn.getFullDocumentNumber() : "";
        String concept = "Nota crédito " + doc + " sobre " + (sale != null && sale.getFullDocumentNumber() != null ? sale.getFullDocumentNumber() : cn.getSaleId());
        String pm = sale != null && sale.getPaymentMethod() != null ? sale.getPaymentMethod().toUpperCase(Locale.ROOT) : "EFECTIVO";
        String creditAccount = ("TRANSFERENCIA".equals(pm) || "TARJETA".equals(pm)) ? cfg.getBankTransferAccount() : cfg.getCashAccount();
        String creditName = ("TRANSFERENCIA".equals(pm) || "TARJETA".equals(pm)) ? "Bancos" : "Caja";

        BigDecimal grand = nz(cn.getGrandTotal());
        BigDecimal tax = nz(cn.getTaxTotal());
        BigDecimal subtotal = nz(cn.getSubtotal());

        // Debit returns
        out.add(row(date, "NC", doc, thirdParty, concept, cfg.getReturnsAccount(), "Devoluciones en ventas", subtotal, BigDecimal.ZERO, pm));
        if (tax.compareTo(BigDecimal.ZERO) > 0) {
            out.add(row(date, "NC", doc, thirdParty, concept, cfg.getVatGeneratedAccount(), "IVA generado (reverso)", tax, BigDecimal.ZERO, pm));
        }
        // Credit cash/bank
        out.add(row(date, "NC", doc, thirdParty, concept, creditAccount, creditName, BigDecimal.ZERO, grand, pm));

        BigDecimal debits = subtotal.add(tax);
        BigDecimal diff = grand.subtract(debits).setScale(2, RoundingMode.HALF_UP);
        if (diff.abs().compareTo(new BigDecimal("0.05")) > 0) {
            out.set(0, row(date, "NC", doc, thirdParty, concept, cfg.getReturnsAccount(), "Devoluciones en ventas", subtotal.add(diff), BigDecimal.ZERO, pm));
        }
        return out;
    }

    private AccountingDtos.AccountingRow row(LocalDate date, String type, String doc, String third, String concept,
                                             String account, String accountName, BigDecimal debit, BigDecimal credit, String pm) {
        return new AccountingDtos.AccountingRow(date, type, doc, third, concept, account, accountName,
                debit == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : debit.setScale(2, RoundingMode.HALF_UP),
                credit == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : credit.setScale(2, RoundingMode.HALF_UP),
                pm);
    }

    private byte[] buildWorkbook(List<AccountingDtos.AccountingRow> rows, LocalDate from, LocalDate to, String format) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Contabilidad");
            CreationHelper helper = wb.getCreationHelper();

            CellStyle headerStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd"));

            int r = 0;
            Row meta = sheet.createRow(r++);
            meta.createCell(0).setCellValue("Formato");
            meta.createCell(1).setCellValue(format);
            meta.createCell(2).setCellValue("Desde");
            Cell cFrom = meta.createCell(3); cFrom.setCellValue(java.sql.Date.valueOf(from)); cFrom.setCellStyle(dateStyle);
            meta.createCell(4).setCellValue("Hasta");
            Cell cTo = meta.createCell(5); cTo.setCellValue(java.sql.Date.valueOf(to)); cTo.setCellStyle(dateStyle);
            r++;

            String[] cols = columnsFor(format);
            Row header = sheet.createRow(r++);
            for (int i = 0; i < cols.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(headerStyle);
            }

            for (AccountingDtos.AccountingRow row : rows) {
                Row rr = sheet.createRow(r++);
                writeRow(rr, row, format, dateStyle);
            }

            for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No fue posible generar el Excel contable", e);
        }
    }

    private String[] columnsFor(String format) {
        return switch (format) {
            case "SIIGO" -> new String[]{"Tipo comprobante", "Consecutivo", "Fecha", "Identificación tercero", "Código cuenta", "Nombre cuenta", "Concepto", "Débito", "Crédito", "Centro costo"};
            case "ALEGRA" -> new String[]{"Fecha", "Tipo", "Número", "Identificación", "Cuenta contable", "Concepto", "Débito", "Crédito"};
            case "WORLD_OFFICE" -> new String[]{"Comprobante", "Número", "Fecha", "Tercero", "Cuenta", "Detalle", "Débito", "Crédito", "Base"};
            default -> new String[]{"Fecha", "Tipo documento", "Número", "Tercero", "Concepto", "Cuenta PUC", "Nombre cuenta", "Débito", "Crédito", "Medio de pago"};
        };
    }

    private void writeRow(Row rr, AccountingDtos.AccountingRow row, String format, CellStyle dateStyle) {
        switch (format) {
            case "SIIGO" -> {
                rr.createCell(0).setCellValue(row.documentType());
                rr.createCell(1).setCellValue(row.documentNumber());
                Cell d = rr.createCell(2); d.setCellValue(java.sql.Date.valueOf(row.date())); d.setCellStyle(dateStyle);
                rr.createCell(3).setCellValue(row.thirdParty());
                rr.createCell(4).setCellValue(row.accountCode());
                rr.createCell(5).setCellValue(row.accountName());
                rr.createCell(6).setCellValue(row.concept());
                rr.createCell(7).setCellValue(row.debit().doubleValue());
                rr.createCell(8).setCellValue(row.credit().doubleValue());
                rr.createCell(9).setCellValue("");
            }
            case "ALEGRA" -> {
                Cell d = rr.createCell(0); d.setCellValue(java.sql.Date.valueOf(row.date())); d.setCellStyle(dateStyle);
                rr.createCell(1).setCellValue(row.documentType());
                rr.createCell(2).setCellValue(row.documentNumber());
                rr.createCell(3).setCellValue(row.thirdParty());
                rr.createCell(4).setCellValue(row.accountCode());
                rr.createCell(5).setCellValue(row.concept());
                rr.createCell(6).setCellValue(row.debit().doubleValue());
                rr.createCell(7).setCellValue(row.credit().doubleValue());
            }
            case "WORLD_OFFICE" -> {
                rr.createCell(0).setCellValue(row.documentType());
                rr.createCell(1).setCellValue(row.documentNumber());
                Cell d = rr.createCell(2); d.setCellValue(java.sql.Date.valueOf(row.date())); d.setCellStyle(dateStyle);
                rr.createCell(3).setCellValue(row.thirdParty());
                rr.createCell(4).setCellValue(row.accountCode());
                rr.createCell(5).setCellValue(row.concept());
                rr.createCell(6).setCellValue(row.debit().doubleValue());
                rr.createCell(7).setCellValue(row.credit().doubleValue());
                rr.createCell(8).setCellValue(row.debit().compareTo(BigDecimal.ZERO) > 0 ? row.debit().doubleValue() : row.credit().doubleValue());
            }
            default -> {
                Cell d = rr.createCell(0); d.setCellValue(java.sql.Date.valueOf(row.date())); d.setCellStyle(dateStyle);
                rr.createCell(1).setCellValue(row.documentType());
                rr.createCell(2).setCellValue(row.documentNumber());
                rr.createCell(3).setCellValue(row.thirdParty());
                rr.createCell(4).setCellValue(row.concept());
                rr.createCell(5).setCellValue(row.accountCode());
                rr.createCell(6).setCellValue(row.accountName());
                rr.createCell(7).setCellValue(row.debit().doubleValue());
                rr.createCell(8).setCellValue(row.credit().doubleValue());
                rr.createCell(9).setCellValue(row.paymentMethod() == null ? "" : row.paymentMethod());
            }
        }
    }

    private TenantAccountingConfig createDefault(UUID tenantId) {
        TenantAccountingConfig cfg = defaultConfig(tenantId);
        configRepository.save(cfg);
        return cfg;
    }

    private TenantAccountingConfig defaultConfig(UUID tenantId) {
        TenantAccountingConfig cfg = new TenantAccountingConfig();
        cfg.setTenantId(tenantId);
        cfg.setTargetSoftware("GENERIC_PUC");
        cfg.setSalesIncomeAccount("413505");
        cfg.setVatGeneratedAccount("240805");
        cfg.setIncGeneratedAccount("249505");
        cfg.setCashAccount("110505");
        cfg.setBankTransferAccount("111005");
        cfg.setReturnsAccount("417505");
        cfg.setUpdatedAt(Instant.now());
        return cfg;
    }

    private AccountingDtos.AccountingConfigDto toDto(TenantAccountingConfig cfg) {
        return new AccountingDtos.AccountingConfigDto(
                cfg.getTargetSoftware(), cfg.getSalesIncomeAccount(), cfg.getVatGeneratedAccount(),
                cfg.getIncGeneratedAccount(), cfg.getCashAccount(), cfg.getBankTransferAccount(), cfg.getReturnsAccount());
    }

    private String normalizeFormat(String format) {
        if (format == null || format.isBlank()) return "GENERIC_PUC";
        String f = format.trim().toUpperCase(Locale.ROOT);
        if (!FORMATS.contains(f)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato contable no soportado");
        }
        return f;
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rango de fechas inválido");
        }
        if (ChronoUnit.DAYS.between(from, to) > 366) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El rango máximo es de 366 días");
        }
    }

    private LocalDate toLocalDate(Instant instant) {
        if (instant == null) return LocalDate.now(ZONE);
        return instant.atZone(ZONE).toLocalDate();
    }

    private BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : v.setScale(2, RoundingMode.HALF_UP); }

    private String orDefault(String v, String d) { return v == null || v.isBlank() ? d : v.trim(); }
}

