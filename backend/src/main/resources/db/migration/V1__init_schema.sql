-- MVP 1: esquema operativo multi-tenant. Toda tabla operativa lleva tenant_id NOT NULL.

CREATE TABLE tenants (
    id                  UUID PRIMARY KEY,
    nit                 VARCHAR(20)  NOT NULL UNIQUE,
    business_name       VARCHAR(255) NOT NULL,
    subscription_status VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE pos_terminals (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL REFERENCES tenants(id),
    name                  VARCHAR(100) NOT NULL,
    dian_prefix           VARCHAR(10)  NOT NULL,
    range_from            BIGINT NOT NULL,
    range_to              BIGINT NOT NULL,
    last_synced_sequence  BIGINT NOT NULL DEFAULT 0,
    is_active             BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_pos_terminals_tenant_prefix UNIQUE (tenant_id, dian_prefix),
    CONSTRAINT ck_pos_terminals_range CHECK (range_from >= 1 AND range_to > range_from),
    CONSTRAINT ck_pos_terminals_sequence CHECK (last_synced_sequence >= 0)
);
CREATE INDEX idx_pos_terminals_tenant ON pos_terminals (tenant_id);

CREATE TABLE products (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL REFERENCES tenants(id),
    barcode       VARCHAR(64),
    sku           VARCHAR(64)  NOT NULL,
    name          VARCHAR(200) NOT NULL,
    sale_price    NUMERIC(14,2) NOT NULL,
    tax_type      VARCHAR(20)  NOT NULL,
    tax_rate      NUMERIC(5,4)  NOT NULL DEFAULT 0,
    current_stock NUMERIC(14,3) NOT NULL DEFAULT 0,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_products_tenant_sku UNIQUE (tenant_id, sku),
    CONSTRAINT ck_products_price CHECK (sale_price >= 0),
    CONSTRAINT ck_products_tax_rate CHECK (tax_rate >= 0 AND tax_rate <= 1),
    CONSTRAINT ck_products_stock CHECK (current_stock >= 0)
);
CREATE INDEX idx_products_tenant_updated ON products (tenant_id, updated_at);
CREATE INDEX idx_products_tenant_barcode ON products (tenant_id, barcode);

CREATE TABLE customers (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    document_type   VARCHAR(20)  NOT NULL,
    document_number VARCHAR(30)  NOT NULL,
    full_name       VARCHAR(200) NOT NULL,
    email           VARCHAR(150),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_customers_tenant_document UNIQUE (tenant_id, document_type, document_number)
);
CREATE INDEX idx_customers_tenant_updated ON customers (tenant_id, updated_at);

CREATE TABLE sales (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL REFERENCES tenants(id),
    terminal_id          UUID NOT NULL REFERENCES pos_terminals(id),
    customer_id          UUID REFERENCES customers(id),
    document_type        VARCHAR(10)  NOT NULL,
    dian_prefix          VARCHAR(10)  NOT NULL,
    sequence_number      BIGINT NOT NULL,
    full_document_number VARCHAR(40)  NOT NULL,
    dian_status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    subtotal             NUMERIC(14,2) NOT NULL,
    tax_total            NUMERIC(14,2) NOT NULL,
    grand_total          NUMERIC(14,2) NOT NULL,
    payment_method       VARCHAR(20)  NOT NULL,
    sold_at              TIMESTAMPTZ NOT NULL,
    synced_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_sales_consecutive UNIQUE (tenant_id, dian_prefix, sequence_number),
    CONSTRAINT ck_sales_sequence CHECK (sequence_number >= 1),
    CONSTRAINT ck_sales_totals CHECK (subtotal >= 0 AND tax_total >= 0 AND grand_total >= 0)
);
CREATE INDEX idx_sales_tenant_sold_at ON sales (tenant_id, sold_at DESC);

CREATE TABLE sale_items (
    id         UUID PRIMARY KEY,
    tenant_id  UUID NOT NULL REFERENCES tenants(id),
    sale_id    UUID NOT NULL REFERENCES sales(id),
    product_id UUID NOT NULL REFERENCES products(id),
    quantity   NUMERIC(14,3) NOT NULL,
    unit_price NUMERIC(14,2) NOT NULL,
    tax_rate   NUMERIC(5,4)  NOT NULL,
    tax_amount NUMERIC(14,2) NOT NULL,
    line_total NUMERIC(14,2) NOT NULL,
    CONSTRAINT ck_sale_items_quantity CHECK (quantity > 0)
);
CREATE INDEX idx_sale_items_tenant_sale ON sale_items (tenant_id, sale_id);

CREATE TABLE sync_inbox_log (
    idempotency_key UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    entity_type     VARCHAR(40) NOT NULL,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    response_payload JSONB
);
CREATE INDEX idx_sync_inbox_tenant_processed ON sync_inbox_log (tenant_id, processed_at DESC);
