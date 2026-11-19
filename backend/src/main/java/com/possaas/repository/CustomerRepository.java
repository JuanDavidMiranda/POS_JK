package com.possaas.repository;

import com.possaas.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    List<Customer> findByTenantIdAndUpdatedAtAfterOrderByUpdatedAtAsc(UUID tenantId, Instant since);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);
}
