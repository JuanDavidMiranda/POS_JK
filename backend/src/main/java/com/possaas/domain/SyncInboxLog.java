package com.possaas.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro de idempotencia. La columna response_payload (JSONB) se escribe con SQL nativo
 * desde el repositorio para evitar depender de un tipo Hibernate específico de JSONB.
 */
@Entity
@Table(name = "sync_inbox_log")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
public class SyncInboxLog {

    @Id
    @Column(name = "idempotency_key")
    private UUID idempotencyKey;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
