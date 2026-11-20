package com.possaas.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.possaas.config.TenantContext;
import com.possaas.config.TenantFilterSupport;
import com.possaas.domain.Branch;
import com.possaas.domain.BranchInventory;
import com.possaas.domain.Customer;
import com.possaas.domain.InventoryMovement;
import com.possaas.domain.MovementType;
import com.possaas.domain.PosTerminal;
import com.possaas.domain.Product;
import com.possaas.domain.Sale;
import com.possaas.domain.SaleItem;
import com.possaas.dto.CustomerDto;
import com.possaas.dto.ProductDto;
import com.possaas.dto.SaleItemPayloadDto;
import com.possaas.dto.SalePayloadDto;
import com.possaas.dto.SecurityPolicyDto;
import com.possaas.dto.SupervisorPinDto;
import com.possaas.dto.SyncEventDto;
import com.possaas.dto.SyncEventResultDto;
import com.possaas.dto.SyncPullResponse;
import com.possaas.dto.SyncPushRequest;
import com.possaas.dto.SyncPushResponse;
import com.possaas.dto.TerminalConfigDto;
import com.possaas.repository.BranchInventoryRepository;
import com.possaas.repository.BranchRepository;
import com.possaas.repository.CustomerRepository;
import com.possaas.repository.InventoryMovementRepository;
import com.possaas.repository.PosTerminalRepository;
import com.possaas.repository.ProductRepository;
import com.possaas.repository.SaleItemRepository;
import com.possaas.repository.SaleRepository;
import com.possaas.repository.SyncInboxLogRepository;
import com.possaas.repository.UserRepository;
import com.possaas.util.UuidV7;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SyncService {

    private static final Set<String> VALID_DOCUMENT_TYPES = Set.of("POS", "FACTURA_ELECTRONICA");
    private static final Set<String> VALID_PAYMENT_METHODS = Set.of("EFECTIVO", "TARJETA", "TRANSFERENCIA");
    private static final Instant INITIAL_SINCE = Instant.EPOCH;

    private final TenantFilterSupport tenantFilterSupport;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final PosTerminalRepository terminalRepository;
    private final BranchRepository branchRepository;
    private final BranchInventoryRepository branchInventoryRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final SyncInboxLogRepository inboxLogRepository;
    private final SecurityService securityService;
    private final UserRepository userRepository;
    private final ElectronicBillingProvider electronicBillingProvider;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public SyncPullResponse pull(UUID terminalId, Instant since) {
        UUID tenantId = TenantContext.require();
        tenantFilterSupport.enable(tenantId);

        PosTerminal terminal = (terminalId != null)
                ? terminalRepository.findByTenantIdAndId(tenantId, terminalId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Terminal no encontrada para el tenant"))
                : terminalRepository.findFirstByTenantIdAndActiveTrueOrderByDianPrefixAsc(tenantId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "No hay terminal POS activa configurada para el tenant"));

        if (!Boolean.TRUE.equals(terminal.getActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La terminal no está activa");
        }

        Branch branch = branchRepository.findByTenantIdAndId(tenantId, terminal.getBranchId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La sede de la terminal no existe"));

        Instant effectiveSince = since == null ? INITIAL_SINCE : since;

        List<Product> allProducts = productRepository.findByTenantIdAndActiveTrue(tenantId);
        Map<UUID, BranchInventory> inventoryByProduct = branchInventoryRepository
                .findByTenantIdAndBranchIdOrderByUpdatedAtAsc(tenantId, branch.getId()).stream()
                .collect(Collectors.toMap(BranchInventory::getProductId, bi -> bi, (a, b) -> b));

        List<ProductDto> products = allProducts.stream()
                .filter(p -> {
                    BranchInventory inv = inventoryByProduct.get(p.getId());
                    Instant invUpdated = inv != null ? inv.getUpdatedAt() : null;
                    Instant prodUpdated = p.getUpdatedAt();
                    Instant max = maxInstant(prodUpdated, invUpdated);
                    return max != null && max.isAfter(effectiveSince);
                })
                .map(p -> toProductDto(p, inventoryByProduct.get(p.getId())))
                .toList();

        List<CustomerDto> customers = customerRepository
                .findByTenantIdAndUpdatedAtAfterOrderByUpdatedAtAsc(tenantId, effectiveSince)
                .stream()
                .map(this::toCustomerDto)
                .toList();

        TerminalConfigDto config = toConfigDto(terminal, branch);
        SecurityPolicyDto securityPolicy = securityService.getPolicy(tenantId);
        List<SupervisorPinDto> supervisorPins = userRepository
                .findByTenantIdAndRoleInAndActiveTrueAndSupervisorPinHashIsNotNull(tenantId, List.of("ADMIN", "SUPERVISOR"))
                .stream()
                .map(u -> new SupervisorPinDto(u.getId(), u.getFullName(), u.getSupervisorPinHash()))
                .toList();

        return new SyncPullResponse(Instant.now(), UuidV7.generate(), tenantId, config, products, customers,
                securityPolicy, supervisorPins);
    }

    @Transactional
    public SyncPushResponse push(SyncPushRequest request) {
        UUID tenantId = TenantContext.require();
        tenantFilterSupport.enable(tenantId);

        List<SyncEventDto> events = request.events() == null ? List.of() : request.events();
        List<SyncEventResultDto> results = new ArrayList<>(events.size());
        Instant processedAt = Instant.now();

        for (SyncEventDto event : events) {
            UUID key = event.idempotencyKey();
            int claimed = inboxLogRepository.claim(key, tenantId, event.entityType());
            if (claimed == 0) {
                results.add(SyncEventResultDto.duplicate(key));
                continue;
            }

            try {
                if (!"SALE".equalsIgnoreCase(event.entityType()) && !"SALE_CREATED".equalsIgnoreCase(event.entityType())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "entityType no soportado: " + event.entityType());
                }
                Sale sale = processSaleEvent(tenantId, event);
                SyncEventResultDto result = SyncEventResultDto.processed(key, sale.getId(), sale.getFullDocumentNumber());
                results.add(result);
                storeResponse(key, result);
            } catch (ResponseStatusException ex) {
                storeResponse(key, SyncEventResultDto.rejected(key, ex.getReason() != null ? ex.getReason() : ex.getStatusCode().toString()));
                throw ex;
            } catch (RuntimeException ex) {
                storeResponse(key, SyncEventResultDto.rejected(key, "Error interno procesando evento"));
                throw ex;
            }
        }

        return new SyncPushResponse(UuidV7.generate(), processedAt, results);
    }

    private Sale processSaleEvent(UUID tenantId, SyncEventDto event) {
        SalePayloadDto payload = event.payload();
        validatePayloadBasics(tenantId, event.idempotencyKey(), payload);

        PosTerminal terminal = terminalRepository.findByTenantIdAndIdForUpdate(tenantId, payload.terminalId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La terminal no pertenece al tenant o no existe"));

        if (!Boolean.TRUE.equals(terminal.getActive())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La terminal está inactiva");
        }

        Branch branch = branchRepository.findByTenantIdAndId(tenantId, terminal.getBranchId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "La sede de la terminal no existe"));

        if (!terminal.getDianPrefix().equals(payload.dianPrefix())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El prefijo DIAN no coincide con la terminal");
        }

        String expectedDocument = payload.dianPrefix() + "-" + payload.sequenceNumber();
        if (!expectedDocument.equals(payload.fullDocumentNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "fullDocumentNumber no coincide con prefijo+secuencia");
        }

        if (saleRepository.existsByTenantIdAndDianPrefixAndSequenceNumber(
                tenantId, payload.dianPrefix(), payload.sequenceNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Consecutivo duplicado para el prefijo " + payload.dianPrefix());
        }

        SaleRecalculation recalculation = recalculateAndValidate(tenantId, payload);

        Sale sale = toSale(tenantId, branch.getId(), payload, recalculation);
        saleRepository.saveAndFlush(sale);

        List<SaleItem> items = toSaleItems(tenantId, sale.getId(), payload.items());
        saleItemRepository.saveAll(items);

        deductStockAndKardex(tenantId, branch.getId(), sale, payload.items());

        terminalRepository.advanceSequence(tenantId, terminal.getId(), payload.sequenceNumber(), Instant.now());
        return sale;
    }

    private void validatePayloadBasics(UUID tenantId, UUID idempotencyKey, SalePayloadDto payload) {
        if (!tenantId.equals(TenantContext.get())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant del contexto inválido");
        }
        if (payload.id() == null || idempotencyKey == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta id de venta o idempotency key");
        }
        if (!payload.id().equals(idempotencyKey)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "idempotencyKey debe ser igual al id de la venta offline");
        }
        if (!VALID_DOCUMENT_TYPES.contains(payload.documentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "documentType inválido");
        }
        if (!VALID_PAYMENT_METHODS.contains(payload.paymentMethod())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "paymentMethod inválido");
        }
        if (payload.sequenceNumber() == null || payload.sequenceNumber() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sequenceNumber debe ser >= 1");
        }
        if (payload.items() == null || payload.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La venta debe tener al menos un ítem");
        }
    }

    private SaleRecalculation recalculateAndValidate(UUID tenantId, SalePayloadDto payload) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;

        for (SaleItemPayloadDto item : payload.items()) {
            if (item.quantity() == null || item.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cantidad inválida en ítem");
            }
            if (item.unitPrice() == null || item.unitPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Precio unitario inválido en ítem");
            }

            Product product = productRepository.findById(item.productId())
                    .filter(p -> tenantId.equals(p.getTenantId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Producto no encontrado para el tenant: " + item.productId()));

            BigDecimal baseLine = item.unitPrice().multiply(item.quantity());
            BigDecimal itemTaxRate = product.getTaxRate() == null ? BigDecimal.ZERO : product.getTaxRate();
            BigDecimal expectedTax = "EXCLUIDO".equalsIgnoreCase(product.getTaxType())
                    ? BigDecimal.ZERO
                    : baseLine.multiply(itemTaxRate);
            BigDecimal expectedLineTotal = baseLine.add(expectedTax);

            subtotal = subtotal.add(baseLine);
            taxTotal = taxTotal.add(expectedTax);

            if (item.taxAmount() != null && notEqualMoney(item.taxAmount(), expectedTax)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Impuesto inconsistente en ítem " + item.productId());
            }
            if (item.lineTotal() != null && notEqualMoney(item.lineTotal(), expectedLineTotal)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Total de línea inconsistente en ítem " + item.productId());
            }
        }

        BigDecimal discount = payload.discountTotal() == null ? BigDecimal.ZERO : payload.discountTotal();
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "discountTotal inválido");
        }
        if (discount.compareTo(subtotal.add(taxTotal)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "discountTotal supera el total de la venta");
        }

        BigDecimal grandTotal = subtotal.add(taxTotal).subtract(discount);

        if (notEqualMoney(payload.subtotal(), subtotal)
                || notEqualMoney(payload.taxTotal(), taxTotal)
                || notEqualMoney(payload.grandTotal(), grandTotal)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Totales inconsistentes: el servidor recalculó subtotal/impuestos/total");
        }

        return new SaleRecalculation(scale(subtotal), scale(taxTotal), scale(grandTotal), scale(discount));
    }

    private void deductStockAndKardex(UUID tenantId, UUID branchId, Sale sale, List<SaleItemPayloadDto> items) {
        Instant now = Instant.now();
        for (SaleItemPayloadDto item : items) {
            Product product = productRepository.findByTenantIdAndIdForUpdate(tenantId, item.productId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Producto no encontrado para el tenant: " + item.productId()));

            BranchInventory inventory = branchInventoryRepository.findForUpdate(tenantId, branchId, item.productId())
                    .orElseGet(() -> {
                        BranchInventory inv = new BranchInventory();
                        inv.setId(UuidV7.generate());
                        inv.setTenantId(tenantId);
                        inv.setBranchId(branchId);
                        inv.setProductId(item.productId());
                        inv.setCurrentStock(BigDecimal.ZERO);
                        inv.setMinStock(BigDecimal.ZERO);
                        inv.setUpdatedAt(now);
                        return branchInventoryRepository.save(inv);
                    });

            BigDecimal previous = inventory.getCurrentStock() == null ? BigDecimal.ZERO : inventory.getCurrentStock();
            BigDecimal next = previous.subtract(item.quantity());
            if (next.compareTo(BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Stock insuficiente para producto " + product.getSku() + " en la sede. Disponible: " + previous);
            }
            inventory.setCurrentStock(next);
            inventory.setUpdatedAt(now);
            branchInventoryRepository.save(inventory);

            InventoryMovement movement = new InventoryMovement();
            movement.setId(UuidV7.generate());
            movement.setTenantId(tenantId);
            movement.setBranchId(branchId);
            movement.setProductId(product.getId());
            movement.setMovementType(MovementType.SALE_OUT);
            movement.setQuantity(item.quantity());
            movement.setPreviousStock(previous);
            movement.setNewStock(next);
            movement.setReferenceId(sale.getId());
            movement.setNotes("Venta " + sale.getFullDocumentNumber());
            movement.setCreatedBy(null);
            movement.setCreatedAt(now);
            inventoryMovementRepository.save(movement);
        }
    }

    private Sale toSale(UUID tenantId, UUID branchId, SalePayloadDto payload, SaleRecalculation recalculation) {
        Sale sale = new Sale();
        sale.setId(payload.id());
        sale.setTenantId(tenantId);
        sale.setBranchId(branchId);
        sale.setTerminalId(payload.terminalId());
        sale.setCustomerId(payload.customerId());
        sale.setDocumentType(payload.documentType());
        sale.setDianPrefix(payload.dianPrefix());
        sale.setSequenceNumber(payload.sequenceNumber());
        sale.setFullDocumentNumber(payload.fullDocumentNumber());
        sale.setDianStatus(payload.dianStatus() == null ? "SYNCED" : payload.dianStatus());
        sale.setStatus("SYNCED");
        sale.setSubtotal(recalculation.subtotal());
        sale.setDiscountTotal(recalculation.discountTotal());
        sale.setTaxTotal(recalculation.taxTotal());
        sale.setGrandTotal(recalculation.grandTotal());
        sale.setPaymentMethod(payload.paymentMethod());
        sale.setAuthorizedBy(payload.authorizedBy());
        sale.setCashShiftId(payload.cashShiftId());
        sale.setSoldAt(payload.soldAt() == null ? Instant.now() : payload.soldAt());
        sale.setSyncedAt(Instant.now());
        return sale;
    }

    private List<SaleItem> toSaleItems(UUID tenantId, UUID saleId, List<SaleItemPayloadDto> itemPayloads) {
        List<SaleItem> result = new ArrayList<>(itemPayloads.size());
        for (SaleItemPayloadDto dto : itemPayloads) {
            SaleItem item = new SaleItem();
            item.setId(UuidV7.generate());
            item.setTenantId(tenantId);
            item.setSaleId(saleId);
            item.setProductId(dto.productId());
            item.setQuantity(dto.quantity());
            item.setUnitPrice(scale(dto.unitPrice()));
            BigDecimal lineBase = dto.unitPrice().multiply(dto.quantity());
            Product product = productRepository.findById(dto.productId()).orElse(null);
            BigDecimal rate = dto.taxRate() != null ? dto.taxRate()
                    : (product != null && product.getTaxRate() != null ? product.getTaxRate() : BigDecimal.ZERO);
            boolean excluded = product != null && "EXCLUIDO".equalsIgnoreCase(product.getTaxType());
            BigDecimal tax = excluded ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : scale(lineBase.multiply(rate));
            item.setTaxRate(rate);
            item.setTaxAmount(tax);
            item.setLineTotal(scale(lineBase.add(tax)));
            result.add(item);
        }
        return result;
    }

    private void storeResponse(UUID key, SyncEventResultDto result) {
        try {
            inboxLogRepository.saveResponse(key, objectMapper.writeValueAsString(result));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo registrar la respuesta de idempotencia", ex);
        }
    }

    private static boolean notEqualMoney(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return true;
        }
        return scale(a).compareTo(scale(b)) != 0;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static Instant maxInstant(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    private ProductDto toProductDto(Product product, BranchInventory inventory) {
        BigDecimal stock = inventory != null && inventory.getCurrentStock() != null ? inventory.getCurrentStock() : BigDecimal.ZERO;
        BigDecimal minStock = inventory != null && inventory.getMinStock() != null ? inventory.getMinStock() : BigDecimal.ZERO;
        return new ProductDto(
                product.getId(),
                product.getBarcode(),
                product.getSku(),
                product.getName(),
                product.getSalePrice(),
                product.getTaxType(),
                product.getTaxRate(),
                stock,
                minStock,
                product.getUpdatedAt()
        );
    }

    private CustomerDto toCustomerDto(Customer customer) {
        return new CustomerDto(
                customer.getId(),
                customer.getDocumentType(),
                customer.getDocumentNumber(),
                customer.getFullName(),
                customer.getEmail(),
                customer.getUpdatedAt()
        );
    }

    private TerminalConfigDto toConfigDto(PosTerminal terminal, Branch branch) {
        return new TerminalConfigDto(
                terminal.getId(),
                terminal.getBranchId(),
                branch != null ? branch.getCode() : null,
                branch != null ? branch.getName() : null,
                terminal.getName(),
                terminal.getDianPrefix(),
                terminal.getRangeFrom(),
                terminal.getRangeTo(),
                terminal.getLastSyncedSequence(),
                terminal.getLastSyncedSequence() + 1,
                terminal.getCreditNotePrefix(),
                terminal.getCreditNoteLastSequence(),
                terminal.getCreditNoteLastSequence() + 1
        );
    }

    private record SaleRecalculation(BigDecimal subtotal, BigDecimal taxTotal, BigDecimal grandTotal, BigDecimal discountTotal) {
    }
}
