package com.possaas.service;

import com.possaas.config.TenantContext;
import com.possaas.config.TenantFilterSupport;
import com.possaas.domain.Branch;
import com.possaas.domain.InventoryTransfer;
import com.possaas.domain.InventoryTransferItem;
import com.possaas.domain.InventoryTransferStatus;
import com.possaas.domain.MovementType;
import com.possaas.domain.Product;
import com.possaas.dto.InventoryTransferDtos;
import com.possaas.dto.PageResponse;
import com.possaas.repository.BranchRepository;
import com.possaas.repository.InventoryTransferItemRepository;
import com.possaas.repository.InventoryTransferRepository;
import com.possaas.repository.ProductRepository;
import com.possaas.util.UuidV7;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class InventoryTransferService {

    private static final DateTimeFormatter NUMBER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.of("UTC"));
    private static final AtomicLong SEQUENCE = new AtomicLong(System.currentTimeMillis());

    private final BranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final InventoryTransferRepository transferRepository;
    private final InventoryTransferItemRepository transferItemRepository;
    private final InventoryService inventoryService;
    private final TenantFilterSupport tenantFilterSupport;

    @Transactional
    public InventoryTransferDtos.TransferResponse createAndDispatch(InventoryTransferDtos.CreateTransferRequest request) {
        UUID tenantId = TenantContext.require();
        tenantFilterSupport.enable(tenantId);

        if (request.sourceBranchId() == null || request.destinationBranchId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe indicar sede origen y destino");
        }
        if (request.sourceBranchId().equals(request.destinationBranchId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La sede origen y destino deben ser diferentes");
        }
        Branch source = branchRepository.findByTenantIdAndId(tenantId, request.sourceBranchId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sede origen inválida"));
        Branch destination = branchRepository.findByTenantIdAndId(tenantId, request.destinationBranchId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sede destino inválida"));
        if (!Boolean.TRUE.equals(source.getActive()) || !Boolean.TRUE.equals(destination.getActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ambas sedes deben estar activas");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe incluir al menos un producto en el traslado");
        }

        Map<UUID, BigDecimal> merged = new LinkedHashMap<>();
        for (InventoryTransferDtos.TransferItemRequest item : request.items()) {
            if (item.productId() == null || item.quantity() == null || item.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cada ítem debe tener producto y cantidad > 0");
            }
            merged.merge(item.productId(), item.quantity(), BigDecimal::add);
        }

        Instant now = Instant.now();
        InventoryTransfer transfer = new InventoryTransfer();
        transfer.setId(UuidV7.generate());
        transfer.setTenantId(tenantId);
        transfer.setTransferNumber(generateTransferNumber(now));
        transfer.setSourceBranchId(source.getId());
        transfer.setDestinationBranchId(destination.getId());
        transfer.setStatus(InventoryTransferStatus.IN_TRANSIT);
        transfer.setNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim());
        transfer.setCreatedBy(request.createdBy());
        transfer.setCreatedAt(now);
        transfer.setUpdatedAt(now);
        transfer.setDispatchedAt(now);
        transferRepository.save(transfer);

        List<InventoryTransferItem> items = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry : merged.entrySet()) {
            Product product = productRepository.findByTenantIdAndIdForUpdate(tenantId, entry.getKey())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Producto no encontrado: " + entry.getKey()));
            InventoryTransferItem item = new InventoryTransferItem();
            item.setId(UuidV7.generate());
            item.setTenantId(tenantId);
            item.setTransferId(transfer.getId());
            item.setProductId(product.getId());
            item.setQuantity(entry.getValue());
            items.add(item);

            inventoryService.createSystemMovement(
                    tenantId,
                    source.getId(),
                    product.getId(),
                    MovementType.TRANSFER_OUT,
                    entry.getValue(),
                    transfer.getId(),
                    "Despacho traslado " + transfer.getTransferNumber(),
                    request.createdBy()
            );
        }
        transferItemRepository.saveAll(items);

        return toResponse(transfer, source, destination, items);
    }

    @Transactional
    public InventoryTransferDtos.TransferResponse receive(UUID transferId, InventoryTransferDtos.ReceiveTransferRequest request) {
        UUID tenantId = TenantContext.require();
        tenantFilterSupport.enable(tenantId);
        InventoryTransfer transfer = transferRepository.findByTenantIdAndId(tenantId, transferId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Traslado no encontrado"));
        if (transfer.getStatus() != InventoryTransferStatus.IN_TRANSIT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se puede recibir un traslado en estado IN_TRANSIT");
        }
        Branch destination = branchRepository.findByTenantIdAndId(tenantId, transfer.getDestinationBranchId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sede destino no encontrada"));
        List<InventoryTransferItem> items = transferItemRepository.findByTenantIdAndTransferId(tenantId, transfer.getId());

        Instant now = Instant.now();
        UUID receivedBy = request != null ? request.receivedBy() : null;
        for (InventoryTransferItem item : items) {
            inventoryService.createSystemMovement(
                    tenantId,
                    destination.getId(),
                    item.getProductId(),
                    MovementType.TRANSFER_IN,
                    item.getQuantity(),
                    transfer.getId(),
                    "Recepción traslado " + transfer.getTransferNumber(),
                    receivedBy
            );
        }

        transfer.setStatus(InventoryTransferStatus.COMPLETED);
        transfer.setReceivedBy(receivedBy);
        transfer.setReceivedAt(now);
        transfer.setUpdatedAt(now);
        transferRepository.save(transfer);

        Branch source = branchRepository.findById(transfer.getSourceBranchId()).orElse(null);
        return toResponse(transfer, source, destination, items);
    }

    @Transactional
    public InventoryTransferDtos.TransferResponse cancel(UUID transferId, InventoryTransferDtos.CancelTransferRequest request) {
        UUID tenantId = TenantContext.require();
        tenantFilterSupport.enable(tenantId);
        InventoryTransfer transfer = transferRepository.findByTenantIdAndId(tenantId, transferId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Traslado no encontrado"));
        if (transfer.getStatus() != InventoryTransferStatus.IN_TRANSIT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se puede cancelar un traslado en estado IN_TRANSIT");
        }
        Branch source = branchRepository.findByTenantIdAndId(tenantId, transfer.getSourceBranchId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sede origen no encontrada"));
        List<InventoryTransferItem> items = transferItemRepository.findByTenantIdAndTransferId(tenantId, transfer.getId());
        String reason = (request != null && request.reason() != null && !request.reason().isBlank()) ? (" - " + request.reason().trim()) : "";

        Instant now = Instant.now();
        for (InventoryTransferItem item : items) {
            inventoryService.createSystemMovement(
                    tenantId,
                    source.getId(),
                    item.getProductId(),
                    MovementType.TRANSFER_IN,
                    item.getQuantity(),
                    transfer.getId(),
                    "Reverso cancelación traslado " + transfer.getTransferNumber() + reason,
                    null
            );
        }

        transfer.setStatus(InventoryTransferStatus.CANCELLED);
        transfer.setCancelledAt(now);
        transfer.setUpdatedAt(now);
        if (reason.length() > 3) {
            String newNotes = (transfer.getNotes() == null ? "" : transfer.getNotes() + " | ") + "Cancelado" + reason;
            transfer.setNotes(newNotes.length() > 500 ? newNotes.substring(0, 500) : newNotes);
        }
        transferRepository.save(transfer);

        Branch destination = branchRepository.findById(transfer.getDestinationBranchId()).orElse(null);
        return toResponse(transfer, source, destination, items);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryTransferDtos.TransferSummaryResponse> list(UUID sourceBranchId,
                                                                           UUID destinationBranchId,
                                                                           InventoryTransferStatus status,
                                                                           int page,
                                                                           int size) {
        UUID tenantId = TenantContext.require();
        tenantFilterSupport.enable(tenantId);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<InventoryTransfer> result = findPage(tenantId, sourceBranchId, destinationBranchId, status, pageable);
        List<InventoryTransferDtos.TransferSummaryResponse> content = result.getContent().stream()
                .map(this::toSummary)
                .toList();
        return new PageResponse<>(content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public InventoryTransferDtos.TransferResponse get(UUID transferId) {
        UUID tenantId = TenantContext.require();
        tenantFilterSupport.enable(tenantId);
        InventoryTransfer transfer = transferRepository.findByTenantIdAndId(tenantId, transferId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Traslado no encontrado"));
        Branch source = branchRepository.findById(transfer.getSourceBranchId()).orElse(null);
        Branch destination = branchRepository.findById(transfer.getDestinationBranchId()).orElse(null);
        List<InventoryTransferItem> items = transferItemRepository.findByTenantIdAndTransferId(tenantId, transfer.getId());
        return toResponse(transfer, source, destination, items);
    }

    private Page<InventoryTransfer> findPage(UUID tenantId, UUID source, UUID dest, InventoryTransferStatus status, PageRequest pageable) {
        if (source != null && dest != null && status != null) {
            return transferRepository.findByTenantIdAndSourceBranchIdAndDestinationBranchIdAndStatus(tenantId, source, dest, status, pageable);
        } else if (source != null && dest != null) {
            return transferRepository.findByTenantIdAndSourceBranchIdAndDestinationBranchId(tenantId, source, dest, pageable);
        } else if (source != null && status != null) {
            return transferRepository.findByTenantIdAndSourceBranchIdAndStatus(tenantId, source, status, pageable);
        } else if (dest != null && status != null) {
            return transferRepository.findByTenantIdAndDestinationBranchIdAndStatus(tenantId, dest, status, pageable);
        } else if (source != null) {
            return transferRepository.findByTenantIdAndSourceBranchId(tenantId, source, pageable);
        } else if (dest != null) {
            return transferRepository.findByTenantIdAndDestinationBranchId(tenantId, dest, pageable);
        } else if (status != null) {
            return transferRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        }
        return transferRepository.findByTenantId(tenantId, pageable);
    }

    private InventoryTransferDtos.TransferSummaryResponse toSummary(InventoryTransfer transfer) {
        Branch source = branchRepository.findById(transfer.getSourceBranchId()).orElse(null);
        Branch destination = branchRepository.findById(transfer.getDestinationBranchId()).orElse(null);
        int count = transferItemRepository.findByTenantIdAndTransferId(transfer.getTenantId(), transfer.getId()).size();
        return new InventoryTransferDtos.TransferSummaryResponse(
                transfer.getId(), transfer.getTransferNumber(),
                transfer.getSourceBranchId(), source != null ? source.getName() : null,
                transfer.getDestinationBranchId(), destination != null ? destination.getName() : null,
                transfer.getStatus(), count, transfer.getCreatedAt(), transfer.getUpdatedAt());
    }

    private InventoryTransferDtos.TransferResponse toResponse(InventoryTransfer transfer, Branch source, Branch destination, List<InventoryTransferItem> items) {
        List<InventoryTransferDtos.TransferItemResponse> itemResponses = items.stream().map(item -> {
            Product p = productRepository.findById(item.getProductId()).orElse(null);
            return new InventoryTransferDtos.TransferItemResponse(
                    item.getId(), item.getProductId(),
                    p != null ? p.getSku() : null,
                    p != null ? p.getName() : null,
                    item.getQuantity());
        }).toList();
        return new InventoryTransferDtos.TransferResponse(
                transfer.getId(), transfer.getTransferNumber(),
                transfer.getSourceBranchId(), source != null ? source.getName() : null,
                transfer.getDestinationBranchId(), destination != null ? destination.getName() : null,
                transfer.getStatus(), transfer.getNotes(),
                transfer.getCreatedBy(), transfer.getReceivedBy(),
                transfer.getCreatedAt(), transfer.getUpdatedAt(),
                transfer.getDispatchedAt(), transfer.getReceivedAt(), transfer.getCancelledAt(),
                itemResponses);
    }

    private String generateTransferNumber(Instant now) {
        long seq = Math.abs(SEQUENCE.incrementAndGet() % 1_000_000L);
        return "TRF-" + NUMBER_DATE.format(now) + "-" + String.format("%06d", seq);
    }
}
