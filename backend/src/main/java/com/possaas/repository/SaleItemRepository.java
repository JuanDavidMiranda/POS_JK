package com.possaas.repository;

import com.possaas.domain.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SaleItemRepository extends JpaRepository<SaleItem, UUID> {

    List<SaleItem> findByTenantIdAndSaleId(UUID tenantId, UUID saleId);

    interface TopProductProjection {
        UUID getProductId();
        String getSku();
        String getName();
        BigDecimal getUnitsSold();
        BigDecimal getRevenue();
    }

    interface TaxBreakdownProjection {
        String getTaxType();
        BigDecimal getTaxRate();
        BigDecimal getTaxableBase();
        BigDecimal getTaxAmount();
        BigDecimal getTotal();
    }

    @Query(value = """
            select si.product_id as productId,
                   p.sku as sku,
                   p.name as name,
                   coalesce(sum(si.quantity), 0) as unitsSold,
                   coalesce(sum(si.line_total), 0) as revenue
              from sale_items si
              join sales s on s.id = si.sale_id and s.tenant_id = si.tenant_id
              join products p on p.id = si.product_id and p.tenant_id = si.tenant_id
             where si.tenant_id = :tenantId
               and s.status <> 'VOIDED'
               and s.sold_at >= :from
               and s.sold_at < :to
             group by si.product_id, p.sku, p.name
             order by revenue desc
             limit :limit
            """, nativeQuery = true)
    List<TopProductProjection> topProducts(@Param("tenantId") UUID tenantId,
                                           @Param("from") Instant from,
                                           @Param("to") Instant to,
                                           @Param("limit") int limit);

    @Query(value = """
            select p.tax_type as taxType,
                   p.tax_rate as taxRate,
                   coalesce(sum(si.quantity * si.unit_price), 0) as taxableBase,
                   coalesce(sum(si.tax_amount), 0) as taxAmount,
                   coalesce(sum(si.line_total), 0) as total
              from sale_items si
              join sales s on s.id = si.sale_id and s.tenant_id = si.tenant_id
              join products p on p.id = si.product_id and p.tenant_id = si.tenant_id
             where si.tenant_id = :tenantId
               and s.status <> 'VOIDED'
               and s.sold_at >= :from
               and s.sold_at < :to
             group by p.tax_type, p.tax_rate
             order by p.tax_type, p.tax_rate
            """, nativeQuery = true)
    List<TaxBreakdownProjection> taxBreakdown(@Param("tenantId") UUID tenantId,
                                              @Param("from") Instant from,
                                              @Param("to") Instant to);
}
