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
@Table(name = "folio_packages")
@Getter
@Setter
@NoArgsConstructor
public class FolioPackage {
    @Id
    @Column(length = 30)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "folios_count", nullable = false)
    private Integer foliosCount;

    @Column(name = "price_cop", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceCop;
}
