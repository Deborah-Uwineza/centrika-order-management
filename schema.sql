-- =====================================================================
-- Centrika Order Management System — Schema (PostgreSQL 15)
-- =====================================================================
-- Design notes (see DESIGN.md for the full rationale):
--   * BIGINT identity PKs are used instead of UUIDs. At tens of millions
--     of rows, sequential bigints keep B-tree indexes smaller and better
--     ordered (less random-write bloat) than UUIDv4, and joins on 8-byte
--     ints are cheaper than on 16-byte UUIDs. We lose "generate the ID
--     client-side" convenience, which we don't need here.
--   * Enum-like columns (tier, status) use VARCHAR + CHECK instead of a
--     native Postgres ENUM type, because adding a new status value later
--     only needs a constraint change, not an ALTER TYPE that locks the
--     table and requires care in production.
--   * order_items.unit_price is a deliberate denormalisation: it freezes
--     the price at the moment of purchase so historical revenue figures
--     never change if products.unit_price is edited later.
-- =====================================================================

CREATE TABLE customers (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(150)  NOT NULL,
    email       VARCHAR(255)  NOT NULL,
    region      VARCHAR(100)  NOT NULL,
    tier        VARCHAR(20)   NOT NULL DEFAULT 'standard',
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_customers_email UNIQUE (email),
    CONSTRAINT chk_customers_tier CHECK (tier IN ('standard', 'premium', 'enterprise'))
);

CREATE TABLE products (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(200)   NOT NULL,
    sku             VARCHAR(64)    NOT NULL,
    category        VARCHAR(100)   NOT NULL,
    unit_price      NUMERIC(12,2)  NOT NULL,
    stock_quantity  INTEGER        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT uq_products_sku UNIQUE (sku),
    CONSTRAINT chk_products_unit_price_nonneg CHECK (unit_price >= 0),
    CONSTRAINT chk_products_stock_nonneg CHECK (stock_quantity >= 0)
);

CREATE TABLE orders (
    id           BIGSERIAL PRIMARY KEY,
    customer_id  BIGINT       NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'pending',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id)
        REFERENCES customers (id) ON DELETE RESTRICT,
    CONSTRAINT chk_orders_status CHECK (
        status IN ('pending', 'processing', 'shipped', 'delivered', 'cancelled')
    )
);

CREATE TABLE order_items (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT        NOT NULL,
    product_id  BIGINT        NOT NULL,
    quantity    INTEGER       NOT NULL,
    unit_price  NUMERIC(12,2) NOT NULL, -- price captured AT THE TIME OF PURCHASE

    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id)
        REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id)
        REFERENCES products (id) ON DELETE RESTRICT,
    CONSTRAINT chk_order_items_qty_positive CHECK (quantity > 0),
    CONSTRAINT chk_order_items_price_nonneg CHECK (unit_price >= 0)
);

-- ---------------------------------------------------------------------
-- Indexing strategy
-- ---------------------------------------------------------------------
-- orders is the hottest table: it's filtered by customer, by status, by
-- date range, and almost always ordered by created_at. We index for the
-- three query shapes we actually expect (see queries.sql and the
-- GET /api/orders endpoint), rather than indexing every column blindly.

-- Covers "orders for customer X, most recent first" and the revenue
-- queries that filter by customer_id + created_at range.
CREATE INDEX idx_orders_customer_created_at ON orders (customer_id, created_at DESC);

-- Covers "orders with status X in date range Y" — used by both the
-- GET /api/orders?status=&from=&to= endpoint and monthly trend queries.
CREATE INDEX idx_orders_status_created_at ON orders (status, created_at DESC);

-- Covers plain "orders in the last N days" scans (no customer/status
-- filter) and general created_at range queries / sorting.
CREATE INDEX idx_orders_created_at ON orders (created_at DESC);

-- order_items is always joined via order_id or product_id — never
-- scanned on its own, so these are the only two indexes it needs.
CREATE INDEX idx_order_items_order_id ON order_items (order_id);
CREATE INDEX idx_order_items_product_id ON order_items (product_id);

-- Supports the "low stock AND recently ordered" query (Part 1, Query 2)
-- as a fast pre-filter before the join to order_items.
CREATE INDEX idx_products_low_stock ON products (stock_quantity) WHERE stock_quantity < 20;

-- Supports category filtering / reporting if added later.
CREATE INDEX idx_products_category ON products (category);

-- Supports grouping revenue by tier (Part 1, Query 3) and any
-- tier-based customer filtering.
CREATE INDEX idx_customers_tier ON customers (tier);

-- ---------------------------------------------------------------------
-- Keep updated_at fresh automatically
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_customers_updated_at
    BEFORE UPDATE ON customers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_products_updated_at
    BEFORE UPDATE ON products
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_orders_updated_at
    BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
