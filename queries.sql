
-- Centrika Order Management System — Required Queries (Part 1)

-- Query 1: Top 10 customers by total revenue in the last 90 days

SELECT
    c.id                                    AS customer_id,
    c.name                                  AS customer_name,
    c.tier                                  AS customer_tier,
    SUM(oi.quantity * oi.unit_price)::NUMERIC(14,2) AS total_revenue,
    COUNT(DISTINCT o.id)                    AS order_count
FROM customers c
JOIN orders o        ON o.customer_id = c.id
JOIN order_items oi  ON oi.order_id   = o.id
WHERE o.created_at >= now() - INTERVAL '90 days'
  AND o.status <> 'cancelled'
GROUP BY c.id, c.name, c.tier
ORDER BY total_revenue DESC
LIMIT 10;



-- Query 2: Products with stock below 20 units that have had at least
-- one order in the last 30 days

SELECT
    p.id,
    p.name,
    p.sku,
    p.category,
    p.stock_quantity
FROM products p
WHERE p.stock_quantity < 20
  AND EXISTS (
      SELECT 1
      FROM order_items oi
      JOIN orders o ON o.id = oi.order_id
      WHERE oi.product_id = p.id
        AND o.created_at >= now() - INTERVAL '30 days'
        AND o.status <> 'cancelled'
  )
ORDER BY p.stock_quantity ASC;



-- Query 3: Monthly revenue trend for the past 12 months, broken down
-- by customer tier


SELECT
    date_trunc('month', o.created_at)               AS revenue_month,
    c.tier                                           AS customer_tier,
    SUM(oi.quantity * oi.unit_price)::NUMERIC(14,2)  AS total_revenue,
    COUNT(DISTINCT o.id)                             AS order_count
FROM orders o
JOIN customers c     ON c.id = o.customer_id
JOIN order_items oi  ON oi.order_id = o.id
WHERE o.created_at >= date_trunc('month', now()) - INTERVAL '11 months'
  AND o.status <> 'cancelled'
GROUP BY revenue_month, c.tier
ORDER BY revenue_month ASC, c.tier ASC;
