package com.possaas.service;

import com.possaas.config.TenantFilterSupport;
import com.possaas.domain.Customer;
import com.possaas.domain.ImportJob;
import com.possaas.domain.InventoryMovement;
import com.possaas.domain.MovementType;
import com.possaas.domain.Product;
import com.possaas.repository.CustomerRepository;
import com.possaas.repository.ImportJobRepository;
import com.possaas.repository.InventoryMovementRepository;
import com.possaas.repository.ProductRepository;
import com.possaas.util.UuidV7;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImportJobTxService {

    private final ImportJobRepository importJobRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final InventoryMovementRepository movementRepository;
    private final TenantFilterSupport tenantFilterSupport;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportJob createPendingJob(UUID tenantId, String entityType, String fileName, UUID createdBy) {
        tenantFilterSupport.enable(tenantId);
        ImportJob job = new ImportJob();
        job.setId(UuidV7.generate());
        job.setTenantId(tenantId);
        job.setEntityType(entityType);
        job.setFileName(fileName);
        job.setStatus("PENDING");
        job.setTotalRows(0);
        job.setProcessedRows(0);
        job.setCreatedCount(0);
        job.setUpdatedCount(0);
        job.setRejectedCount(0);
        job.setCreatedBy(createdBy);
        job.setCreatedAt(Instant.now());
        importJobRepository.save(job);
        return job;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessing(UUID jobId, UUID tenantId, int totalRows) {
        tenantFilterSupport.enable(tenantId);
        ImportJob job = importJobRepository.findByIdAndTenantId(jobId, tenantId).orElse(null);
        if (job == null) {
            return;
        }
        job.setStatus("PROCESSING");
        job.setTotalRows(totalRows);
        job.setProcessedRows(Math.min(job.getProcessedRows() == null ? 0 : job.getProcessedRows(), totalRows));
        importJobRepository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgress(UUID jobId, UUID tenantId, int processed, int created, int updated, int rejected) {
        tenantFilterSupport.enable(tenantId);
        ImportJob job = importJobRepository.findByIdAndTenantId(jobId, tenantId).orElse(null);
        if (job == null) {
            return;
        }
        job.setProcessedRows(processed);
        job.setCreatedCount(created);
        job.setUpdatedCount(updated);
        job.setRejectedCount(rejected);
        importJobRepository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID jobId, UUID tenantId, int total, int processed, int created, int updated, int rejected, byte[] rejectedFile) {
        tenantFilterSupport.enable(tenantId);
        ImportJob job = importJobRepository.findByIdAndTenantId(jobId, tenantId).orElse(null);
        if (job == null) {
            return;
        }
        job.setStatus("COMPLETED");
        job.setTotalRows(total);
        job.setProcessedRows(processed);
        job.setCreatedCount(created);
        job.setUpdatedCount(updated);
        job.setRejectedCount(rejected);
        job.setRejectedFileData(rejectedFile);
        job.setFinishedAt(Instant.now());
        importJobRepository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID jobId, UUID tenantId) {
        tenantFilterSupport.enable(tenantId);
        ImportJob job = importJobRepository.findByIdAndTenantId(jobId, tenantId).orElse(null);
        if (job == null) {
            return;
        }
        job.setStatus("FAILED");
        job.setFinishedAt(Instant.now());
        importJobRepository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BulkImportModels.BatchOutcome processBatch(UUID tenantId, String entityType,
                                                      List<BulkImportModels.ExcelRowResult> batch,
                                                      List<BulkImportModels.ExcelRowResult> rejectedCollector) {
        tenantFilterSupport.enable(tenantId);
        int created = 0;
        int updated = 0;

        for (BulkImportModels.ExcelRowResult row : batch) {
            try {
                if ("PRODUCTS".equals(entityType)) {
                    boolean isNew = upsertProduct(tenantId, (BulkImportModels.ProductRow) row.data());
                    if (isNew) {
                        created++;
                    } else {
                        updated++;
                    }
                } else {
                    boolean isNew = upsertCustomer(tenantId, (BulkImportModels.CustomerRow) row.data());
                    if (isNew) {
                        created++;
                    } else {
                        updated++;
                    }
                }
            } catch (Exception ex) {
                String message = ex.getMessage();
                if (message == null || message.isBlank()) {
                    message = "Error al procesar la fila";
                }
                rejectedCollector.add(BulkImportModels.ExcelRowResult.err(
                        row.rowNumber(), row.identifier(), row.rawValues(), message));
            }
        }
        return new BulkImportModels.BatchOutcome(created, updated);
    }

    private boolean upsertProduct(UUID tenantId, BulkImportModels.ProductRow row) {
        Instant now = Instant.now();
        Product existing = null;
        if (row.barcode() != null && !row.barcode().isBlank()) {
            existing = productRepository.findByTenantIdAndBarcodeForUpdate(tenantId, row.barcode()).orElse(null);
        }
        if (existing == null && row.sku() != null && !row.sku().isBlank()) {
            existing = productRepository.findByTenantIdAndSkuForUpdate(tenantId, row.sku()).orElse(null);
        }

        if (existing != null) {
            existing.setName(row.name());
            existing.setSalePrice(row.salePrice());
            existing.setTaxType(row.taxType());
            existing.setTaxRate(row.taxRate());
            existing.setMinStock(nvl(row.minStock()));
            if ((existing.getBarcode() == null || existing.getBarcode().isBlank()) && row.barcode() != null) {
                existing.setBarcode(row.barcode());
            }
            if (existing.getSku() == null || existing.getSku().isBlank()) {
                existing.setSku(row.sku());
            }
            existing.setUpdatedAt(now);
            productRepository.save(existing);
            return false;
        }

        BigDecimal initial = nvl(row.initialStock());
        Product product = new Product();
        product.setId(UuidV7.generate());
        product.setTenantId(tenantId);
        product.setBarcode(row.barcode());
        product.setSku(row.sku());
        product.setName(row.name());
        product.setSalePrice(row.salePrice());
        product.setTaxType(row.taxType());
        product.setTaxRate(row.taxRate());
        product.setCurrentStock(initial);
        product.setMinStock(nvl(row.minStock()));
        product.setAllowNegativeStock(false);
        product.setActive(true);
        product.setUpdatedAt(now);
        productRepository.saveAndFlush(product);

        InventoryMovement movement = new InventoryMovement();
        movement.setId(UuidV7.generate());
        movement.setTenantId(tenantId);
        movement.setProductId(product.getId());
        movement.setMovementType(MovementType.INITIAL_BALANCE);
        movement.setQuantity(initial);
        movement.setPreviousStock(BigDecimal.ZERO);
        movement.setNewStock(initial);
        movement.setReferenceId(null);
        movement.setNotes("Saldo inicial por cargue masivo");
        movement.setCreatedBy(null);
        movement.setCreatedAt(now);
        movementRepository.save(movement);
        return true;
    }

    private boolean upsertCustomer(UUID tenantId, BulkImportModels.CustomerRow row) {
        Instant now = Instant.now();
        Customer existing = customerRepository
                .findByTenantIdAndDocumentTypeAndDocumentNumberForUpdate(tenantId, row.documentType(), row.documentNumber())
                .orElse(null);

        if (existing != null) {
            existing.setFullName(row.fullName());
            existing.setEmail(row.email());
            existing.setUpdatedAt(now);
            customerRepository.save(existing);
            return false;
        }

        Customer customer = new Customer();
        customer.setId(UuidV7.generate());
        customer.setTenantId(tenantId);
        customer.setDocumentType(row.documentType());
        customer.setDocumentNumber(row.documentNumber());
        customer.setFullName(row.fullName());
        customer.setEmail(row.email());
        customer.setUpdatedAt(now);
        customerRepository.save(customer);
        return true;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
