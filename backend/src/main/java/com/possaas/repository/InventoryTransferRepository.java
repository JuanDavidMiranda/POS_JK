package com.possaas.repository;

import com.possaas.domain.InventoryTransfer;
import com.possaas.domain.InventoryTransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InventoryTransferRepository extends JpaRepository<InventoryTransfer, UUID> {

    Optional<InventoryTransfer> findByTenantIdAndId(UUID tenantId, UUID id);

    Page<InventoryTransfer> findByTenantId(UUID tenantId, Pageable pageable);

    Page<InventoryTransfer> findByTenantIdAndStatus(UUID tenantId, InventoryTransferStatus status, Pageable pageable);

    Page<InventoryTransfer> findByTenantIdAndSourceBranchId(UUID tenantId, UUID sourceBranchId, Pageable pageable);

    Page<InventoryTransfer> findByTenantIdAndDestinationBranchId(UUID tenantId, UUID destinationBranchId, Pageable pageable);

    Page<InventoryTransfer> findByTenantIdAndSourceBranchIdAndStatus(UUID tenantId, UUID sourceBranchId, InventoryTransferStatus status, Pageable pageable);

    Page<InventoryTransfer> findByTenantIdAndDestinationBranchIdAndStatus(UUID tenantId, UUID destinationBranchId, InventoryTransferStatus status, Pageable pageable);

    Page<InventoryTransfer> findByTenantIdAndSourceBranchIdAndDestinationBranchId(UUID tenantId, UUID sourceBranchId, UUID destinationBranchId, Pageable pageable);

    Page<InventoryTransfer> findByTenantIdAndSourceBranchIdAndDestinationBranchIdAndStatus(UUID tenantId, UUID sourceBranchId, UUID destinationBranchId, InventoryTransferStatus status, Pageable pageable);
}
