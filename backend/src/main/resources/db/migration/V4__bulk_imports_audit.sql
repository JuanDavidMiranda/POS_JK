-- MVP 2 Fase 2.2: auditoría y seguimiento de cargues masivos desde Excel.

CREATE TABLE import_jobs (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL REFERENCES tenants(id),
    entity_type        VARCHAR(20) NOT NULL,
    file_name          VARCHAR(255) NOT NULL,
    status             VARCHAR(20) NOT NULL,
    total_rows         INT NOT NULL DEFAULT 0,
    processed_rows     INT NOT NULL DEFAULT 0,
    created_count      INT NOT NULL DEFAULT 0,
    updated_count      INT NOT NULL DEFAULT 0,
    rejected_count     INT NOT NULL DEFAULT 0,
    rejected_file_data BYTEA,
    created_by         UUID NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at        TIMESTAMPTZ,
    CONSTRAINT ck_import_jobs_entity_type CHECK (entity_type IN ('PRODUCTS', 'CUSTOMERS')),
    CONSTRAINT ck_import_jobs_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_import_jobs_counts CHECK (
        total_rows >= 0 AND processed_rows >= 0 AND created_count >= 0 AND
        updated_count >= 0 AND rejected_count >= 0
    )
);

CREATE INDEX idx_import_jobs_tenant_created ON import_jobs (tenant_id, created_at DESC);
