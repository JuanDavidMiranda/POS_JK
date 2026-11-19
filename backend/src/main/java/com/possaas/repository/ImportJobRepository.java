package com.possaas.repository;

import com.possaas.domain.ImportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {

    List<ImportJob> findTop20ByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<ImportJob> findByIdAndTenantId(UUID id, UUID tenantId);
}
