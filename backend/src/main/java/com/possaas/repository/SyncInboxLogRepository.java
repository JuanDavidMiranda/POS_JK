package com.possaas.repository;

import com.possaas.domain.SyncInboxLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface SyncInboxLogRepository extends JpaRepository<SyncInboxLog, UUID> {

    /**
     * Reclama la clave de idempotencia. Devuelve 1 si es nueva y 0 si ya fue procesada.
     * Todo ocurre dentro de la transacción del push: si falla la venta, el reclamo se revierte.
     */
    @Modifying
    @Query(value = """
            INSERT INTO sync_inbox_log (idempotency_key, tenant_id, entity_type, processed_at)
            VALUES (:key, :tenantId, :entityType, now())
            ON CONFLICT (idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int claim(@Param("key") UUID key,
              @Param("tenantId") UUID tenantId,
              @Param("entityType") String entityType);

    @Modifying
    @Query(value = "UPDATE sync_inbox_log SET response_payload = CAST(:payload AS jsonb) WHERE idempotency_key = :key",
            nativeQuery = true)
    int saveResponse(@Param("key") UUID key, @Param("payload") String payload);
}
