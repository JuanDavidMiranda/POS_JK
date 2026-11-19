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

    java.util.Optional<Product> findByTenantIdAndSku(UUID tenantId, String sku);

    java.util.Optional<Product> findByTenantIdAndBarcode(UUID tenantId, String barcode);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.tenantId = :tenantId and p.id = :id")
    java.util.Optional<Product> findByTenantIdAndIdForUpdate(@Param("tenantId") UUID tenantId,
                                                             @Param("id") UUID id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.tenantId = :tenantId and p.sku = :sku")
    java.util.Optional<Product> findByTenantIdAndSkuForUpdate(@Param("tenantId") UUID tenantId,
                                                              @Param("sku") String sku);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.tenantId = :tenantId and p.barcode = :barcode")
    java.util.Optional<Product> findByTenantIdAndBarcodeForUpdate(@Param("tenantId") UUID tenantId,
                                                                  @Param("barcode") String barcode);

    @Query("""
            select p from Product p
             where p.tenantId = :tenantId
               and p.active = true
               and p.currentStock <= p.minStock
             order by p.currentStock asc
            """)
    List<Product> findLowStock(@Param("tenantId") UUID tenantId);

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
