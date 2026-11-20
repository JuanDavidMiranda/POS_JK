package com.possaas.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cash_shifts")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
public class CashShift {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "terminal_id")
    private UUID terminalId;

    @Column(name = "opened_by")
    private UUID openedBy;

    @Column(name = "closed_by")
    private UUID closedBy;

    @Column(name = "authorized_by_user_id")
    private UUID authorizedByUserId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "opening_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal openingAmount;

    @Column(name = "expected_cash_amount", precision = 14, scale = 2)
    private BigDecimal expectedCashAmount;

    @Column(name = "counted_cash_amount", precision = 14, scale = 2)
    private BigDecimal countedCashAmount;

    @Column(name = "difference_amount", precision = 14, scale = 2)
    private BigDecimal differenceAmount;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "notes", length = 255)
    private String notes;
}
