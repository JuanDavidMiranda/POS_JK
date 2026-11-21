package com.possaas.repository;

import com.possaas.domain.SaasPaymentTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SaasPaymentTransactionRepository extends JpaRepository<SaasPaymentTransaction, UUID> {
    Optional<SaasPaymentTransaction> findByReferenceCode(String referenceCode);

    Optional<SaasPaymentTransaction> findByGatewayTransactionId(String gatewayTransactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from SaasPaymentTransaction t where t.referenceCode = :referenceCode")
    Optional<SaasPaymentTransaction> findByReferenceCodeForUpdate(@Param("referenceCode") String referenceCode);

    List<SaasPaymentTransaction> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
}
