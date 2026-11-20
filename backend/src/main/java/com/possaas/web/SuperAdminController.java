package com.possaas.web;

import com.possaas.dto.superadmin.SuperAdminDtos;
import com.possaas.service.SuperAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/super-admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminController {

    private final SuperAdminService superAdminService;

    @GetMapping("/dashboard")
    public ResponseEntity<SuperAdminDtos.DashboardResponse> dashboard() {
        return ResponseEntity.ok(superAdminService.dashboard());
    }

    @GetMapping("/tenants")
    public ResponseEntity<SuperAdminDtos.TenantListResponse> tenants(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "status", required = false, defaultValue = "ALL") String status,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "20") int size) {
        return ResponseEntity.ok(superAdminService.listTenants(q, status, page, size));
    }

    @PatchMapping("/tenants/{tenantId}/subscription")
    public ResponseEntity<SuperAdminDtos.TenantListItem> updateSubscription(
            @PathVariable("tenantId") UUID tenantId,
            @RequestBody SuperAdminDtos.SubscriptionUpdateRequest request) {
        return ResponseEntity.ok(superAdminService.updateSubscription(tenantId, request));
    }

    @GetMapping("/tenants/{tenantId}/health")
    public ResponseEntity<SuperAdminDtos.TenantHealthResponse> health(@PathVariable("tenantId") UUID tenantId) {
        return ResponseEntity.ok(superAdminService.health(tenantId));
    }
}
