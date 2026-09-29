# SUBMISSION — Al Qaseh Software Engineer Take-Home Assignment

**Candidate:** Abbas · **Stack:** Java 21, Spring Boot 3.4, PostgreSQL, Flyway, Spring Security (JWT), MapStruct

REST API for the small e-commerce backend described in the assignment: login, product catalogue for admins and customers, orders with
discount codes and two payment methods, admin and customer order lists.

## 1. Prerequisites

| Needed | Notes |
| --- | --- |
| JDK 21 | `JAVA_HOME` must point to it (`java -version` shows 21) |
| Docker with Docker Compose | only to run PostgreSQL; any PostgreSQL 16+ works instead (see below) |
| Free ports | 5432 (PostgreSQL) and 8080 (API); both can be changed, see the notes in step 2 and 3 |

Maven is not required, the wrapper `./mvnw` (`mvnw.cmd` on Windows) is included.

## 2. Database setup

> **Fastest way (only Docker needed):** `git clone -b java https://github.com/Abbas-F-R/alqaseh-ecommerce-task.git && cd alqaseh-ecommerce-task && docker compose up --build`, then open <http://localhost:8080/swagger-ui/index.html> (this starts PostgreSQL *and* the API; skip steps 2–3). Ports busy? `DB_PORT=5433 API_PORT=9090 docker compose up --build`.

To run the API from source, start only the database:

```bash
docker compose up -d postgres
```

Starts PostgreSQL 17 (`postgres:17-alpine`) with database `alqaseh_db`, user `postgres`, password `dev_only_fake_password` (a fake, local-only placeholder for the throw-away Docker database: it protects nothing and is not a real credential; the same value is the default in `application-dev.yml`) on `localhost:5432`. Wait a few seconds until
`docker compose ps` shows `healthy` before starting the API. Nothing else is needed: the application creates the schema (Flyway) and the demo data on first start.

* Port 5432 already used (for example by a local PostgreSQL)? Use another port for both steps: `DB_PORT=5433 docker compose up -d postgres`, then start the API with `DB_PORT=5433` (PowerShell: `$env:DB_PORT="5433"` first).
* Own PostgreSQL instead of Docker: create an empty database and start the API with `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
* Reset the data: `docker compose down -v && docker compose up -d postgres`.

## 3. Run

```bash
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

API at <http://localhost:8080> (`SERVER_PORT=9090 ./mvnw spring-boot:run` for another port; `DB_PORT` as in step 2).
On start Flyway applies the migrations (`V1` schema, `V2` demo products and discount codes, `V3` hardening) and the dev seeder creates the demo users.
Stop with Ctrl+C; `docker compose down` stops the database.

## 4. API documentation (Swagger / OpenAPI)

* Swagger UI: <http://localhost:8080/swagger-ui/index.html>
* OpenAPI JSON: <http://localhost:8080/v3/api-docs>

Every endpoint lists all its real responses (success, validation error, business errors, 401/403, 404/409, 500) with example bodies, the request
body examples, the query parameters with defaults and limits, and the required role. To try secured endpoints: call `POST /api/auth/login`, copy
`data.token`, press **Authorize** and paste it.

## 5. Test users and demo data

| Role | Username | Password |
| --- | --- | --- |
| ADMIN | `admin` | `Admin123!` |
| CUSTOMER | `customer1` | `Customer123!` |
| CUSTOMER | `customer2` | `Customer123!` |

Demo products (9, several categories and stock levels, one out of stock) and discount codes:

| Code | Amount | Minimum order | State |
| --- | --- | --- | --- |
| `WELCOME10` | 10 | 50 | valid (single use) |
| `EXPIRED50` | 50 | 100 | expired |
| `USED25` | 25 | 60 | already used |
| `ABC123` | 5,000 | 25,000 | valid (single use); the assignment's example code |
| `ZYX123` | 10,000 | 50,000 | valid (single use); the assignment's example code |

Fake payments: a credit card with number `0000000000000000` is declined and an XYZ wallet with password `wrongPassword` is rejected; anything else succeeds.
These users and the demo data exist only in the default `dev` profile; the `prod` profile creates none of them (see section 9).

## 6. Tests

```bash
./mvnw clean test
```

Unit tests, Spring integration tests (H2) and PostgreSQL tests. `PostgresSchemaIntegrationTest` runs the real migrations on PostgreSQL and needs Docker (started
automatically through Testcontainers) or an existing server: `./mvnw test -Dtest=PostgresSchemaIntegrationTest -Dtest.postgres.url=jdbc:postgresql://localhost:5432/test_db -Dtest.postgres.user=postgres -Dtest.postgres.password=postgres`.
Without either it is skipped (reported as skipped tests, the rest still runs).

In addition the running application was tested from the outside through HTTP only (see section 10).

## 7. Requirements → implementation

| Requirement | Implementation |
| --- | --- |
| Login with username and password; everything else needs authentication | `POST /api/auth/login` returns a JWT; all other endpoints require `Authorization: Bearer <token>` |
| Product: name, category, price, cost, quantity; fixed categories; unique name | `ProductRequest`, enum `ProductCategory` (`furniture, electronics, beauty, garden`), unique index on `LOWER(name)` |
| Who created/last updated a product and when | `createdBy/At`, `updatedBy/At` (Spring Data JPA auditing), returned to admins |
| Create / update product: admin only | `POST /api/products`, `PUT /api/products/{id}` |
| List products, filter by name and category, large catalogue | `GET /api/admin/products?name=&category=&page=&size=` (page/offset, totals, exact quantity) and `GET /api/customer/products?name=&category=&limit=&cursor=` (keyset/cursor, stock status): filtering and pagination in PostgreSQL; the cursor is an opaque token over the primary key, served by `idx_products_category_id` |
| Admin sees exact quantity; customer sees `low` / `limited` / `available` | admin rows: `availableQuantity`; customer rows: `stockStatus` (0–4 `low`, 5–9 `limited`, 10+ `available`) |
| Place an order (customer only), one or more products with quantities | `POST /api/orders`; stock is checked and deducted atomically |
| Payment: CreditCard or XyzWallet (phone + wallet password), fake providers | `PaymentProcessor` strategy: `CreditCard`, `XyzWallet` |
| One discount code per order: fixed amount, minimum order total, expiry, single use | `DiscountServiceImpl.redeem`, `discountCode` field of the order |
| Admin: list all orders with profit, filter by customer and payment method | `GET /api/orders?customer=&customerId=&paymentMethod=` |
| Customer: own orders with total price, payment method, date, discount amount | `GET /api/orders/my` |
| Bonus: tests, migrations, API documentation | 150+ tests, Flyway `V1–V3`, Swagger UI |

## 8. Assumptions

1. **Enum vocabulary.** Values are sent in any letter case (`furniture`, `Furniture`, `FURNITURE`, `credit_card`, `creditcard`) and always returned exactly as the assignment spells them:
   categories `furniture, electronics, beauty, garden`, stock status `low, limited, available`, payment methods `CreditCard, XyzWallet`. (The database stores the enum constant names.)
2. **Product names** are unique case-insensitively and are stored without leading/trailing spaces. The database enforces it, the application checks first to answer 409 with a clear message.
3. **Minimum order total** of a discount code is compared with the order **subtotal** (before the discount). A discount larger than the subtotal is **rejected**
   (`DISCOUNT_EXCEEDS_TOTAL`), not clamped to zero. Codes are matched ignoring case and surrounding spaces.
4. **"Used once"** means the first *successful* order consumes the code. An order that fails (declined payment, insufficient stock) does not consume it.
5. **Profit of an order** = amount charged (subtotal − discount) − Σ(unit cost × quantity). Unit price and unit cost are copied into the order line at purchase time,
   so later price changes never alter history.
6. **Stock** is deducted when the order is placed. Concurrent orders cannot oversell: the loser gets `409 CONCURRENT_MODIFICATION` and may retry.
   The same product listed twice in one order counts as one line with the summed quantity.
7. **Payments are simulated** in-process; no payment record is stored except the method (the fake transaction id is written to the audit log). Wallet payments need phone number and
   wallet password, card payments need a card number; missing fields are a 400.
8. **Who created/updated** a product is stored as user id plus timestamp and returned to admins only; customers never see cost, exact quantity or audit fields.
9. **Lists:** default page size 10, maximum 50, server-side sort (products oldest first, orders newest first). All filters are optional and combinable. The customer filter of the admin
   order list matches the username (case-insensitive part) or the exact `customerId`. A page without matches is a normal `200` with empty `content`.
10. **Roles:** customers see only their own orders; admins cannot place orders; only admins list all orders.
11. **Currency** is not modelled; amounts are decimals with two digits (e.g. IQD).
12. **Users, products and discount codes are seeded** (users by a startup seeder, products and codes by a Flyway script), as allowed by the assignment. No registration or admin endpoints for them.
13. **Limits:** a product price and cost have at most 10 digits before and 2 after the decimal point; an order line has 1–10,000 units and an order at most 100 lines, so an order has at most 1,000,000 units and its amounts (`NUMERIC(18, 2)`, migration `V7`) can never overflow. Anything larger is a `400`.
14. **No deletion:** the assignment does not ask for it, so products cannot be deleted (there is no `DELETE`).

## 9. Key technical decisions

* **Expected failures are values, unexpected failures are exceptions.** Services return `Result<T>` with a stable `ErrorCode` for business outcomes (not found, out of stock,
  invalid discount, payment declined). A single `GlobalExceptionHandler` turns everything unexpected into a generic `500` (details only in the log) and keeps Spring's own client
  errors as 4xx. The error body is `{timestamp, status, code, message, path, validationErrors?}`; the message is localised (English/Arabic, `Accept-Language`), the code is not.
* **Validation:** request shape (blank name, negative price, page size, payment fields per method) is checked by Bean Validation before a service runs; only rules that need database state
  are in the services.
* **Order creation is one atomic transaction.** A returned `Result.failure` would still commit, so the order flow changes nothing until every check has passed, reserves stock and the
  discount (flushed, so concurrent conflicts appear before any charge), processes the payment and rolls everything back if it is declined. Tests with real commits prove that a failed
  order leaves stock and discount code untouched.
* **Concurrency:** optimistic locking (`@Version`) on products and discount codes; a unique index on `LOWER(name)` is the final guard for product names, and its violation is answered with 409.
* **Performance:** filtering and pagination in the database; no N+1 (batch loading of order lines, one query for all products of an order, join fetch of the customer for the admin list);
  indexes chosen from the actual queries (`orders(customer_id, created_at)`, `orders(payment_method, created_at)`, `products(category, id)`, unique indexes). Statement counts are asserted in tests.
* **Money** is `BigDecimal` / `NUMERIC(12,2)`. Primary keys are UUID v7 (time ordered, index friendly). Audit fields come from Spring Data JPA auditing; a small `audit_logs` table records
  business events (product created/updated, order created, discount applied) and is not exposed by the API.
* **Configuration:** profiles `dev` (default; demo data and users, local defaults, random JWT key per start), `prod` (everything from environment variables, no defaults, no demo data, Swagger off) and `test`.
  Production needs `SPRING_PROFILES_ACTIVE=prod`, `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` and `JWT_SECRET` (Base64, at least 256 bit); the application refuses to start without them.
* **Simplicity:** no generic base service/repository, no cache, no messaging. Patterns only where they solve a concrete problem: strategy for payments, JPA specification for the two
  filters, MapStruct for mapping.

## 10. How it was verified

* `./mvnw clean test` and the same suite against PostgreSQL 16 with the Flyway migrations and `ddl-auto=validate`.
* **External HTTP test** against the application started as described in sections 2–3 with PostgreSQL from `docker compose`: about 300 checks through HTTP only — login, 401/403 for every endpoint,
  product create/update/duplicates (also 10 parallel creates of one name), listing with stock statuses, filters and pagination, orders with every discount and payment case, customer and admin
  order lists, concurrent orders, validation and business errors, error handling (including a database outage answered with 500), and a comparison of every observed status/error code with the
  published OpenAPI document.

## 11. Known limitations

* Payments are simulated; a real gateway needs authorize/capture (or refund on failure) and a short call while stock rows are reserved.
* Production has no seeded admin: the first admin user must be inserted (BCrypt hash) as part of deployment.
* Name search is a case-insensitive `LIKE '%text%'` (fine for a catalogue of this size; `pg_trgm` would be the next step for millions of products).
* Bean Validation messages (field errors) are English only; business error messages are English and Arabic.
* Every authenticated request loads the user (one indexed query) so that a removed user is locked out immediately.
* Product prices are not versioned other than through the order snapshots.

## 12. cURL walkthrough

```bash
# log in (admin), keep the token
curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"Admin123!"}'

# create a product (admin)
curl -s -X POST http://localhost:8080/api/products -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Smart Ultra Watch","category":"electronics","price":250.00,"cost":140.00,"availableQuantity":20}'

# list products as a customer (stockStatus only), filtered and paginated
curl -s "http://localhost:8080/api/customer/products?category=electronics&name=watch&limit=10" -H "Authorization: Bearer $CUSTOMER_TOKEN"

# place an order with a discount code (customer)
curl -s -X POST http://localhost:8080/api/orders -H "Authorization: Bearer $CUSTOMER_TOKEN" -H "Content-Type: application/json" \
  -d '{"items":[{"productId":"01923450-0000-7000-8000-000000000003","quantity":2}],"discountCode":"WELCOME10",
       "payment":{"method":"CreditCard","cardNumber":"4111222233334444"}}'

# a declined payment changes nothing (stock and discount code stay available) -> 402 PAYMENT_FAILED
curl -s -X POST http://localhost:8080/api/orders -H "Authorization: Bearer $CUSTOMER_TOKEN" -H "Content-Type: application/json" \
  -d '{"items":[{"productId":"01923450-0000-7000-8000-000000000003","quantity":2}],
       "payment":{"method":"CreditCard","cardNumber":"0000000000000000"}}'

# my orders (customer) / all orders with profit, filtered (admin)
curl -s "http://localhost:8080/api/orders/my" -H "Authorization: Bearer $CUSTOMER_TOKEN"
curl -s "http://localhost:8080/api/orders?paymentMethod=CreditCard&customer=customer1" -H "Authorization: Bearer $ADMIN_TOKEN"
```


**Product lists (two endpoints, two pagination styles, on purpose):** `GET /api/admin/products` uses page/offset pagination (`page` from 0, `size` 1–50) and answers
`{ "data": [...], "pagesCount", "currentPage", "totalCount", "isLast" }` because an admin dashboard needs totals and page jumping; `GET /api/customer/products` uses
cursor (keyset) pagination (`limit` 1–50, `cursor`) and answers `{ "data": [...], "nextCursor", "hasMore" }` because customers browse a large catalogue page after page
(constant cost per page, no duplicates or gaps while products change). Orders keep page/offset pagination.
