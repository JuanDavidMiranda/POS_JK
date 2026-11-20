package com.possaas.repository;

import com.possaas.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    List<Customer> findByTenantIdAndUpdatedAtAfterOrderByUpdatedAtAsc(UUID tenantId, Instant since);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Customer> findByTenantIdAndDocumentTypeAndDocumentNumber(UUID tenantId,
                                                                       String documentType,
                                                                       String documentNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from Customer c
             where c.tenantId = :tenantId
               and c.documentType = :documentType
               and c.documentNumber = :documentNumber
            """)
    Optional<Customer> findByTenantIdAndDocumentTypeAndDocumentNumberForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("documentType") String documentType,
            @Param("documentNumber") String documentNumber);

    // Phase 3.2 helpers
    List<Customer> findByTenantId(UUID tenantId);

    Optional<Customer> findByTenantIdAndId(UUID tenantId, UUID id);
}
