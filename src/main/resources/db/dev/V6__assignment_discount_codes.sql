-- =============================================================================
-- Migration: V6__assignment_discount_codes.sql
-- Description: DEV-ONLY demo discount codes with the values used as examples in the assignment
-- (added after V2 so that databases that already applied V2 keep a valid Flyway history)
-- =============================================================================

INSERT INTO discount_codes (id, code, amount, minimum_order_total, expires_at, used, created_at)
VALUES
    ('01923450-0000-7000-8000-000000000014', 'ABC123', 5000.00, 25000.00, '2035-01-01 00:00:00+00', FALSE, CURRENT_TIMESTAMP),
    ('01923450-0000-7000-8000-000000000015', 'ZYX123', 10000.00, 50000.00, '2035-01-01 00:00:00+00', FALSE, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
