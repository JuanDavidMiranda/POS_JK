-- MVP 2 Fase 2.1: Kardex de inventario y notas crédito / anulaciones DIAN.

-- 1) Inventario configurable por producto.
ALTER TABLE products
    ADD COLUMN min_stock NUMERIC(12,3) NOT NULL DEFAULT 0,
    ADD COLUMN allow_negative_stock BOOLEAN NOT NULL DEFAULT FALSE;

-- El check original impedía cualquier ajuste negativo. Ahora la regla seevalúa
-- en InventoryService según allow_negative_stock.
ALTER TABLE products DROP CONSTRAINT IF EXISTS ck_products_stock;

-- 2) Estado de ciclo de vida de la venta (independiente del estado DIAN).
ALTER TABLE sales
    ADD COLUMN status VARCHAR(25) NOT NULL DEFAULT 'ACTIVE';
UPDATE sales SET status = 'ACTIVE' WHERE status IS NULL;
UPDATE sales SET dian_status = 'SYNCED' WHERE dian_status = 'PENDING';
ALTER TABLE sales ADD CONSTRAINT ck_sales_status
    CHECK (status IN ('ACTIVE', 'PARTIALLY_RETURNED', 'VOIDED'));

-- 3) Consecutivo independiente para notas crédito por terminal.
ALTER TABLE pos_terminals
    ADD COLUMN credit_note_prefix VARCHAR(10) NOT NULL DEFAULT 'NC1',
    ADD COLUMN credit_note_last_sequence BIGINT NOT NULL DEFAULT 0;
ALTER TABLE pos_terminals ADD CONSTRAINT ck_terminal_nc_sequence
    CHECK (credit_note_last_sequence >= 0);

-- 4) Kardex.
CREATE TABLE inventory_movements (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL REFERENCES tenants(id),
    product_id     UUID NOT NULL REFERENCES products(id),
    movement_type  VARCHAR(30) NOT NULL,
    quantity       NUMERIC(12,3) NOT NULL,
    previous_stock NUMERIC(12,3) NOT NULL,
    new_stock      NUMERIC(12,3) NOT NULL,
    reference_id   UUID,
    notes          VARCHAR(255),
    created_by     UUID,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_inventory_movement_type CHECK (movement_type IN (
        'INITIAL_BALANCE', 'PURCHASE_IN', 'SALE_OUT', 'CREDIT_NOTE_IN',
        'ADJUSTMENT_IN', 'ADJUSTMENT_OUT')),
    CONSTRAINT ck_inventory_movement_quantity CHECK (quantity > 0)
);
CREATE INDEX idx_inventory_movements_tenant_product_created
    ON inventory_movements (tenant_id, product_id, created_at DESC);
CREATE INDEX idx_inventory_movements_tenant_reference
    ON inventory_movements (tenant_id, reference_id);

-- 5) Notas crédito DIAN.
CREATE TABLE credit_notes (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL REFERENCES tenants(id),
    sale_id              UUID NOT NULL REFERENCES sales(id),
    terminal_id          UUID NOT NULL REFERENCES pos_terminals(id),
    dian_prefix          VARCHAR(10) NOT NULL,
    sequence_number      BIGINT NOT NULL,
    full_document_number VARCHAR(30) NOT NULL,
    reason_code          VARCHAR(10) NOT NULL,
    reason_description   VARCHAR(255) NOT NULL,
    dian_status          VARCHAR(25) NOT NULL,
    cude                 VARCHAR(120),
    subtotal             NUMERIC(12,2) NOT NULL,
    tax_total            NUMERIC(12,2) NOT NULL,
    grand_total          NUMERIC(12,2) NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_credit_notes_consecutive UNIQUE (tenant_id, dian_prefix, sequence_number),
    CONSTRAINT ck_credit_note_reason CHECK (reason_code IN ('1', '2', '3')),
    CONSTRAINT ck_credit_note_status CHECK (dian_status IN ('PENDING_TRANSMISSION', 'ACCEPTED', 'REJECTED')),
    CONSTRAINT ck_credit_note_totals CHECK (subtotal >= 0 AND tax_total >= 0 AND grand_total >= 0),
    CONSTRAINT ck_credit_note_sequence CHECK (sequence_number >= 1)
);
CREATE INDEX idx_credit_notes_tenant_sale ON credit_notes (tenant_id, sale_id);
CREATE INDEX idx_credit_notes_tenant_created ON credit_notes (tenant_id, created_at DESC);

CREATE TABLE credit_note_items (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL REFERENCES tenants(id),
    credit_note_id UUID NOT NULL REFERENCES credit_notes(id) ON DELETE CASCADE,
    product_id    UUID NOT NULL REFERENCES products(id),
    quantity      NUMERIC(12,3) NOT NULL,
    unit_price    NUMERIC(12,2) NOT NULL,
    tax_amount    NUMERIC(12,2) NOT NULL,
    line_total    NUMERIC(12,2) NOT NULL,
    CONSTRAINT ck_credit_note_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_credit_note_item_totals CHECK (unit_price >= 0 AND tax_amount >= 0 AND line_total >= 0)
);
CREATE INDEX idx_credit_note_items_tenant_note ON credit_note_items (tenant_id, credit_note_id);

-- 6) Saldos iniciales del catálogo demo y umbrales de alerta.
INSERT INTO inventory_movements (
    id, tenant_id, product_id, movement_type, quantity, previous_stock, new_stock,
    reference_id, notes, created_by, created_at)
SELECT ('77777777-7777-7777-8888-' || lpad(suffix, 12, '0'))::uuid,
       tenant_id, id, 'INITIAL_BALANCE', current_stock, 0, current_stock,
       NULL, 'Saldo inicial al activar kardex', tenant_id, now()
FROM (
    SELECT id, tenant_id, current_stock, lpad(row_number() OVER (ORDER BY sku)::text, 12, '0') AS suffix
    FROM products
    WHERE tenant_id = '11111111-1111-1111-1111-111111111111'
) demo;

UPDATE products
   SET min_stock = CASE sku
       WHEN 'BEB-001' THEN 10
       WHEN 'PAN-001' THEN 8
       WHEN 'LAC-001' THEN 15
       WHEN 'HUE-001' THEN 12
       WHEN 'FRU-001' THEN 10
       ELSE 0
   END
 WHERE tenant_id = '11111111-1111-1111-1111-111111111111';
