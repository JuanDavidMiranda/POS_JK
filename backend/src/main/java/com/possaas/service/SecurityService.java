package com.possaas.service;

import com.possaas.config.TenantContext;
import com.possaas.domain.TenantSecurityPolicy;
import com.possaas.domain.User;
import com.possaas.dto.SecurityPolicyDto;
import com.possaas.dto.SecurityPolicyUpdateRequest;
import com.possaas.repository.TenantSecurityPolicyRepository;
import com.possaas.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SecurityService {

    private final TenantSecurityPolicyRepository policyRepository;
    private final UserRepository userRepository;

    @Transactional
    public SecurityPolicyDto getPolicy() {
        return getPolicy(TenantContext.require());
    }

    @Transactional
    public SecurityPolicyDto getPolicy(UUID tenantId) {
        TenantSecurityPolicy policy = policyRepository.findById(tenantId)
                .orElseGet(() -> createDefault(tenantId));
        return toDto(policy);
    }

    @Transactional
    public SecurityPolicyDto updatePolicy(SecurityPolicyUpdateRequest request) {
        UUID tenantId = TenantContext.require();
        TenantSecurityPolicy policy = policyRepository.findById(tenantId)
                .orElseGet(() -> createDefault(tenantId));
        policy.setRequirePinForDiscounts(Boolean.TRUE.equals(request.requirePinForDiscounts()));
        policy.setMaxCashierDiscountPercent(request.maxCashierDiscountPercent().setScale(2, RoundingMode.HALF_UP));
        policy.setRequirePinForVoidItem(Boolean.TRUE.equals(request.requirePinForVoidItem()));
        policy.setBlindCashClose(Boolean.TRUE.equals(request.blindCashClose()));
        policy.setUpdatedAt(Instant.now());
        policyRepository.save(policy);
        return toDto(policy);
    }

    @Transactional
    public void setSupervisorPin(UUID userId, String pin) {
        UUID tenantId = TenantContext.require();
        if (!pin.matches("\\d{4,6}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El PIN debe tener entre 4 y 6 dígitos numéricos");
        }
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        if (!"ADMIN".equals(user.getRole()) && !"SUPERVISOR".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo usuarios ADMIN o SUPERVISOR pueden tener PIN de autorización");
        }
        user.setSupervisorPinHash(hashPin(tenantId, pin));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }

    public String hashPin(UUID tenantId, String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((tenantId + ":" + pin).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private TenantSecurityPolicy createDefault(UUID tenantId) {
        TenantSecurityPolicy policy = new TenantSecurityPolicy();
        policy.setTenantId(tenantId);
        policy.setRequirePinForDiscounts(true);
        policy.setMaxCashierDiscountPercent(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        policy.setRequirePinForVoidItem(true);
        policy.setBlindCashClose(true);
        policy.setUpdatedAt(Instant.now());
        return policyRepository.save(policy);
    }

    private SecurityPolicyDto toDto(TenantSecurityPolicy policy) {
        return new SecurityPolicyDto(
                Boolean.TRUE.equals(policy.getRequirePinForDiscounts()),
                policy.getMaxCashierDiscountPercent(),
                Boolean.TRUE.equals(policy.getRequirePinForVoidItem()),
                Boolean.TRUE.equals(policy.getBlindCashClose()),
                policy.getUpdatedAt()
        );
    }
}