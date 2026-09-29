-- =============================================================================
-- V8: idx_products_category (category) is a prefix of idx_products_category_id (category, id) from V5
--
-- Every query the first one served (a category filter, its count, the ordered page, the keyset page) is served by the second:
-- measured on 200,000 products, the category count uses an index-only scan of it and the page an index scan, both as cheap as before.
-- Keeping both only made each product insert and update maintain two indexes.
-- =============================================================================

DROP INDEX IF EXISTS idx_products_category;
