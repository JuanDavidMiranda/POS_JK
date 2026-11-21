package com.possaas.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "saas_plans")
@Getter
@Setter
@NoArgsConstructor
public class SaasPlan {
    @Id
    @Column(length = 30)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "monthly_price_cop", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyPriceCop;

    @Column(name = "annual_price_cop", nullable = false, precision = 12, scale = 2)
    private BigDecimal annualPriceCop;

    @Column(name = "max_branches", nullable = false)
    private Integer maxBranches;

    @Column(name = "max_terminals", nullable = false)
    private Integer maxTerminals;

    @Column(name = "included_folios_per_month", nullable = false)
    private Integer includedFoliosPerMonth;

    @Column(name = "is_active", nullable = false)
    private Boolean active = true;
}
