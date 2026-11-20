package com.possaas.repository;

import com.possaas.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);

    List<User> findByTenantId(UUID tenantId);

    List<User> findByTenantIdAndActiveTrueAndSupervisorPinHashIsNotNull(UUID tenantId);

    List<User> findByTenantIdAndRoleInAndActiveTrueAndSupervisorPinHashIsNotNull(UUID tenantId, List<String> roles);

    // Phase 3.2 auth/onboarding
    List<User> findByTenantIdAndActiveTrue(UUID tenantId);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByEmailIgnoreCaseAndActiveTrue(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByTenantIdAndUsernameIgnoreCase(UUID tenantId, String username);
}
