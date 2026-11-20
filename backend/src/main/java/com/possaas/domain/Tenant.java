package com.possaas.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
public class Tenant {

    @Id
    private UUID id;

    @Column(name = "nit", nullable = false, unique = true, length = 20)
    private String nit;

    @Column(name = "business_name", nullable = false, length = 255)
    private String businessName;

    @Column(name = "trade_name", length = 150)
    private String tradeName;

    @Column(name = "tax_regime", nullable = false, length = 40)
    private String taxRegime = "NO_RESPONSABLE_IVA";

    @Column(name = "fiscal_responsibilities", length = 100)
    private String fiscalResponsibilities = "R-99-PN";

    @Column(name = "city", length = 80)
    private String city;

    @Column(name = "address", length = 200)
    private String address;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email", length = 120)
    private String email;

    @Column(name = "receipt_footer_message", length = 255)
    private String receiptFooterMessage = "Gracias por su compra";

    @Column(name = "subscription_status", nullable = false, length = 30)
    private String subscriptionStatus;

    @Column(name = "plan_code", nullable = false, length = 30)
    private String planCode = "TRIAL";

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Column(name = "subscription_ends_at")
    private Instant subscriptionEndsAt;

    @Column(name = "electronic_folios_limit", nullable = false)
    private Integer electronicFoliosLimit = 50;

    @Column(name = "electronic_folios_used", nullable = false)
    private Integer electronicFoliosUsed = 0;

    @Column(name = "onboarding_completed", nullable = false)
    private Boolean onboardingCompleted = false;

    @Column(name = "onboarding_step", nullable = false)
    private Integer onboardingStep = 1;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
