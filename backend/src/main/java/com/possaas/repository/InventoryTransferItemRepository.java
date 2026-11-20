package com.possaas.repository;

import com.possaas.domain.InventoryTransferItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InventoryTransferItemRepository extends JpaRepository<InventoryTransferItem, UUID> {

    List<InventoryTransferItem> findByTenantIdAndTransferId(UUID tenantId, UUID transferId);
}
