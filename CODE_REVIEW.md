# Code Review — alqaseh-ecommerce-api

Review of the implementation delivered by the previous agent, followed by the refactoring done in response.
Scope: read everything (main, tests, Flyway, config, docs), criticise, fix, test, review again. No features were added.

**Verdict on the code as received:** it looked professional and passed its own 117 tests (116 executed, 1 skipped), but the tests were mostly
mock-based and never exercised the real transaction, so they could not see the worst bug (C1). The architecture
carried a lot of ceremony (six validators, a second error system made of ten dead exception classes, a 165-line
pagination class) and the README/SUBMISSION claimed things the code did not do. The most serious problems were not
style problems; they were correctness and security bugs.

Main code: **96 → 75 files, 3,916 → 2,880 lines (−26 %)**, with the bugs below fixed.

---

## 1. Critical Problems (all fixed)

### C1. A failed order committed half of its work — money-relevant data corruption
`OrderServiceImpl.createOrder` was `@Transactional` and changed managed entities (stock deduction, discount `used=true`)
**before** it could still return `Result.failure(...)`. A returned `Result` is not an exception, so Spring **commits**;
Hibernate dirty-checking then wrote the changes.

* declined payment → stock permanently deducted, discount code burned, no order;
* order with 3 lines where the 3rd has too little stock → lines 1 and 2 deducted anyway;
* invalid discount after valid lines → stock deducted anyway.

Nothing caught it because `CreateOrderServiceTest` used mocks (no transaction) and `StockConcurrencyTest` only checked
"stock never negative".
**Fix:** nothing is modified until every check passed; then stock/discount are reserved and *flushed*, payment is
processed, and a decline calls `setRollbackOnly()`. **Proof:** `OrderTransactionIntegrationTest` (real commits/rollbacks).
I removed the rollback line as a mutation check: `expected: 10 but was: 8` and the customer's retry failed because the
discount was burned. The test fails without the fix.

### C2. Spring's own client errors became HTTP 500
`@ExceptionHandler(Exception.class)` swallowed every Spring MVC exception. Unknown route (404), wrong method (405),
wrong media type (415), malformed JSON, unknown enum value (`category=TOYS`), malformed UUID in the path — all returned
**500**. Also: name uniqueness was checked case-sensitively but the DB index is on `LOWER(name)`, so `"chair"` vs
`"Chair"` passed the check and hit the unique index → **500**; an over-long name or a huge price overflowed the column → **500**.
**Fix:** `GlobalExceptionHandler` extends `ResponseEntityExceptionHandler` (proper 4xx), maps the unique-index violation
to 409 `PRODUCT_NAME_ALREADY_EXISTS`, optimistic-lock conflicts to 409 `CONCURRENT_MODIFICATION`, everything else to a safe
500. `@Size`/`@Digits` on the DTOs stop overflow at the door.

### C3. Demo credentials and a JWT secret were active in every environment
* `application.yml` shipped a default JWT secret → if `JWT_SECRET` is forgotten in production, anyone can forge an admin token.
* `DataSeeder` created `admin / Admin123!` in every environment, including production.
* `V2__seed_data.sql` (demo products/discounts) was a normal migration, so it ran in production too.
* `flyway.baseline-on-migrate: true` silently baselines a non-empty schema.

**Fix:** profiles `dev` (default) / `prod` / `test`. `prod` has no defaults for DB or secret, refuses to start with a
missing/weak `JWT_SECRET` (clear message), does not seed users, does not load demo data, and disables Swagger.
Seed SQL moved to `db/dev` (only in the dev profile). Verified by booting `prod` on an empty DB: admin login → 401,
`/v3/api-docs` → 404.

### C4. The JWT filter turned infrastructure failures into "401 Unauthorized"
`catch (Exception)` around token parsing *and* the user lookup: a database outage looked like an invalid token.
**Fix:** only `JwtException` / `IllegalArgumentException` / `UsernameNotFoundException` are treated as "bad token".

### C5. A discount code could be redeemed twice concurrently
`DiscountCode` had no lock; two simultaneous orders both read `used=false`. **Fix:** `@Version` (+ migration V3);
`OrderTransactionIntegrationTest.concurrentRedemptionOfOneCode` (6 threads → exactly 1 order).

### C6. `GET /api/orders/my` paginated in memory
`@EntityGraph(items, items.product, customer)` on a paged query: a collection fetch join with paging makes Hibernate load
**all** of the customer's orders and paginate in Java (HHH90003004) — the opposite of what the README claimed. It also
joined every product although only `product.id` is returned.
**Fix:** page query without collection fetch; items of the page loaded by batch fetching.
`QueryCountIntegrationTest` asserts the exact statement count.

---

## 2. Overengineering (removed or simplified)

| What | Why it was unjustified | Now |
| --- | --- | --- |
| `CreateProductValidator`, `UpdateProductValidator`, `CreateOrderValidator`, `OrderItemValidator`, `DiscountValidator`, `ValidationResult` | Three of them re-checked Bean Validation (`null`, `<= 0`); the rest wrapped one `if` each and forced every service into `ValidationResult → Result` translation | Deleted. Shape validation = `@Valid`; DB-state rules are plain `if`s in the service |
| 10 `AppException` subclasses + `AppException` + handler branch | Never thrown (only `ProductNotFoundException` in one controller): a second, dead error system next to `Result` | Deleted; one system (`Result` for expected outcomes, exceptions only for unexpected ones) |
| `PaginationRequest` (165 lines, 8 factory methods, `getValidatedPage/Size` clamping already guaranteed by `@Min/@Max`) | Client-supplied `sortBy` was accepted but ignored by the product endpoint and would have raised `PropertyReferenceException` (500) on others | 40 lines: `page`, `size`, `toPageable(Sort)`; each endpoint picks its sort |
| `CreateProductRequest` + `UpdateProductRequest` | Byte-for-byte identical | One `ProductRequest` |
| Test-only overloads (`listProducts(name, category, page, size)`, `listAllOrders(...)`, `OrderSpecification.filterBy(String, PaymentMethod)`), `OrderItemRepository`, unused repo methods (`findByName`, `existsByCode`, `findByUserId`, `findByAction`, ...) | Production code that only exists for tests or for nobody | Deleted |
| Manual `DaoAuthenticationProvider` bean | Spring Boot builds it from `UserDetailsService` + `PasswordEncoder` | Deleted (`ApplicationConfig` gone) |
| `CustomAccessDeniedHandler` + `JwtAuthenticationEntryPoint` | Same 30 lines twice | `SecurityErrorHandler` |
| Hand-written `@Builder` constructors with `if (id != null) setId(id)` on 4 entities, `@AllArgsConstructor` on entities | Boilerplate caused by `@Builder` not seeing superclass fields | `@SuperBuilder` |
| `@PrePersist` id fallback in 3 classes next to `@UuidGenerator`; separate `UuidUtils` | Two mechanisms for one thing | `UuidV7Generator` only |
| `ErrorCode.code` string equal to the enum name; `Result.map/ifSuccess`; `ApiResponse.message`; `messages_en.properties` (identical to the default file) | Dead or duplicated | Removed |
| `ApiResponse`, `ApiErrorResponse`, `PageResponse`, `PaymentResult` with Lombok builder/setters/all-args | Immutable data carriers | records |
| Payment processors re-validated required fields and logged every call | Request-shape validation + noise | Fields validated in `PaymentRequest`; processors only decide accept/decline |
| Hikari block (`minimum-idle 5`, arbitrary timeouts) | Numbers without a reason; a fixed-size pool is what Hikari recommends | Removed, defaults used |
| `OrderStatus.FAILED/CANCELLED` | Never produced (a failed checkout leaves no order) | Removed |

Patterns that were kept because they earn their place: `PaymentProcessor` strategy (+ small factory), service
interfaces (as requested), JPA `Specification` for the two dynamic filters, `Result` for expected business outcomes,
Spring Data auditing, MapStruct.

## 3. Duplication (removed)

* Audit call boilerplate: `recordAudit(action, "PRODUCT", id.toString(), String.format(...))` ×5 → `recordAudit(action, id, details)`; the entity type
  now belongs to the `AuditAction`. Application log lines that repeated what the audit table already records were removed.
* Pagination validation repeated in each service → one place.
* `existsById` + `findById` (two queries) in update → one `findById`.
* Order building/`saveAll`/`save` on already-managed entities → dirty checking.

## 4. Performance Problems

Read endpoints are measured with Hibernate statistics (`QueryCountIntegrationTest`), so their counts do not depend on the number of rows; the write path and login are by inspection:

| Endpoint | Statements | Notes |
| --- | --- | --- |
| `GET /api/products` | 2 (page + count) | only the page's rows are hydrated |
| `GET /api/orders/my` | 3 (page + count + all items of the page) | no product loaded |
| `GET /api/orders` (admin) | 3 | customer joined into the page query |
| `POST /api/orders` | 1 (products) + 1 (discount, if used) + batched UPDATEs/INSERTs | no `SELECT` per line, no `SELECT` for the user (`getReferenceById`) |
| `POST /api/auth/login` | 1 (user) + one BCrypt (intended CPU cost) | |

Also fixed: in-memory pagination (C6); `default_batch_fetch_size` replaces per-entity `@BatchSize`; repeated lines of one
product in an order are merged (previously each line was checked against stock separately and stock was deducted per
line); order lines capped at 100 to bound the `IN (...)` list.

## 5. Architecture Problems

* Service methods returned `Result.failure` **after** mutating state (C1). Rule now documented in `OrderServiceImpl`.
* Two error-handling systems (`Result` and exceptions) → one.
* `StockStatus` (a domain rule) lived in the `dto.response` package and was computed inside a MapStruct mapper
  `default` method → the rule is `Product.getStockStatus()`, the mapper is pure field mapping.
* `DiscountService.validateAndApplyDiscount` mutated state while its name promised validation → `redeem`.
* `listMyOrders` returned `Result` although "unauthenticated" is unreachable behind `@PreAuthorize` → plain page; the
  "no user" case is an `IllegalStateException` (a bug, 500), not a business result.
* Class-level `@AllArgsConstructor` on entities, `is_deleted` field named `isDeleted` (bad Lombok/MapStruct interplay) → `deleted`.

## 6. Security Problems

C3 (secrets/seed/prod), C4 (filter swallowing), plus:
* `Decoders.BASE64.decode(secret)` on **every** token operation → the key is parsed once at startup and validated
  (must be Base64, ≥ 256 bit).
* JWT body carried `userId` and `role` claims that nothing read (the filter reloads the user) → removed; a role change or
  a deleted user takes effect immediately.
* Swagger was public in every environment → off in `prod`.
* 500 responses: generic body, no exception/SQL/stack trace; details only in the log. `debug` field only when
  `application.error.include-debug-details=true` (dev). Verified with a test that puts a JDBC URL and a password in the
  exception message and asserts none of it reaches the client.
* No length limits on `name`, `discountCode`, `items` → added.

Checked and fine: `@PreAuthorize` on every endpoint, stateless sessions, BCrypt, generic `INVALID_CREDENTIALS` for both
unknown user and wrong password, no password in any response, audit fields cannot be forged through the request body
(`SpringAuditingIntegrationTest`).

## 7. Validation Problems — what is Bean Validation, what stays business validation

| Rule | Where |
| --- | --- |
| blank name, missing category/price/cost/quantity, negative price/cost/quantity, name > 150, price precision | `ProductRequest` (Bean Validation) |
| empty `items`, > 100 items, `quantity < 1`, missing payment, description length | `CreateOrderRequest`, `OrderItemRequest` |
| "card number required for CreditCard", "phone + wallet password required for XyzWallet" | `PaymentRequest` (`@AssertTrue`) — used to be scattered in the processors |
| `page >= 0`, `1 <= size <= 50` | `PaginationRequest` |
| `PRODUCT_NOT_FOUND`, `PRODUCT_NAME_ALREADY_EXISTS`, `INSUFFICIENT_STOCK`, `DISCOUNT_*`, `PAYMENT_FAILED`, `PAYMENT_METHOD_NOT_SUPPORTED`, `INVALID_CREDENTIALS` | services, returned as `Result.failure(ErrorCode.X[, arg])` |
| unexpected DB / NPE / infrastructure | exception → `GlobalExceptionHandler` → 500 |

Result API is exactly `Result.failure(ErrorCode.PRODUCT_NOT_FOUND)` / `Result.failure(ErrorCode.PRODUCT_NOT_FOUND, id)`.
`ProductServiceImpl.createProduct` went from 45 lines with a validator round-trip to: check name, save, audit, map.
Validation error body: `{ "code": "VALIDATION_ERROR", "message": "...", "validationErrors": { "field": "message" } }`
(a map keyed by field rather than an array — one message per field, deterministic order).

## 8. Database Problems

Flyway `V3__review_hardening.sql` (V1/V2 untouched — applied migrations must not change; V3 itself was never released, so it was reworked in round 2, see section 12):

* `discount_codes.version` (C5).
* Discount lookups use the upper-cased code with `WHERE code = ?`, but V1 indexed `UPPER(code)` (unusable for that
  query) → one unique index on `code`, data normalised.
* **Dropped 12 indexes** nothing can use (each one a write penalty) and replaced 3 (`idx_orders_payment_method`, the two discount-code indexes): the four `is_deleted` boolean indexes (the column itself is gone, section 12); `idx_users_username`
  (duplicate of the unique constraint), `idx_products_name` (duplicate of the unique index; `LIKE '%x%'` cannot use a
  b-tree anyway), `idx_orders_customer_id` (prefix of `(customer_id, created_at)`), `idx_orders_status` (one value),
  `idx_order_items_product_id` (products are never hard-deleted), three of four `audit_logs` indexes (write-only table).
* Added `orders(payment_method, created_at DESC)` for the admin filter + newest-first + `LIMIT`.
* JPA `@Index` annotations were out of sync with Flyway (they drive the H2 test schema) → aligned; `Order.items` now uses
  `@OnDelete(CASCADE)` like the FK in V1 and `cascade = PERSIST` instead of `ALL + orphanRemoval`.
* `@Version Long version = 0L` on `Product` made Spring Data treat every new product as detached (`merge` → extra `SELECT`)
  → left `null` for new entities.
* Java-side `LocalDateTime` for `expires_at timestamptz` (time-zone dependent, `now()` inside the entity) → `Instant` +
  injectable `Clock`.
* Name uniqueness: the unique index on `LOWER(name)` (`uq_products_name`) is the final guard; the application pre-check uses the same `lower()`
  expression, and the constraint violation of a real race is mapped to 409.

UUID v7: kept. Spring Boot 3.4 ships Hibernate 6.6 (no built-in v7; that arrives with Hibernate 7 / Boot 4) and PostgreSQL 16/17 has no
`uuidv7()`. FasterXML JUG through a 1-class generator is the smallest correct option; v4 is not used anywhere.

## 9. Refactoring Done — summary

* Error handling: one system, one handler, one response shape; Spring MVC errors keep their 4xx.
* `OrderServiceImpl.createOrder`: linear, commented flow — load products (one query, merge lines) → validate stock →
  totals → discount → final total → reserve stock + redeem discount (flush) → pay (rollback on decline) → order + items → audit.
  Nine collaborators → seven; no repository call in a loop; no manual timestamps or audit metadata.
* Profiles and configuration (`application.yml` / `-dev` / `-prod` / test), `JwtProperties` (validated), no secrets in prod config.
* Entities on `@SuperBuilder`; DTO carriers as records; MapStruct for all mapping, business rules out of the mappers.
* Schema/index changes (V3), see section 8.
* Docs (`README`, `SUBMISSION`) rewritten: the old ones claimed a `userId` JWT claim, Hikari tuning, in-memory-free
  pagination and inconsistent test counts (101 / 117) that did not match the code.

## 10. Verification

See section 12 for the final numbers: unit and integration tests on H2 and on PostgreSQL (Docker), the application started from `docker compose` + `./mvnw spring-boot:run`
and exercised through HTTP only (300 checks), `prod` profile boot checks, and an OpenAPI document compared with the real behaviour.

## 11. Remaining Decisions (need a human)

1. **Payment vs. stock order (deliberate deviation from the requested flow).** The requested flow was
   *validate → pay → update stock → create order*. With that order a concurrent order that wins the stock race makes the
   loser's commit fail **after** the customer was charged. The implemented order is *validate → reserve stock/discount (flush) →
   pay → create order*, so conflicts surface before money moves, and a decline rolls the reservation back. The price: the
   row locks of the reservation are held while the payment processor runs. Fine for the current in-process simulation; with a
   real gateway use *authorize → reserve → capture* (or refund on failure) and keep the call short. Decide when a real gateway is chosen.
2. **First admin in production.** The seeder is dev/test only, so production needs an ops procedure (SQL insert with a BCrypt hash, or an
   explicit bootstrap command) — intentionally not built.
3. **`spring.profiles.default=dev`.** Convenient (`./mvnw spring-boot:run` just works) but a deployment that forgets
   `SPRING_PROFILES_ACTIVE=prod` runs with dev settings. Alternative: default to `prod` and make dev opt-in.
4. **JWT filter reloads the user on every request** (1 indexed query). Chosen for immediate revocation; removing it saves a query per request
   but makes tokens valid until expiry.
5. **Validation messages are English only** (business errors are localized EN/AR). Localizing Bean Validation messages needs message keys in the annotations.
6. **`name LIKE '%x%'`** cannot use a b-tree. Fine for a catalogue of this size; add `pg_trgm` + GIN if it grows.
7. **Role-dependent `GET /api/products` returns `PageResponse<?>`** (admin vs customer row type). Two endpoints or a sealed row type would be stricter; kept one endpoint per the spec.
8. **`BaseController` uses one `@Autowired` field and `HttpServletRequest` parameters** to build the error `path`. Pragmatic; a `ResponseBodyAdvice`/exception-free alternative would add more code than it removes.
9. **`Order.status` has a single value (`COMPLETED`)**: keep the column for a future cancellation feature, or drop it.
10. **CI needs a PostgreSQL** for `PostgresSchemaIntegrationTest`: a Docker daemon (Testcontainers 1.21.4, which works with Docker Engine 29) or a service container passed with `-Dtest.postgres.url`. Without either the class is skipped and the PostgreSQL-only guarantees go untested.

---

## 12. Round 2 — scope freeze, API contract, external testing

**Decisions applied:** no product deletion, keep the audit log (no endpoints, not exposed), keep English/Arabic messages (services return stable `ErrorCode`s only),
accept enum values in any letter case, no new features.

### Scope
* `DELETE /api/products/{id}` removed with everything it needed: `deleteProduct`, `softDelete`, the `PRODUCT_DELETED` audit action, `BaseController.toNoContent`, tests, docs.
  Nothing sets the soft-delete flag any more, so the flag itself went too: `is_deleted` column, `@SQLRestriction` on four entities, the partial unique indexes
  (now plain `uq_products_name` on `LOWER(name)` and `uq_discount_codes_code`), all in `V3`.
* Unused API surface removed: `cardExpiry` and `cvv` (accepted, never validated or used) and the constant `status: COMPLETED` of the admin order response
  (the column stays as internal state).
* Kept on purpose: `audit_logs` (traceability, internal only), Arabic messages, the `customerId` order filter (an alternative way to filter by customer).

### Enum input
`spring.jackson.mapper.accept-case-insensitive-enums` for request bodies and one small `ConverterFactory` for query parameters (the default conversion there is case-sensitive):
`furniture`, `Furniture`, `FURNITURE`, `credit_card` are accepted; responses use the assignment's spelling (`furniture`, `low`, `CreditCard`); unknown values are still a 400. Covered by `EnumInputCaseIntegrationTest`.

### Bugs found only by testing the running application from the outside
| # | Finding | Fix |
| --- | --- | --- |
| 1 | **Login during a database outage answered 401** (`InternalAuthenticationServiceException` is an `AuthenticationException`, and `AuthServiceImpl` treated all of them as bad credentials) | only `BadCredentialsException` is a business outcome; the rest is a 500 |
| 2 | **Any authenticated request during a database outage answered 401 on `/error`**: the JWT filter runs before Spring MVC, its exception left the filter chain and was blocked by the security rules | the filter hands unexpected exceptions to the MVC exception resolver: the same JSON 500 as everywhere (`JwtAuthenticationFilterTest`) |
| 3 | **Invalid query values leaked internals**: `?category=toys` answered `validationErrors` with "Failed to convert property value … to required type `com.alqaseh.ecommerce...ProductCategory`" | binding failures get the generic, localised message "Invalid value" |
| 4 | **`PUT /api/products/{id}` returned the OLD `updatedAt/updatedBy`**: the audit fields are filled at flush, after the response was built; "who last updated it and when" is an assignment requirement | flush before building the response (`ProductControllerIntegrationTest`, mutation-checked) |
| 5 | `createdAt` in the create response had nanosecond precision while PostgreSQL stores microseconds, so the value changed between the create response and the next read | auditing timestamps are truncated to microseconds |
| 6 | Testcontainers 1.20.4 cannot talk to Docker Engine 29: `PostgresSchemaIntegrationTest` was silently skipped | Testcontainers 1.21.4 |

### Swagger / OpenAPI
Generated by springdoc from the controllers and DTOs, completed centrally in `OpenApiConfig` (responses, typed envelope schemas, examples) with the real examples in `ApiExamples`.
Before: list parameters were one opaque `filter` object, every endpoint showed only `200 OK` with an empty schema (although creates answer 201), and login was marked as secured.
Now: expanded query parameters with defaults and limits; per endpoint every real response (`201`, `400` as `VALIDATION_ERROR` or `BAD_REQUEST`, `401`, `402`, `403`, `404`, `409`, `500`);
request examples; admin / customer / empty-page examples; bearer authorisation only where required. `OpenApiDocumentationTest` guards it (exact endpoint set, no DELETE, statuses per endpoint,
every error example uses an existing `ErrorCode` with its real status and English message). The external test additionally checks that every status/code observed on the real API is
documented and that every documented case was observed.

### Verification (final)
| What | Result |
| --- | --- |
| `./mvnw clean test` | 159 tests, 0 failures, 0 skipped |
| the same suite on PostgreSQL 17 from `docker compose` (Flyway V1+V3, `ddl-auto=validate`) | 159 tests, 0 failures |
| `PostgresSchemaIntegrationTest` through Testcontainers | 5 tests pass |
| **External HTTP test** against `./mvnw spring-boot:run` + PostgreSQL from `docker compose up -d` (Node client, HTTP only, no service calls): login, 401/403 for every endpoint, product create/update/duplicates (10 parallel creates give one 201), stock statuses 0/4/5/9/10, filters, pagination, empty pages, every discount and payment case, declined payment rolls back, customer/admin lists with profit, concurrent orders, one discount code used by 6 parallel orders, validation/business/error handling, database outage answered with 500 and recovery, OpenAPI comparison | 300 checks, 0 failures |

Not testable on this machine: the default ports. A local PostgreSQL service occupies 5432 and another application 8080, so the documented start was tested with the documented overrides
(`DB_PORT=5433`, `SERVER_PORT=18080`). The final checks ran against the Docker PostgreSQL, never the embedded one.
