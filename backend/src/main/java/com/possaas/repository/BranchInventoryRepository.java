package com.possaas.repository;

import com.possaas.domain.BranchInventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchInventoryRepository extends JpaRepository<BranchInventory, UUID> {

    Optional<BranchInventory> findByTenantIdAndBranchIdAndProductId(UUID tenantId, UUID branchId, UUID productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select bi from BranchInventory bi
             where bi.tenantId = :tenantId and bi.branchId = :branchId and bi.productId = :productId
            """)
    Optional<BranchInventory> findForUpdate(@Param("tenantId") UUID tenantId,
                                            @Param("branchId") UUID branchId,
                                            @Param("productId") UUID productId);

    List<BranchInventory> findByTenantIdAndBranchIdOrderByUpdatedAtAsc(UUID tenantId, UUID branchId);

    List<BranchInventory> findByTenantId(UUID tenantId);

    List<BranchInventory> findByTenantIdAndProductId(UUID tenantId, UUID productId);

    @Query("""
            select bi from BranchInventory bi
             where bi.tenantId = :tenantId and bi.branchId = :branchId and bi.updatedAt > :since
             order by bi.updatedAt asc
            """)
    List<BranchInventory> findUpdatedSince(@Param("tenantId") UUID tenantId,
                                           @Param("branchId") UUID branchId,
                                           @Param("since") Instant since);

    @Query("""
            select bi from BranchInventory bi
             where bi.tenantId = :tenantId and bi.branchId = :branchId and bi.currentStock <= bi.minStock
             order by bi.currentStock asc
            """)
    List<BranchInventory> findLowStock(@Param("tenantId") UUID tenantId, @Param("branchId") UUID branchId);

    @Modifying
    @Query("""
            delete from BranchInventory bi
             where bi.tenantId = :tenantId and bi.branchId = :branchId
            """)
    int deleteByTenantIdAndBranchId(@Param("tenantId") UUID tenantId, @Param("branchId") UUID branchId);
}
