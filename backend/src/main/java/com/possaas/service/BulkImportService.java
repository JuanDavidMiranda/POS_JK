package com.possaas.service;

import com.possaas.config.TenantContext;
import com.possaas.config.TenantFilterSupport;
import com.possaas.domain.ImportJob;
import com.possaas.dto.ImportExecuteResponse;
import com.possaas.dto.ImportJobResponse;
import com.possaas.dto.ImportPreviewResponse;
import com.possaas.repository.ImportJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BulkImportService {

    private final ImportJobRepository importJobRepository;
    private final TenantFilterSupport tenantFilterSupport;
    private final ImportTemplateService templateService;
    private final AdminAccessGuard adminAccessGuard;
    private final ImportExcelProcessor excelProcessor;
    private final ImportJobTxService txService;
    private final BulkImportWorker worker;

    public ImportPreviewResponse preview(String entityType, MultipartFile file) throws IOException {
        adminAccessGuard.requireAdmin();
        String type = templateService.normalizeEntityType(entityType);
        excelProcessor.validateFile(file);
        try (InputStream in = file.getInputStream()) {
            List<BulkImportModels.ExcelRowResult> results = excelProcessor.parseAndValidate(type, in);
            return excelProcessor.toPreview(results);
        }
    }

    public ImportExecuteResponse execute(String entityType, MultipartFile file) throws IOException {
        UUID adminUserId = adminAccessGuard.currentAdminUserId();
        String type = templateService.normalizeEntityType(entityType);
        excelProcessor.validateFile(file);
        UUID tenantId = TenantContext.require();

        byte[] bytes = file.getBytes();
        String fileName = excelProcessor.safeFileName(file.getOriginalFilename(), type);
        ImportJob job = txService.createPendingJob(tenantId, type, fileName, adminUserId);

        worker.executeAsync(job.getId(), tenantId, type, bytes);
        return new ImportExecuteResponse(job.getId());
    }

    @Transactional(readOnly = true)
    public ImportJobResponse getJob(UUID jobId) {
        adminAccessGuard.requireAdmin();
        UUID tenantId = TenantContext.require();
        tenantFilterSupport.enable(tenantId);
        ImportJob job = importJobRepository.findByIdAndTenantId(jobId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trabajo de importación no encontrado"));
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public List<ImportJobResponse> history() {
        adminAccessGuard.requireAdmin();
        UUID tenantId = TenantContext.require();
        tenantFilterSupport.enable(tenantId);
        return importJobRepository.findTop20ByTenantIdOrderByCreatedAtDesc(tenantId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public BulkImportModels.RejectedFile downloadRejected(UUID jobId) {
        adminAccessGuard.requireAdmin();
        UUID tenantId = TenantContext.require();
        tenantFilterSupport.enable(tenantId);
        ImportJob job = importJobRepository.findByIdAndTenantId(jobId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trabajo de importación no encontrado"));
        if (job.getRejectedFileData() == null || job.getRejectedFileData().length == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Este trabajo no tiene archivo de rechazados");
        }
        String base = job.getFileName() == null ? "rechazados" : job.getFileName().replaceAll("(?i)\\.xlsx$", "");
        return new BulkImportModels.RejectedFile(base + "_rechazados.xlsx", job.getRejectedFileData());
    }

    private ImportJobResponse toResponse(ImportJob job) {
        return new ImportJobResponse(
                job.getId(), job.getEntityType(), job.getFileName(), job.getStatus(),
                n(job.getTotalRows()), n(job.getProcessedRows()), n(job.getCreatedCount()),
                n(job.getUpdatedCount()), n(job.getRejectedCount()),
                job.getRejectedFileData() != null && job.getRejectedFileData().length > 0,
                job.getCreatedAt(), job.getFinishedAt()
        );
    }

    private int n(Integer v) {
        return v == null ? 0 : v;
    }
}
