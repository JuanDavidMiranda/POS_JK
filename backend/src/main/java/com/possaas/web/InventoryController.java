package com.possaas.web;

import com.possaas.dto.InventoryMovementRequest;
import com.possaas.dto.InventoryMovementResponse;
import com.possaas.dto.LowStockProductDto;
import com.possaas.dto.PageResponse;
import com.possaas.service.InventoryService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/movements")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryMovementResponse registerMovement(@Valid @RequestBody InventoryMovementRequest request) {
        return inventoryService.registerMovement(request);
    }

    @GetMapping("/kardex/{productId}")
    public PageResponse<InventoryMovementResponse> kardex(@PathVariable UUID productId,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return inventoryService.kardex(productId, page, size);
    }

    @GetMapping("/low-stock")
    public List<LowStockProductDto> lowStock() {
        return inventoryService.lowStock();
    }
}
