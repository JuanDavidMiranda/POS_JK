package com.possaas.service;

import com.possaas.config.TenantContext;
import com.possaas.config.TenantFilterSupport;
import com.possaas.domain.BranchInventory;
import com.possaas.domain.InventoryMovement;
import com.possaas.domain.MovementType;
import com.possaas.domain.Product;
import com.possaas.dto.InventoryMovementRequest;
import com.possaas.dto.InventoryMovementResponse;
import com.possaas.dto.LowStockProductDto;
import com.possaas.dto.PageResponse;
import com.possaas.repository.BranchInventoryRepository;
import com.possaas.repository.BranchRepository;
import com.possaas.repository.InventoryMovementRepository;
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
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final BranchRepository branchRepository;
    private final BranchInventoryRepository branchInventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final ProductRepository productRepository;
    private final TenantFilterSupport tenantFilterSupport;

    @Transactional
    public InventoryMovementResponse recordMovement(InventoryMovementRequest request) {
        UUID tenantId = TenantContext.require();
        tenantFilterSupport.enable(tenantId);
        return recordMovement(tenantId, request);
    }

    @Transactional
    public InventoryMovementResponse recordMovement(UUID tenantId, InventoryMovementRequest request) {
        if (request.branchId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "branchId es obligatorio para registrar movimientos");
        }
        if (request.quantity() == null || request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad debe ser mayor a cero");
        }

        branchRepository.findByTenantIdAndId(tenantId, request.branchId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "La sede no existe o no pertenece al tenant"));

        Product product = productRepository.findByTenantIdAndIdForUpdate(tenantId, request.productId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        BranchInventory inventory = getOrCreateInventoryForUpdate(tenantId, request.branchId(), product.getId());
        BigDecimal previousStock = inventory.getCurrentStock() == null ? BigDecimal.ZERO : inventory.getCurrentStock();
        BigDecimal newStock = applyMovement(previousStock, request.movementType(), request.quantity());

        Instant now = Instant.now();
        inventory.setCurrentStock(newStock);
        inventory.setUpdatedAt(now);
        branchInventoryRepository.save(inventory);

        InventoryMovement movement = new InventoryMovement();
        movement.setId(UuidV7.generate());
        movement.setTenantId(tenantId);
        movement.setBranchId(request.branchId());
        movement.setProductId(product.getId());
        movement.setMovementType(request.movementType());
        movement.setQuantity(request.quantity());
        movement.setPreviousStock(previousStock);
        movement.setNewStock(newStock);
        movement.setReferenceId(null);
        movement.setNotes(request.notes());
        movement.setCreatedBy(request.createdBy());
        movement.setCreatedAt(now);
        movementRepository.save(movement);

        return toResponse(movement, product);
    }

    @Transactional
    public InventoryMovement createSystemMovement(UUID tenantId,
                                                  UUID branchId,
                                                  UUID productId,
                                                  MovementType movementType,
                                                  BigDecimal quantity,
                                                  UUID referenceId,
                                                  String notes,
                                                  UUID createdBy) {
        tenantFilterSupport.enable(tenantId);
        if (branchId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "branchId es obligatorio");
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad debe ser mayor a cero");
        }
        Product product = productRepository.findByTenantIdAndIdForUpdate(tenantId, productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        BranchInventory inventory = getOrCreateInventoryForUpdate(tenantId, branchId, productId);
        BigDecimal previousStock = inventory.getCurrentStock() == null ? BigDecimal.ZERO : inventory.getCurrentStock();
        BigDecimal newStock = applyMovement(previousStock, movementType, quantity);
        Instant now = Instant.now();
        inventory.setCurrentStock(newStock);
        inventory.setUpdatedAt(now);
        branchInventoryRepository.save(inventory);

        InventoryMovement movement = new InventoryMovement();
        movement.setId(UuidV7.generate());
        movement.setTenantId(tenantId);
        movement.setBranchId(branchId);
        movement.setProductId(productId);
        movement.setMovementType(movementType);
        movement.setQuantity(quantity);
        movement.setPreviousStock(previousStock);
        movement.setNewStock(newStock);
        movement.setReferenceId(referenceId);
        movement.setNotes(notes);
        movement.setCreatedBy(createdBy);
        movement.setCreatedAt(now);
        return movementRepository.save(movement);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryMovementResponse> getKardex(UUID productId, UUID branchId, int page, int size) {
        UUID tenantId = TenantContext.require();
        tenantFilterSupport.enable(tenantId);
        if (!productRepository.existsByIdAndTenantId(productId, tenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado");
        }
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<InventoryMovement> result = (branchId != null)
                ? movementRepository.findByTenantIdAndBranchIdAndProductId(tenantId, branchId, productId, pageable)
                : movementRepository.findByTenantIdAndProductId(tenantId, productId, pageable);
        List<InventoryMovementResponse> content = result.getContent().stream()
                .map(this::toResponse)
                .toList();
        return new PageResponse<>(content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<LowStockProductDto> getLowStock(UUID branchId) {
        UUID tenantId = TenantContext.require();
        tenantFilterSupport.enable(tenantId);
        if (branchId != null) {
            branchRepository.findByTenantIdAndId(tenantId, branchId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "La sede no existe o no pertenece al tenant"));
            return branchInventoryRepository.findLowStock(tenantId, branchId).stream()
                    .map(this::toLowStockDto)
                    .toList();
        }
        return branchInventoryRepository.findByTenantId(tenantId).stream()
                .filter(inv -> inv.getCurrentStock() != null && inv.getMinStock() != null
                        && inv.getCurrentStock().compareTo(inv.getMinStock()) <= 0)
                .map(this::toLowStockDto)
                .toList();
    }

    @Transactional
    public BranchInventory getOrCreateInventoryForUpdate(UUID tenantId, UUID branchId, UUID productId) {
        return branchInventoryRepository.findForUpdate(tenantId, branchId, productId)
                .orElseGet(() -> {
                    BranchInventory inventory = new BranchInventory();
                    inventory.setId(UuidV7.generate());
                    inventory.setTenantId(tenantId);
                    inventory.setBranchId(branchId);
                    inventory.setProductId(productId);
                    inventory.setCurrentStock(BigDecimal.ZERO);
                    inventory.setMinStock(BigDecimal.ZERO);
                    inventory.setUpdatedAt(Instant.now());
                    return branchInventoryRepository.save(inventory);
                });
    }

    @Transactional(readOnly = true)
    public BranchInventory getInventory(UUID tenantId, UUID branchId, UUID productId) {
        tenantFilterSupport.enable(tenantId);
        return branchInventoryRepository.findByTenantIdAndBranchIdAndProductId(tenantId, branchId, productId)
                .orElse(null);
    }

    private BigDecimal applyMovement(BigDecimal previousStock, MovementType movementType, BigDecimal quantity) {
        boolean increases = switch (movementType) {
            case INITIAL_BALANCE, PURCHASE_IN, CREDIT_NOTE_IN, ADJUSTMENT_IN, TRANSFER_IN -> true;
            case SALE_OUT, ADJUSTMENT_OUT, TRANSFER_OUT -> false;
        };
        BigDecimal newStock = increases ? previousStock.add(quantity) : previousStock.subtract(quantity);
        if (newStock.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Stock insuficiente. Disponible: " + previousStock + ", solicitado: " + quantity);
        }
        return newStock;
    }

    private InventoryMovementResponse toResponse(InventoryMovement movement) {
        Product product = productRepository.findById(movement.getProductId()).orElse(null);
        return toResponse(movement, product);
    }

    private InventoryMovementResponse toResponse(InventoryMovement movement, Product product) {
        return new InventoryMovementResponse(
                movement.getId(),
                movement.getBranchId(),
                movement.getProductId(),
                product != null ? product.getSku() : null,
                product != null ? product.getName() : null,
                movement.getMovementType(),
                movement.getQuantity(),
                movement.getPreviousStock(),
                movement.getNewStock(),
                movement.getReferenceId(),
                movement.getNotes(),
                movement.getCreatedBy(),
                movement.getCreatedAt()
        );
    }

    private LowStockProductDto toLowStockDto(BranchInventory inventory) {
        Product product = productRepository.findById(inventory.getProductId()).orElse(null);
        return new LowStockProductDto(
                inventory.getProductId(),
                inventory.getBranchId(),
                product != null ? product.getSku() : null,
                product != null ? product.getName() : null,
                inventory.getCurrentStock(),
                inventory.getMinStock()
        );
    }
}
