package com.possaas.service;

import com.possaas.config.TenantContext;
import com.possaas.config.TenantFilterSupport;
import com.possaas.domain.CreditNote;
import com.possaas.domain.CreditNoteItem;
import com.possaas.domain.MovementType;
import com.possaas.domain.PosTerminal;
import com.possaas.domain.Sale;
import com.possaas.domain.SaleItem;
import com.possaas.dto.CreditNoteItemRequest;
import com.possaas.dto.CreditNoteItemResponse;
import com.possaas.dto.CreditNoteRequest;
import com.possaas.dto.CreditNoteResponse;
import com.possaas.dto.DianCreditNoteResult;
import com.possaas.repository.CreditNoteItemRepository;
import com.possaas.repository.CreditNoteRepository;
import com.possaas.repository.PosTerminalRepository;
import com.possaas.repository.ReturnedQuantity;
import com.possaas.repository.SaleItemRepository;
import com.possaas.repository.SaleRepository;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CreditNoteService {

    private static final Set<String> VALID_REASONS = Set.of("1", "2", "3");

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final PosTerminalRepository terminalRepository;
    private final CreditNoteRepository creditNoteRepository;
    private final CreditNoteItemRepository creditNoteItemRepository;
    private final InventoryService inventoryService;
    private final ElectronicBillingProvider billingProvider;
    private final TenantFilterSupport tenantFilterSupport;

    @Transactional
    public CreditNoteResponse create(CreditNoteRequest request) {
        UUID tenantId = TenantContext.require();
        tenantFilterSupport.enable(tenantId);

        if (!VALID_REASONS.contains(request.reasonCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Motivo DIAN inválido. Use 1, 2 o 3");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe indicar al menos un ítem a devolver");
        }
        if (creditNoteRepository.existsById(request.id())) {
            return toResponse(creditNoteRepository.findById(request.id()).orElseThrow(), null);
        }

        Sale sale = saleRepository.findByIdForUpdate(request.saleId(), tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venta no encontrada"));

        if (!"ACCEPTED".equalsIgnoreCase(sale.getDianStatus()) && !"SYNCED".equalsIgnoreCase(sale.getDianStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo se pueden anular ventas en estado ACCEPTED o SYNCED");
        }
        if ("VOIDED".equalsIgnoreCase(sale.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La venta ya se encuentra anulada");
        }

        PosTerminal terminal = terminalRepository.findByTenantIdAndIdForUpdate(tenantId, request.terminalId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La terminal no pertenece al tenant"));

        List<SaleItem> saleItems = saleItemRepository.findByTenantIdAndSaleId(tenantId, sale.getId());
        Map<UUID, SaleItem> saleItemByProduct = saleItems.stream()
                .collect(Collectors.toMap(SaleItem::getProductId, item -> item));

        Map<UUID, BigDecimal> alreadyReturned = creditNoteItemRepository.sumReturnedBySale(tenantId, sale.getId())
                .stream()
                .collect(Collectors.toMap(ReturnedQuantity::getProductId, ReturnedQuantity::getQuantity));

        Map<UUID, BigDecimal> requestedByProduct = new HashMap<>();
        for (CreditNoteItemRequest itemRequest : request.items()) {
            if (itemRequest.quantity() == null || itemRequest.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad a devolver debe ser mayor a cero");
            }
            requestedByProduct.merge(itemRequest.productId(), itemRequest.quantity(), BigDecimal::add);
        }

        List<CreditNoteItem> noteItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (Map.Entry<UUID, BigDecimal> entry : requestedByProduct.entrySet()) {
            SaleItem saleItem = saleItemByProduct.get(entry.getKey());
            if (saleItem == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El producto " + entry.getKey() + " no pertenece a la venta");
            }

            BigDecimal qty = entry.getValue();
            BigDecimal returned = alreadyReturned.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            BigDecimal available = saleItem.getQuantity().subtract(returned);
            if (qty.compareTo(available) > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Cantidad a devolver excede lo disponible para " + entry.getKey());
            }

            BigDecimal lineSubtotal = saleItem.getUnitPrice().multiply(qty)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTax = lineSubtotal.multiply(saleItem.getTaxRate()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = lineSubtotal.add(lineTax);

            CreditNoteItem noteItem = new CreditNoteItem();
            noteItem.setId(UuidV7.generate());
            noteItem.setTenantId(tenantId);
            noteItem.setCreditNoteId(request.id());
            noteItem.setProductId(entry.getKey());
            noteItem.setQuantity(qty);
            noteItem.setUnitPrice(saleItem.getUnitPrice());
            noteItem.setTaxAmount(lineTax);
            noteItem.setLineTotal(lineTotal);
            noteItems.add(noteItem);

            subtotal = subtotal.add(lineSubtotal);
            taxTotal = taxTotal.add(lineTax);
        }

        BigDecimal grandTotal = subtotal.add(taxTotal);
        boolean fullReturn = isFullReturn(saleItems, mergeReturned(alreadyReturned, requestedByProduct));

        if ("2".equals(request.reasonCode()) && !fullReturn) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La anulación total (código 2) requiere devolver todos los ítems de la venta");
        }
        if ("1".equals(request.reasonCode()) && fullReturn) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Use el código 2 para anulación total de la factura");
        }

        Instant now = Instant.now();
        long sequence = terminal.getCreditNoteLastSequence() + 1;
        String prefix = terminal.getCreditNotePrefix() == null || terminal.getCreditNotePrefix().isBlank()
                ? "NC1" : terminal.getCreditNotePrefix();
        String fullNumber = prefix + "-" + String.format("%06d", sequence);

        CreditNote creditNote = new CreditNote();
        creditNote.setId(request.id());
        creditNote.setTenantId(tenantId);
        creditNote.setSaleId(sale.getId());
        creditNote.setTerminalId(terminal.getId());
        creditNote.setDianPrefix(prefix);
        creditNote.setSequenceNumber(sequence);
        creditNote.setFullDocumentNumber(fullNumber);
        creditNote.setReasonCode(request.reasonCode());
        creditNote.setReasonDescription(request.reasonDescription().trim());
        creditNote.setSubtotal(subtotal);
        creditNote.setTaxTotal(taxTotal);
        creditNote.setGrandTotal(grandTotal);
        creditNote.setDianStatus("PENDING_TRANSMISSION");
        creditNote.setCreatedAt(now);
        creditNoteRepository.save(creditNote);
        creditNoteItemRepository.saveAll(noteItems);

        for (CreditNoteItem item : noteItems) {
            inventoryService.applyMovement(tenantId, item.getProductId(), MovementType.CREDIT_NOTE_IN,
                    item.getQuantity(), creditNote.getId(),
                    "Nota crédito " + fullNumber + " - " + request.reasonDescription().trim(),
                    null, now);
        }

        sale.setStatus(fullReturn ? "VOIDED" : "PARTIALLY_RETURNED");
        saleRepository.save(sale);
        terminalRepository.advanceCreditNoteSequence(tenantId, terminal.getId(), sequence, now);

        DianCreditNoteResult result = billingProvider.sendCreditNote(tenantId, creditNote, sale);
        creditNote.setDianStatus(result.status() == null ? "REJECTED" : result.status());
        creditNote.setCude(result.cude());
        creditNoteRepository.save(creditNote);

        return toResponse(creditNote, noteItems);
    }

    private boolean isFullReturn(List<SaleItem> saleItems, Map<UUID, BigDecimal> returnedByProduct) {
        for (SaleItem saleItem : saleItems) {
            BigDecimal returned = returnedByProduct.getOrDefault(saleItem.getProductId(), BigDecimal.ZERO);
            if (returned.compareTo(saleItem.getQuantity()) < 0) {
                return false;
            }
        }
        return true;
    }

    private Map<UUID, BigDecimal> mergeReturned(Map<UUID, BigDecimal> base, Map<UUID, BigDecimal> current) {
        Map<UUID, BigDecimal> merged = new HashMap<>(base);
        current.forEach((id, qty) -> merged.merge(id, qty, BigDecimal::add));
        return merged;
    }

    private CreditNoteResponse toResponse(CreditNote note, List<CreditNoteItem> items) {
        List<CreditNoteItem> noteItems = items == null
                ? creditNoteItemRepository.findByTenantIdAndCreditNoteId(note.getTenantId(), note.getId())
                : items;

        List<CreditNoteItemResponse> itemResponses = noteItems.stream()
                .map(i -> new CreditNoteItemResponse(i.getId(), i.getProductId(), i.getQuantity(),
                        i.getUnitPrice(), i.getTaxAmount(), i.getLineTotal()))
                .toList();

        String saleStatus = saleRepository.findById(note.getSaleId()).map(Sale::getStatus).orElse(null);
        return new CreditNoteResponse(note.getId(), note.getSaleId(), note.getTerminalId(),
                note.getDianPrefix(), note.getSequenceNumber(), note.getFullDocumentNumber(),
                note.getReasonCode(), note.getReasonDescription(), note.getDianStatus(), note.getCude(),
                note.getSubtotal(), note.getTaxTotal(), note.getGrandTotal(), note.getCreatedAt(),
                saleStatus, itemResponses);
    }
}
