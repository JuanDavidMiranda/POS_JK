package com.possaas.repository;

import com.possaas.domain.Sale;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SaleRepository extends JpaRepository<Sale, UUID> {

    boolean existsByTenantIdAndDianPrefixAndSequenceNumber(UUID tenantId, String dianPrefix, long sequenceNumber);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.id = :id and s.tenantId = :tenantId")
    Optional<Sale> findByIdForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
}
