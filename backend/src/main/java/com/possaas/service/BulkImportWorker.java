package com.possaas.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkImportWorker {

    private static final int BATCH_SIZE = 200;

    private final ImportExcelProcessor excelProcessor;
    private final ImportJobTxService txService;

    @Async("importTaskExecutor")
    public void executeAsync(UUID jobId, UUID tenantId, String entityType, byte[] fileBytes) {
        try {
            run(jobId, tenantId, entityType, fileBytes);
        } catch (Exception ex) {
            log.error("Error ejecutando importación {}: {}", jobId, ex.getMessage(), ex);
            try {
                txService.markFailed(jobId, tenantId);
            } catch (Exception ignored) {
                log.error("No fue posible marcar como FAILED el trabajo {}", jobId, ignored);
            }
        }
    }

    private void run(UUID jobId, UUID tenantId, String entityType, byte[] fileBytes) {
        List<BulkImportModels.ExcelRowResult> allResults = excelProcessor.parseAndValidate(entityType, fileBytes);

        List<BulkImportModels.ExcelRowResult> valid = allResults.stream()
                .filter(r -> r.error() == null)
                .toList();
        List<BulkImportModels.ExcelRowResult> rejected = new ArrayList<>(allResults.stream()
                .filter(r -> r.error() != null)
                .toList());

        int total = allResults.size();
        int preRejected = rejected.size();
        int created = 0;
        int updated = 0;
        int processedValid = 0;

        txService.markProcessing(jobId, tenantId, total);
        txService.updateProgress(jobId, tenantId, preRejected, 0, 0, preRejected);

        for (int i = 0; i < valid.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, valid.size());
            List<BulkImportModels.ExcelRowResult> batch = valid.subList(i, end);
            BulkImportModels.BatchOutcome outcome = txService.processBatch(tenantId, entityType, batch, rejected);
            created += outcome.created();
            updated += outcome.updated();
            processedValid += batch.size();
            txService.updateProgress(jobId, tenantId, preRejected + processedValid, created, updated, rejected.size());
        }

        byte[] rejectedFile = rejected.isEmpty() ? null : excelProcessor.buildRejectedWorkbook(entityType, rejected);
        txService.complete(jobId, tenantId, total, total, created, updated, rejected.size(), rejectedFile);
    }
}
