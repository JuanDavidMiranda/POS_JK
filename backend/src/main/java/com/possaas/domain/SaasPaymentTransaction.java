package com.possaas.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saas_payment_transactions")
@Getter
@Setter
@NoArgsConstructor
public class SaasPaymentTransaction {
    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "reference_code", nullable = false, unique = true, length = 80)
    private String referenceCode;

    @Column(name = "gateway_transaction_id", unique = true, length = 100)
    private String gatewayTransactionId;

    @Column(name = "payment_type", nullable = false, length = 30)
    private String paymentType;

    @Column(name = "item_code", nullable = false, length = 30)
    private String itemCode;

    @Column(name = "amount_cop", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountCop;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "payment_method_type", length = 40)
    private String paymentMethodType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_webhook_payload", columnDefinition = "jsonb")
    private String rawWebhookPayload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
