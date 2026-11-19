package com.possaas.repository;

import com.possaas.domain.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SaleItemRepository extends JpaRepository<SaleItem, UUID> {

    List<SaleItem> findByTenantIdAndSaleId(UUID tenantId, UUID saleId);
}
