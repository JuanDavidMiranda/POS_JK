-- V7: Onboarding, fiscal/SaaS tenant fields, DIAN terminal config, user auth fields, SUPER_ADMIN seed

-- 1) Tenants: fiscal data, SaaS lifecycle, onboarding tracking
ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS trade_name VARCHAR(150),
    ADD COLUMN IF NOT EXISTS tax_regime VARCHAR(40) NOT NULL DEFAULT 'NO_RESPONSABLE_IVA',
    ADD COLUMN IF NOT EXISTS fiscal_responsibilities VARCHAR(100) DEFAULT 'R-99-PN',
    ADD COLUMN IF NOT EXISTS city VARCHAR(80),
    ADD COLUMN IF NOT EXISTS address VARCHAR(200),
    ADD COLUMN IF NOT EXISTS phone VARCHAR(30),
    ADD COLUMN IF NOT EXISTS email VARCHAR(120),
    ADD COLUMN IF NOT EXISTS receipt_footer_message VARCHAR(255) DEFAULT 'Gracias por su compra',
    ADD COLUMN IF NOT EXISTS plan_code VARCHAR(30) NOT NULL DEFAULT 'TRIAL',
    ADD COLUMN IF NOT EXISTS trial_ends_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS subscription_ends_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS electronic_folios_limit INT NOT NULL DEFAULT 50,
    ADD COLUMN IF NOT EXISTS electronic_folios_used INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS onboarding_step INT NOT NULL DEFAULT 1;

ALTER TABLE tenants
    DROP CONSTRAINT IF EXISTS ck_tenants_tax_regime,
    ADD CONSTRAINT ck_tenants_tax_regime CHECK (tax_regime IN ('RESPONSABLE_IVA', 'NO_RESPONSABLE_IVA', 'REGIMEN_SIMPLE'));
ALTER TABLE tenants
    DROP CONSTRAINT IF EXISTS ck_tenants_plan_code,
    ADD CONSTRAINT ck_tenants_plan_code CHECK (plan_code IN ('TRIAL', 'BASIC', 'PRO', 'MULTI_BRANCH'));
ALTER TABLE tenants
    DROP CONSTRAINT IF EXISTS ck_tenants_onboarding_step,
    ADD CONSTRAINT ck_tenants_onboarding_step CHECK (onboarding_step BETWEEN 1 AND 4);
ALTER TABLE tenants
    DROP CONSTRAINT IF EXISTS ck_tenants_folios,
    ADD CONSTRAINT ck_tenants_folios CHECK (electronic_folios_limit >= 0 AND electronic_folios_used >= 0);

-- 2) pos_terminals: full DIAN resolution configuration
ALTER TABLE pos_terminals
    ADD COLUMN IF NOT EXISTS dian_resolution_number VARCHAR(60),
    ADD COLUMN IF NOT EXISTS dian_resolution_date DATE,
    ADD COLUMN IF NOT EXISTS dian_valid_from DATE,
    ADD COLUMN IF NOT EXISTS dian_valid_to DATE,
    ADD COLUMN IF NOT EXISTS dian_technical_key VARCHAR(120);

-- 3) users: auth fields and SUPER_ADMIN support (nullable tenant)
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email VARCHAR(120),
    ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);

ALTER TABLE users ALTER COLUMN tenant_id DROP NOT NULL;

-- replace unique constraint to support nullable tenant_id safely
ALTER TABLE users DROP CONSTRAINT IF EXISTS uq_users_tenant_username;
ALTER TABLE users
    ADD CONSTRAINT uq_users_tenant_username UNIQUE NULLS NOT DISTINCT (tenant_id, username);

-- allow SUPER_ADMIN role
ALTER TABLE users DROP CONSTRAINT IF EXISTS ck_users_role;
ALTER TABLE users
    ADD CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'CASHIER', 'SUPERVISOR', 'SUPER_ADMIN'));

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_lower ON users (lower(email)) WHERE email IS NOT NULL;

-- 4) Existing tenants already operating: mark onboarding as completed
UPDATE tenants
   SET onboarding_completed = TRUE,
       onboarding_step = 4
 WHERE id IN (SELECT DISTINCT tenant_id FROM pos_terminals);

-- 5) Seed SUPER_ADMIN (platform owner). Password: SuperAdmin123! (BCrypt)
CREATE EXTENSION IF NOT EXISTS pgcrypto;
INSERT INTO users (id, tenant_id, username, full_name, email, role, password_hash, active, updated_at, created_at)
VALUES (
    '99999999-9999-9999-9999-999999999999',
    NULL,
    'superadmin',
    'Super Administrador Plataforma',
    'superadmin@poscolombia.co',
    'SUPER_ADMIN',
    crypt('SuperAdmin123!', gen_salt('bf', 10)),
    TRUE,
    now(),
    now()
)
ON CONFLICT (id) DO UPDATE SET
    email = EXCLUDED.email,
    password_hash = EXCLUDED.password_hash,
    role = EXCLUDED.role,
    active = TRUE,
    updated_at = now();
