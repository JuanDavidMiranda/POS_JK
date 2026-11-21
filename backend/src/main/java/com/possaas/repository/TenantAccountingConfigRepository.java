package com.possaas.repository;

import com.possaas.domain.TenantAccountingConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TenantAccountingConfigRepository extends JpaRepository<TenantAccountingConfig, UUID> {
}
