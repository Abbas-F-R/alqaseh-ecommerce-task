-- =============================================================================
-- Migration: V1__init_schema.sql
-- Description: Production-grade schema for Al Qaseh E-Commerce REST API
-- Features: RFC 9562 UUID v7 primary keys, Spring Data JPA Auditing,
--           Hibernate 6 soft-delete, named CHECK constraints, and strategic indexes.
-- Tables: users, products, discount_codes, orders, order_items, audit_logs
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. USERS TABLE
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_users_username UNIQUE (username)
);

CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_is_deleted ON users(is_deleted);

-- -----------------------------------------------------------------------------
-- 2. PRODUCTS TABLE
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS products (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    category VARCHAR(50) NOT NULL,
    price NUMERIC(12, 2) NOT NULL,
    cost NUMERIC(12, 2) NOT NULL,
    available_quantity INT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_products_price CHECK (price >= 0),
    CONSTRAINT ck_products_cost CHECK (cost >= 0),
    CONSTRAINT ck_products_available_quantity CHECK (available_quantity >= 0)
);

-- Partial unique index: enforces name uniqueness among ACTIVE products,
-- while allowing reuse of product names once a product is soft-deleted.
CREATE UNIQUE INDEX IF NOT EXISTS uq_products_active_name ON products(LOWER(name)) WHERE is_deleted = FALSE;

-- Functional indexes for access patterns
CREATE INDEX IF NOT EXISTS idx_products_category ON products(category);
CREATE INDEX IF NOT EXISTS idx_products_name ON products(name);
CREATE INDEX IF NOT EXISTS idx_products_is_deleted ON products(is_deleted);

-- -----------------------------------------------------------------------------
-- 3. DISCOUNT CODES TABLE
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS discount_codes (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    minimum_order_total NUMERIC(12, 2) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_discount_codes_amount CHECK (amount > 0),
    CONSTRAINT ck_discount_codes_minimum_order_total CHECK (minimum_order_total >= 0)
);

-- Partial unique index: ensures active discount codes are unique
CREATE UNIQUE INDEX IF NOT EXISTS uq_discount_codes_active_code ON discount_codes(UPPER(code)) WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_discount_codes_code ON discount_codes(code);
CREATE INDEX IF NOT EXISTS idx_discount_codes_is_deleted ON discount_codes(is_deleted);

-- -----------------------------------------------------------------------------
-- 4. ORDERS TABLE
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS orders (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    subtotal_amount NUMERIC(12, 2) NOT NULL,
    discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    total_amount NUMERIC(12, 2) NOT NULL,
    total_cost NUMERIC(12, 2) NOT NULL,
    discount_code_id UUID,
    payment_method VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES users(id),
    CONSTRAINT fk_orders_discount_code FOREIGN KEY (discount_code_id) REFERENCES discount_codes(id),
    CONSTRAINT ck_orders_subtotal_amount CHECK (subtotal_amount >= 0),
    CONSTRAINT ck_orders_discount_amount CHECK (discount_amount >= 0),
    CONSTRAINT ck_orders_total_amount CHECK (total_amount >= 0),
    CONSTRAINT ck_orders_total_cost CHECK (total_cost >= 0)
);

-- Indexes for Order query patterns
CREATE INDEX IF NOT EXISTS idx_orders_customer_id ON orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders(created_at);
CREATE INDEX IF NOT EXISTS idx_orders_payment_method ON orders(payment_method);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_is_deleted ON orders(is_deleted);
CREATE INDEX IF NOT EXISTS idx_orders_customer_created ON orders(customer_id, created_at DESC);

-- -----------------------------------------------------------------------------
-- 5. ORDER ITEMS TABLE (Contains immutable snapshot of price & cost)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    product_id UUID NOT NULL,
    product_name VARCHAR(150) NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    unit_cost NUMERIC(12, 2) NOT NULL,
    quantity INT NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_items_unit_price CHECK (unit_price >= 0),
    CONSTRAINT ck_order_items_unit_cost CHECK (unit_cost >= 0)
);

-- Foreign key indexes (vital for performance on joins & cascades)
CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items(order_id);
CREATE INDEX IF NOT EXISTS idx_order_items_product_id ON order_items(product_id);

-- -----------------------------------------------------------------------------
-- 6. AUDIT LOGS TABLE
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(50) NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    details TEXT
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp ON audit_logs(timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action ON audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
