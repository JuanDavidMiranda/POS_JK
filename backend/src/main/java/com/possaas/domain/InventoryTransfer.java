package com.possaas.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_transfers")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
public class InventoryTransfer {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "transfer_number", nullable = false, length = 40)
    private String transferNumber;

    @Column(name = "source_branch_id", nullable = false)
    private UUID sourceBranchId;

    @Column(name = "destination_branch_id", nullable = false)
    private UUID destinationBranchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InventoryTransferStatus status;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "received_by")
    private UUID receivedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;
}
