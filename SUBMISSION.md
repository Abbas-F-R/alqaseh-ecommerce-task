# Submission – Al Qaseh E-Commerce API

Backend for a small part of an e-commerce application: JWT login for two roles (admin, customer), a product catalog, orders with two payment methods and one-time discount codes, and order lists for admins and customers.

**Stack:** ASP.NET Core (.NET 10), SQL Server 2022, Dapper with stored procedures, FluentValidation, JWT bearer, Swagger (OpenAPI), xUnit.

---

## 1. Setup and run

### Fastest way (only Docker needed)

```bash
git clone -b dotnet https://github.com/Abbas-F-R/alqaseh-ecommerce-task.git
cd alqaseh-ecommerce-task
docker compose up --build
```

Open <http://localhost:5207/swagger>, log in as `admin` or `customer1` (users below) and click **Authorize**. Ports busy? `DB_PORT=14330 API_PORT=9090 docker compose up --build`. Stop and wipe: `docker compose down -v`.

### From source

**Prerequisites:** [.NET 10 SDK](https://dotnet.microsoft.com/download) and Docker (or any SQL Server 2019+ you can reach).

```bash
docker compose up -d sqlserver --wait   # SQL Server 2022 on localhost:1433 (wait for "healthy")
dotnet run                       # API on http://localhost:5207, opens Swagger UI
```

That is all: on start the application **creates the database `AlQaseh_Ecommerce` if it does not exist, applies the SQL migrations, and loads the demo data** (Development only). Nothing has to be run by hand.

* Swagger UI: <http://localhost:5207/swagger> (click **Authorize** and paste the token from `POST /api/auth/login`)
* Two documents, one per role, chosen from the dropdown at the top of Swagger UI: **Admin** (`/swagger/1-admin/swagger.json`) and **Customer** (`/swagger/2-customer/swagger.json`). Each lists only the endpoints that role may call; `POST /api/auth/login` is in both.

If `dotnet run` does not use the launch profile (for example `--no-launch-profile`), set `ASPNETCORE_ENVIRONMENT=Development` yourself; the demo data and the generated development JWT key are Development-only.

**Port 1433 already in use?**

```bash
DB_PORT=14330 docker compose up -d sqlserver --wait
ConnectionStrings__DefaultConnection="Server=localhost,14330;Database=AlQaseh_Ecommerce;User Id=sa;Password=DevOnly_Fake_Pass1!;TrustServerCertificate=True" dotnet run
```

**Use your own SQL Server:** set `ConnectionStrings__DefaultConnection` (the login needs permission to create the database, or create an empty database first).

> **The database password in `appsettings.json` and `docker-compose.yml` (`DevOnly_Fake_Pass1!`) is a fake, local-only placeholder** for the throw-away Docker database, kept identical in both files so that setup is one command. It protects nothing and is not a real credential; change it in both places (or override with `ConnectionStrings__DefaultConnection` / edit the compose file) for anything else. Likewise the demo users below exist only when `Seed:Enabled` is on (Development).

### Demo users (Development only)

| Role | Username | Password |
|---|---|---|
| Admin | `admin` | `Admin123!` |
| Customer | `customer1` | `Customer123!` |
| Customer | `customer2` | `Customer123!` |

### Demo discount codes (fixed amounts in IQD)

| Code | Amount | Minimum order total | State |
|---|---|---|---|
| `ABC123` | 5,000 | 25,000 | valid, unused |
| `ZYX123` | 10,000 | 50,000 | valid, unused |
| `EXPIRED10` | 10,000 | 50,000 | expired |
| `USED5` | 5,000 | 25,000 | already used |

### Fake payments

No real provider is called. To exercise the failure path: a `CreditCard` payment with card number `4000000000000002` is declined, and an `XyzWallet` payment with wallet password `wrong-password` is rejected. Every other card / wallet succeeds.

### Configuration

| Key (environment variable) | Meaning | Default |
|---|---|---|
| `ConnectionStrings:DefaultConnection` (`ConnectionStrings__DefaultConnection`) | SQL Server connection | local Docker SQL Server |
| `Jwt:SecretKey` (`Jwt__SecretKey`) | HMAC key, at least 32 characters | **none in the repository**: random on every start in Development, **required** elsewhere (the application refuses to start without it) |
| `Jwt:ExpiresMinutes` | token lifetime | 60 |
| `Database:AutoMigrate` | apply migrations at startup | `true` |
| `Seed:Enabled` | load the demo data | `true` in Development only |
| `Swagger:Enabled` | serve Swagger UI / OpenAPI | `true` |

Because the Development JWT key is random, tokens stop working after a restart (log in again). Production example:

```bash
ASPNETCORE_ENVIRONMENT=Production Jwt__SecretKey="$(openssl rand -base64 48)" ConnectionStrings__DefaultConnection="..." \
  dotnet run --no-launch-profile --urls http://localhost:5207
```

## 2. API

Every endpoint except login needs `Authorization: Bearer <token>`. Order lists and the admin product list are paged with `pageNumber` (from 0, default 0) and `pageSize` (1–50, default 10; the aliases `page` and `size` also work) and return
`{ "data": [...], "currentPage": 0, "pagesCount": 3, "totalCount": 21, "isLast": false }`. The customer product list uses cursor pagination instead (below).

| Endpoint | Who | What |
|---|---|---|
| `POST /api/auth/login` | anyone | username + password → bearer token, role |
| `POST /api/products` | admin | create a product |
| `PUT /api/products/{id}` | admin | replace a product's fields |
| `GET /api/admin/products?name=&category=` | admin | filter by name (contains) and category, page/offset pagination with totals; rows show `cost` and the exact `availableQuantity` |
| `GET /api/customer/products?name=&category=&limit=&cursor=` | customer | same filters, cursor (keyset) pagination: `{ "data": [...], "nextCursor": "...", "hasMore": true }`; rows show `stockStatus` (`low` 0–4, `limited` 5–9, `available` 10+), never the quantity or cost |
| `POST /api/orders` | customer | place an order (items, optional `discountCode`, `payment`) |
| `GET /api/orders/my` | customer | own orders: total price, payment method, purchase date, discount amount (+ items) |
| `GET /api/orders?customerId=&customer=&paymentMethod=` | admin | all orders with `profit`; filter by customer and payment method |

Errors are RFC 7807 problem details with a stable machine-readable `code` and a human `detail` in the language of the `Accept-Language` header (`en` default, `ar` supported):

```json
{ "title": "Conflict", "status": 409, "detail": "A product with this name already exists.", "code": "ProductNameAlreadyExists" }
```

| Status | When |
|---|---|
| 400 | invalid input (validation `errors` are listed), unsupported payment method |
| 401 | missing / invalid / expired token, wrong credentials (`InvalidCredentials`) |
| 402 | payment declined (`PaymentFailed`) – nothing was changed |
| 403 | the caller's role may not use the endpoint |
| 404 | `ProductNotFound`, `DiscountNotFound` |
| 409 | `ProductNameAlreadyExists`, `InsufficientStock`, `DiscountAlreadyUsed` |
| 422 | `DiscountExpired`, `MinimumOrderTotalNotMet`, `DiscountExceedsTotal` |
| 500 | unexpected error: generic body with a `traceId`, details only in Development |

Ids are opaque strings (Sqids, e.g. `"b9X7mK2p"`): use them exactly as returned. All timestamps are UTC.

### Example

```bash
TOKEN=$(curl -s localhost:5207/api/auth/login -H 'Content-Type: application/json' \
        -d '{"userName":"customer1","password":"Customer123!"}' | jq -r .token)

curl -s "localhost:5207/api/customer/products?category=garden&limit=10" -H "Authorization: Bearer $TOKEN"

curl -s localhost:5207/api/orders -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
     -d '{"items":[{"productId":"<id from the list>","quantity":2}],
          "discountCode":"ABC123",
          "payment":{"method":"CreditCard","cardNumber":"4111111111111111"}}'
```

Payment fields: `CreditCard` needs `cardNumber` (12–19 digits); `XyzWallet` needs `phoneNumber` and `walletPassword`.

## 3. Tests

```bash
dotnet test                       # unit tests; the database integration tests are skipped
```

The **integration tests start the real application in memory against a real SQL Server** (migrations, seed, JWT, validation, stored procedures, concurrency). They run when `ALQASEH_TEST_SQLSERVER` holds a server-level connection string; each run uses a throw-away database that is dropped afterwards:

```bash
docker compose up -d sqlserver --wait
ALQASEH_TEST_SQLSERVER="Server=localhost,1433;User Id=sa;Password=DevOnly_Fake_Pass1!;TrustServerCertificate=True" dotnet test
```

(PowerShell: `$env:ALQASEH_TEST_SQLSERVER = "Server=localhost,1433;User Id=sa;Password=DevOnly_Fake_Pass1!;TrustServerCertificate=True"; dotnet test`.)

What they cover: login and roles; product create/update/duplicate names (also under 8 parallel creations); stock status bands; filters and pagination; discount code rules (minimum, expiry, unknown, too large, single use, race for one code); declined payment rolling everything back; all-or-nothing multi-item orders; parallel orders never overselling; customer and admin order lists including profit; localization; the Swagger document listing exactly the seven endpoints.

## 4. Approach and key design decisions

* **Vertical slices** (`Features/Auth|Products|Orders|Discounts|Payments`), each with controller, DTOs, service, repository and validators; shared building blocks in `Shared/`, technical plumbing in `Infrastructure/`.
* **Validation in layers:** request shape by FluentValidation before any service runs; business rules in services, returned as `ServiceResult` with a stable error code (no exceptions for expected outcomes); the database enforces the invariants that must never be violated (unique product name, the four categories, non-negative amounts, non-blank names and codes, one order per discount code, one line per product in an order, a discount amount only together with a code).
* **Checkout is one database transaction, all or nothing.** In order: take the stock (a guarded `UPDATE … WHERE AvailableQuantity >= @qty` that also returns the price and cost at that instant), validate and redeem the discount code (its row is locked, and a unique index on `Orders.DiscountCodeId` makes "used once" a database guarantee), store the order, its lines and the audit entry, and **only then charge the payment**. A declined payment, missing stock, a bad code or any error rolls everything back, so stock, codes and orders are never left half-changed. Concurrent orders can neither oversell a product nor redeem a code twice (both covered by tests).
* **Order lines are snapshots** (name, price, cost at purchase time), so later price changes do not rewrite history and profit stays correct.
* **Profit** of an order = the amount paid (subtotal − discount) − the cost of its items.
* **Product audit fields:** `createdBy/createdAt` on creation and `updatedBy/updatedAt` on every admin update, written by the stored procedures in the same transaction as the change. Stock reduced by an order does not touch them.
* **Performance:** all lists are paged in SQL: `OFFSET/FETCH` with the total count in the same round trip for the admin lists, keyset (`TOP (limit + 1)` after the last id, no count, no OFFSET) for the customer product list; order lines for a whole page are loaded with one query (no N+1); indexes match the filters (category + id, customer + date, payment method + date). The list procedures share one statement for every combination of optional filters, so they use `OPTION (RECOMPILE)`: without it SQL Server reuses the plan of the first call and a deep cursor page or a customer-filtered order list scans the table (measured at 200,000 products / 300,000 orders: 2,436 page reads instead of 3, and 8,304 instead of 3). `QueryPlanTests` guards this.
* **Migrations:** versioned SQL scripts (`V001__Schema` tables, `V002__Procedures` views and stored procedures, `V003__AuditTriggers`, `V004__ProductsKeysetPagination` index and cursor procedure, `V005__IntegrityConstraints`, `V006__ProductsGetAllPagesFromZero`, `V007__OptionalFilterPlans`) are embedded in the assembly and applied once, in order, at startup (journal table `SchemaMigrations`, an application lock protects against two instances starting together). All data access goes through stored procedures and views; the checkout procedures take the transaction the application opens, so they run inside it.
* **Security:** BCrypt password hashes, unknown user and wrong password are indistinguishable (same error, same timing); JWT with issuer/audience/lifetime validation and short claim names (`sub`, `name`, `role`); no key or secret in the repository; the unhandled-exception middleware never leaks internals outside Development.
* **Audit trail by database triggers** (the same design as the Official Correspondence System): `AuditTables` registers the audited tables, `usp_CreateAuditTrigger` creates a trigger per table, and the trigger calls `AuditWrite`, which stores one `AuditLog` row per changed record (action, table id, record id, the row as JSON) with the acting user taken from `SESSION_CONTEXT('UserId')`. Products, DiscountCodes, Orders and OrderItems are audited; the application only tells the database who is acting (the product procedures and the checkout transaction set the context), it writes no audit rows itself. Because triggers run inside the statement's transaction, a rolled-back checkout leaves no audit trace. `Users` is deliberately not audited (its rows hold password hashes). The trail is not exposed by any endpoint, as the assignment asks for none.
* **Logging:** Serilog to `Logs/` (gitignored), partitioned by error type.

## 5. Assumptions

1. **Currency:** amounts are IQD, stored as `decimal(18,2)`; there is no currency field.
2. **Enumerations:** categories (`furniture`, `electronics`, `beauty`, `garden`) and payment methods (`CreditCard`, `XyzWallet`) are accepted in any letter case, stored and returned in the canonical spelling.
3. **Product names** are unique case-insensitively after trimming (enforced by a database unique constraint). A product update may keep its own name.
4. **Product values:** price > 0, cost ≥ 0, quantity ≥ 0; price and cost have at most 10 digits before and 2 after the decimal point (so a value is never rounded by the `decimal(18,2)` column and any order, at most 1,000,000 units in total, still fits it). Products are only created and updated (no delete was requested); an update replaces all fields (`PUT`).
5. **Stock status:** exactly the bands of the assignment; a product with quantity 0 is still listed (`low`) and cannot be ordered (`409 InsufficientStock`).
6. **Discount codes:** one fixed amount per code; the minimum is compared with the order's items total *before* the discount (equal qualifies); a code larger than the items total is rejected; a code is single-use across all customers; lookup is case-insensitive; expired means the current time is after `ExpiresAt`.
7. **One code per order**, optional. The order total is items total − discount.
8. **Customers see only their own orders; admins cannot place orders** (`403`).
9. **Order lists** are newest first; the admin list can filter by `customerId` (exact) and `customer` (username contains) and by `paymentMethod`. The order lines are included in both lists.
10. **Users, products and discount codes are seeded**; there is no registration or admin endpoint for them. In Development the seed runs at startup; in other environments nothing is seeded.
11. **Ids** are returned as opaque strings; numeric ids are also accepted on input.
12. **Language:** error messages are English or Arabic by `Accept-Language`; field validation messages are English.

## 6. Known limitations

* If the process dies in the instant between a successful (fake) charge and the database commit, the customer would be charged without an order. A real gateway needs an idempotency key and a compensating refund / outbox; the fake gateway makes that out of scope.
* The name filter is a `LIKE '%text%'` search: correct and paged, but it scans the name column. It is fine for tens of thousands of products; millions would call for full-text search.
* Tokens cannot be revoked before they expire and there is no refresh token or login rate limiting.
* The default connection string uses the local Docker `sa` password for convenience; real deployments must supply their own via environment variables.


**Product lists (two endpoints, two pagination styles, on purpose):** `GET /api/admin/products` uses page/offset pagination (`pageNumber` from 0, `pageSize` 1–50) and answers
`{ "data": [...], "pagesCount", "currentPage", "totalCount", "isLast" }` because an admin dashboard needs totals and page jumping; `GET /api/customer/products` uses
cursor (keyset) pagination (`limit` 1–50, `cursor`) and answers `{ "data": [...], "nextCursor", "hasMore" }` because customers browse a large catalogue page after page
(constant cost per page, no duplicates or gaps while products change). Orders keep page/offset pagination.
