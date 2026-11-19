package com.possaas.repository;

import com.possaas.domain.CreditNoteItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
