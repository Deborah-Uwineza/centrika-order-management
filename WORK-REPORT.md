# Centrika Take-Home Exam — Work Report

**Candidate:** Uwineza Deborah
**Project:** Order Management System — Backend Engineer take-home exam
**Stack:** Java 17 · Spring Boot 3 · PostgreSQL 15 · Docker

---

## 1. Step-by-step process followed

1. **Environment setup**
   - Installed **JDK 17** (Eclipse Temurin) — the specific version Spring Boot 3 requires.
   - Installed **VS Code** with the Extension Pack for Java, Spring Boot Extension Pack, and Lombok support.
   - Confirmed **Docker Desktop** was already installed (fixed an initial download of the wrong CPU architecture — ARM64 instead of AMD64).
   - Confirmed **Git** was already installed.

2. **Built the project**
   - Part 1: `schema.sql` (tables, constraints, composite indexes) and `queries.sql` (the 3 required aggregate queries).
   - Part 2: a full Spring Boot REST API — entities, repositories, services, controllers, DTOs, and a global exception handler for structured error responses.
   - Part 3: `DESIGN.md` — indexing rationale, ORM justification, concurrency trade-off explanation, and the system-design write-up (diagnosis, caching, pagination at scale, constrained optimisation).

3. **Ran it locally**
   - `docker compose up --build` — PostgreSQL starts and auto-runs `schema.sql` on first boot; the Spring Boot app builds and starts on port 8080.
   - Inserted a test customer and product directly via `psql` inside the running container.

4. **Tested every endpoint with curl**, catching and fixing 2 real bugs along the way:
   - A missing log statement in the global exception handler that was hiding a real error.
   - A `ClassCastException` in the customer-summary aggregate query, plus a related edge case (a customer with zero orders would have returned no data at all instead of zeros) — both fixed by rewriting the query with correlated subqueries.

5. **Prepared for submission**
   - Added a `.gitignore` (build artifacts, IDE files) so the pushed repo stays clean.
   - Verified `README.md` accurately reflects the final setup and testing approach.

---

## 2. Why VS Code instead of IntelliJ IDEA

IntelliJ IDEA is arguably the stronger tool for large, long-term Spring Boot work — better Spring-aware navigation, more mature Lombok integration, a built-in database browser. That said, two things made it the wrong choice **for this specific 48-hour exam**:

- The IntelliJ installer was taking an entire day to complete on this machine — an unacceptable cost against a hard deadline.
- VS Code was already installed, and with three extensions (Java Extension Pack, Spring Boot Extension Pack, Lombok support) it provided everything actually needed: syntax highlighting, Java IntelliSense, Maven awareness, and Lombok-generated method recognition.

The Java/Spring Boot code itself is 100% IDE-agnostic — nothing about the project depends on which editor built it.

---

## 3. Why curl instead of Postman

Postman's web client (`postman.co`) cannot reach `localhost` directly — it requires installing and running a separate "Desktop Agent" to bridge the browser to the local machine. That's an extra install and a point of failure with no real benefit for a one-off verification pass.

`curl.exe` ships with Windows 10 and later, needed no installation, and is a completely standard tool a reviewer could use to check the same endpoints. To avoid PowerShell's awkward quote-escaping for JSON bodies, each request body was kept in its own `.json` file (`create-order.json`, `update-status.json`) and passed with `-d "@filename.json"` — avoiding an entire class of "my JSON string got mangled by the shell" errors.

---

## 4. How to verify the data yourself (inserted / updated / everything)

### A. Direct database inspection (psql)

Open a shell into the running Postgres container:
```powershell
docker exec -it centrika-orders-db psql -U centrika -d centrika_orders
```

Then, at the `centrika_orders=#` prompt:
```sql
SELECT * FROM customers;
SELECT * FROM products;      -- check stock_quantity after placing orders
SELECT * FROM orders;
SELECT * FROM order_items;
```

Type `\q` to exit back to PowerShell.

### B. Through the API itself (curl)

```powershell
# See a specific order, including its items and computed total
curl.exe http://localhost:8080/api/orders/1

# See all orders, paginated, newest first
curl.exe "http://localhost:8080/api/orders?page=0&size=10"

# Filter by status — proves filtering is real, not decorative
curl.exe "http://localhost:8080/api/orders?page=0&size=10&status=PENDING"

# Customer's aggregate spend, order count, last order date
curl.exe http://localhost:8080/api/customers/1/summary
```

### C. What was actually verified during this exam

| Check | Method used | Result |
|---|---|---|
| Stock deducted atomically on order creation | `psql` before/after `POST /api/orders` | 50 → 46 across two 2-unit orders |
| Price frozen at time of purchase | `GET /api/orders/1` | `unitPrice` stayed 15000.00 regardless of catalogue |
| Pagination/filtering server-side | `curl` with `status=PENDING` vs `status=SHIPPED` | Correctly returned 2 vs 0 results |
| Status state machine enforced | `PUT /api/orders/1/status` with an invalid jump | `409 Conflict` with a clear message |
| Customer summary aggregate correct | `curl` after 2 orders of 30,000 each | `totalSpend: 60000.00`, `orderCount: 2` |
