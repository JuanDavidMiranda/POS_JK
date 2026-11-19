package com.possaas.repository;

import com.possaas.domain.Sale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SaleRepository extends JpaRepository<Sale, UUID> {

    boolean existsByTenantIdAndDianPrefixAndSequenceNumber(UUID tenantId, String dianPrefix, long sequenceNumber);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);
}
