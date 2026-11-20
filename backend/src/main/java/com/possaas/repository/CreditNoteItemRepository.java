package com.possaas.repository;

import com.possaas.domain.CreditNoteItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CreditNoteItemRepository extends JpaRepository<CreditNoteItem, UUID> {

    @Query(value = """
            select i.product_id as "productId", sum(i.quantity) as "quantity"
              from credit_note_items i
              join credit_notes cn on cn.id = i.credit_note_id
             where cn.tenant_id = :tenantId
               and cn.sale_id = :saleId
             group by i.product_id
            """, nativeQuery = true)
    List<ReturnedQuantity> sumReturnedBySale(@Param("tenantId") UUID tenantId,
                                             @Param("saleId") UUID saleId);

    List<CreditNoteItem> findByTenantIdAndCreditNoteId(UUID tenantId, UUID creditNoteId);

    interface CreditTaxBreakdownProjection {
        String getTaxType();
        BigDecimal getTaxRate();
        BigDecimal getTaxableBase();
        BigDecimal getTaxAmount();
        BigDecimal getTotal();
    }

    @Query(value = """
            select p.tax_type as taxType,
                   p.tax_rate as taxRate,
                   coalesce(sum(cni.quantity * cni.unit_price), 0) as taxableBase,
                   coalesce(sum(cni.tax_amount), 0) as taxAmount,
                   coalesce(sum(cni.line_total), 0) as total
              from credit_note_items cni
              join credit_notes cn on cn.id = cni.credit_note_id and cn.tenant_id = cni.tenant_id
              join products p on p.id = cni.product_id and p.tenant_id = cni.tenant_id
             where cni.tenant_id = :tenantId
               and cn.dian_status <> 'REJECTED'
               and cn.created_at >= :from
               and cn.created_at < :to
               and (:branchId is null or cn.branch_id = :branchId)
             group by p.tax_type, p.tax_rate
             order by p.tax_type, p.tax_rate
            """, nativeQuery = true)
    List<CreditTaxBreakdownProjection> creditTaxBreakdown(@Param("tenantId") UUID tenantId,
                                                          @Param("from") Instant from,
                                                          @Param("to") Instant to,
                                                          @Param("branchId") UUID branchId);
}
