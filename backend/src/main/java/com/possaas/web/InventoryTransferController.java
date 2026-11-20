package com.possaas.web;

import com.possaas.domain.InventoryTransferStatus;
import com.possaas.dto.InventoryTransferDtos;
import com.possaas.dto.PageResponse;
import com.possaas.service.AdminAccessGuard;
import com.possaas.service.InventoryTransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory/transfers")
@RequiredArgsConstructor
public class InventoryTransferController {

    private final InventoryTransferService transferService;
    private final AdminAccessGuard adminAccessGuard;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryTransferDtos.TransferResponse create(@Valid @RequestBody InventoryTransferDtos.CreateTransferRequest request) {
        adminAccessGuard.requireAdmin();
        return transferService.createAndDispatch(request);
    }

    @PostMapping("/{id}/receive")
    public InventoryTransferDtos.TransferResponse receive(@PathVariable("id") UUID transferId,
                                                          @RequestBody(required = false) InventoryTransferDtos.ReceiveTransferRequest request) {
        adminAccessGuard.requireAdmin();
        return transferService.receive(transferId, request);
    }

    @PostMapping("/{id}/cancel")
    public InventoryTransferDtos.TransferResponse cancel(@PathVariable("id") UUID transferId,
                                                         @RequestBody(required = false) InventoryTransferDtos.CancelTransferRequest request) {
        adminAccessGuard.requireAdmin();
        return transferService.cancel(transferId, request);
    }

    @GetMapping
    public PageResponse<InventoryTransferDtos.TransferSummaryResponse> list(
            @RequestParam(required = false) UUID sourceBranchId,
            @RequestParam(required = false) UUID destinationBranchId,
            @RequestParam(required = false) InventoryTransferStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        adminAccessGuard.requireAdmin();
        return transferService.list(sourceBranchId, destinationBranchId, status, page, size);
    }

    @GetMapping("/{id}")
    public InventoryTransferDtos.TransferResponse get(@PathVariable("id") UUID transferId) {
        adminAccessGuard.requireAdmin();
        return transferService.get(transferId);
    }
}
