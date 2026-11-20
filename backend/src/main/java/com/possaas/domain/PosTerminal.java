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
import java.time.LocalDate;
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

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

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

    @Column(name = "credit_note_prefix", nullable = false, length = 10)
    private String creditNotePrefix;

    @Column(name = "credit_note_last_sequence", nullable = false)
    private Long creditNoteLastSequence;

    @Column(name = "dian_resolution_number", length = 60)
    private String dianResolutionNumber;

    @Column(name = "dian_resolution_date")
    private LocalDate dianResolutionDate;

    @Column(name = "dian_valid_from")
    private LocalDate dianValidFrom;

    @Column(name = "dian_valid_to")
    private LocalDate dianValidTo;

    @Column(name = "dian_technical_key", length = 120)
    private String dianTechnicalKey;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
