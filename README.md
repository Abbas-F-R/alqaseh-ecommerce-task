# Al Qaseh E-Commerce REST API

Spring Boot 3.4 / Java 21 / PostgreSQL REST API for a small e-commerce backend: JWT login with `ADMIN` and `CUSTOMER` roles, a product catalogue with
role-dependent stock visibility, atomic order checkout (stock, discount code, payment, profit), and admin/customer order lists.

**Setup, run, test users, assumptions, design decisions and known limitations: [SUBMISSION.md](SUBMISSION.md).**
Development notes (review findings and fixes): [CODE_REVIEW.md](CODE_REVIEW.md).

## Quick start

**Only Docker needed** (no JDK, no Maven, no database to install):

```bash
docker compose up --build       # builds the API, starts PostgreSQL + API; ready when the log shows "Started"
```

Then open **<http://localhost:8080/swagger-ui/index.html>**, pick **Admin (Dashboard)** or **Customer (App)** in the definition dropdown, log in with a demo user (below) and click *Authorize*.

Prefer running from source? `docker compose up -d postgres`, then `DB_PASSWORD=dev_only_fake_password ./mvnw spring-boot:run` (Windows: set `$env:DB_PASSWORD="dev_only_fake_password"`, then `mvnw.cmd spring-boot:run`). The password is the throw-away one of the compose database; `application-dev.yml` defaults to a different one.

> The same task is also implemented in .NET 10 / SQL Server on the [`dotnet` branch](../../tree/dotnet).

* OpenAPI JSON per role: <http://localhost:8080/v3/api-docs/1-admin> · <http://localhost:8080/v3/api-docs/2-customer>
* Demo users (dev profile): `admin / Admin123!`, `customer1 / Customer123!`, `customer2 / Customer123!`
* Tests: `./mvnw clean test`
* Ports in use? `DB_PORT=5433 API_PORT=9090 docker compose up --build` (from source: `DB_PORT=5433 SERVER_PORT=9090 ./mvnw spring-boot:run`)

## Endpoints

| Endpoint | Who | Purpose |
| --- | --- | --- |
| `POST /api/auth/login` | public | returns a Bearer JWT |
| `GET /api/admin/products` | admin | filter `name`, `category`; page/offset pagination (`page` from 0, `size` 1–50) with totals; rows show cost and exact quantity |
| `GET /api/customer/products` | customer | filter `name`, `category`; cursor pagination (`limit` 1–50, `cursor`); rows show `stockStatus` only |
| `POST /api/products`, `PUT /api/products/{id}` | admin | create / replace a product |
| `POST /api/orders` | customer | place an order (items, optional `discountCode`, `payment`) |
| `GET /api/orders/my` | customer | own orders |
| `GET /api/orders` | admin | all orders with profit; filters `customer`, `customerId`, `paymentMethod` |

Single-object responses are `{ "timestamp", "success", "data" }`; lists are `{ "data", "pagesCount", "currentPage", "totalCount", "isLast" }` (admin products, orders) or
`{ "data", "nextCursor", "hasMore" }` (customer products); errors are `{ "timestamp", "status", "code", "message", "path", "validationErrors"? }` with stable codes and
messages in the language of `Accept-Language` (`en`, `ar`). Enum values (`category`, `paymentMethod`) are accepted in any letter case and returned as the assignment spells them (`furniture`, `low`, `CreditCard`).

## Configuration

| Profile | Use |
| --- | --- |
| `dev` (default) | local: database defaults, a random JWT key per start (or `JWT_SECRET`), token lifetime 24 h (`JWT_EXPIRATION_MS`), demo data and users, debug details in 500 responses |
| `prod` | everything from the environment, no defaults, no demo data or users, Swagger off |
| `test` | automated tests |

`prod` requires `SPRING_PROFILES_ACTIVE=prod`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` and `JWT_SECRET`
(Base64, at least 256 bit, e.g. `openssl rand -base64 32`); optional `JWT_EXPIRATION_MS` (default 1 hour). The application does not start without them.
The schema is owned by Flyway (`src/main/resources/db/migration`); Hibernate only validates it. Demo data lives in `db/dev` and is loaded only in `dev`.

## Code map

```text
com.alqaseh.ecommerce
├── features/{auth,product,order,discount,payment}   controller · dto · entity · mapper · repository · service
├── shared/          BaseEntity (UUID v7, auditing), Result + ErrorCode, GlobalExceptionHandler, localization, audit log, response records
├── infrastructure/  JWT filter and service, security helpers, User
└── config/          security, auditing, OpenAPI documentation, enum parsing, dev/test seeder
```

Validation: request shape by Bean Validation before a service runs; database-dependent rules in services as `Result.failure(ErrorCode.X)`; unexpected errors are exceptions handled once.
