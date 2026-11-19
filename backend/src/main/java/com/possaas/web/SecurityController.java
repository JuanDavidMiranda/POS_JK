package com.possaas.web;

import com.possaas.dto.SecurityPolicyDto;
import com.possaas.dto.SecurityPolicyUpdateRequest;
import com.possaas.service.AdminAccessGuard;
import com.possaas.service.SecurityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/security")
@RequiredArgsConstructor
public class SecurityController {

    private final SecurityService securityService;
    private final AdminAccessGuard adminAccessGuard;

    @GetMapping("/policies")
    public SecurityPolicyDto getPolicies() {
        adminAccessGuard.requireAdmin();
        return securityService.getPolicy();
    }

    @PutMapping("/policies")
    public SecurityPolicyDto updatePolicies(@Valid @RequestBody SecurityPolicyUpdateRequest request) {
        adminAccessGuard.requireAdmin();
        return securityService.updatePolicy(request);
    }
}