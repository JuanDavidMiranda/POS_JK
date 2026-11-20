package com.possaas.repository;

import com.possaas.domain.Sale;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SaleRepository extends JpaRepository<Sale, UUID> {

    boolean existsByTenantIdAndDianPrefixAndSequenceNumber(UUID tenantId, String dianPrefix, long sequenceNumber);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.id = :id and s.tenantId = :tenantId")
    Optional<Sale> findByIdForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    interface SalesTotalsProjection {
        BigDecimal getGrossSales();
        BigDecimal getTaxTotal();
        Long getTransactions();
    }

    interface PaymentBreakdownProjection {
        String getPaymentMethod();
        BigDecimal getGrossTotal();
        Long getTransactions();
    }

    interface DailyBreakdownProjection {
        LocalDate getDate();
        BigDecimal getGrossSales();
        BigDecimal getTaxTotal();
        Long getTransactions();
    }

    @Query(value = """
            select coalesce(sum(s.grand_total), 0) as grossSales,
                   coalesce(sum(s.tax_total), 0) as taxTotal,
                   count(*) as transactions
              from sales s
             where s.tenant_id = :tenantId
               and s.status <> 'VOIDED'
               and s.sold_at >= :from
               and s.sold_at < :to
               and (:branchId is null or s.branch_id = :branchId)
            """, nativeQuery = true)
    SalesTotalsProjection totals(@Param("tenantId") UUID tenantId,
                                 @Param("from") Instant from,
                                 @Param("to") Instant to,
                                 @Param("branchId") UUID branchId);

    @Query(value = """
            select s.payment_method as paymentMethod,
                   coalesce(sum(s.grand_total), 0) as grossTotal,
                   count(*) as transactions
              from sales s
             where s.tenant_id = :tenantId
               and s.status <> 'VOIDED'
               and s.sold_at >= :from
               and s.sold_at < :to
               and (:branchId is null or s.branch_id = :branchId)
             group by s.payment_method
             order by grossTotal desc
            """, nativeQuery = true)
    List<PaymentBreakdownProjection> paymentBreakdown(@Param("tenantId") UUID tenantId,
                                                      @Param("from") Instant from,
                                                      @Param("to") Instant to,
                                                      @Param("branchId") UUID branchId);

    @Query(value = """
            select cast(s.sold_at at time zone 'America/Bogota' as date) as date,
                   coalesce(sum(s.grand_total), 0) as grossSales,
                   coalesce(sum(s.tax_total), 0) as taxTotal,
                   count(*) as transactions
              from sales s
             where s.tenant_id = :tenantId
               and s.status <> 'VOIDED'
               and s.sold_at >= :from
               and s.sold_at < :to
               and (:branchId is null or s.branch_id = :branchId)
             group by 1
             order by 1 asc
            """, nativeQuery = true)
    List<DailyBreakdownProjection> dailyBreakdown(@Param("tenantId") UUID tenantId,
                                                  @Param("from") Instant from,
                                                  @Param("to") Instant to,
                                                  @Param("branchId") UUID branchId);

    // Existing + Phase 3.2 helpers
    Optional<Sale> findByTenantIdAndFullDocumentNumber(UUID tenantId, String fullDocumentNumber);

    Optional<Sale> findByTenantIdAndId(UUID tenantId, UUID id);

    List<Sale> findByTenantIdAndBranchIdAndSoldAtBetween(UUID tenantId, UUID branchId, Instant from, Instant to);

    List<Sale> findByTenantIdAndSoldAtBetween(UUID tenantId, Instant from, Instant to);

    List<Sale> findByTenantIdAndCashShiftId(UUID tenantId, UUID cashShiftId);

    @Query("""
            select coalesce(sum(s.grandTotal), 0) from Sale s
             where s.soldAt >= :from and s.soldAt < :to
            """)
    BigDecimal sumGrandTotalBetweenAllTenants(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select count(s) from Sale s
             where s.soldAt >= :from and s.soldAt < :to
            """)
    long countBetweenAllTenants(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select s from Sale s
             where s.tenantId = :tenantId and (s.dianStatus = 'REJECTED' or s.dianStatus = 'PENDING' or s.dianStatus = 'PENDING_TRANSMISSION')
             order by s.soldAt desc
            """)
    List<Sale> findDianPendingOrErrorByTenant(@Param("tenantId") UUID tenantId);

    Optional<Sale> findTopByTenantIdOrderBySyncedAtDesc(UUID tenantId);
}
