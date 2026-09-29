-- =============================================================================
-- V7: the money columns of an order hold every order the API accepts
--
-- A product price is at most 9,999,999,999.99 (10 digits before the decimal point, see ProductRequest) and an order holds at
-- most 1,000,000 units (100 lines of at most 10,000, see OrderItemRequest), so an order total can reach 9,999,999,999,990,000.00.
-- NUMERIC(12, 2) overflowed at 9,999,999,999.99: an order of 100 units of a product costing 1,000,000,000 answered 500.
-- Product and discount-code amounts keep NUMERIC(12, 2): they are bounded by the same 10 digits.
-- (Numbered 7 because the dev-only demo data uses V2 and V6 in db/dev; Flyway does not need consecutive versions.)
-- =============================================================================

ALTER TABLE orders
    ALTER COLUMN subtotal_amount TYPE NUMERIC(18, 2),
    ALTER COLUMN discount_amount TYPE NUMERIC(18, 2),
    ALTER COLUMN total_amount    TYPE NUMERIC(18, 2),
    ALTER COLUMN total_cost      TYPE NUMERIC(18, 2);

ALTER TABLE order_items
    ALTER COLUMN unit_price TYPE NUMERIC(18, 2),
    ALTER COLUMN unit_cost  TYPE NUMERIC(18, 2);
