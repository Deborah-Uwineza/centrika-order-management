# Centrika Order Management System

Backend take-home exam submission — Order Management System REST API.

**Stack:** Java 17 · Spring Boot 3 · PostgreSQL 15 · Maven · Docker

## Project layout

```
.
├── schema.sql              # Part 1 — full DDL (tables, constraints, indexes)
├── queries.sql              # Part 1 — the 3 required SQL queries
├── DESIGN.md                 # ORM rationale + Part 3 system design write-up
├── docker-compose.yml        # Postgres (auto-seeded from schema.sql) + the app
├── Dockerfile                 # Multi-stage build for the Spring Boot app
├── pom.xml
└── src/
    ├── main/java/rw/centrika/orders/
    │   ├── domain/            # JPA entities + enums + converters
    │   ├── repository/        # Spring Data repositories + Specifications
    │   ├── service/            # Business logic (order placement, status updates)
    │   ├── controller/         # REST controllers
    │   ├── dto/                 # Request/response records
    │   └── exception/          # Custom exceptions + global error handler
    └── test/java/rw/centrika/orders/  # Unit + context-load tests
```

## Option A — Run everything with Docker (recommended)

This is the fastest path and matches exactly how the exam says the project should run.

```cmd
docker compose up --build
```

What happens:
1. Postgres 15 starts and automatically runs `schema.sql` on first boot (via `docker-entrypoint-initdb.d`) — no manual DB setup needed.
2. The Spring Boot app builds (multi-stage Docker build) and starts on **http://localhost:8080**.

To stop everything:
```cmd
docker compose down
```

To wipe the database and start completely fresh (re-runs schema.sql):
```cmd
docker compose down -v
docker compose up --build
```

## Option B — Run Postgres in Docker, app locally via Maven

Useful while actively developing, since you get faster restarts than rebuilding the Docker image each time.

```cmd
docker compose up db
mvn spring-boot:run
```

The app will connect to `localhost:5432` using the defaults already set in `application.yml`.

## Testing the API

These examples use `curl` rather than Postman. On Windows, Postman's
web client requires installing and running a separate "Desktop Agent"
before it can reach `localhost` at all — an extra moving part with no
real benefit for a one-off verification pass. `curl.exe` ships with
Windows 10+, needs no install or sign-in, and is the same tool a
reviewer would reasonably use to sanity-check this API from a
terminal. For any request with a JSON body, the body is kept in a
separate `.json` file (e.g. `create-order.json`) and passed with
`-d "@create-order.json"`, rather than escaping quotes inline in
PowerShell — that avoids a whole class of "my JSON string got mangled
by the shell" bugs.

```cmd
:: create-order.json contains:
:: { "customerId": 1, "items": [{ "productId": 1, "quantity": 2 }] }
curl.exe -X POST http://localhost:8080/api/orders -H "Content-Type: application/json" -d "@create-order.json"

:: GET /api/orders — every filter is optional and combinable
curl.exe "http://localhost:8080/api/orders?page=0&size=10"
curl.exe "http://localhost:8080/api/orders?page=0&size=10&status=PENDING"
curl.exe "http://localhost:8080/api/orders?page=0&size=10&customerId=1"
curl.exe "http://localhost:8080/api/orders?page=0&size=10&from=2026-01-01T00:00:00Z&to=2026-12-31T23:59:59Z"

curl.exe http://localhost:8080/api/orders/1

:: update-status.json contains: { "status": "PROCESSING" }
curl.exe -X PUT http://localhost:8080/api/orders/1/status -H "Content-Type: application/json" -d "@update-status.json"

curl.exe http://localhost:8080/api/customers/1/summary
```

> Note: `schema.sql` creates the tables but no rows. Insert a test customer and product first (via `psql`, the IntelliJ/VS Code database tool, or a `INSERT` statement) before calling `POST /api/orders`.

### Alternative: testing with Postman

If you prefer Postman over `curl`, it works the same way — just be aware that Postman's **web client** (`postman.co` in a browser) cannot reach `localhost` directly; it needs the **Postman Desktop Agent** installed and running first (Postman will prompt you to download it the first time you try to send a request to `localhost`). The standalone **Postman desktop app** doesn't have this issue at all.

For each endpoint, set the method and URL, and for `POST`/`PUT` requests set the **Body** tab to **raw** + **JSON**:

| Endpoint | Method | URL | Body (raw JSON) |
|---|---|---|---|
| List orders | `GET` | `http://localhost:8080/api/orders?page=0&size=10&status=PENDING` | — |
| Get one order | `GET` | `http://localhost:8080/api/orders/1` | — |
| Create order | `POST` | `http://localhost:8080/api/orders` | `{"customerId": 1, "items": [{"productId": 1, "quantity": 2}]}` |
| Update status | `PUT` | `http://localhost:8080/api/orders/1/status` | `{"status": "PROCESSING"}` |
| Customer summary | `GET` | `http://localhost:8080/api/customers/1/summary` | — |

For `POST`/`PUT` requests, also add a header: **Key** `Content-Type`, **Value** `application/json`.

## Running the tests

```cmd
mvn test
```

## Design decisions

See **DESIGN.md** for:
- Indexing strategy and denormalisation rationale (Part 1)
- ORM choice justification (Part 2)
- Concurrency / stock-deduction trade-offs (Part 2)
- Diagnosis process, caching strategy, pagination at scale, and constrained JOIN optimisation (Part 3)
