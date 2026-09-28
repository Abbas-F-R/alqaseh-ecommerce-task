-- =============================================================================
-- Migration: V3__review_hardening.sql
-- Description: Concurrency guard for discount redemption, removal of the unused soft-delete
--              column and index cleanup driven by the actual query patterns.
--              V1/V2 are left untouched (already-applied migrations must never change).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Discount codes: optimistic locking so one code can never be redeemed twice concurrently
-- -----------------------------------------------------------------------------
ALTER TABLE discount_codes ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- -----------------------------------------------------------------------------
-- 2. Soft delete is not used by the application (there is no delete operation), so the flag and the
--    "WHERE is_deleted = FALSE" partial indexes go away. Uniqueness becomes plain unique indexes.
--    Discount lookups use the upper-cased code with "WHERE code = ?", but V1 indexed UPPER(code),
--    which that query cannot use: normalise the data and index the code itself.
--    (Safe: V1 already guaranteed uniqueness of LOWER(name) / UPPER(code).)
-- -----------------------------------------------------------------------------
UPDATE discount_codes SET code = UPPER(code);

DROP INDEX IF EXISTS uq_products_active_name;
DROP INDEX IF EXISTS uq_discount_codes_active_code;
DROP INDEX IF EXISTS idx_discount_codes_code;
DROP INDEX IF EXISTS idx_users_is_deleted;
DROP INDEX IF EXISTS idx_products_is_deleted;
DROP INDEX IF EXISTS idx_discount_codes_is_deleted;
DROP INDEX IF EXISTS idx_orders_is_deleted;

ALTER TABLE users          DROP COLUMN is_deleted;
ALTER TABLE products       DROP COLUMN is_deleted;
ALTER TABLE discount_codes DROP COLUMN is_deleted;
ALTER TABLE orders         DROP COLUMN is_deleted;

CREATE UNIQUE INDEX uq_products_name ON products(LOWER(name));
CREATE UNIQUE INDEX uq_discount_codes_code ON discount_codes(code);

-- -----------------------------------------------------------------------------
-- 3. Drop indexes that no query can use (each one only slows down writes)
-- -----------------------------------------------------------------------------
-- Duplicates of a constraint/index that already exists.
DROP INDEX IF EXISTS idx_users_username;         -- uq_users_username
DROP INDEX IF EXISTS idx_products_name;          -- uq_products_name; LIKE '%x%' cannot use a b-tree anyway
DROP INDEX IF EXISTS idx_orders_customer_id;     -- prefix of idx_orders_customer_created

-- Columns that are never filtered on.
DROP INDEX IF EXISTS idx_orders_status;          -- only one status is ever written
DROP INDEX IF EXISTS idx_order_items_product_id; -- products are never deleted or joined from order_items

-- Audit log is write-only from the application; keep just the "history of one entity" lookup.
DROP INDEX IF EXISTS idx_audit_logs_timestamp;
DROP INDEX IF EXISTS idx_audit_logs_action;
DROP INDEX IF EXISTS idx_audit_logs_user_id;

-- -----------------------------------------------------------------------------
-- 4. Admin order listing filtered by payment method, newest first
--    (payment_method alone has 2 values; combined with created_at it serves filter + sort + LIMIT).
-- -----------------------------------------------------------------------------
DROP INDEX IF EXISTS idx_orders_payment_method;
CREATE INDEX idx_orders_payment_created ON orders(payment_method, created_at DESC);
