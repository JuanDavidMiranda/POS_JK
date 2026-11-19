-- Datos demo. Los IDs son fijos para que el frontend pueda arrancar con el tenant de prueba.

INSERT INTO tenants (id, nit, business_name, subscription_status, created_at)
VALUES ('11111111-1111-1111-1111-111111111111', '900123456-7', 'Minimercado La Esquina', 'ACTIVE', now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO pos_terminals (id, tenant_id, name, dian_prefix, range_from, range_to,
                           last_synced_sequence, is_active, updated_at)
VALUES ('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111',
        'Caja 1', 'POS1', 1, 10000, 0, TRUE, now())
ON CONFLICT (tenant_id, dian_prefix) DO NOTHING;

INSERT INTO customers (id, tenant_id, document_type, document_number, full_name, email, updated_at)
VALUES ('44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111',
        'CF', '222222222222', 'Consumidor Final', NULL, now())
ON CONFLICT (tenant_id, document_type, document_number) DO NOTHING;

INSERT INTO products (id, tenant_id, barcode, sku, name, sale_price, tax_type, tax_rate,
                      current_stock, is_active, updated_at)
VALUES
    ('55555555-5555-5555-5555-000000000001', '11111111-1111-1111-1111-111111111111',
     '7700010000011', 'BEB-001', 'Agua cristal 600 ml', 2500.00, 'IVA', 0.1900, 48.000, TRUE, now()),
    ('55555555-5555-5555-5555-000000000002', '11111111-1111-1111-1111-111111111111',
     '7700010000028', 'PAN-001', 'Pan integral artesanal', 6800.00, 'IVA', 0.1900, 20.000, TRUE, now()),
    ('55555555-5555-5555-5555-000000000003', '11111111-1111-1111-1111-111111111111',
     '7700010000035', 'LAC-001', 'Leche entera 1 L', 6200.00, 'IVA', 0.1900, 15.000, TRUE, now()),
    ('55555555-5555-5555-5555-000000000004', '11111111-1111-1111-1111-111111111111',
     '7700010000042', 'HUE-001', 'Huevos campesinos docena', 12900.00, 'EXENTO', 0.0000, 10.000, TRUE, now()),
    ('55555555-5555-5555-5555-000000000005', '11111111-1111-1111-1111-111111111111',
     '7700010000059', 'FRU-001', 'Plátano verde (kg)', 3900.00, 'EXENTO', 0.0000, 30.000, TRUE, now())
ON CONFLICT (id) DO NOTHING;
