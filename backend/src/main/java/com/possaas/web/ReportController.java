package com.possaas.web;

import com.possaas.dto.report.SalesSummaryDtos;
import com.possaas.dto.report.TaxSummaryDtos;
import com.possaas.dto.report.TopProductDtos;
import com.possaas.service.AdminAccessGuard;
import com.possaas.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReportService reportService;
    private final AdminAccessGuard adminAccessGuard;

    @GetMapping("/sales-summary")
    public SalesSummaryDtos.SalesSummaryResponse salesSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        adminAccessGuard.requireAdmin();
        return reportService.salesSummary(from, to);
    }

    @GetMapping("/top-products")
    public TopProductDtos.TopProductsResponse topProducts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "20") Integer limit) {
        adminAccessGuard.requireAdmin();
        return reportService.topProducts(from, to, limit);
    }

    @GetMapping("/tax-summary")
    public TaxSummaryDtos.TaxSummaryResponse taxSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        adminAccessGuard.requireAdmin();
        return reportService.taxSummary(from, to);
    }

    @GetMapping("/tax-summary/export")
    public ResponseEntity<byte[]> exportTaxSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        adminAccessGuard.requireAdmin();
        byte[] bytes = reportService.exportTaxSummary(from, to);
        String fileName = reportService.exportFileName(from, to);
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .body(bytes);
    }
}