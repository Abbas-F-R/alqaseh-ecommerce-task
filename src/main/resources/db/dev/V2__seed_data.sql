-- =============================================================================
-- Migration: V2__seed_data.sql
-- Description: DEV-ONLY demo products and discount codes (loaded via spring.flyway.locations in application-dev.yml)
-- =============================================================================

-- Seed Products (UUID Version 7, variant 2)
INSERT INTO products (id, name, category, price, cost, available_quantity, version, is_deleted, created_at)
VALUES
    ('01923450-0000-7000-8000-000000000001', 'Ergonomic Office Chair', 'FURNITURE', 150.00, 90.00, 12, 0, FALSE, CURRENT_TIMESTAMP),
    ('01923450-0000-7000-8000-000000000002', 'Oak Dining Table', 'FURNITURE', 450.00, 300.00, 3, 0, FALSE, CURRENT_TIMESTAMP),
    ('01923450-0000-7000-8000-000000000003', 'Smartphone Pro Max', 'ELECTRONICS', 1200.00, 850.00, 25, 0, FALSE, CURRENT_TIMESTAMP),
    ('01923450-0000-7000-8000-000000000004', 'Wireless Noise-Canceling Headphones', 'ELECTRONICS', 80.00, 45.00, 7, 0, FALSE, CURRENT_TIMESTAMP),
    ('01923450-0000-7000-8000-000000000005', 'Smart TV 55 Inch Ultra HD', 'ELECTRONICS', 600.00, 400.00, 0, 0, FALSE, CURRENT_TIMESTAMP),
    ('01923450-0000-7000-8000-000000000006', 'Organic Vitamin C Face Serum', 'BEAUTY', 35.00, 15.00, 8, 0, FALSE, CURRENT_TIMESTAMP),
    ('01923450-0000-7000-8000-000000000007', 'Hydrating Daily Moisturizer', 'BEAUTY', 25.00, 10.00, 15, 0, FALSE, CURRENT_TIMESTAMP),
    ('01923450-0000-7000-8000-000000000008', 'Lawn Mower Electric 1800W', 'GARDEN', 280.00, 190.00, 4, 0, FALSE, CURRENT_TIMESTAMP),
    ('01923450-0000-7000-8000-000000000009', 'Heavy Duty Garden Hose 50ft', 'GARDEN', 30.00, 14.00, 20, 0, FALSE, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Seed Discount Codes (UUID Version 7, variant 2)
INSERT INTO discount_codes (id, code, amount, minimum_order_total, expires_at, used, is_deleted, created_at)
VALUES
    ('01923450-0000-7000-8000-000000000011', 'WELCOME10', 10.00, 50.00, '2035-01-01 00:00:00+00', FALSE, FALSE, CURRENT_TIMESTAMP),
    ('01923450-0000-7000-8000-000000000012', 'EXPIRED50', 50.00, 100.00, '2024-01-01 00:00:00+00', FALSE, FALSE, CURRENT_TIMESTAMP),
    ('01923450-0000-7000-8000-000000000013', 'USED25', 25.00, 60.00, '2035-01-01 00:00:00+00', TRUE, FALSE, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
