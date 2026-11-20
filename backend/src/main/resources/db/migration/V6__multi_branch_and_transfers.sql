-- MVP 3.1: Multi-sucursal, inventario por sede y traslados (retrocompatible)

-- 1) Nuevas tablas

CREATE TABLE IF NOT EXISTS branches (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    code VARCHAR(30) NOT NULL,
    name VARCHAR(120) NOT NULL,
    address VARCHAR(255),
    city VARCHAR(120),
    phone VARCHAR(40),
    is_main BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_branches_tenant_code UNIQUE (tenant_id, code)
);

CREATE INDEX IF NOT EXISTS idx_branches_tenant_active ON branches(tenant_id, is_active);

CREATE TABLE IF NOT EXISTS branch_inventory (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    branch_id UUID NOT NULL REFERENCES branches(id),
    product_id UUID NOT NULL REFERENCES products(id),
    current_stock NUMERIC(12, 3) NOT NULL DEFAULT 0,
    min_stock NUMERIC(12, 3) NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_branch_inventory_tenant_branch_product UNIQUE (tenant_id, branch_id, product_id)
);

CREATE INDEX IF NOT EXISTS idx_branch_inventory_tenant_branch_updated
    ON branch_inventory(tenant_id, branch_id, updated_at);

CREATE TABLE IF NOT EXISTS inventory_transfers (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    transfer_number VARCHAR(40) NOT NULL,
    source_branch_id UUID NOT NULL REFERENCES branches(id),
    destination_branch_id UUID NOT NULL REFERENCES branches(id),
    status VARCHAR(20) NOT NULL,
    notes VARCHAR(500),
    created_by UUID,
    received_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    dispatched_at TIMESTAMP,
    received_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    CONSTRAINT uq_inventory_transfers_tenant_number UNIQUE (tenant_id, transfer_number),
    CONSTRAINT ck_inventory_transfers_status CHECK (status IN ('DRAFT', 'IN_TRANSIT', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_inventory_transfers_branches CHECK (source_branch_id <> destination_branch_id)
);

CREATE INDEX IF NOT EXISTS idx_inventory_transfers_tenant_status_created
    ON inventory_transfers(tenant_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_inventory_transfers_tenant_source
    ON inventory_transfers(tenant_id, source_branch_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_inventory_transfers_tenant_destination
    ON inventory_transfers(tenant_id, destination_branch_id, created_at DESC);

CREATE TABLE IF NOT EXISTS inventory_transfer_items (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    transfer_id UUID NOT NULL REFERENCES inventory_transfers(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id),
    quantity NUMERIC(12, 3) NOT NULL,
    CONSTRAINT ck_inventory_transfer_items_qty CHECK (quantity > 0)
);

CREATE INDEX IF NOT EXISTS idx_inventory_transfer_items_transfer
    ON inventory_transfer_items(transfer_id);

-- 2) Columnas branch_id en tablas operativas (nullable para backfill)

ALTER TABLE pos_terminals ADD COLUMN IF NOT EXISTS branch_id UUID;
ALTER TABLE sales ADD COLUMN IF NOT EXISTS branch_id UUID;
ALTER TABLE cash_shifts ADD COLUMN IF NOT EXISTS branch_id UUID;
ALTER TABLE inventory_movements ADD COLUMN IF NOT EXISTS branch_id UUID;
ALTER TABLE credit_notes ADD COLUMN IF NOT EXISTS branch_id UUID;

-- 3) Sede principal por tenant existente (idempotente)

INSERT INTO branches (id, tenant_id, code, name, address, city, phone, is_main, is_active, created_at, updated_at)
SELECT
    md5(t.id::text || ':MAIN')::uuid,
    t.id,
    'MAIN',
    'Sede Principal',
    NULL,
    NULL,
    NULL,
    TRUE,
    TRUE,
    now(),
    now()
FROM tenants t
WHERE NOT EXISTS (
    SELECT 1 FROM branches b WHERE b.tenant_id = t.id AND b.code = 'MAIN'
);

-- Asegura exactamente una sede principal marcada por tenant (si ya existiera alguna otra marcada)
UPDATE branches b
SET is_main = FALSE, updated_at = now()
WHERE b.is_main = TRUE
  AND b.code <> 'MAIN';

UPDATE branches b
SET is_main = TRUE, updated_at = now()
WHERE b.code = 'MAIN';

-- 4) Copiar stock de productos a branch_inventory de la sede principal (sin pérdida)
-- Condicional para permitir re-ejecución idempotente cuando las columnas ya fueron eliminadas.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'products'
          AND column_name = 'current_stock'
    ) THEN
        EXECUTE $ins$
            INSERT INTO branch_inventory (id, tenant_id, branch_id, product_id, current_stock, min_stock, updated_at)
            SELECT
                md5(p.tenant_id::text || ':' || mb.id::text || ':' || p.id::text)::uuid,
                p.tenant_id,
                mb.id,
                p.id,
                COALESCE(p.current_stock, 0),
                COALESCE(p.min_stock, 0),
                COALESCE(p.updated_at, now())
            FROM products p
            JOIN branches mb ON mb.tenant_id = p.tenant_id AND mb.code = 'MAIN'
            ON CONFLICT (tenant_id, branch_id, product_id) DO UPDATE
            SET current_stock = EXCLUDED.current_stock,
                min_stock = EXCLUDED.min_stock,
                updated_at = EXCLUDED.updated_at
        $ins$;
    END IF;
END $$;

-- 5) Backfill branch_id en tablas operativas hacia la sede principal del tenant

UPDATE pos_terminals pt
SET branch_id = mb.id
FROM branches mb
WHERE mb.tenant_id = pt.tenant_id AND mb.code = 'MAIN' AND pt.branch_id IS NULL;

UPDATE sales s
SET branch_id = mb.id
FROM branches mb
WHERE mb.tenant_id = s.tenant_id AND mb.code = 'MAIN' AND s.branch_id IS NULL;

UPDATE cash_shifts cs
SET branch_id = mb.id
FROM branches mb
WHERE mb.tenant_id = cs.tenant_id AND mb.code = 'MAIN' AND cs.branch_id IS NULL;

UPDATE inventory_movements im
SET branch_id = mb.id
FROM branches mb
WHERE mb.tenant_id = im.tenant_id AND mb.code = 'MAIN' AND im.branch_id IS NULL;

UPDATE credit_notes cn
SET branch_id = mb.id
FROM branches mb
WHERE mb.tenant_id = cn.tenant_id AND mb.code = 'MAIN' AND cn.branch_id IS NULL;

-- 6) Constraints NOT NULL y FKs de branch_id

ALTER TABLE pos_terminals ALTER COLUMN branch_id SET NOT NULL;
ALTER TABLE sales ALTER COLUMN branch_id SET NOT NULL;
ALTER TABLE cash_shifts ALTER COLUMN branch_id SET NOT NULL;
ALTER TABLE inventory_movements ALTER COLUMN branch_id SET NOT NULL;
ALTER TABLE credit_notes ALTER COLUMN branch_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_pos_terminals_branch') THEN
        ALTER TABLE pos_terminals ADD CONSTRAINT fk_pos_terminals_branch FOREIGN KEY (branch_id) REFERENCES branches(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_sales_branch') THEN
        ALTER TABLE sales ADD CONSTRAINT fk_sales_branch FOREIGN KEY (branch_id) REFERENCES branches(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_cash_shifts_branch') THEN
        ALTER TABLE cash_shifts ADD CONSTRAINT fk_cash_shifts_branch FOREIGN KEY (branch_id) REFERENCES branches(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_inventory_movements_branch') THEN
        ALTER TABLE inventory_movements ADD CONSTRAINT fk_inventory_movements_branch FOREIGN KEY (branch_id) REFERENCES branches(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_credit_notes_branch') THEN
        ALTER TABLE credit_notes ADD CONSTRAINT fk_credit_notes_branch FOREIGN KEY (branch_id) REFERENCES branches(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_pos_terminals_tenant_branch ON pos_terminals(tenant_id, branch_id);
CREATE INDEX IF NOT EXISTS idx_sales_tenant_branch_sold_at ON sales(tenant_id, branch_id, sold_at);
CREATE INDEX IF NOT EXISTS idx_cash_shifts_tenant_branch_opened ON cash_shifts(tenant_id, branch_id, opened_at DESC);
CREATE INDEX IF NOT EXISTS idx_inventory_movements_tenant_branch_created ON inventory_movements(tenant_id, branch_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_credit_notes_tenant_branch_created ON credit_notes(tenant_id, branch_id, created_at DESC);

-- 7) Kardex: permitir tipos de traslado

ALTER TABLE inventory_movements DROP CONSTRAINT IF EXISTS ck_inventory_movement_type;
ALTER TABLE inventory_movements DROP CONSTRAINT IF EXISTS ck_inventory_movements_type;
ALTER TABLE inventory_movements
    ADD CONSTRAINT ck_inventory_movements_type
    CHECK (movement_type IN ('INITIAL_BALANCE','PURCHASE_IN','SALE_OUT','CREDIT_NOTE_IN','ADJUSTMENT_IN','ADJUSTMENT_OUT','TRANSFER_OUT','TRANSFER_IN'));

-- 8) Deprecar columnas de stock en products (stock físico pasa a branch_inventory)

ALTER TABLE products DROP COLUMN IF EXISTS current_stock;
ALTER TABLE products DROP COLUMN IF EXISTS min_stock;
ALTER TABLE products DROP COLUMN IF EXISTS allow_negative_stock;
