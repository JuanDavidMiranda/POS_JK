package com.possaas.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_security_policies")
@Getter
@Setter
@NoArgsConstructor
public class TenantSecurityPolicy {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "require_pin_for_discounts", nullable = false)
    private Boolean requirePinForDiscounts = true;

    @Column(name = "max_cashier_discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxCashierDiscountPercent = BigDecimal.ZERO;

    @Column(name = "require_pin_for_void_item", nullable = false)
    private Boolean requirePinForVoidItem = true;

    @Column(name = "blind_cash_close", nullable = false)
    private Boolean blindCashClose = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
