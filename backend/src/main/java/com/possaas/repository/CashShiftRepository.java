package com.possaas.repository;

import com.possaas.domain.CashShift;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CashShiftRepository extends JpaRepository<CashShift, UUID> {

    Optional<CashShift> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<CashShift> findFirstByTenantIdAndStatusOrderByOpenedAtDesc(UUID tenantId, String status);

    List<CashShift> findTop20ByTenantIdOrderByOpenedAtDesc(UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CashShift c where c.id = :id and c.tenantId = :tenantId")
    Optional<CashShift> findByIdAndTenantIdForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    @Query("""
            select coalesce(sum(case when s.paymentMethod = 'EFECTIVO' then s.grandTotal else 0 end), 0)
              from Sale s
             where s.tenantId = :tenantId
               and s.cashShiftId = :shiftId
               and s.status <> 'VOIDED'
            """)
    java.math.BigDecimal sumCashSalesByShift(@Param("tenantId") UUID tenantId, @Param("shiftId") UUID shiftId);

    @Query("""
            select coalesce(sum(case when s.paymentMethod = 'EFECTIVO' then s.grandTotal else 0 end), 0)
              from Sale s
             where s.tenantId = :tenantId
               and s.cashShiftId = :shiftId
               and s.status <> 'VOIDED'
            """)
    java.math.BigDecimal sumCashSalesByShiftOpen(@Param("tenantId") UUID tenantId, @Param("shiftId") UUID shiftId);
}
