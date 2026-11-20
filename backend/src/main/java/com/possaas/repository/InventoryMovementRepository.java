package com.possaas.repository;

import com.possaas.domain.InventoryMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {

    Page<InventoryMovement> findByTenantIdAndProductId(UUID tenantId, UUID productId, Pageable pageable);

    Page<InventoryMovement> findByTenantIdAndBranchIdAndProductId(UUID tenantId, UUID branchId, UUID productId, Pageable pageable);
}
