package com.possaas.web;

import com.possaas.config.TenantContext;
import com.possaas.dto.SetPinRequest;
import com.possaas.dto.UserSummaryDto;
import com.possaas.repository.UserRepository;
import com.possaas.service.AdminAccessGuard;
import com.possaas.service.SecurityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final SecurityService securityService;
    private final AdminAccessGuard adminAccessGuard;
    private final UserRepository userRepository;

    @GetMapping
    public List<UserSummaryDto> list() {
        adminAccessGuard.requireAdmin();
        UUID tenantId = TenantContext.require();
        return userRepository.findByTenantId(tenantId).stream()
                .map(u -> new UserSummaryDto(
                        u.getId(), u.getUsername(), u.getFullName(), u.getRole(),
                        Boolean.TRUE.equals(u.getActive()),
                        u.getSupervisorPinHash() != null && !u.getSupervisorPinHash().isBlank(),
                        u.getUpdatedAt()))
                .toList();
    }

    @PutMapping("/{userId}/pin")
    public ResponseEntity<Map<String, Object>> setPin(@PathVariable UUID userId,
                                                      @Valid @RequestBody SetPinRequest request) {
        adminAccessGuard.requireAdmin();
        securityService.setSupervisorPin(userId, request.pin());
        return ResponseEntity.ok(Map.of("userId", userId, "updated", true));
    }
}