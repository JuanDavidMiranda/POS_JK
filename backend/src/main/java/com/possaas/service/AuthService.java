package com.possaas.service;

import com.possaas.config.AuthPrincipal;
import com.possaas.config.JwtService;
import com.possaas.config.TenantContext;
import com.possaas.domain.Branch;
import com.possaas.domain.Customer;
import com.possaas.domain.PosTerminal;
import com.possaas.domain.Tenant;
import com.possaas.domain.TenantSecurityPolicy;
import com.possaas.domain.User;
import com.possaas.dto.auth.AuthDtos;
import com.possaas.repository.BranchRepository;
import com.possaas.repository.CustomerRepository;
import com.possaas.repository.PosTerminalRepository;
import com.possaas.repository.TenantRepository;
import com.possaas.repository.TenantSecurityPolicyRepository;
import com.possaas.repository.UserRepository;
import com.possaas.util.UuidV7;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final PosTerminalRepository terminalRepository;
    private final TenantSecurityPolicyRepository policyRepository;
    private final CustomerRepository customerRepository;
    private final SecurityService securityService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest req) {
        String nit = req.nit().trim();
        String email = req.adminEmail().trim().toLowerCase();
        if (tenantRepository.existsByNit(nit)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una empresa registrada con ese NIT");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un usuario registrado con ese correo");
        }

        Instant now = Instant.now();
        UUID tenantId = UuidV7.generate();

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setNit(nit);
        tenant.setBusinessName(req.businessName().trim());
        tenant.setTradeName(req.tradeName() == null ? null : req.tradeName().trim());
        tenant.setCity(req.city() == null ? null : req.city().trim());
        tenant.setPhone(req.phone() == null ? null : req.phone().trim());
        tenant.setEmail(email);
        tenant.setSubscriptionStatus("ACTIVE");
        tenant.setPlanCode("TRIAL");
        tenant.setTrialEndsAt(now.plus(14, ChronoUnit.DAYS));
        tenant.setSubscriptionEndsAt(null);
        tenant.setElectronicFoliosLimit(50);
        tenant.setElectronicFoliosUsed(0);
        tenant.setOnboardingCompleted(false);
        tenant.setOnboardingStep(1);
        tenant.setCreatedAt(now);
        tenantRepository.save(tenant);

        Branch branch = new Branch();
        branch.setId(UuidV7.generate());
        branch.setTenantId(tenantId);
        branch.setCode("MAIN");
        branch.setName("Sede Principal");
        branch.setCity(tenant.getCity());
        branch.setMain(true);
        branch.setActive(true);
        branch.setCreatedAt(now);
        branch.setUpdatedAt(now);
        branchRepository.save(branch);

        PosTerminal terminal = new PosTerminal();
        terminal.setId(UuidV7.generate());
        terminal.setTenantId(tenantId);
        terminal.setBranchId(branch.getId());
        terminal.setName("Caja Principal");
        terminal.setDianPrefix("POS1");
        terminal.setRangeFrom(1L);
        terminal.setRangeTo(10000L);
        terminal.setLastSyncedSequence(0L);
        terminal.setCreditNotePrefix("NC");
        terminal.setCreditNoteLastSequence(0L);
        terminal.setActive(true);
        terminal.setUpdatedAt(now);
        terminalRepository.save(terminal);

        TenantSecurityPolicy policy = new TenantSecurityPolicy();
        policy.setTenantId(tenantId);
        policy.setRequirePinForDiscounts(true);
        policy.setMaxCashierDiscountPercent(BigDecimal.ZERO);
        policy.setRequirePinForVoidItem(true);
        policy.setBlindCashClose(true);
        policy.setUpdatedAt(now);
        policyRepository.save(policy);

        User admin = new User();
        admin.setId(UuidV7.generate());
        admin.setTenantId(tenantId);
        admin.setUsername(deriveUsername(email, tenantId));
        admin.setFullName(req.adminName().trim());
        admin.setEmail(email);
        admin.setRole("ADMIN");
        admin.setPasswordHash(passwordEncoder.encode(req.password()));
        admin.setSupervisorPinHash(securityService.hashPin(tenantId, "1234"));
        admin.setActive(true);
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        userRepository.save(admin);

        Customer consumidorFinal = new Customer();
        consumidorFinal.setId(UuidV7.generate());
        consumidorFinal.setTenantId(tenantId);
        consumidorFinal.setDocumentType("CF");
        consumidorFinal.setDocumentNumber("222222222222");
        consumidorFinal.setFullName("CONSUMIDOR FINAL");
        consumidorFinal.setEmail(null);
        consumidorFinal.setUpdatedAt(now);
        customerRepository.save(consumidorFinal);

        return toAuthResponse(admin, tenant);
    }

    @Transactional(readOnly = true)
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest req) {
        String email = req.email().trim().toLowerCase();
        User user = userRepository.findByEmailIgnoreCaseAndActiveTrue(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales invalidas"));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales invalidas");
        }
        Tenant tenant = null;
        if (user.getTenantId() != null) {
            tenant = tenantRepository.findById(user.getTenantId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Empresa no disponible"));
            if (!"ACTIVE".equalsIgnoreCase(tenant.getSubscriptionStatus())
                    && !"TRIAL".equalsIgnoreCase(tenant.getPlanCode())) {
                // allow ACTIVE; TRIAL plan may still be ACTIVE
            }
            if ("SUSPENDED".equalsIgnoreCase(tenant.getSubscriptionStatus())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La cuenta de la empresa esta suspendida");
            }
        }
        return toAuthResponse(user, tenant);
    }

    @Transactional(readOnly = true)
    public AuthDtos.MeResponse me(AuthPrincipal principal) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
        Tenant tenant = null;
        if (user.getTenantId() != null) {
            tenant = tenantRepository.findById(user.getTenantId()).orElse(null);
        }
        return new AuthDtos.MeResponse(toAuthUser(user), tenant == null ? null : toAuthTenant(tenant));
    }

    public AuthDtos.AuthResponse toAuthResponse(User user, Tenant tenant) {
        String token = jwtService.generateToken(user);
        return new AuthDtos.AuthResponse(
                token,
                "Bearer",
                jwtService.getExpirationSeconds(),
                toAuthUser(user),
                tenant == null ? null : toAuthTenant(tenant)
        );
    }

    public static AuthDtos.AuthUser toAuthUser(User user) {
        return new AuthDtos.AuthUser(user.getId(), user.getTenantId(), user.getFullName(), user.getEmail(), user.getRole());
    }

    public static AuthDtos.AuthTenant toAuthTenant(Tenant tenant) {
        return new AuthDtos.AuthTenant(
                tenant.getId(),
                tenant.getNit(),
                tenant.getBusinessName(),
                tenant.getTradeName(),
                tenant.getPlanCode(),
                tenant.getSubscriptionStatus(),
                Boolean.TRUE.equals(tenant.getOnboardingCompleted()),
                tenant.getOnboardingStep() == null ? 1 : tenant.getOnboardingStep(),
                tenant.getTrialEndsAt(),
                tenant.getSubscriptionEndsAt()
        );
    }

    private String deriveUsername(String email, UUID tenantId) {
        String base = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        base = base.replaceAll("[^a-zA-Z0-9._-]", "").toLowerCase();
        if (base.isBlank()) base = "admin";
        String candidate = base;
        int i = 1;
        while (userRepository.findByTenantIdAndUsernameIgnoreCase(tenantId, candidate).isPresent()) {
            candidate = base + i++;
        }
        return candidate;
    }
}
