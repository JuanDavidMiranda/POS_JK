package com.possaas.repository;

import com.possaas.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    boolean existsById(UUID id);
}
