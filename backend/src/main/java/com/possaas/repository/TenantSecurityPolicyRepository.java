package com.possaas.repository;

import com.possaas.domain.TenantSecurityPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TenantSecurityPolicyRepository extends JpaRepository<TenantSecurityPolicy, UUID> {
}
