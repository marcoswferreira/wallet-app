# Multi-Tenancy Financial Engine

A **production-grade** multi-tenant financial engine built with **Spring Boot 3.3** + **Java 21** + **PostgreSQL 16**.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          HTTP Request                                    │
│             Authorization: Bearer <JWT with tenant_id claim>            │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                    ┌────────▼────────┐
                    │  JwtAuthFilter  │  ← Validates JWT, sets SecurityContext
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │  TenantFilter   │  ← Extracts tenant_id → ThreadLocal
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │   Controller    │
                    └────────┬────────┘
                             │
          ┌──────────────────▼──────────────────────────────┐
          │         TenantFilterActivationAspect (AOP)       │
          │  Enables Hibernate @Filter(tenant_id = ?)        │
          └──────────────────┬──────────────────────────────-┘
                             │
                    ┌────────▼────────┐
                    │  AccountService  │  ← @Retryable(OptimisticLockException)
                    │  + @Version      │    (max 3 retries, 100ms backoff)
                    └────────┬────────┘
                             │
               ┌─────────────▼─────────────┐
               │       PostgreSQL 16         │
               │  account (tenant_id, @Ver) │
               │  transaction (immutable)    │
               └────────────────────────────┘
```

### Key Patterns

| Pattern | Implementation |
|---|---|
| Multi-tenancy | Shared DB/Schema, `tenant_id` column + Hibernate `@Filter` |
| Optimistic Locking | `@Version` on `Account` + `@Retryable` on service methods |
| Tenant Propagation | `TenantFilter` → `ThreadLocal` → AOP aspect activates filter |
| Immutable Ledger | `Transaction` entity with `@Immutable`, append-only |
| Auth | Stateless JWT with embedded `tenant_id` claim |

---

## Project Structure

```
src/main/java/com/financial/multitenancy/
├── MultiTenancyApplication.java
├── controller/
│   ├── AccountController.java
│   ├── TransactionController.java
│   └── AuthController.java
├── domain/
│   ├── Account.java          ← @Version, @FilterDef, @Filter
│   ├── Transaction.java      ← @Immutable, @Filter
│   └── TransactionType.java
├── dto/
│   ├── AccountResponse.java
│   ├── CreateAccountRequest.java
│   ├── MoneyRequest.java
│   ├── TransferRequest.java
│   ├── TransactionResponse.java
│   └── AuthRequest.java
├── infra/
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java
│   │   ├── InsufficientFundsException.java
│   │   └── TenantMismatchException.java
│   ├── security/
│   │   ├── JwtUtil.java
│   │   ├── JwtAuthFilter.java
│   │   └── SecurityConfig.java
│   └── tenant/
│       ├── TenantContext.java          ← ThreadLocal
│       ├── TenantFilter.java          ← OncePerRequestFilter
│       └── TenantFilterActivationAspect.java ← AOP
├── repository/
│   ├── AccountRepository.java
│   └── TransactionRepository.java
└── service/
    ├── AccountService.java     ← @Retryable
    └── TransactionService.java

src/main/resources/
├── application.yml
└── db/migration/
    ├── V1__create_account_table.sql
    └── V2__create_transaction_table.sql
```

---

## Running Locally

### Prerequisites
- Docker Desktop
- Java 21 (for non-Docker run)
- Maven 3.9+

### With Docker Compose (recommended)

```bash
cd c:\repos\my-repos\multi-tenancy-app

# Start PostgreSQL + App
docker-compose up -d

# View logs
docker-compose logs -f app

# Stop everything
docker-compose down -v
```

### Without Docker (PostgreSQL must be running locally)

```bash
cd c:\repos\my-repos\multi-tenancy-app
mvn spring-boot:run
```

---

## API Usage Examples

### 1. Get a JWT token

```bash
curl -X POST http://localhost:8080/api/auth/token \
  -H "Content-Type: application/json" \
  -d '{"tenantId":"550e8400-e29b-41d4-a716-446655440000","userId":"user-001"}'
```

### 2. Create an account

```bash
TOKEN="<JWT from step 1>"

curl -X POST http://localhost:8080/api/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"userId":"550e8400-e29b-41d4-a716-446655440001","initialBalance":1000.00}'
```

### 3. Deposit

```bash
ACCOUNT_ID="<id from step 2>"

curl -X POST http://localhost:8080/api/accounts/$ACCOUNT_ID/deposit \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount":500.00,"description":"Monthly salary"}'
```

### 4. Withdraw

```bash
curl -X POST http://localhost:8080/api/accounts/$ACCOUNT_ID/withdraw \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount":200.00,"description":"Rent"}'
```

### 5. Transfer between accounts

```bash
curl -X POST http://localhost:8080/api/transfers \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"sourceAccountId":"<id-a>","targetAccountId":"<id-b>","amount":100.00,"description":"Split bill"}'
```

### 6. Transaction history

```bash
curl http://localhost:8080/api/accounts/$ACCOUNT_ID/transactions?page=0&size=20 \
  -H "Authorization: Bearer $TOKEN"
```

### 7. Tenant isolation test

```bash
# Get token for a DIFFERENT tenant
TOKEN_B=$(curl -s -X POST http://localhost:8080/api/auth/token \
  -H "Content-Type: application/json" \
  -d '{"tenantId":"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee","userId":"user-999"}' | jq -r .token)

# Try to access tenant A's account — must return 404
curl http://localhost:8080/api/accounts/$ACCOUNT_ID/balance \
  -H "Authorization: Bearer $TOKEN_B"
```

---

## Running Tests

```bash
# All tests (unit + integration + concurrency)
mvn test

# Only unit tests
mvn test -Dtest=AccountServiceTest

# Only integration tests
mvn test -Dtest=AccountControllerIntegrationTest

# Concurrency double-spend test
mvn test -Dtest=ConcurrentWithdrawTest
```

---

## Concurrency Design

### Optimistic Locking Flow (default)

```
Thread A reads Account(balance=100, version=0)
Thread B reads Account(balance=100, version=0)

Thread A: balance -= 100 → UPDATE SET balance=0, version=1 WHERE version=0   ✅ OK
Thread B: balance -= 100 → UPDATE SET balance=0, version=1 WHERE version=0   ❌ 0 rows updated
                                             ↓
                               OptimisticLockException thrown
                                             ↓
                          @Retryable retries (reads version=1, balance=0)
                                             ↓
                          InsufficientFundsException (correct!)
```

### Pessimistic Locking Alternative (higher isolation, lower throughput)

Uncomment `findByIdForUpdate` in `AccountRepository` and use it in `AccountService` to get `SELECT ... FOR UPDATE` behavior.

---

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/financial_db` | DB connection |
| `SPRING_DATASOURCE_USERNAME` | `financial_user` | DB user |
| `SPRING_DATASOURCE_PASSWORD` | `financial_pass` | DB password |
| `APP_JWT_SECRET` | (set in application.yml) | Base64 HMAC secret (min 256-bit) |
| `APP_JWT_EXPIRATION_MS` | `86400000` (24h) | Token expiry |
