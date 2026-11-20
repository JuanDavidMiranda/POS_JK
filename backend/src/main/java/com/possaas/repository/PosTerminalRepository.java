package com.possaas.repository;

import com.possaas.domain.PosTerminal;
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

public interface PosTerminalRepository extends JpaRepository<PosTerminal, UUID> {

    Optional<PosTerminal> findByTenantIdAndDianPrefix(UUID tenantId, String dianPrefix);

    Optional<PosTerminal> findByTenantIdAndId(UUID tenantId, UUID terminalId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PosTerminal t where t.tenantId = :tenantId and t.id = :terminalId")
    Optional<PosTerminal> findByTenantIdAndIdForUpdate(@Param("tenantId") UUID tenantId,
                                                        @Param("terminalId") UUID terminalId);

    Optional<PosTerminal> findFirstByTenantIdAndActiveTrueOrderByDianPrefixAsc(UUID tenantId);

    List<PosTerminal> findByTenantIdOrderByNameAsc(UUID tenantId);

    List<PosTerminal> findByTenantIdAndBranchIdOrderByNameAsc(UUID tenantId, UUID branchId);

    @Modifying
    @Query("""
            update PosTerminal t
               set t.lastSyncedSequence = case when t.lastSyncedSequence < :sequence
                                               then :sequence else t.lastSyncedSequence end,
                   t.updatedAt = :now
             where t.tenantId = :tenantId and t.id = :terminalId
            """)
    int advanceSequence(@Param("tenantId") UUID tenantId,
                        @Param("terminalId") UUID terminalId,
                        @Param("sequence") long sequence,
                        @Param("now") Instant now);

    @Modifying
    @Query("""
            update PosTerminal t
               set t.creditNoteLastSequence = case when t.creditNoteLastSequence < :sequence
                                                   then :sequence else t.creditNoteLastSequence end,
                   t.updatedAt = :now
             where t.tenantId = :tenantId and t.id = :terminalId
            """)
    int advanceCreditNoteSequence(@Param("tenantId") UUID tenantId,
                                  @Param("terminalId") UUID terminalId,
                                  @Param("sequence") long sequence,
                                  @Param("now") Instant now);

    // Phase 3.2 helpers
    List<PosTerminal> findByTenantIdAndActiveTrue(UUID tenantId);

    List<PosTerminal> findByTenantId(UUID tenantId);

    Optional<PosTerminal> findFirstByTenantIdAndName(UUID tenantId, String name);

    long countByTenantId(UUID tenantId);
}
