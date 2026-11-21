package com.possaas.web;

import com.possaas.dto.billing.BillingDtos;
import com.possaas.service.BillingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    @GetMapping("/overview")
    public BillingDtos.OverviewResponse overview() {
        return billingService.overview();
    }

    @PostMapping("/checkout-intent")
    public BillingDtos.CheckoutIntentResponse checkoutIntent(@Valid @RequestBody BillingDtos.CheckoutIntentRequest request) {
        return billingService.checkoutIntent(request);
    }

    @PostMapping("/sandbox/simulate")
    public BillingDtos.PaymentTransactionDto simulate(@RequestBody BillingDtos.SandboxSimulateRequest request) {
        return billingService.simulateSandboxPayment(request.referenceCode(), request.approve());
    }
}
