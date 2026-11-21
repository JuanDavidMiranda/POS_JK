-- V8: SaaS billing (Wompi), subscription lifecycle support, accounting export config

CREATE TABLE IF NOT EXISTS saas_plans (
    code VARCHAR(30) PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    monthly_price_cop NUMERIC(12, 2) NOT NULL CHECK (monthly_price_cop >= 0),
    annual_price_cop NUMERIC(12, 2) NOT NULL CHECK (annual_price_cop >= 0),
    max_branches INT NOT NULL CHECK (max_branches > 0),
    max_terminals INT NOT NULL CHECK (max_terminals > 0),
    included_folios_per_month INT NOT NULL CHECK (included_folios_per_month >= 0),
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS folio_packages (
    code VARCHAR(30) PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    folios_count INT NOT NULL CHECK (folios_count > 0),
    price_cop NUMERIC(12, 2) NOT NULL CHECK (price_cop >= 0)
);

CREATE TABLE IF NOT EXISTS saas_payment_transactions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    reference_code VARCHAR(80) NOT NULL UNIQUE,
    gateway_transaction_id VARCHAR(100) UNIQUE,
    payment_type VARCHAR(30) NOT NULL CHECK (payment_type IN ('SUBSCRIPTION_MONTHLY', 'SUBSCRIPTION_ANNUAL', 'FOLIO_TOPUP')),
    item_code VARCHAR(30) NOT NULL,
    amount_cop NUMERIC(12, 2) NOT NULL CHECK (amount_cop >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'DECLINED', 'VOIDED', 'ERROR')),
    payment_method_type VARCHAR(40),
    raw_webhook_payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_saas_payments_tenant_created ON saas_payment_transactions(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_saas_payments_status ON saas_payment_transactions(status);
CREATE INDEX IF NOT EXISTS idx_saas_payments_gateway_tx ON saas_payment_transactions(gateway_transaction_id);

CREATE TABLE IF NOT EXISTS tenant_accounting_configs (
    tenant_id UUID PRIMARY KEY REFERENCES tenants(id),
    target_software VARCHAR(30) NOT NULL DEFAULT 'GENERIC_PUC' CHECK (target_software IN ('SIIGO', 'ALEGRA', 'WORLD_OFFICE', 'GENERIC_PUC')),
    sales_income_account VARCHAR(20) NOT NULL DEFAULT '413505',
    vat_generated_account VARCHAR(20) NOT NULL DEFAULT '240805',
    inc_generated_account VARCHAR(20) NOT NULL DEFAULT '249505',
    cash_account VARCHAR(20) NOT NULL DEFAULT '110505',
    bank_transfer_account VARCHAR(20) NOT NULL DEFAULT '111005',
    returns_account VARCHAR(20) NOT NULL DEFAULT '417505',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed plans (annual = 10 months, 2 months free)
INSERT INTO saas_plans (code, name, monthly_price_cop, annual_price_cop, max_branches, max_terminals, included_folios_per_month, is_active)
VALUES
    ('TRIAL', 'Trial', 0.00, 0.00, 1, 1, 50, TRUE),
    ('BASIC', 'Básico', 59000.00, 590000.00, 1, 1, 200, TRUE),
    ('PRO', 'Pro', 99000.00, 990000.00, 1, 3, 800, TRUE),
    ('MULTI_BRANCH', 'Multi-Sede', 169000.00, 1690000.00, 5, 10, 2500, TRUE)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    monthly_price_cop = EXCLUDED.monthly_price_cop,
    annual_price_cop = EXCLUDED.annual_price_cop,
    max_branches = EXCLUDED.max_branches,
    max_terminals = EXCLUDED.max_terminals,
    included_folios_per_month = EXCLUDED.included_folios_per_month,
    is_active = EXCLUDED.is_active;

INSERT INTO folio_packages (code, name, folios_count, price_cop)
VALUES
    ('FOLIOS_100', '100 Folios DIAN', 100, 15000.00),
    ('FOLIOS_500', '500 Folios DIAN', 500, 55000.00),
    ('FOLIOS_2000', '2000 Folios DIAN', 2000, 180000.00)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    folios_count = EXCLUDED.folios_count,
    price_cop = EXCLUDED.price_cop;

-- Ensure existing tenants have accounting config defaults
INSERT INTO tenant_accounting_configs (tenant_id)
SELECT t.id FROM tenants t
ON CONFLICT (tenant_id) DO NOTHING;
