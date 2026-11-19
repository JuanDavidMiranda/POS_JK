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

@Entity
@Table(name = "pos_terminals")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
public class PosTerminal {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "dian_prefix", nullable = false, length = 10)
    private String dianPrefix;

    @Column(name = "range_from", nullable = false)
    private Long rangeFrom;

    @Column(name = "range_to", nullable = false)
    private Long rangeTo;

    @Column(name = "last_synced_sequence", nullable = false)
    private Long lastSyncedSequence;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
