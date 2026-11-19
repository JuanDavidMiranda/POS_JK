package com.possaas.repository;

import com.possaas.domain.PosTerminal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PosTerminalRepository extends JpaRepository<PosTerminal, UUID> {

    Optional<PosTerminal> findByTenantIdAndDianPrefix(UUID tenantId, String dianPrefix);

    Optional<PosTerminal> findByTenantIdAndId(UUID tenantId, UUID terminalId);

    Optional<PosTerminal> findFirstByTenantIdAndActiveTrueOrderByDianPrefixAsc(UUID tenantId);

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
}
