-- Migration: V5__products_keyset_index.sql
-- Description: Composite index to optimize keyset / seek pagination on (category, id)

CREATE INDEX IF NOT EXISTS idx_products_category_id ON products(category, id);
