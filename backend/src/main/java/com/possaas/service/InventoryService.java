package com.possaas.service;

import com.possaas.config.TenantContext;
import com.possaas.config.TenantFilterSupport;
import com.possaas.domain.InventoryMovement;
import com.possaas.domain.MovementType;
import com.possaas.domain.Product;
import com.possaas.dto.InventoryMovementRequest;
import com.possaas.dto.InventoryMovementResponse;
import com.possaas.dto.LowStockProductDto;
import com.possaas.dto.PageResponse;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final Set<MovementType> MANUAL_TYPES = EnumSet.of(
            MovementType.PURCHASE_IN, MovementType.ADJUSTMENT_IN, MovementType.ADJUSTMENT_OUT);

    private final ProductRepository productRepository;
    private final InventoryMovementRepository movementRepository;
    private final TenantFilterSupport tenantFilterSupport;

    @Transactional
    public InventoryMovementResponse registerMovement(InventoryMovementRequest request) {
        UUID tenantId = TenantContext.require();
        tenantFilterSupport.enable(tenantId);

        if (!MANUAL_TYPES.contains(request.movementType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Solo se permiten movimientos manuales: PURCHASE_IN, ADJUSTMENT_IN o ADJUSTMENT_OUT");
        }
        if (request.quantity() == null || request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad debe ser mayor a cero");
        }
        if (request.notes() == null || request.notes().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La observación es obligatoria");
        }

        InventoryMovement movement = applyMovement(tenantId, request.productId(), request.movementType(),
                request.quantity(), null, request.notes().trim(), request.createdBy(), Instant.now());
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        return toResponse(movement, product);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryMovementResponse> kardex(UUID productId, int page, int size) {
        UUID tenantId = TenantContext.require();
        tenantFilterSupport.enable(tenantId);

        Product product = productRepository.findById(productId)
                .filter(p -> tenantId.equals(p.getTenantId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<InventoryMovement> result = movementRepository.findByTenantIdAndProductId(
                tenantId, productId, PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<InventoryMovementResponse> content = result.getContent().stream()
                .map(m -> toResponse(m, product))
                .toList();
        return new PageResponse<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<LowStockProductDto> lowStock() {
        UUID tenantId = TenantContext.require();
        tenantFilterSupport.enable(tenantId);

        return productRepository.findLowStock(tenantId).stream()
                .map(p -> new LowStockProductDto(p.getId(), p.getSku(), p.getName(),
                        p.getCurrentStock(), p.getMinStock(), Boolean.TRUE.equals(p.getAllowNegativeStock())))
                .toList();
    }

    /**
     * Aplica un cambio de stock y registra el movimiento en la transacción actual.
     * Debe llamarse con el tenant filter ya habilitado cuando se usa desde otros servicios.
     */
    @Transactional
    public InventoryMovement applyMovement(UUID tenantId,
                                          UUID productId,
                                          MovementType movementType,
                                          BigDecimal quantity,
                                          UUID referenceId,
                                          String notes,
                                          UUID createdBy,
                                          Instant now) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad del movimiento debe ser mayor a cero");
        }

        Product product = productRepository.findByTenantIdAndIdForUpdate(tenantId, productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Producto no encontrado: " + productId));

        BigDecimal previous = nvl(product.getCurrentStock());
        BigDecimal delta = signedDelta(movementType, quantity);
        BigDecimal next = previous.add(delta);

        if (next.compareTo(BigDecimal.ZERO) < 0 && !Boolean.TRUE.equals(product.getAllowNegativeStock())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Stock insuficiente para " + product.getName() + " (actual: " + previous + ")");
        }

        product.setCurrentStock(next);
        product.setUpdatedAt(now);
        productRepository.save(product);

        InventoryMovement movement = new InventoryMovement();
        movement.setId(UuidV7.generate());
        movement.setTenantId(tenantId);
        movement.setProductId(productId);
        movement.setMovementType(movementType);
        movement.setQuantity(quantity);
        movement.setPreviousStock(previous);
        movement.setNewStock(next);
        movement.setReferenceId(referenceId);
        movement.setNotes(notes);
        movement.setCreatedBy(createdBy);
        movement.setCreatedAt(now);
        return movementRepository.save(movement);
    }

    private BigDecimal signedDelta(MovementType type, BigDecimal quantity) {
        return switch (type) {
            case SALE_OUT, ADJUSTMENT_OUT -> quantity.negate();
            case PURCHASE_IN, CREDIT_NOTE_IN, ADJUSTMENT_IN, INITIAL_BALANCE -> quantity;
        };
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private InventoryMovementResponse toResponse(InventoryMovement movement, Product product) {
        return new InventoryMovementResponse(
                movement.getId(),
                movement.getProductId(),
                product.getSku(),
                product.getName(),
                movement.getMovementType(),
                movement.getQuantity(),
                movement.getPreviousStock(),
                movement.getNewStock(),
                movement.getReferenceId(),
                movement.getNotes(),
                movement.getCreatedBy(),
                movement.getCreatedAt());
    }
}
