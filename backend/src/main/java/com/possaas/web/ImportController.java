package com.possaas.web;

import com.possaas.dto.ImportExecuteResponse;
import com.possaas.dto.ImportJobResponse;
import com.possaas.dto.ImportPreviewResponse;
import com.possaas.service.AdminAccessGuard;
import com.possaas.service.BulkImportModels;
import com.possaas.service.BulkImportService;
import com.possaas.service.ImportTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/imports")
@RequiredArgsConstructor
public class ImportController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final BulkImportService importService;
    private final ImportTemplateService templateService;
    private final AdminAccessGuard adminAccessGuard;

    @GetMapping("/template/{entityType}")
    public ResponseEntity<byte[]> template(@PathVariable String entityType) {
        adminAccessGuard.requireAdmin();
        byte[] bytes = templateService.buildTemplate(entityType);
        String fileName = templateService.fileNameFor(entityType);
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .body(bytes);
    }

    @PostMapping(value = "/preview/{entityType}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportPreviewResponse preview(@PathVariable String entityType,
                                         @RequestParam("file") MultipartFile file) throws IOException {
        return importService.preview(entityType, file);
    }

    @PostMapping(value = "/execute/{entityType}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportExecuteResponse execute(@PathVariable String entityType,
                                         @RequestParam("file") MultipartFile file) throws IOException {
        return importService.execute(entityType, file);
    }

    @GetMapping("/jobs/{jobId}")
    public ImportJobResponse job(@PathVariable UUID jobId) {
        return importService.getJob(jobId);
    }

    @GetMapping("/jobs/{jobId}/rejected-file")
    public ResponseEntity<byte[]> rejectedFile(@PathVariable UUID jobId) {
        BulkImportModels.RejectedFile file = importService.downloadRejected(jobId);
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .body(file.data());
    }

    @GetMapping("/history")
    public List<ImportJobResponse> history() {
        return importService.history();
    }
}
