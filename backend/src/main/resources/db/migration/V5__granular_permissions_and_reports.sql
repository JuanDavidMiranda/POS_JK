-- MVP 2 Fase 2.3: Permisos granulares con PIN offline, turnos de caja y reportes.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1) Usuarios (para autorización/auditoría y PIN de supervisor).
CREATE TABLE IF NOT EXISTS users (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    username            VARCHAR(80) NOT NULL,
    full_name           VARCHAR(200) NOT NULL,
    role                VARCHAR(20) NOT NULL,
    supervisor_pin_hash VARCHAR(255),
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_tenant_username UNIQUE (tenant_id, username),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'CASHIER', 'SUPERVISOR'))
);
CREATE INDEX IF NOT EXISTS idx_users_tenant_role_active ON users (tenant_id, role, active);

-- Usuario admin demo (PIN "1234", hash SHA-256(tenant_id + ":" + pin) en hex).
INSERT INTO users (id, tenant_id, username, full_name, role, supervisor_pin_hash, active, updated_at, created_at)
VALUES (
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    '11111111-1111-1111-1111-111111111111',
    'admin',
    'Administrador Demo',
    'ADMIN',
    encode(digest('11111111-1111-1111-1111-111111111111:1234', 'sha256'), 'hex'),
    TRUE,
    now(),
    now()
)
ON CONFLICT (id) DO UPDATE SET
    supervisor_pin_hash = EXCLUDED.supervisor_pin_hash,
    updated_at = now(),
    active = TRUE;

-- 2) Políticas de seguridad por tenant.
CREATE TABLE IF NOT EXISTS tenant_security_policies (
    tenant_id                     UUID PRIMARY KEY REFERENCES tenants(id),
    require_pin_for_discounts     BOOLEAN NOT NULL DEFAULT TRUE,
    max_cashier_discount_percent  NUMERIC(5, 2) NOT NULL DEFAULT 0.00,
    require_pin_for_void_item     BOOLEAN NOT NULL DEFAULT TRUE,
    blind_cash_close              BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at                    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_policies_max_discount CHECK (max_cashier_discount_percent >= 0 AND max_cashier_discount_percent <= 100)
);

INSERT INTO tenant_security_policies (tenant_id, require_pin_for_discounts, max_cashier_discount_percent, require_pin_for_void_item, blind_cash_close, updated_at)
VALUES ('11111111-1111-1111-1111-111111111111', TRUE, 0.00, TRUE, TRUE, now())
ON CONFLICT (tenant_id) DO NOTHING;

-- 3) Turnos de caja (cierre ciego/auditable).
CREATE TABLE IF NOT EXISTS cash_shifts (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL REFERENCES tenants(id),
    terminal_id           UUID REFERENCES pos_terminals(id),
    opened_by             UUID REFERENCES users(id),
    closed_by             UUID REFERENCES users(id),
    authorized_by_user_id UUID REFERENCES users(id),
    status                VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    opening_amount        NUMERIC(14, 2) NOT NULL DEFAULT 0,
    expected_cash_amount  NUMERIC(14, 2),
    counted_cash_amount   NUMERIC(14, 2),
    difference_amount     NUMERIC(14, 2),
    opened_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at             TIMESTAMPTZ,
    notes                 VARCHAR(255),
    CONSTRAINT ck_cash_shifts_status CHECK (status IN ('OPEN', 'CLOSED'))
);
CREATE INDEX IF NOT EXISTS idx_cash_shifts_tenant_status_opened ON cash_shifts (tenant_id, status, opened_at DESC);
CREATE INDEX IF NOT EXISTS idx_cash_shifts_tenant_closed ON cash_shifts (tenant_id, closed_at DESC);

-- 4) Auditoría/descuentos en ventas.
ALTER TABLE sales
    ADD COLUMN IF NOT EXISTS discount_total NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS authorized_by UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS cash_shift_id UUID REFERENCES cash_shifts(id);

CREATE INDEX IF NOT EXISTS idx_sales_tenant_sold_at_payment ON sales (tenant_id, sold_at DESC, payment_method);
CREATE INDEX IF NOT EXISTS idx_sales_cash_shift ON sales (cash_shift_id);

-- Asegurar precisión solicitada para auditoría/descuentos.
ALTER TABLE sales ALTER COLUMN discount_total TYPE NUMERIC(12, 2);
ALTER TABLE sales ALTER COLUMN discount_total SET NOT NULL;
ALTER TABLE sales ALTER COLUMN discount_total SET DEFAULT 0;

-- Índices de apoyo para reportes (ventas/NC).
CREATE INDEX IF NOT EXISTS idx_sale_items_tenant_product ON sale_items (tenant_id, product_id);
CREATE INDEX IF NOT EXISTS idx_credit_notes_tenant_created_sale ON credit_notes (tenant_id, created_at DESC, sale_id);
