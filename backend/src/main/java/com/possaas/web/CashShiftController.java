package com.possaas.web;

import com.possaas.dto.CashShiftDtos;
import com.possaas.service.CashShiftService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cash-shifts")
@RequiredArgsConstructor
public class CashShiftController {

    private final CashShiftService cashShiftService;

    @PostMapping("/open")
    @ResponseStatus(HttpStatus.CREATED)
    public CashShiftDtos.CashShiftResponse open(@Valid @RequestBody CashShiftDtos.OpenRequest request) {
        return cashShiftService.open(request);
    }

    @PostMapping("/{shiftId}/close")
    public CashShiftDtos.CashShiftResponse close(@PathVariable UUID shiftId,
                                                 @Valid @RequestBody CashShiftDtos.CloseRequest request) {
        return cashShiftService.close(shiftId, request);
    }

    @GetMapping("/current")
    public CashShiftDtos.CashShiftResponse current() {
        return cashShiftService.current();
    }

    @GetMapping
    public List<CashShiftDtos.CashShiftResponse> recent() {
        return cashShiftService.recent();
    }
}