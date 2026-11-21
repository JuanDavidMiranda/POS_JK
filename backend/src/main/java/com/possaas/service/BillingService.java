package com.possaas.service;

import com.possaas.config.TenantContext;
import com.possaas.domain.FolioPackage;
import com.possaas.domain.SaasPaymentTransaction;
import com.possaas.domain.SaasPlan;
import com.possaas.domain.Tenant;
import com.possaas.dto.billing.BillingDtos;
import com.possaas.repository.FolioPackageRepository;
import com.possaas.repository.SaasPaymentTransactionRepository;
import com.possaas.repository.SaasPlanRepository;
import com.possaas.repository.TenantRepository;
import com.possaas.util.UuidV7;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillingService {

    private static final String SUB_MONTHLY = "SUBSCRIPTION_MONTHLY";
    private static final String SUB_ANNUAL = "SUBSCRIPTION_ANNUAL";
    private static final String FOLIO_TOPUP = "FOLIO_TOPUP";

    private final TenantRepository tenantRepository;
    private final SaasPlanRepository planRepository;
    private final FolioPackageRepository folioPackageRepository;
    private final SaasPaymentTransactionRepository paymentRepository;
    private final WompiService wompiService;

    @Transactional(readOnly = true)
    public BillingDtos.OverviewResponse overview() {
        UUID tenantId = TenantContext.require();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant no encontrado"));

        List<SaasPlan> plans = planRepository.findByActiveTrueOrderByMonthlyPriceCopAsc();
        List<FolioPackage> packages = folioPackageRepository.findAllByOrderByFoliosCountAsc();
        List<SaasPaymentTransaction> txs = paymentRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, 15));

        String planName = plans.stream().filter(p -> p.getCode().equalsIgnoreCase(tenant.getPlanCode()))
                .map(SaasPlan::getName).findFirst().orElse(tenant.getPlanCode() == null ? "TRIAL" : tenant.getPlanCode());

        int limit = nz(tenant.getElectronicFoliosLimit());
        int used = nz(tenant.getElectronicFoliosUsed());
        int remaining = Math.max(0, limit - used);
        Instant end = tenant.getSubscriptionEndsAt() != null ? tenant.getSubscriptionEndsAt() : tenant.getTrialEndsAt();
        long daysRemaining = end == null ? 0 : Math.max(0, Duration.between(Instant.now(), end).toDays());
        boolean grace = SubscriptionPolicy.inGracePeriod(tenant);
        int graceDaysRemaining = grace ? SubscriptionPolicy.graceDaysRemaining(tenant) : 0;

        BillingDtos.CurrentSubscriptionDto subscription = new BillingDtos.CurrentSubscriptionDto(
                tenant.getPlanCode(),
                planName,
                tenant.getSubscriptionStatus(),
                tenant.getTrialEndsAt(),
                tenant.getSubscriptionEndsAt(),
                used,
                limit,
                remaining,
                daysRemaining,
                grace,
                graceDaysRemaining
        );

        return new BillingDtos.OverviewResponse(
                subscription,
                plans.stream().map(this::toPlanDto).toList(),
                packages.stream().map(this::toPackageDto).toList(),
                txs.stream().map(this::toTxDto).toList(),
                wompiService.isSandbox(),
                wompiService.getPublicKey()
        );
    }

    @Transactional
    public BillingDtos.CheckoutIntentResponse checkoutIntent(BillingDtos.CheckoutIntentRequest request) {
        UUID tenantId = TenantContext.require();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant no encontrado"));

        String paymentType = normalizePaymentType(request.paymentType());
        String itemCode = request.itemCode() == null ? "" : request.itemCode().trim().toUpperCase(Locale.ROOT);
        if (itemCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes seleccionar un plan o paquete");
        }

        BigDecimal amountCop;
        String itemName;
        if (SUB_MONTHLY.equals(paymentType) || SUB_ANNUAL.equals(paymentType)) {
            SaasPlan plan = planRepository.findById(itemCode)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plan no válido"));
            if (!Boolean.TRUE.equals(plan.getActive())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El plan seleccionado no está disponible");
            }
            if ("TRIAL".equalsIgnoreCase(plan.getCode())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No es posible comprar el plan TRIAL");
            }
            amountCop = SUB_MONTHLY.equals(paymentType) ? plan.getMonthlyPriceCop() : plan.getAnnualPriceCop();
            itemName = plan.getName() + (SUB_MONTHLY.equals(paymentType) ? " (Mensual)" : " (Anual)");
        } else {
            FolioPackage pkg = folioPackageRepository.findById(itemCode)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paquete de folios no válido"));
            amountCop = pkg.getPriceCop();
            itemName = pkg.getName();
        }

        if (amountCop == null || amountCop.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El monto a pagar debe ser mayor a cero");
        }

        long amountInCents = amountCop.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
        String shortTenant = tenant.getId().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        String reference = "SUB-" + shortTenant + "-" + System.currentTimeMillis();

        SaasPaymentTransaction tx = new SaasPaymentTransaction();
        tx.setId(UuidV7.generate());
        tx.setTenantId(tenantId);
        tx.setReferenceCode(reference);
        tx.setGatewayTransactionId(null);
        tx.setPaymentType(paymentType);
        tx.setItemCode(itemCode);
        tx.setAmountCop(amountCop.setScale(2, RoundingMode.HALF_UP));
        tx.setStatus("PENDING");
        tx.setPaymentMethodType(null);
        tx.setRawWebhookPayload(null);
        Instant now = Instant.now();
        tx.setCreatedAt(now);
        tx.setUpdatedAt(now);
        paymentRepository.save(tx);

        String signature = wompiService.buildIntegritySignature(reference, amountInCents, wompiService.getCurrency());

        return new BillingDtos.CheckoutIntentResponse(
                tx.getId(),
                wompiService.getPublicKey(),
                reference,
                amountInCents,
                wompiService.getCurrency(),
                signature,
                wompiService.getRedirectUrl(),
                wompiService.isSandbox(),
                itemName,
                paymentType,
                itemCode
        );
    }

    @Transactional
    public void processApprovedPayment(String referenceCode, String gatewayTransactionId, String paymentMethodType, String rawPayload) {
        SaasPaymentTransaction tx = paymentRepository.findByReferenceCodeForUpdate(referenceCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transacción no encontrada: " + referenceCode));

        if ("APPROVED".equalsIgnoreCase(tx.getStatus())) {
            // Idempotent: already applied
            if (gatewayTransactionId != null && !gatewayTransactionId.isBlank()
                    && (tx.getGatewayTransactionId() == null || tx.getGatewayTransactionId().isBlank())) {
                tx.setGatewayTransactionId(gatewayTransactionId);
                tx.setUpdatedAt(Instant.now());
                paymentRepository.save(tx);
            }
            return;
        }

        if (gatewayTransactionId != null && !gatewayTransactionId.isBlank()) {
            paymentRepository.findByGatewayTransactionId(gatewayTransactionId).ifPresent(existing -> {
                if (!existing.getId().equals(tx.getId()) && "APPROVED".equalsIgnoreCase(existing.getStatus())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Transacción de pasarela ya procesada");
                }
            });
            tx.setGatewayTransactionId(gatewayTransactionId);
        }

        tx.setStatus("APPROVED");
        tx.setPaymentMethodType(paymentMethodType);
        tx.setRawWebhookPayload(rawPayload);
        tx.setUpdatedAt(Instant.now());
        paymentRepository.save(tx);

        Tenant tenant = tenantRepository.findById(tx.getTenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant no encontrado"));

        if (SUB_MONTHLY.equals(tx.getPaymentType()) || SUB_ANNUAL.equals(tx.getPaymentType())) {
            applySubscriptionPayment(tenant, tx);
        } else if (FOLIO_TOPUP.equals(tx.getPaymentType())) {
            applyFolioTopup(tenant, tx);
        }
        tenantRepository.save(tenant);
    }

    @Transactional
    public void processNonApprovedPayment(String referenceCode, String gatewayTransactionId, String status, String paymentMethodType, String rawPayload) {
        SaasPaymentTransaction tx = paymentRepository.findByReferenceCodeForUpdate(referenceCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transacción no encontrada: " + referenceCode));
        if ("APPROVED".equalsIgnoreCase(tx.getStatus())) {
            return; // never downgrade an approved/applied payment
        }
        if (gatewayTransactionId != null && !gatewayTransactionId.isBlank()) {
            tx.setGatewayTransactionId(gatewayTransactionId);
        }
        tx.setStatus(normalizeTxStatus(status));
        tx.setPaymentMethodType(paymentMethodType);
        tx.setRawWebhookPayload(rawPayload);
        tx.setUpdatedAt(Instant.now());
        paymentRepository.save(tx);
    }

    @Transactional
    public BillingDtos.PaymentTransactionDto simulateSandboxPayment(String referenceCode, boolean approve) {
        if (!wompiService.isSandbox()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Simulación solo disponible en modo SANDBOX");
        }
        SaasPaymentTransaction tx = paymentRepository.findByReferenceCode(referenceCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transacción no encontrada"));
        String gwId = "SIM-" + tx.getReferenceCode() + "-" + (approve ? "APPROVED" : "DECLINED");
        String raw = "{\"simulated\":true,\"approved\":" + approve + ",\"reference\":\"" + escapeJson(referenceCode) + "\"}";
        if (approve) {
            processApprovedPayment(referenceCode, gwId, "SIMULATED", raw);
        } else {
            processNonApprovedPayment(referenceCode, gwId, "DECLINED", "SIMULATED", raw);
        }
        return toTxDto(paymentRepository.findByReferenceCode(referenceCode).orElse(tx));
    }

    private void applySubscriptionPayment(Tenant tenant, SaasPaymentTransaction tx) {
        SaasPlan plan = planRepository.findById(tx.getItemCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plan no encontrado para la transacción"));
        Instant now = Instant.now();
        Instant base = maxInstant(now, tenant.getSubscriptionEndsAt(), tenant.getTrialEndsAt());
        long days = SUB_ANNUAL.equals(tx.getPaymentType()) ? 365 : 30;
        Instant newEnd = base.plus(days, ChronoUnit.DAYS);

        tenant.setPlanCode(plan.getCode());
        tenant.setSubscriptionStatus("ACTIVE");
        tenant.setSubscriptionEndsAt(newEnd);
        tenant.setTrialEndsAt(null);
        int currentLimit = nz(tenant.getElectronicFoliosLimit());
        tenant.setElectronicFoliosLimit(currentLimit + nz(plan.getIncludedFoliosPerMonth()));
    }

    private void applyFolioTopup(Tenant tenant, SaasPaymentTransaction tx) {
        FolioPackage pkg = folioPackageRepository.findById(tx.getItemCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paquete de folios no encontrado"));
        tenant.setElectronicFoliosLimit(nz(tenant.getElectronicFoliosLimit()) + nz(pkg.getFoliosCount()));
    }

    private String normalizePaymentType(String paymentType) {
        if (paymentType == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "paymentType es obligatorio");
        String v = paymentType.trim().toUpperCase(Locale.ROOT);
        if (!java.util.Set.of(SUB_MONTHLY, SUB_ANNUAL, FOLIO_TOPUP).contains(v)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "paymentType no válido");
        }
        return v;
    }

    private String normalizeTxStatus(String status) {
        if (status == null) return "ERROR";
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "APPROVED" -> "APPROVED";
            case "DECLINED" -> "DECLINED";
            case "VOIDED" -> "VOIDED";
            case "PENDING" -> "PENDING";
            default -> "ERROR";
        };
    }

    private Instant maxInstant(Instant a, Instant b, Instant c) {
        Instant max = a;
        if (b != null && b.isAfter(max)) max = b;
        if (c != null && c.isAfter(max)) max = c;
        return max;
    }

    private int nz(Integer v) { return v == null ? 0 : v; }

    private BillingDtos.PlanDto toPlanDto(SaasPlan p) {
        return new BillingDtos.PlanDto(p.getCode(), p.getName(), p.getMonthlyPriceCop(), p.getAnnualPriceCop(),
                p.getMaxBranches(), p.getMaxTerminals(), p.getIncludedFoliosPerMonth(), Boolean.TRUE.equals(p.getActive()));
    }

    private BillingDtos.FolioPackageDto toPackageDto(FolioPackage p) {
        return new BillingDtos.FolioPackageDto(p.getCode(), p.getName(), p.getFoliosCount(), p.getPriceCop());
    }

    private BillingDtos.PaymentTransactionDto toTxDto(SaasPaymentTransaction t) {
        return new BillingDtos.PaymentTransactionDto(t.getId(), t.getReferenceCode(), t.getGatewayTransactionId(),
                t.getPaymentType(), t.getItemCode(), t.getAmountCop(), t.getStatus(), t.getPaymentMethodType(),
                t.getCreatedAt(), t.getUpdatedAt());
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
