package com.possaas.repository;

import com.possaas.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    List<Customer> findByTenantIdAndUpdatedAtAfterOrderByUpdatedAtAsc(UUID tenantId, Instant since);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    java.util.Optional<Customer> findByTenantIdAndDocumentTypeAndDocumentNumber(UUID tenantId,
                                                                                String documentType,
                                                                                String documentNumber);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("""
            select c from Customer c
             where c.tenantId = :tenantId
               and c.documentType = :documentType
               and c.documentNumber = :documentNumber
            """)
    java.util.Optional<Customer> findByTenantIdAndDocumentTypeAndDocumentNumberForUpdate(
            @org.springframework.data.repository.query.Param("tenantId") UUID tenantId,
            @org.springframework.data.repository.query.Param("documentType") String documentType,
            @org.springframework.data.repository.query.Param("documentNumber") String documentNumber);
}
