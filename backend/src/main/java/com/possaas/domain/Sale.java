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
@Table(name = "sales")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
public class Sale {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "terminal_id", nullable = false)
    private UUID terminalId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "document_type", nullable = false, length = 10)
    private String documentType;

    @Column(name = "dian_prefix", nullable = false, length = 10)
    private String dianPrefix;

    @Column(name = "sequence_number", nullable = false)
    private Long sequenceNumber;

    @Column(name = "full_document_number", nullable = false, length = 40)
    private String fullDocumentNumber;

    @Column(name = "dian_status", nullable = false, length = 20)
    private String dianStatus;

    @Column(name = "subtotal", nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "tax_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxTotal;

    @Column(name = "grand_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal grandTotal;

    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMethod;

    @Column(name = "sold_at", nullable = false)
    private Instant soldAt;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;
}
