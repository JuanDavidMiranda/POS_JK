package com.possaas.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "import_jobs")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
public class ImportJob {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "entity_type", nullable = false, length = 20)
    private String entityType;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "total_rows", nullable = false)
    private Integer totalRows;

    @Column(name = "processed_rows", nullable = false)
    private Integer processedRows;

    @Column(name = "created_count", nullable = false)
    private Integer createdCount;

    @Column(name = "updated_count", nullable = false)
    private Integer updatedCount;

    @Column(name = "rejected_count", nullable = false)
    private Integer rejectedCount;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "rejected_file_data")
    private byte[] rejectedFileData;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
