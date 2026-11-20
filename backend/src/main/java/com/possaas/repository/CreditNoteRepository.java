package com.possaas.repository;

import com.possaas.domain.CreditNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CreditNoteRepository extends JpaRepository<CreditNote, UUID> {

    List<CreditNote> findByTenantIdAndSaleIdOrderByCreatedAtDesc(UUID tenantId, UUID saleId);

    interface CreditNoteTotalsProjection {
        BigDecimal getTotal();
        BigDecimal getTaxTotal();
    }

    interface DailyCreditNoteProjection {
        LocalDate getDate();
        BigDecimal getTotal();
    }

    @Query(value = """
            select coalesce(sum(cn.grand_total), 0) as total,
                   coalesce(sum(cn.tax_total), 0) as taxTotal
              from credit_notes cn
             where cn.tenant_id = :tenantId
               and cn.dian_status <> 'REJECTED'
               and cn.created_at >= :from
               and cn.created_at < :to
               and (:branchId is null or cn.branch_id = :branchId)
            """, nativeQuery = true)
    CreditNoteTotalsProjection totals(@Param("tenantId") UUID tenantId,
                                      @Param("from") Instant from,
                                      @Param("to") Instant to,
                                      @Param("branchId") UUID branchId);

    @Query(value = """
            select cast(cn.created_at at time zone 'America/Bogota' as date) as date,
                   coalesce(sum(cn.grand_total), 0) as total
              from credit_notes cn
             where cn.tenant_id = :tenantId
               and cn.dian_status <> 'REJECTED'
               and cn.created_at >= :from
               and cn.created_at < :to
               and (:branchId is null or cn.branch_id = :branchId)
             group by 1
             order by 1 asc
            """, nativeQuery = true)
    List<DailyCreditNoteProjection> dailyTotals(@Param("tenantId") UUID tenantId,
                                                @Param("from") Instant from,
                                                @Param("to") Instant to,
                                                @Param("branchId") UUID branchId);
}
