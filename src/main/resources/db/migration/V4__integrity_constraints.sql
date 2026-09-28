-- =============================================================================
-- V4: database-level integrity rules that used to be enforced by the application only.
-- The application still validates first (to answer with a clear 4xx); these constraints guarantee the rules
-- even for data written by anything else (seed scripts, other clients, future code).
-- =============================================================================

-- Fixed vocabularies: the database stores the enum constant names.
ALTER TABLE products ADD CONSTRAINT ck_products_category
    CHECK (category IN ('FURNITURE', 'ELECTRONICS', 'BEAUTY', 'GARDEN'));
ALTER TABLE orders   ADD CONSTRAINT ck_orders_payment_method
    CHECK (payment_method IN ('CREDIT_CARD', 'XYZ_WALLET'));
ALTER TABLE orders   ADD CONSTRAINT ck_orders_status
    CHECK (status = 'COMPLETED');
ALTER TABLE users    ADD CONSTRAINT ck_users_role
    CHECK (role IN ('ADMIN', 'CUSTOMER'));

-- No blank names / usernames; discount codes are stored upper-case (the unique index is then case-insensitive).
ALTER TABLE products       ADD CONSTRAINT ck_products_name_not_blank  CHECK (btrim(name) <> '');
ALTER TABLE users          ADD CONSTRAINT ck_users_username_not_blank CHECK (btrim(username) <> '');
ALTER TABLE discount_codes ADD CONSTRAINT ck_discount_codes_code      CHECK (btrim(code) <> '' AND code = UPPER(code));

-- "Who created it / who last updated it" must point at a real user.
ALTER TABLE users          ADD CONSTRAINT fk_users_created_by          FOREIGN KEY (created_by) REFERENCES users(id);
ALTER TABLE users          ADD CONSTRAINT fk_users_updated_by          FOREIGN KEY (updated_by) REFERENCES users(id);
ALTER TABLE products       ADD CONSTRAINT fk_products_created_by       FOREIGN KEY (created_by) REFERENCES users(id);
ALTER TABLE products       ADD CONSTRAINT fk_products_updated_by       FOREIGN KEY (updated_by) REFERENCES users(id);
ALTER TABLE discount_codes ADD CONSTRAINT fk_discount_codes_created_by FOREIGN KEY (created_by) REFERENCES users(id);
ALTER TABLE discount_codes ADD CONSTRAINT fk_discount_codes_updated_by FOREIGN KEY (updated_by) REFERENCES users(id);
ALTER TABLE orders         ADD CONSTRAINT fk_orders_created_by         FOREIGN KEY (created_by) REFERENCES users(id);
ALTER TABLE orders         ADD CONSTRAINT fk_orders_updated_by         FOREIGN KEY (updated_by) REFERENCES users(id);

-- Order amounts are consistent: total = subtotal - discount, and a discount exists exactly when a code was used.
ALTER TABLE orders ADD CONSTRAINT ck_orders_totals
    CHECK (total_amount = subtotal_amount - discount_amount);
ALTER TABLE orders ADD CONSTRAINT ck_orders_discount_consistency
    CHECK ((discount_code_id IS NULL AND discount_amount = 0) OR (discount_code_id IS NOT NULL AND discount_amount > 0));

-- A discount code can be used by at most one order: "each code can be used only once", guaranteed by the database.
CREATE UNIQUE INDEX uq_orders_discount_code ON orders(discount_code_id) WHERE discount_code_id IS NOT NULL;

-- One line per product in an order.
ALTER TABLE order_items ADD CONSTRAINT uq_order_items_order_product UNIQUE (order_id, product_id);

-- The unique constraint above starts with order_id, so it also serves lookups by order: the separate index is redundant.
DROP INDEX IF EXISTS idx_order_items_order_id;
