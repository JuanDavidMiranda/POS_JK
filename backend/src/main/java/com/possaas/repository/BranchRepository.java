package com.possaas.repository;

import com.possaas.domain.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchRepository extends JpaRepository<Branch, UUID> {

    List<Branch> findByTenantIdOrderByMainDescNameAsc(UUID tenantId);

    Optional<Branch> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<Branch> findByTenantIdAndCode(UUID tenantId, String code);

    Optional<Branch> findByTenantIdAndMainTrue(UUID tenantId);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    long countByTenantId(UUID tenantId);
}
