package com.possaas.web;

import com.possaas.dto.accounting.AccountingDtos;
import com.possaas.service.AccountingService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/accounting")
@RequiredArgsConstructor
public class AccountingController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final AccountingService accountingService;

    @GetMapping("/config")
    public AccountingDtos.AccountingConfigDto getConfig() {
        return accountingService.getConfig();
    }

    @PutMapping("/config")
    public AccountingDtos.AccountingConfigDto updateConfig(@RequestBody AccountingDtos.AccountingConfigRequest request) {
        return accountingService.updateConfig(request);
    }

    @GetMapping("/preview")
    public AccountingDtos.AccountingPreviewResponse preview(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam String format) {
        return accountingService.preview(from, to, format);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam String format) {
        byte[] bytes = accountingService.exportXlsx(from, to, format);
        String filename = accountingService.filename(from, to, format);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(XLSX);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(bytes.length);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}
