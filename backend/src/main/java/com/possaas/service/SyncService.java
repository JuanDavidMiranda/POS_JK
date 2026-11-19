package com.possaas.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.possaas.config.TenantContext;
import com.possaas.config.TenantFilterSupport;
import com.possaas.domain.Customer;
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
import com.possaas.repository.CustomerRepository;
import com.possaas.repository.PosTerminalRepository;
import com.possaas.repository.ProductRepository;
import com.possaas.repository.SaleItemRepository;
import com.possaas.repository.SaleRepository;
import com.possaas.repository.SyncInboxLogRepository;
import com.possaas.repository.TenantRepository;
import com.possaas.repository.UserRepository;
import com.possaas.util.UuidV7;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SyncService {

    private static final String EVENT_TYPE_SALE = "SALE";
    private static final BigDecimal ONE_CENT = new BigDecimal("0.01");
    private static final BigDecimal TWO_DECIMALS = new BigDecimal("0.00");

    private final TenantRepository tenantRepository;
    private final PosTerminalRepository terminalRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final SyncInboxLogRepository inboxLogRepository;
    private final UserRepository userRepository;
    private final TenantFilterSupport tenantFilterSupport;
    private final InventoryService inventoryService;
    private final SecurityService securityService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public SyncPullResponse pull(Instant since) {
        UUID tenantId = TenantContext.require();
        requireTenant(tenantId);
        tenantFilterSupport.enable(tenantId);

        Instant cursor = since == null ? Instant.EPOCH : since;
        PosTerminal terminal = terminalRepository
                .findFirstByTenantIdAndActiveTrueOrderByDianPrefixAsc(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No hay terminal activa configurada para el tenant"));

        if (terminal.getLastSyncedSequence() >= terminal.getRangeTo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El rango autorizado DIAN de la terminal está agotado");
        }

        List<ProductDto> products = productRepository
                .findByTenantIdAndUpdatedAtAfterOrderByUpdatedAtAsc(tenantId, cursor).stream()
                .filter(product -> Boolean.TRUE.equals(product.getActive()))
                .map(this::toProductDto)
                .toList();

        List<CustomerDto> customers = customerRepository
                .findByTenantIdAndUpdatedAtAfterOrderByUpdatedAtAsc(tenantId, cursor).stream()
                .map(this::toCustomerDto)
                .toList();

        long nextSequence = Math.min(terminal.getLastSyncedSequence() + 1, terminal.getRangeTo());
        String ncPrefix = terminal.getCreditNotePrefix() == null ? "NC1" : terminal.getCreditNotePrefix();
        long ncLast = terminal.getCreditNoteLastSequence() == null ? 0L : terminal.getCreditNoteLastSequence();
        TerminalConfigDto config = new TerminalConfigDto(
                terminal.getId(),
                terminal.getName(),
                terminal.getDianPrefix(),
                terminal.getRangeFrom(),
                terminal.getRangeTo(),
                terminal.getLastSyncedSequence(),
                nextSequence,
                ncPrefix,
                ncLast,
                ncLast + 1
        );

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
        requireTenant(tenantId);
        tenantFilterSupport.enable(tenantId);

        Instant processedAt = Instant.now();
        List<SyncEventResultDto> results = new ArrayList<>();

        for (SyncEventDto event : request.events()) {
            UUID key = event.idempotencyKey();

            // Reclamar la clave dentro de la misma transacción: ON CONFLICT DO NOTHING
            // garantiza que un reintento nunca vuelva a descontar inventario.
            int claimed = inboxLogRepository.claim(key, tenantId, event.entityType());
            if (claimed == 0) {
                results.add(SyncEventResultDto.duplicate(key));
                continue;
            }

            if (!EVENT_TYPE_SALE.equalsIgnoreCase(event.entityType())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "entityType no soportado en MVP 1: " + event.entityType());
            }

            SyncEventResultDto result = processSale(tenantId, event.payload(), processedAt, key);
            results.add(result);
            storeResponse(key, result);
        }

        return new SyncPushResponse(UuidV7.generate(), processedAt, results);
    }

    private SyncEventResultDto processSale(UUID tenantId, SalePayloadDto payload, Instant now, UUID key) {
        if (saleRepository.existsByIdAndTenantId(payload.id(), tenantId)
                || saleRepository.existsByTenantIdAndDianPrefixAndSequenceNumber(
                        tenantId, payload.dianPrefix(), payload.sequenceNumber())) {
            return SyncEventResultDto.rejected(key,
                    "El consecutivo " + payload.fullDocumentNumber() + " ya está registrado en este tenant");
        }

        PosTerminal terminal = terminalRepository.findByTenantIdAndId(tenantId, payload.terminalId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La terminal de la venta no pertenece al tenant"));
        if (!Boolean.TRUE.equals(terminal.getActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La terminal está inactiva");
        }
        if (!terminal.getDianPrefix().equals(payload.dianPrefix())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El prefijo DIAN no corresponde a la terminal");
        }
        if (payload.sequenceNumber() < terminal.getRangeFrom()
                || payload.sequenceNumber() > terminal.getRangeTo()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El consecutivo está fuera del rango autorizado por la DIAN");
        }
        if (payload.customerId() != null && !customerRepository.existsByIdAndTenantId(payload.customerId(), tenantId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El cliente no pertenece al tenant");
        }

        BigDecimal subtotal = TWO_DECIMALS;
        BigDecimal taxTotal = TWO_DECIMALS;
        Map<UUID, BigDecimal> requiredStock = new HashMap<>();

        for (SaleItemPayloadDto item : payload.items()) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Producto no encontrado: " + item.productId()));
            if (!tenantId.equals(product.getTenantId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El producto no pertenece al tenant");
            }

            BigDecimal lineSubtotal = item.unitPrice().multiply(item.quantity()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTax = lineSubtotal.multiply(item.taxRate()).setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineSubtotal);
            taxTotal = taxTotal.add(lineTax);
            requiredStock.merge(item.productId(), item.quantity(), BigDecimal::add);

            if (product.getCurrentStock().compareTo(requiredStock.get(item.productId())) < 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Stock insuficiente para " + product.getName());
            }
        }

        BigDecimal discountTotal = payload.discountTotal() == null
                ? TWO_DECIMALS
                : payload.discountTotal().setScale(2, RoundingMode.HALF_UP);
        if (discountTotal.compareTo(BigDecimal.ZERO) < 0) {
            discountTotal = TWO_DECIMALS;
        }
        if (discountTotal.compareTo(subtotal) > 0) {
            discountTotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal computedGrand = subtotal.subtract(discountTotal).add(taxTotal).setScale(2, RoundingMode.HALF_UP);
        if (computedGrand.compareTo(BigDecimal.ZERO) < 0) {
            computedGrand = TWO_DECIMALS;
        }

        requireSameAmount("subtotal", subtotal, payload.subtotal());
        requireSameAmount("taxTotal", taxTotal, payload.taxTotal());
        // Compatibilidad: aceptar grandTotal enviado si coincide con cálculo con descuento
        requireSameAmount("grandTotal", computedGrand, payload.grandTotal());

        Sale sale = new Sale();
        sale.setId(payload.id());
        sale.setTenantId(tenantId);
        sale.setTerminalId(terminal.getId());
        sale.setCustomerId(payload.customerId());
        sale.setDocumentType(payload.documentType());
        sale.setDianPrefix(payload.dianPrefix());
        sale.setSequenceNumber(payload.sequenceNumber());
        sale.setFullDocumentNumber(payload.fullDocumentNumber());
        sale.setDianStatus(payload.dianStatus() == null ? "SYNCED" : payload.dianStatus());
        sale.setStatus("ACTIVE");
        sale.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
        sale.setDiscountTotal(discountTotal);
        sale.setTaxTotal(taxTotal.setScale(2, RoundingMode.HALF_UP));
        sale.setGrandTotal(computedGrand);
        sale.setPaymentMethod(payload.paymentMethod());
        sale.setAuthorizedBy(payload.authorizedBy());
        sale.setCashShiftId(payload.cashShiftId());
        sale.setSoldAt(payload.soldAt());
        sale.setSyncedAt(now);
        saleRepository.saveAndFlush(sale);

        for (SaleItemPayloadDto item : payload.items()) {
            BigDecimal lineSubtotal = item.unitPrice().multiply(item.quantity()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTax = lineSubtotal.multiply(item.taxRate()).setScale(2, RoundingMode.HALF_UP);

            SaleItem saleItem = new SaleItem();
            saleItem.setId(item.id());
            saleItem.setTenantId(tenantId);
            saleItem.setSaleId(sale.getId());
            saleItem.setProductId(item.productId());
            saleItem.setQuantity(item.quantity());
            saleItem.setUnitPrice(item.unitPrice());
            saleItem.setTaxRate(item.taxRate());
            saleItem.setTaxAmount(lineTax);
            saleItem.setLineTotal(lineSubtotal.add(lineTax));
            saleItemRepository.save(saleItem);

            inventoryService.applyMovement(tenantId, item.productId(), MovementType.SALE_OUT,
                    item.quantity(), sale.getId(), "Venta " + sale.getFullDocumentNumber(), null, now);
        }

        terminalRepository.advanceSequence(tenantId, terminal.getId(), payload.sequenceNumber(), now);
        return SyncEventResultDto.processed(key, sale.getId(), sale.getFullDocumentNumber());
    }

    private void requireSameAmount(String field, BigDecimal expected, BigDecimal actual) {
        if (actual.subtract(expected).abs().compareTo(ONE_CENT) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El total " + field + " enviado no coincide con el calculado desde los ítems");
        }
    }

    private void requireTenant(UUID tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant no encontrado: " + tenantId);
        }
    }

    private void storeResponse(UUID key, SyncEventResultDto result) {
        try {
            inboxLogRepository.saveResponse(key, objectMapper.writeValueAsString(result));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo registrar la respuesta de idempotencia", ex);
        }
    }

    private ProductDto toProductDto(Product product) {
        return new ProductDto(product.getId(), product.getBarcode(), product.getSku(), product.getName(),
                product.getSalePrice(), product.getTaxType(), product.getTaxRate(),
                product.getCurrentStock(), product.getMinStock(), product.getUpdatedAt());
    }

    private CustomerDto toCustomerDto(Customer customer) {
        return new CustomerDto(customer.getId(), customer.getDocumentType(), customer.getDocumentNumber(),
                customer.getFullName(), customer.getEmail(), customer.getUpdatedAt());
    }
}