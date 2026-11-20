package com.possaas.repository;

import com.possaas.domain.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByNit(String nit);

    boolean existsByNit(String nit);

    @Query("""
            select t from Tenant t
             where (:q is null or :q = '' or lower(t.nit) like lower(concat('%', :q, '%'))
                    or lower(t.businessName) like lower(concat('%', :q, '%'))
                    or lower(coalesce(t.tradeName, '')) like lower(concat('%', :q, '%')))
               and (:status is null or :status = '' or :status = 'ALL'
                    or (:status = 'TRIAL' and t.planCode = 'TRIAL')
                    or (:status <> 'TRIAL' and t.subscriptionStatus = :status))
            """)
    Page<Tenant> search(@Param("q") String q, @Param("status") String status, Pageable pageable);

    long countBySubscriptionStatus(String subscriptionStatus);

    long countByPlanCode(String planCode);

    @Query("select coalesce(sum(t.electronicFoliosUsed), 0) from Tenant t")
    long sumElectronicFoliosUsed();

    @Query("""
            select count(t) from Tenant t
             where t.subscriptionStatus = 'SUSPENDED'
                or (t.subscriptionStatus = 'ACTIVE' and t.subscriptionEndsAt is not null and t.subscriptionEndsAt < :now)
                or t.subscriptionStatus = 'PAST_DUE'
            """)
    long countExpiredOrSuspended(@Param("now") Instant now);

    @Query("""
            select t.id as tenantId, count(distinct b.id) as branchCount, count(distinct pt.id) as terminalCount
              from Tenant t
              left join com.possaas.domain.Branch b on b.tenantId = t.id
              left join com.possaas.domain.PosTerminal pt on pt.tenantId = t.id
             where t.id in :ids
             group by t.id
            """)
    List<TenantCounts> countsForTenants(@Param("ids") List<UUID> ids);

    interface TenantCounts {
        UUID getTenantId();
        long getBranchCount();
        long getTerminalCount();
    }
}
