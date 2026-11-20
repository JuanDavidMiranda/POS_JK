package com.possaas.repository;

import com.possaas.domain.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findByTenantIdAndUpdatedAtAfterOrderByUpdatedAtAsc(UUID tenantId, Instant since);

    List<Product> findByTenantIdAndActiveTrue(UUID tenantId);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Product> findByTenantIdAndSku(UUID tenantId, String sku);

    Optional<Product> findByTenantIdAndBarcode(UUID tenantId, String barcode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.tenantId = :tenantId and p.id = :id")
    Optional<Product> findByTenantIdAndIdForUpdate(@Param("tenantId") UUID tenantId,
                                                   @Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.tenantId = :tenantId and p.sku = :sku")
    Optional<Product> findByTenantIdAndSkuForUpdate(@Param("tenantId") UUID tenantId,
                                                    @Param("sku") String sku);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.tenantId = :tenantId and p.barcode = :barcode")
    Optional<Product> findByTenantIdAndBarcodeForUpdate(@Param("tenantId") UUID tenantId,
                                                        @Param("barcode") String barcode);
}
