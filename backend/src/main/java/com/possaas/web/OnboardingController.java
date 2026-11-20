package com.possaas.web;

import com.possaas.dto.onboarding.OnboardingDtos;
import com.possaas.service.OnboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    @GetMapping("/status")
    public ResponseEntity<OnboardingDtos.OnboardingStatusResponse> status() {
        return ResponseEntity.ok(onboardingService.status());
    }

    @PutMapping("/step-1/fiscal")
    public ResponseEntity<OnboardingDtos.OnboardingStatusResponse> step1(@Valid @RequestBody OnboardingDtos.FiscalStepRequest request) {
        return ResponseEntity.ok(onboardingService.saveFiscalStep(request));
    }

    @PutMapping("/step-2/dian")
    public ResponseEntity<OnboardingDtos.DianStepResponse> step2(@Valid @RequestBody OnboardingDtos.DianStepRequest request) {
        return ResponseEntity.ok(onboardingService.saveDianStep(request));
    }

    @PostMapping("/step-3/seed-catalog")
    public ResponseEntity<OnboardingDtos.SeedCatalogResponse> step3(@RequestBody(required = false) OnboardingDtos.SeedCatalogRequest request) {
        return ResponseEntity.ok(onboardingService.seedCatalog(request));
    }
}
