-- =============================================================================
-- V9: integrity rules of a product that the API already enforces, now also enforced by the database
--
--   * the name has no leading or trailing whitespace (the API stores it trimmed): otherwise " Chair" and "Chair" would both pass
--     the unique index on lower(name) and look like two different products;
--   * the price is greater than 0 (ck_products_price allowed a free product, which the API no longer accepts);
--   * the cost never exceeds the price: a product is not sold below what it costs (ProductRequest);
--   * the stock is at most 1,000,000 units (ProductRequest.MAX_QUANTITY);
--   * "last updated" never precedes "created".
--
-- Every constraint is added NOT VALID (enforced for each new or changed row at once) and validated right away when the existing rows
-- satisfy it. A database that already holds a row that breaks a rule keeps starting: that row stays as it is until it is corrected, and
--     ALTER TABLE products VALIDATE CONSTRAINT <name>;
-- validates the constraint afterwards. On a new database nothing is left NOT VALID.
-- =============================================================================

ALTER TABLE products DROP CONSTRAINT ck_products_price;
ALTER TABLE products ADD CONSTRAINT ck_products_price          CHECK (price > 0) NOT VALID;
ALTER TABLE products ADD CONSTRAINT ck_products_cost_within_price CHECK (cost <= price) NOT VALID;
ALTER TABLE products ADD CONSTRAINT ck_products_name_trimmed   CHECK (name = btrim(name)) NOT VALID;
ALTER TABLE products ADD CONSTRAINT ck_products_quantity_max   CHECK (available_quantity <= 1000000) NOT VALID;
ALTER TABLE products ADD CONSTRAINT ck_products_updated_after_created CHECK (updated_at IS NULL OR updated_at >= created_at) NOT VALID;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM products WHERE price <= 0) THEN
        ALTER TABLE products VALIDATE CONSTRAINT ck_products_price;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM products WHERE cost > price) THEN
        ALTER TABLE products VALIDATE CONSTRAINT ck_products_cost_within_price;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM products WHERE name <> btrim(name)) THEN
        ALTER TABLE products VALIDATE CONSTRAINT ck_products_name_trimmed;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM products WHERE available_quantity > 1000000) THEN
        ALTER TABLE products VALIDATE CONSTRAINT ck_products_quantity_max;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM products WHERE updated_at IS NOT NULL AND updated_at < created_at) THEN
        ALTER TABLE products VALIDATE CONSTRAINT ck_products_updated_after_created;
    END IF;
END $$;
