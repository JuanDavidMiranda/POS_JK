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
@Table(name = "tenant_accounting_configs")
@Getter
@Setter
@NoArgsConstructor
public class TenantAccountingConfig {
    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "target_software", nullable = false, length = 30)
    private String targetSoftware = "GENERIC_PUC";

    @Column(name = "sales_income_account", nullable = false, length = 20)
    private String salesIncomeAccount = "413505";

    @Column(name = "vat_generated_account", nullable = false, length = 20)
    private String vatGeneratedAccount = "240805";

    @Column(name = "inc_generated_account", nullable = false, length = 20)
    private String incGeneratedAccount = "249505";

    @Column(name = "cash_account", nullable = false, length = 20)
    private String cashAccount = "110505";

    @Column(name = "bank_transfer_account", nullable = false, length = 20)
    private String bankTransferAccount = "111005";

    @Column(name = "returns_account", nullable = false, length = 20)
    private String returnsAccount = "417505";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
