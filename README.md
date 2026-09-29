# Al Qaseh E-Commerce API

ASP.NET Core (.NET 10) + SQL Server backend: JWT login for admins and customers, a product catalog with role-dependent stock visibility, transactional checkout with fake CreditCard / XyzWallet payments and one-time discount codes, and order lists for admins (with profit) and customers.

**Setup, run, users, API, design decisions, assumptions and limitations: [SUBMISSION.md](SUBMISSION.md).**

**Only Docker needed** (no .NET SDK, no SQL Server to install):

```bash
docker compose up --build        # builds the API, starts SQL Server + API; the database is created, migrated and seeded automatically
```

Then open **<http://localhost:5207/swagger>**, pick **Admin** or **Customer** in the definition dropdown (or *All Endpoints*), log in with a demo user and click *Authorize*. A Scalar view of the *All Endpoints* document is at <http://localhost:5207/scalar/v1>.

Prefer running from source? Start only the database (`docker compose up -d sqlserver --wait`) and run `dotnet run` with `ConnectionStrings__DefaultConnection` set to that database (exact command in SUBMISSION.md, section 1: the placeholder password in `appsettings.json` is not the one of the compose database).

> The same task is also implemented in Java 21 / Spring Boot / PostgreSQL on the [`java` branch](../../tree/java).

Tests: `dotnet test tests/AlQaseh_Ecommerce_API.Tests` (unit tests only; see SUBMISSION.md, section 3, for the real-SQL-Server integration tests).

Demo users (Development): `admin / Admin123!`, `customer1 / Customer123!`, `customer2 / Customer123!`.

## Code map

```text
Features/
  Auth/       login (BCrypt, JWT)
  Products/   create, update, list (admin view / customer view), fixed categories
  Orders/     checkout (one transaction), my orders, admin orders with profit
  Discounts/  fixed-amount, single-use codes
  Payments/   CreditCard and XyzWallet (fake gateways)
Infrastructure/
  Persistence/  Dapper context, unit of work, SQL migrator, demo seeder
  Middleware/   global exception handling, user-context check
  Logging/      Serilog partitioned by error type
Shared/         base controller, results and error codes (en/ar), JWT and Swagger setup, Sqid ids
Database/Migrations/   versioned SQL scripts V001-V010: schema, procedures, audit triggers, keyset paging, integrity constraints, ... (embedded, applied at startup)
tests/          unit tests + integration tests on a real SQL Server
```
