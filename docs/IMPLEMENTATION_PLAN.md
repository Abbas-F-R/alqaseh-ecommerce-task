> **Historical document.** This is the original planning blueprint. It predates the code review and parts of it are no longer accurate
> (validators, exception classes, seed data in a migration, the payment-before-stock order, index list, ...).
> The current behaviour is described in [README.md](../README.md), the reasons for the changes in [CODE_REVIEW.md](../CODE_REVIEW.md).

# Al Qaseh — E-Commerce REST API Take-Home Assignment
## Master Implementation Plan & Architectural Blueprint

**Project Name:** `alqaseh-ecommerce-api`  
**Target Directory:** `C:\Users\husean01\IdeaProjects\alqaseh-ecommerce-api`  
**Base Reference Project:** `C:\Users\husean01\IdeaProjects\Graduation_Project_BE`  
**Target Role:** Senior Java / Spring Boot Backend Engineer  
**Candidate:** Abbas  
**Reviewer:** Bahaa Qasim Al-Musawi (Al Qaseh Development Department)

---

## 1. Executive Summary & Goals

This project implements a production-grade Monolithic RESTful API for a small e-commerce application. The system strictly adheres to Clean Code, SOLID principles, Feature-First architecture, Test-Driven Development (TDD), safe concurrency controls, robust database constraints, database-level pagination, multi-language localization (EN/AR), and a clear separation between operational logging and business audit trails.

No non-essential features (e.g., registration endpoints, user management, carts, microservices, Kafka, Redis, GraphQL) are added. The goal is pristine software craftsmanship and engineering depth.

---

## 2. Technology Stack & Environment

| Component | Choice | Rationale / Specification |
| :--- | :--- | :--- |
| **Language** | Java 21 (LTS) | Installed at `C:\Users\husean01\.jdks\ms-21.0.11` |
| **Framework** | Spring Boot 3.4.3 | Latest stable Boot 3 release on Java 21 |
| **Build Tool** | Apache Maven 3.9.x | Maven-based build with wrapper (`mvnw`) |
| **Database** | PostgreSQL 16 | Primary production database with Flyway migrations |
| **Persistence** | Spring Data JPA / Hibernate 6.x | `ddl-auto=validate`, strictly driven by Flyway |
| **Migrations** | Flyway (`flyway-core`, `flyway-database-postgresql`) | `V1__init_schema.sql`, `V2__seed_data.sql` |
| **Security** | Spring Security 6 + JJWT (`0.12.6`) | Stateless, BCrypt hashing, `@PreAuthorize` role checking |
| **Validation** | Jakarta Bean Validation (`hibernate-validator`) | Request-level validation |
| **API Docs** | Springdoc OpenAPI 2.8.5 (Swagger UI) | Interactive documentation at `/swagger-ui/index.html` |
| **Logging** | SLF4J + Logback | Structured technical logs, sanitized of credentials |
| **Audit Log** | Database-backed `audit_logs` table | Business & security event traceability |
| **Localization** | Spring `MessageSource` (`messages_en`, `messages_ar`) | `Accept-Language` support with stable error codes |
| **Testing** | JUnit 5, Mockito, AssertJ, Testcontainers PostgreSQL | High unit and integration test coverage |

---

## 3. Findings & Patterns Inherited from Base Project (`Graduation_Project_BE`)

After inspecting `Graduation_Project_BE`, the following patterns and setups were analyzed and will be incorporated or upgraded:

1. **Spring Security & JWT Filter:**
   - *From Base:* `JwtAuthenticationFilter` with Bearer token extraction and `UsernamePasswordAuthenticationToken`.
   - *Upgrade for Al Qaseh:* Upgrade JJWT to modern API (0.12.x), eliminate broad `permitAll()` routes, apply strict "secure-by-default" policy where only `/api/auth/login` and Swagger UI are public, and attach `CustomAccessDeniedHandler` and `JwtAuthenticationEntryPoint` returning localized JSON errors.
2. **JPA Auditing:**
   - *From Base:* `AuditConfig` with `AuditorAware` extracting user identity from `SecurityContextHolder`.
   - *Application in Al Qaseh:* Used for `Product` entity audit fields (`createdBy`, `createdAt`, `updatedBy`, `updatedAt`).
3. **Environment & Configuration:**
   - *From Base:* Decoupled credentials via `.env` / externalized properties.
   - *Application in Al Qaseh:* `application.yml` with sensible local defaults and environment variable overrides (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`).
4. **Architecture Shift:**
   - *From Base:* Layered packaging (`Controllers/`, `Dto/`, `Model/`, `Repository/`, `Service/`).
   - *Upgrade for Al Qaseh:* **Feature-First Architecture with Internal Layers** as explicitly demanded in the prompt.

---

## 4. Package Structure (Feature-First Architecture)

```text
com.alqaseh.ecommerce
│
├── AlQasehEcommerceApplication.java
│
├── auth
│   ├── controller
│   │   └── AuthController.java
│   ├── dto
│   │   ├── LoginRequest.java
│   │   └── LoginResponse.java
│   └── service
│       ├── AuthService.java
│       └── CustomUserDetailsService.java
│
├── product
│   ├── controller
│   │   └── ProductController.java
│   ├── dto
│   │   ├── CreateProductRequest.java
│   │   ├── UpdateProductRequest.java
│   │   ├── AdminProductResponse.java
│   │   ├── CustomerProductResponse.java
│   │   └── StockStatus.java
│   ├── entity
│   │   ├── Product.java
│   │   └── ProductCategory.java
│   ├── mapper
│   │   └── ProductMapper.java
│   ├── repository
│   │   ├── ProductRepository.java
│   │   └── ProductSpecification.java
│   └── service
│       └── ProductService.java
│
├── order
│   ├── controller
│   │   └── OrderController.java
│   ├── dto
│   │   ├── CreateOrderRequest.java
│   │   ├── OrderItemRequest.java
│   │   ├── AdminOrderResponse.java
│   │   ├── CustomerOrderResponse.java
│   │   └── OrderItemResponse.java
│   ├── entity
│   │   ├── Order.java
│   │   ├── OrderItem.java
│   │   └── OrderStatus.java
│   ├── mapper
│   │   └── OrderMapper.java
│   ├── repository
│   │   ├── OrderRepository.java
│   │   └── OrderSpecification.java
│   └── service
│       └── OrderService.java
│
├── discount
│   ├── entity
│   │   └── DiscountCode.java
│   ├── repository
│   │   └── DiscountCodeRepository.java
│   └── service
│       └── DiscountService.java
│
├── payment
│   ├── dto
│   │   ├── PaymentDetails.java
│   │   ├── CreditCardDetails.java
│   │   └── XyzWalletDetails.java
│   ├── entity
│   │   └── PaymentMethod.java
│   ├── processor
│   │   ├── PaymentProcessor.java
│   │   ├── CreditCardPaymentProcessor.java
│   │   ├── XyzWalletPaymentProcessor.java
│   │   └── PaymentProcessorFactory.java
│   └── service
│       └── PaymentResult.java
│
├── shared
│   ├── audit
│   │   ├── entity
│   │   │   ├── AuditAction.java
│   │   │   └── AuditLog.java
│   │   ├── repository
│   │   │   └── AuditLogRepository.java
│   │   └── service
│   │       └── AuditService.java
│   ├── exception
│   │   ├── AppException.java
│   │   ├── ResourceNotFoundException.java
│   │   ├── ProductNotFoundException.java
│   │   ├── OrderNotFoundException.java
│   │   ├── InsufficientStockException.java
│   │   ├── DiscountNotFoundException.java
│   │   ├── DiscountExpiredException.java
│   │   ├── DiscountAlreadyUsedException.java
│   │   ├── MinimumOrderTotalNotMetException.java
│   │   ├── PaymentFailedException.java
│   │   ├── DuplicateResourceException.java
│   │   └── GlobalExceptionHandler.java
│   ├── localization
│   │   ├── LocalizationService.java
│   │   └── MessageKey.java
│   ├── response
│   │   ├── ApiResponse.java
│   │   ├── ApiErrorResponse.java
│   │   └── PageResponse.java
│   └── validation
│       └── ValidationGroup.java
│
├── infrastructure
│   ├── persistence
│   │   └── BaseAuditableEntity.java
│   ├── security
│   │   ├── JwtService.java
│   │   ├── JwtAuthenticationFilter.java
│   │   ├── JwtAuthenticationEntryPoint.java
│   │   ├── CustomAccessDeniedHandler.java
│   │   ├── SecurityUtils.java
│   │   └── UserPrincipal.java
│   └── user
│       ├── entity
│       │   ├── Role.java
│       │   └── User.java
│       └── repository
│           └── UserRepository.java
│
└── config
    ├── SecurityConfig.java
    ├── ApplicationConfig.java
    ├── AuditConfig.java
    ├── OpenApiConfig.java
    ├── WebConfig.java
    └── DataSeeder.java
```

---

## 5. Domain Model & Database Schema

### 5.1 Entities

1. **`users`**
   - `id`: BIGSERIAL / UUID (using `BIGSERIAL` for primary keys for clean relational indexing)
   - `username`: VARCHAR(50) UNIQUE NOT NULL
   - `password`: VARCHAR(255) NOT NULL (BCrypt hash)
   - `role`: VARCHAR(20) NOT NULL (`ADMIN`, `CUSTOMER`)
   - `created_at`: TIMESTAMP NOT NULL

2. **`products`**
   - `id`: BIGSERIAL PRIMARY KEY
   - `name`: VARCHAR(150) UNIQUE NOT NULL
   - `category`: VARCHAR(50) NOT NULL (`FURNITURE`, `ELECTRONICS`, `BEAUTY`, `GARDEN`)
   - `price`: NUMERIC(12, 2) NOT NULL CHECK (price >= 0)
   - `cost`: NUMERIC(12, 2) NOT NULL CHECK (cost >= 0)
   - `available_quantity`: INT NOT NULL CHECK (available_quantity >= 0)
   - `version`: BIGINT NOT NULL DEFAULT 0 (Optimistic Locking for concurrency)
   - `created_by`: VARCHAR(50) NOT NULL
   - `created_at`: TIMESTAMP NOT NULL
   - `updated_by`: VARCHAR(50)
   - `updated_at`: TIMESTAMP

3. **`discount_codes`**
   - `id`: BIGSERIAL PRIMARY KEY
   - `code`: VARCHAR(50) UNIQUE NOT NULL
   - `amount`: NUMERIC(12, 2) NOT NULL CHECK (amount > 0)
   - `minimum_order_total`: NUMERIC(12, 2) NOT NULL CHECK (minimum_order_total >= 0)
   - `expires_at`: TIMESTAMP NOT NULL
   - `used`: BOOLEAN NOT NULL DEFAULT FALSE

4. **`orders`**
   - `id`: BIGSERIAL PRIMARY KEY
   - `customer_id`: BIGINT NOT NULL REFERENCES users(id)
   - `subtotal_amount`: NUMERIC(12, 2) NOT NULL CHECK (subtotal_amount >= 0)
   - `discount_amount`: NUMERIC(12, 2) NOT NULL DEFAULT 0.00 CHECK (discount_amount >= 0)
   - `total_amount`: NUMERIC(12, 2) NOT NULL CHECK (total_amount >= 0)
   - `total_cost`: NUMERIC(12, 2) NOT NULL CHECK (total_cost >= 0)
   - `discount_code_id`: BIGINT REFERENCES discount_codes(id)
   - `payment_method`: VARCHAR(30) NOT NULL (`CREDIT_CARD`, `XYZ_WALLET`)
   - `status`: VARCHAR(30) NOT NULL (`COMPLETED`, `FAILED`, `CANCELLED`)
   - `created_at`: TIMESTAMP NOT NULL

5. **`order_items`**
   - `id`: BIGSERIAL PRIMARY KEY
   - `order_id`: BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE
   - `product_id`: BIGINT NOT NULL REFERENCES products(id)
   - `product_name`: VARCHAR(150) NOT NULL
   - `unit_price`: NUMERIC(12, 2) NOT NULL CHECK (unit_price >= 0) *(Snapshot)*
   - `unit_cost`: NUMERIC(12, 2) NOT NULL CHECK (unit_cost >= 0) *(Snapshot)*
   - `quantity`: INT NOT NULL CHECK (quantity > 0)

6. **`audit_logs`**
   - `id`: BIGSERIAL PRIMARY KEY
   - `user_id`: VARCHAR(50) NOT NULL
   - `action`: VARCHAR(50) NOT NULL (`PRODUCT_CREATED`, `PRODUCT_UPDATED`, `ORDER_CREATED`, `DISCOUNT_APPLIED`)
   - `entity_type`: VARCHAR(50) NOT NULL (`PRODUCT`, `ORDER`, `DISCOUNT`)
   - `entity_id`: VARCHAR(50) NOT NULL
   - `timestamp`: TIMESTAMP NOT NULL
   - `details`: TEXT

---

## 6. Key Design Decisions

### Decision 1: Order Item Snapshotting (REQUIRED)
- At the moment an order is finalized, the current `Product.price` and `Product.cost` are captured into `OrderItem.unitPrice` and `OrderItem.unitCost`.
- Historical orders and profit calculations are guaranteed immutable, regardless of future price or cost modifications on the catalog.

### Decision 2: Profit Calculation
- Admin Order Listing computes profit for each order as:
  $$\text{Profit} = \text{Total Revenue after Discount} - \text{Total Historical Cost}$$
  $$\text{Total Historical Cost} = \sum (\text{unitCost} \times \text{quantity})$$
  $$\text{Total Revenue after Discount} = \text{Total Order Amount}$$
- The discount directly reduces the merchant's recognized revenue.

### Decision 3: Stock Concurrency & Contention Handling
- The `Product` entity includes `@Version private Long version;` (Optimistic Locking).
- Under concurrent checkouts attempting to purchase the same inventory:
  1. The transaction checks available quantity.
  2. If insufficient, immediately throws `InsufficientStockException` (`400 BAD_REQUEST`).
  3. If another transaction commits stock deduction first, Hibernate throws `OptimisticLockingFailureException`.
  4. The application catches this in the checkout service or exception handler and translates it into a localized, user-friendly `StockConflictException` (`409 CONFLICT`), preventing double-spending without deadlock risks of heavy table locks.
  5. Tested with multi-threaded concurrency unit/integration tests.

### Decision 4: Differential Stock Visibility (Admin vs. Customer)
- Customer DTO (`CustomerProductResponse`):
  - Never reveals `cost` or `availableQuantity`.
  - Exposes `stockStatus`:
    - `0` to `4` $\rightarrow$ `"LOW"`
    - `5` to `9` $\rightarrow$ `"LIMITED"`
    - `10+` $\rightarrow$ `"AVAILABLE"`
- Admin DTO (`AdminProductResponse`):
  - Exposes exact `availableQuantity` and `cost`.
  - Role-based mapping handled cleanly in `ProductService` / `ProductController`.

### Decision 5: Strategy Pattern for Payment Processing
- `PaymentProcessor` interface defines `PaymentResult processPayment(BigDecimal amount, PaymentDetails details)`.
- `CreditCardPaymentProcessor`: Validates card format, mocks authorization.
- `XyzWalletPaymentProcessor`: Requires `phoneNumber` and `walletPassword`.
- **Security Rule:** Neither the wallet password nor credit card CVV/details are ever logged. `PaymentProcessorFactory` resolves the processor dynamically based on `PaymentMethod` enum, eliminating `if/else` sprawl.

### Decision 6: Localization (i18n) & Stable Error Codes
- Spring `MessageSource` backed by `messages_en.properties` and `messages_ar.properties`.
- Resolves language using `Accept-Language: en` (default) or `Accept-Language: ar`.
- API error responses return:
  ```json
  {
    "timestamp": "2026-09-28T21:35:00Z",
    "status": 400,
    "code": "INSUFFICIENT_STOCK",
    "message": "الكمية المطلوبة غير متوفرة في المخزون",
    "path": "/api/orders"
  }
  ```
- **Rule:** Clients branch logic based on `code`, not `message`.

### Decision 7: Separation of Operational Logging vs. Audit Logging
- **Application Logging (SLF4J / Logback):** Technical diagnostic log statements (`INFO`, `WARN`, `ERROR`). Includes contextual IDs (`orderId`, `productId`, `customerId`). Never contains credentials, tokens, or card numbers.
- **Audit Logging (`audit_logs` table):** Persistent compliance record of business transactions (`ORDER_CREATED`, `PRODUCT_CREATED`, `PRODUCT_UPDATED`, `DISCOUNT_APPLIED`). Stored synchronously or asynchronously within the transaction lifecycle.

---

## 7. API Endpoints Specification

### 7.1 Authentication
| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/login` | Public | Authenticates username & password, returns JWT token |

### 7.2 Products
| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/products` | `ADMIN` | Create new product (unique name, valid category) |
| `PUT` | `/api/products/{id}` | `ADMIN` | Update existing product details & stock |
| `GET` | `/api/products` | `ADMIN` & `CUSTOMER` | List products with pagination, name filter, category filter. Returns `AdminProductResponse` for Admin and `CustomerProductResponse` for Customer |

### 7.3 Orders
| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/orders` | `CUSTOMER` | Place an order with 1+ items, optional discount, and payment details |
| `GET` | `/api/orders/my` | `CUSTOMER` | List authenticated customer's orders |
| `GET` | `/api/orders` | `ADMIN` | List all orders with pagination, customer filter, payment method filter, and profit calculation |

---

## 8. Order Creation Flow (`@Transactional`)

```text
1. Validate CreateOrderRequest (Bean Validation: items not empty, quantity > 0)
             ↓
2. Identify authenticated Customer from SecurityContext
             ↓
3. Load Products by IDs with version check
             ↓
4. Validate Stock (Ensure product.availableQuantity >= requestedQuantity)
             ↓
5. Calculate Subtotal = Sum(unitPrice * quantity)
             ↓
6. If Discount Code provided:
   a. Verify code exists in discount_codes
   b. Verify not expired (expiresAt > now)
   c. Verify not used (used == false)
   d. Verify subtotal >= minimumOrderTotal
   e. Verify discountAmount <= subtotal (non-negative total)
   f. Mark discount as used
             ↓
7. Compute Final Total = Subtotal - DiscountAmount
             ↓
8. Calculate Total Cost = Sum(unitCost * quantity)
             ↓
9. Execute Payment via PaymentProcessor (CreditCard or XyzWallet)
   If payment fails -> throw PaymentFailedException (Transaction rolls back)
             ↓
10. Deduct Product Stock (availableQuantity -= requestedQuantity)
             ↓
11. Persist Order & OrderItems (capturing snapshots: unitPrice, unitCost)
             ↓
12. Create persistent AuditLog entries (ORDER_CREATED, DISCOUNT_APPLIED)
             ↓
13. Transaction commits -> Return OrderResponse
```

---

## 9. Testing Strategy (TDD Approach)

### Unit Tests
- **DiscountServiceTest:**
  - Valid discount successfully applied.
  - Expired discount rejected.
  - Already used discount rejected.
  - Order subtotal below minimum threshold rejected.
  - Discount greater than total handled cleanly without negative totals.
- **ProductStockVisibilityTest:**
  - 0–4 returns `LOW`.
  - 5–9 returns `LIMITED`.
  - 10+ returns `AVAILABLE`.
  - Admin receives exact quantities and costs.
- **PaymentProcessorTest:**
  - CreditCard processor simulation success & failure.
  - XyzWallet processor simulation with valid & invalid phone/password.
- **OrderServiceTest:**
  - Multi-item calculation, subtotal, discount, snapshot retention, profit calculation.
  - Stock deficit throwing `InsufficientStockException`.

### Integration Tests
- **Testcontainers PostgreSQL:**
  - Full Spring Boot test environment testing against actual PostgreSQL.
  - Flyway migrations execution from scratch.
  - Database constraint enforcement (unique product name, unique discount code, positive prices).
  - High-concurrency checkout race-condition tests (multiple threads competing for limited stock).
  - Security integration tests (verifying customer cannot access admin endpoints, admin cannot place orders, customer only sees their own orders).

---

## 10. Seed Data Credentials (Documented for Submission)

To test the system immediately out of the box, Flyway seed migration `V2__seed_data.sql` will populate:

| Role | Username | Password | Notes |
| :--- | :--- | :--- | :--- |
| **ADMIN** | `admin` | `Admin123!` | System administrator |
| **CUSTOMER** | `customer1` | `Customer123!` | Regular customer |
| **CUSTOMER** | `customer2` | `Customer123!` | Regular customer |

**Pre-seeded Discount Codes:**
- `WELCOME10`: 10,000 IQD discount, min order 50,000 IQD, not expired.
- `EXPIRED50`: 50,000 IQD discount, min order 100,000 IQD, expired yesterday.
- `USED25`: 25,000 IQD discount, min order 60,000 IQD, already used.

---

## 11. Implementation Order (Roadmap)

1. **Bootstrap Project:** Setup directory `C:\Users\husean01\IdeaProjects\alqaseh-ecommerce-api`, `pom.xml`, Maven wrapper, `.gitignore`.
2. **Flyway Migrations:** `V1__init_schema.sql` (tables, constraints, indexes) & `V2__seed_data.sql`.
3. **Shared Infrastructure:** Base entities, standardized `ApiResponse`, `ApiErrorResponse`, `PageResponse`, `MessageSource` localization (EN/AR).
4. **Security & Auth Feature:** JWT utility, User entity, `CustomUserDetailsService`, `JwtAuthenticationFilter`, `SecurityConfig`, `AuthController`, login flow.
5. **Product Feature:** Entity, DTOs, Repository with `ProductSpecification`, Service with stock status logic and JPA Auditing, Controller with `@PreAuthorize`, Unit & Integration tests.
6. **Audit Feature:** `AuditLog` entity, repository, service for business event tracking.
7. **Discount Feature:** Entity, repository, validation logic, unit tests.
8. **Payment Feature:** Strategy pattern with `PaymentProcessor`, `CreditCardPaymentProcessor`, `XyzWalletPaymentProcessor`, `PaymentProcessorFactory`.
9. **Order Feature:** Entity, OrderItem with snapshot fields, repository with specification, transactional checkout pipeline, profit calculation, stock deduction, concurrency protection.
10. **Order APIs:** Customer place order, Customer my-orders, Admin list-orders with filters and profit metrics.
11. **Centralized Exception Handling & Localization:** `@RestControllerAdvice` mapping domain exceptions to HTTP statuses and localized messages.
12. **Integration & Concurrency Tests:** Testcontainers PostgreSQL tests, concurrency stock tests, role authorization tests.
13. **Documentation:** `README.md`, `SUBMISSION.md`, Swagger OpenAPI configuration.
14. **Final Verification:** Clean build, test suite execution, manual verification of all acceptance criteria.
