package com.possaas.repository;

import com.possaas.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findByTenantIdAndUpdatedAtAfterOrderByUpdatedAtAsc(UUID tenantId, Instant since);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    @Modifying
    @Query("""
            update Product p
               set p.currentStock = p.currentStock - :quantity,
                   p.updatedAt = :now
             where p.tenantId = :tenantId
               and p.id = :productId
               and p.currentStock >= :quantity
            """)
    int decrementStock(@Param("tenantId") UUID tenantId,
                       @Param("productId") UUID productId,
                       @Param("quantity") BigDecimal quantity,
                       @Param("now") Instant now);
}
