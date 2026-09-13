# DESIGN.md

## Part 1 — Indexing strategy & denormalisation

**Indexing.** `orders` is the table almost every query touches, and it's
always filtered by some combination of `customer_id`, `status`, and a
`created_at` range, usually sorted by `created_at DESC`. Rather than
indexing every column individually, `schema.sql` adds two composite
indexes — `(customer_id, created_at DESC)` and `(status, created_at DESC)`
— because Postgres can use a composite index for the leading column
alone *or* both columns together, so these two cover the large majority
of realistic query shapes (a customer's order history, orders in a
given status over time, or both combined) without needing a separate
index per column. A plain `(created_at DESC)` index backs unfiltered
date-range scans. `order_items` only ever appears joined via `order_id`
or `product_id`, so it gets exactly those two indexes and nothing more.
For the low-stock query, a **partial index** (`WHERE stock_quantity < 20`)
keeps the index tiny regardless of catalogue size, since it only
indexes the small slice of products that are actually low on stock.

**Denormalisation.** The one deliberate denormalisation is
`order_items.unit_price`: it's a snapshot of the product's price at the
moment of purchase, not a live reference to `products.unit_price`. This
matters because catalogue prices change over time — without this
snapshot, editing a product's price would silently rewrite the total of
every historical order that ever included it, which would corrupt
financial reporting. The extra 2 bytes-ish of storage per line item is
a trivial cost next to that correctness guarantee.

A smaller decision: enum-like columns (`tier`, `status`) use
`VARCHAR + CHECK` rather than native Postgres `ENUM` types. Native enums
are marginally more storage-efficient, but adding a new value later
requires `ALTER TYPE ... ADD VALUE`, which historically couldn't run
inside a transaction and can behave awkwardly with concurrent readers.
A `CHECK` constraint is a plain `ALTER TABLE ... DROP CONSTRAINT` /
`ADD CONSTRAINT` — safer to evolve as the business adds statuses.

---

## Part 2 — ORM choice

**Spring Data JPA (Hibernate)** was chosen over plain Spring JDBC.

The trade-off is genuinely two-sided, so here's the actual reasoning
rather than just a conclusion:

- **JPA wins here because:** the domain has real relationships
  (Customer → Orders → OrderItems → Products) that get navigated
  constantly — building an `OrderResponse` needs the customer's name,
  every line item, and each item's product name. JPA's associations,
  `@EntityGraph`, and `JOIN FETCH` express that navigation declaratively
  and let Hibernate manage change tracking (e.g. `product.deductStock()`
  followed by `save()` inside a transaction). Bean Validation
  (`@Valid`) also integrates for free.
- **Where plain JDBC would have won:** the three read-heavy aggregate
  queries (Part 1) are exactly the kind of hand-tuned SQL that ORMs
  are historically bad at generating efficiently. That's why they're
  **not** expressed as JPA criteria queries — `queries.sql` is raw SQL,
  and even the customer-summary aggregate inside the app
  (`OrderRepository.aggregateCustomerSummary`) is a native-feeling JPQL
  query written by hand, not something left to Hibernate's query
  generation to "figure out." In other words: JPA for the
  relationship-heavy write path, hand-written SQL/JPQL for the
  aggregation-heavy read path. That's a middle ground rather than an
  all-or-nothing choice.
- `spring.jpa.hibernate.ddl-auto=validate` — Hibernate never generates
  or runs DDL. `schema.sql` is the single source of truth for the
  database structure; Hibernate only checks the entity mappings agree
  with it at startup, which catches drift early without letting an ORM
  silently "fix" the schema in production.

## Part 2 — Concurrency & stock deduction

**Chosen approach: pessimistic locking (`SELECT ... FOR UPDATE`)**, via
`ProductRepository.findByIdForUpdate()` inside a single `@Transactional`
method in `OrderService.placeOrder()`.

**Why pessimistic over optimistic here:** optimistic locking (a
`@Version` column) is usually the better default for low-contention
data — updating a customer's email, for example — because it avoids
taking any lock at all in the common case where nobody else is editing
the same row. It fails cheaply: on conflict, Hibernate throws
`OptimisticLockException` and the caller retries. But stock deduction
under a flash-sale-style spike is exactly the scenario optimistic
locking handles badly: if 50 concurrent requests target the last 3
units of one product, only 1 will ever *commit* successfully under
optimistic locking, and the other 49 must each detect the conflict,
retry, re-read, and fail again — potentially in a long queue of retries
that all still contend for the same row. Pessimistic locking instead
makes the 49 losing requests simply **wait** in the database's lock
queue and get an immediate, correct answer (success or
`InsufficientStockException`) the moment the row is free — no retry
loop needed in application code, no wasted repeated work.

**The trade-off we accept:** pessimistic locking reduces throughput
under *heavy* contention on a single row, because every request for
that product serialises through the lock rather than running in
parallel. For an internal operations tool (the stated context — not a
public flash sale processing tens of thousands of concurrent checkouts
on one SKU), that's an acceptable and honestly preferable cost in
exchange for simplicity and correctness guarantees that don't depend on
retry logic being right.

**Deadlock avoidance:** an order with multiple line items locks
multiple product rows. If two concurrent orders both touch products
`{5, 9}` but request them in a different order, each transaction could
end up waiting on a row the other already holds — a classic deadlock.
`OrderService.placeOrder()` avoids this by always sorting line items by
`productId` before locking, so every transaction acquires locks in the
same global order regardless of what order the client listed them in
the request.

---

## Part 3 — System Design Write-Up

**Scenario:** `GET /api/orders` is slow at 50,000 requests/minute peak,
and the database server itself cannot be upgraded or changed.

### 1. Diagnosis first

I wouldn't guess at a fix — I'd narrow down where the 50k req/min is
actually being lost, in this order:

1. **Application-level metrics first (cheapest, fastest signal).**
   Check HikariCP's exposed metrics (`hikaricp.connections.active`,
   `.pending`, `.timeout`) via Actuator/Micrometer. If `pending` is
   consistently non-zero and `timeout` is climbing, the bottleneck is
   connection pool saturation — requests are queuing for a DB
   connection before a single query even runs. That's a very different
   fix (pool sizing, or reducing per-request DB round-trips) from a
   slow query.
2. **Postgres's own view of what's slow.** Enable
   `log_min_duration_statement` (e.g. flag anything over 200ms) and
   check `pg_stat_statements` for the queries with the highest total
   time — not necessarily the slowest *individual* query, since a
   moderately-slow query run 50,000 times/minute matters more than a
   rare 2-second outlier.
3. **`EXPLAIN (ANALYZE, BUFFERS)` on the worst offenders** — specifically
   looking for sequential scans on `orders` where an index scan was
   expected, or a nested-loop join blowing up because a row-count
   estimate was wrong (stale statistics — worth an `ANALYZE orders;`
   as a first cheap check).
4. **OS/instance-level as a last resort** — CPU, disk I/O wait, and
   `pg_stat_activity` for lock contention (long-running transactions
   holding locks that block reads), only if the above don't explain it.

The point of this order is: don't touch the query or add a cache until
the connection-pool numbers rule out the simpler, cheaper explanation.

### 2. Caching strategy

If the diagnosis points to genuine query cost (not pool saturation), I
would **not** cache the raw paginated `GET /api/orders` results
directly — the combination of arbitrary filters (`status`,
`customerId`, `from`, `to`) and pages produces effectively unlimited
cache keys with poor hit rates.

Instead, I'd cache the pieces that are actually expensive and reused:

- **Per-order aggregate data** — e.g. an order's computed total (the
  `sumTotalsForOrderIds` join) — keyed by `order:{id}:total`, since an
  order's total practically never changes after creation.
- **The customer summary endpoint** (`GET /api/customers/{id}/summary`)
  is a strong caching candidate: it's a full aggregate over all of a
  customer's orders, expensive to recompute, and doesn't need to be
  real-time to the second.

**TTL:** short (30–60s) for anything derived from `orders`, since order
status changes need to become visible reasonably quickly to an
operations team. Longer (hours) for things like a customer's total
lifetime spend if only recent activity matters for the "hot" number.

**Invalidation on status update:** rather than a blanket cache flush,
`OrderService.updateStatus()` would explicitly evict the affected
order's cache key(s) — `order:{id}`, and the affected customer's
summary key, `customer:{customerId}:summary` — as part of the same
transaction that changes the status. Precise, targeted eviction on
write beats a TTL-only approach here, because a stale status directly
misleads an operations team acting on it.

### 3. Pagination at scale

`OFFSET` pagination (`LIMIT 20 OFFSET 49980` for page 2500) degrades
because Postgres cannot jump straight to row 49,980 — it must actually
**scan and discard** all 49,980 preceding rows for every single request,
even though the client only wants the next 20. At 50M rows, deep pages
turn into scans of tens of millions of rows repeated on every request,
and this cost grows linearly with the page number rather than staying
constant.

**Alternative: keyset (cursor) pagination.** Instead of "give me rows
49,980–50,000," the client sends the last row it saw —
`?after_id=182734&after_created_at=2026-01-04T10:00:00Z` — and the
query becomes:

```sql
SELECT * FROM orders
WHERE (created_at, id) < ('2026-01-04T10:00:00Z', 182734)
ORDER BY created_at DESC, id DESC
LIMIT 20;
```

This is a direct index seek on `idx_orders_created_at` (or the
customer/status composites) regardless of how deep into the result set
the client is — cost stays flat whether it's page 2 or page 250,000.
The trade-off: clients can no longer jump to "page 47 directly," only
"next page from here" — acceptable for an operations dashboard that
scrolls/paginates sequentially, less so for a UI that needs arbitrary
page-number jump links.

### 4. Constrained optimisation

Bottleneck identified as a single expensive JOIN, and adding new
indexes to `orders` is off the table. Options, roughly in order of how
invasive they are:

1. **Index the *other* side of the join instead.** If the JOIN is
   `orders ⋈ order_items` or `orders ⋈ customers`, an index on
   `order_items.order_id` or `customers.id` (already present here)
   might not be the constraint — check whether the *other* table in
   the join is missing the index that would let Postgres do a fast
   nested-loop/index lookup instead of a hash join over the full table.
   This isn't "adding an index to `orders`," so it's still on the table.
2. **Rewrite the join as a covering subquery/CTE materialised once.**
   If the same expensive join result is computed repeatedly per
   request with only the WHERE clause changing, restructure so Postgres
   computes the expensive side once and filters the cheap side against
   it, rather than joining full tables every time.
3. **A precomputed/materialized view**, refreshed on a schedule (e.g.
   every 1–5 minutes) or incrementally via triggers, holding exactly
   the joined shape the endpoint needs. This trades a small amount of
   staleness for removing the JOIN entirely from the hot path — a
   reasonable trade for an operations dashboard that doesn't need
   millisecond-fresh data.
4. **Push the JOIN out of Postgres entirely** — denormalise the
   frequently-needed customer fields (name, tier) directly onto
   `orders` as a maintained, redundant copy, updated via the same
   trigger pattern already used for `updated_at`. This directly
   contradicts normal normalisation practice, so I'd only reach for it
   if 1–3 didn't move the needle, and I'd document clearly why the
   redundancy exists so a future engineer doesn't "fix" it by removing it.
5. **Read replica for this specific read-heavy endpoint.** If the
   constraint is "can't change the database server" rather than "can't
   add another one," routing `GET /api/orders` reads to a replica takes
   load off the primary without touching schema or indexes at all —
   worth confirming this is genuinely out of scope before ruling it out.
