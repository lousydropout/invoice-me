# InvoiceMe — Implementation Task List

**Version:** 1.0 (Complete Implementation Roadmap)

**Purpose:** This document provides a comprehensive, actionable task list for implementing InvoiceMe, optimized for Cursor execution and manual verification.

---

## Task Execution Legend

| Icon  | Meaning                                |
| ----- | -------------------------------------- |
| ✅     | Fully automatable by Cursor            |
| ⚙️    | Cursor scaffolds code, human verifies  |
| 🧍‍♂️ | Manual testing or cloud setup required |

---

## 🧩 Epic 1: Project Setup & Infrastructure

### Task 1.1 — Initialize Spring Boot Project ✅

**Goal:** Scaffold Spring Boot 3.5 app using Gradle 8.14 and Java 21.

**Acceptance Criteria:**
- [ ] Project builds with `./gradlew bootRun`
- [ ] Includes dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-security`, `postgresql`
- [ ] Configured for PostgreSQL 17.4
- [ ] Folder structure supports **Value Stream Architecture (VSA)**

**Execution:** ✅ **Cursor-automatable**

**Files to Create:**
- `build.gradle.kts` (with all dependencies)
- VSA folder structure (`customer/`, `invoice/`, `payment/`, `shared/`)
- `application.yml` (PostgreSQL configuration)

---

### Task 1.2 — Configure Docker Compose (PostgreSQL + API) ✅

**Goal:** Add local Docker Compose setup.

**Acceptance Criteria:**
- [ ] `docker-compose.yml` spins up Postgres (port 5432) and API (port 8080)
- [ ] Environment variables loaded from `.env`
- [ ] Connection URL: `jdbc:postgresql://postgres:5432/invoiceme`

**Execution:** ✅ **Cursor-automatable**

**Files to Create/Update:**
- `docker-compose.yml` (Postgres + API services)
- `.env.example` (template for environment variables)
- `backend/Dockerfile` (if not exists)

---

### Task 1.3 — Configure BasicAuth ⚙️

**Goal:** Secure all endpoints via Spring Security.

**Acceptance Criteria:**
- [ ] Requires BasicAuth on all `/api/**` routes
- [ ] Username/password sourced from env vars
- [ ] Returns `401` for unauthenticated requests

**Execution:** ⚙️ **Cursor scaffolding + manual validation via Postman**

**Files to Create:**
- `shared/infrastructure/security/BasicAuthConfig.java`
- `application.yml` (security configuration)

**Manual Verification:**
- Test with Postman (unauthorized request → 401)
- Test with valid credentials → 200

---

### Task 1.4 — Shared Kernel Setup ✅

**Goal:** Create shared primitives and interfaces.

**Acceptance Criteria:**
- [ ] `/shared` package contains:
  - `DomainEvent`, `DomainEventPublisher`, `Money`, `Email`, `Address`
- [ ] No inter-context dependencies (e.g., Invoice → Customer)

**Execution:** ✅ **Cursor-automatable**

**Files to Create:**
- `shared/domain/DomainEvent.java` (interface)
- `shared/domain/Money.java` (value object)
- `shared/domain/Email.java` (value object)
- `shared/domain/Address.java` (value object)
- `shared/application/bus/DomainEventPublisher.java` (interface)
- `shared/infrastructure/events/SimpleDomainEventPublisher.java` (implementation)

---

## 🧭 Epic 2: Customer Context

### Task 2.1 — Domain Model ✅

**Goal:** Implement `Customer` aggregate and events.

**Acceptance Criteria:**
- [ ] `Customer` has `id`, `name`, `email`, `phone`, `address`, `paymentTerms`
- [ ] Domain events: `CustomerCreated`, `CustomerUpdated`, `CustomerDeleted`
- [ ] Email validation enforced via `Email` VO

**Execution:** ✅ **Cursor-automatable**

**Files to Create:**
- `customer/domain/Customer.java` (aggregate root)
- `customer/domain/events/CustomerCreated.java`
- `customer/domain/events/CustomerUpdated.java`
- `customer/domain/events/CustomerDeleted.java`
- `customer/domain/valueobjects/PaymentTerms.java`

---

### Task 2.2 — Repository & JPA Adapter ⚙️

**Goal:** Persist customers via Spring Data JPA.

**Acceptance Criteria:**
- [ ] `CustomerRepository` domain interface defined
- [ ] `CustomerEntity` maps to `customers` table
- [ ] Adapter converts entity ↔ domain

**Execution:** ⚙️ **Cursor code + manual DB check**

**Files to Create:**
- `customer/domain/CustomerRepository.java` (domain interface)
- `customer/infrastructure/persistence/entities/CustomerEntity.java`
- `customer/infrastructure/persistence/CustomerJpaRepository.java` (Spring Data JPA)
- `customer/infrastructure/persistence/CustomerRepositoryAdapter.java`
- `customer/infrastructure/persistence/mappers/CustomerMapper.java`
- Flyway migration: `V1__create_customers_table.sql`

**Manual Verification:**
- Check `customers` table exists in Postgres
- Verify CRUD operations persist correctly

---

### Task 2.3 — Application Commands & Handlers ✅

**Goal:** Implement CRUD commands.

**Acceptance Criteria:**
- [ ] Commands: `CreateCustomer`, `UpdateCustomer`, `DeleteCustomer`
- [ ] Each emits proper domain events
- [ ] All handlers transactional

**Execution:** ✅ **Cursor-automatable**

**Files to Create:**
- `customer/application/commands/CreateCustomerCommand.java`
- `customer/application/commands/UpdateCustomerCommand.java`
- `customer/application/commands/DeleteCustomerCommand.java`
- `customer/application/commands/CreateCustomerHandler.java`
- `customer/application/commands/UpdateCustomerHandler.java`
- `customer/application/commands/DeleteCustomerHandler.java`

---

### Task 2.4 — REST API ⚙️

**Goal:** Expose CRUD endpoints under `/api/customers`.

**Acceptance Criteria:**
- [ ] `POST /customers` → create
- [ ] `PUT /customers/{id}` → update
- [ ] `DELETE /customers/{id}` → delete
- [ ] `GET /customers` → list all
- [ ] Validations applied (`@Email`, `@NotNull`)

**Execution:** ⚙️ **Cursor + manual Postman validation**

**Files to Create:**
- `customer/api/CustomerController.java`
- `customer/api/dto/CreateCustomerRequest.java`
- `customer/api/dto/UpdateCustomerRequest.java`
- `customer/api/dto/CustomerResponse.java`

**Manual Verification:**
- Test all endpoints with Postman
- Verify validation errors return 400
- Verify BasicAuth protection

---

## 📜 Epic 3: Invoice Context

### Task 3.1 — Domain Model ✅

**Goal:** Define `Invoice` aggregate and sub-entities.

**Acceptance Criteria:**
- [ ] `Invoice` holds `customerId`, `status`, `issueDate`, `dueDate`, `lineItems`, `payments`
- [ ] Lifecycle: DRAFT → SENT → PAID
- [ ] Domain events: `InvoiceCreated`, `InvoiceUpdated`, `InvoiceSent`, `PaymentRecorded`, `InvoicePaid`
- [ ] Business rules enforced:
  - Cannot edit SENT invoices
  - Payments ≤ balance

**Execution:** ✅ **Cursor-automatable**

**Files to Create:**
- `invoice/domain/Invoice.java` (aggregate root)
- `invoice/domain/LineItem.java` (value object)
- `invoice/domain/Payment.java` (entity)
- `invoice/domain/valueobjects/InvoiceStatus.java` (enum)
- `invoice/domain/valueobjects/InvoiceNumber.java`
- `invoice/domain/events/InvoiceCreated.java`
- `invoice/domain/events/InvoiceUpdated.java`
- `invoice/domain/events/InvoiceSent.java`
- `invoice/domain/events/PaymentRecorded.java`
- `invoice/domain/events/InvoicePaid.java`

---

### Task 3.2 — Repository & Persistence Mapping ⚙️

**Goal:** Store invoices, line items, and payments in Postgres.

**Acceptance Criteria:**
- [ ] Tables: `invoices`, `line_items`, `payments`
- [ ] JPA relations: One invoice → many line items/payments
- [ ] Query verified via DB console

**Execution:** ⚙️ **Cursor + manual DB verification**

**Files to Create:**
- `invoice/domain/InvoiceRepository.java` (domain interface)
- `invoice/infrastructure/persistence/entities/InvoiceEntity.java`
- `invoice/infrastructure/persistence/entities/LineItemEntity.java`
- `invoice/infrastructure/persistence/entities/PaymentEntity.java`
- `invoice/infrastructure/persistence/InvoiceJpaRepository.java`
- `invoice/infrastructure/persistence/InvoiceRepositoryAdapter.java`
- `invoice/infrastructure/persistence/mappers/InvoiceMapper.java`
- Flyway migration: `V2__create_invoices_tables.sql`

**Manual Verification:**
- Check tables exist in Postgres
- Verify relationships (FKs)
- Test cascade operations

---

### Task 3.3 — Command Handlers ✅

**Goal:** Implement core invoice commands.

**Acceptance Criteria:**
- [ ] Handlers for:
  - `CreateInvoice` (draft)
  - `UpdateInvoice` (edit)
  - `SendInvoice` (state change)
  - `RecordPayment` (balance update)
- [ ] Emits appropriate events; all transactional

**Execution:** ✅ **Cursor-automatable**

**Files to Create:**
- `invoice/application/commands/CreateInvoiceCommand.java`
- `invoice/application/commands/UpdateInvoiceCommand.java`
- `invoice/application/commands/SendInvoiceCommand.java`
- `invoice/application/commands/RecordPaymentCommand.java`
- `invoice/application/commands/CreateInvoiceHandler.java`
- `invoice/application/commands/UpdateInvoiceHandler.java`
- `invoice/application/commands/SendInvoiceHandler.java`
- `invoice/application/commands/RecordPaymentHandler.java`

---

### Task 3.4 — Invoice REST API ⚙️

**Goal:** Implement endpoints for managing invoices and payments.

**Acceptance Criteria:**
- [ ] `POST /invoices` → create draft
- [ ] `PUT /invoices/{id}` → update draft
- [ ] `POST /invoices/{id}/send` → send
- [ ] `POST /invoices/{id}/payments` → record payment
- [ ] `GET /invoices/{id}` → full invoice details
- [ ] `GET /invoices` → list all
- [ ] All endpoints return proper HTTP codes

**Execution:** ⚙️ **Cursor + manual Postman validation**

**Files to Create:**
- `invoice/api/InvoiceController.java`
- `invoice/api/dto/CreateInvoiceRequest.java`
- `invoice/api/dto/UpdateInvoiceRequest.java`
- `invoice/api/dto/InvoiceResponse.java`
- `invoice/api/dto/RecordPaymentRequest.java`

**Manual Verification:**
- Test all endpoints with Postman
- Verify state transitions (DRAFT → SENT → PAID)
- Verify business rules (cannot edit SENT, payment ≤ balance)

---

### Task 3.5 — Domain Event Publisher ✅

**Goal:** Implement synchronous in-memory event publication.

**Acceptance Criteria:**
- [ ] `SimpleDomainEventPublisher` delegates to `ApplicationEventPublisher`
- [ ] Listeners log emitted events
- [ ] Verified via console output after invoice actions

**Execution:** ✅ **Cursor-automatable**

**Files to Create:**
- `shared/infrastructure/events/LoggingEventListener.java` (already in Task 1.4)

**Manual Verification:**
- Create invoice → check logs for `InvoiceCreated`
- Send invoice → check logs for `InvoiceSent`
- Record payment → check logs for `PaymentRecorded` and `InvoicePaid`

---

## 💰 Epic 4: Payment Context

### Task 4.1 — Domain Model ✅

**Goal:** Implement `Payment` entity under `Invoice`.

**Acceptance Criteria:**
- [ ] Fields: `amount`, `paymentDate`, `method`, `reference`
- [ ] Persisted via JPA `@OneToMany` mapping
- [ ] Emits `PaymentRecorded` event on add

**Execution:** ✅ **Cursor-automatable**

**Note:** Payment domain model is part of Invoice context (Task 3.1). This task focuses on Payment-specific concerns.

**Files to Create:**
- `payment/domain/Payment.java` (entity, owned by Invoice)
- `payment/domain/PaymentMethod.java` (enum or VO)

---

### Task 4.2 — Manual Payment Command ⚙️

**Goal:** Add command for manual payment recording.

**Acceptance Criteria:**
- [ ] Validates amount ≤ outstanding balance
- [ ] Emits `PaymentRecorded` and possibly `InvoicePaid`
- [ ] Updates invoice balance and status

**Execution:** ⚙️ **Cursor + manual test**

**Note:** Payment recording is handled by Invoice context (Task 3.3). This task verifies Payment-specific logic.

**Manual Verification:**
- Record payment exceeding balance → should fail
- Record payment equal to balance → should emit `InvoicePaid`
- Record partial payment → balance updated correctly

---

## 🔍 Epic 5: CQRS (Read Side)

### Task 5.1 — Define Read Models ✅

**Goal:** Add projection DTOs for queries.

**Acceptance Criteria:**
- [ ] DTOs: `InvoiceSummaryView`, `InvoiceDetailView`, `CustomerView`, `OutstandingByCustomerView`
- [ ] Match API response shape

**Execution:** ✅ **Cursor-automatable**

**Files to Create:**
- `invoice/application/queries/dto/InvoiceSummaryView.java`
- `invoice/application/queries/dto/InvoiceDetailView.java`
- `customer/application/queries/dto/CustomerView.java`
- `customer/application/queries/dto/CustomerOutstandingView.java`

---

### Task 5.2 — Query Handlers ⚙️

**Goal:** Implement SQL/JPA-based read-side queries.

**Acceptance Criteria:**
- [ ] Handlers:
  - `ListInvoicesHandler`
  - `GetInvoiceByIdHandler`
  - `ListOverdueInvoicesHandler`
  - `OutstandingByCustomerHandler`
- [ ] Efficient SQL joins; results validated via sample data

**Execution:** ⚙️ **Cursor + manual validation**

**Files to Create:**
- `invoice/application/queries/GetInvoiceByIdQuery.java`
- `invoice/application/queries/GetInvoiceByIdHandler.java`
- `invoice/application/queries/ListInvoicesQuery.java`
- `invoice/application/queries/ListInvoicesHandler.java`
- `invoice/application/queries/ListOverdueInvoicesQuery.java`
- `invoice/application/queries/ListOverdueInvoicesHandler.java`
- `customer/application/queries/OutstandingByCustomerQuery.java`
- `customer/application/queries/OutstandingByCustomerHandler.java`

**Manual Verification:**
- Create test data (customers, invoices, payments)
- Verify queries return correct results
- Verify derived state (overdue, balance) calculated correctly

---

### Task 5.3 — Read API ⚙️

**Goal:** Expose endpoints for reports and read models.

**Acceptance Criteria:**
- [ ] `GET /invoices` → summary list
- [ ] `GET /invoices/{id}` → detailed view
- [ ] `GET /invoices/overdue` → overdue invoices
- [ ] `GET /customers/outstanding` → outstanding balances

**Execution:** ⚙️ **Cursor + manual API test**

**Files to Create/Update:**
- `invoice/api/InvoiceQueryController.java` (or extend InvoiceController)
- `customer/api/CustomerQueryController.java` (or extend CustomerController)

**Manual Verification:**
- Test all query endpoints with Postman
- Verify response shapes match DTOs
- Verify derived state calculations

---

## 🧱 Epic 6: Cross-Cutting Concerns

### Task 6.1 — Logging ✅

**Goal:** Log all emitted domain events and repository actions.

**Acceptance Criteria:**
- [ ] Logs include event name, invoiceId, customerId, timestamp
- [ ] Accessible in local and AWS logs

**Execution:** ✅ **Cursor-automatable**

**Files to Create/Update:**
- `shared/infrastructure/events/LoggingEventListener.java` (enhance with structured logging)
- `application.yml` (logging configuration)

---

### Task 6.2 — Global Error Handling ✅

**Goal:** Add consistent REST error handling.

**Acceptance Criteria:**
- [ ] `@ControllerAdvice` maps exceptions to:
  - 400 Validation
  - 404 Not Found
  - 409 Conflict
  - 422 Business Rule

**Execution:** ✅ **Cursor-automatable**

**Files to Create:**
- `shared/api/exception/GlobalExceptionHandler.java`
- `shared/application/errors/ApplicationError.java`
- `shared/application/errors/ErrorCodes.java`

---

### Task 6.3 — DTO Validation ✅

**Goal:** Enforce field validation.

**Acceptance Criteria:**
- [ ] DTOs annotated with `@NotNull`, `@Email`, `@Min`, etc.
- [ ] Invalid requests return `400`

**Execution:** ✅ **Cursor-automatable**

**Note:** Validation annotations should be added to all DTOs created in previous tasks.

**Manual Verification:**
- Test invalid requests → 400 response
- Verify validation error messages are clear

---

## 🧪 Epic 7: Testing & Validation

### Task 7.1 — Unit Tests ✅

**Goal:** Validate domain invariants and aggregates.

**Acceptance Criteria:**
- [ ] Tests for `Invoice`, `Customer`, `Payment`
- [ ] Ensures:
  - Cannot edit SENT invoice
  - Payment cannot exceed balance
  - Correct events emitted

**Execution:** ✅ **Cursor-automatable**

**Files to Create:**
- `customer/domain/CustomerTest.java`
- `invoice/domain/InvoiceTest.java`
- `invoice/domain/PaymentTest.java`

---

### Task 7.2 — Integration Tests ⚙️

**Goal:** Test full persistence and CQRS.

**Acceptance Criteria:**
- [ ] End-to-end: Create customer → invoice → send → record payment
- [ ] DB state verified

**Execution:** ⚙️ **Cursor scaffolding + manual test verification**

**Files to Create:**
- `customer/application/commands/CreateCustomerHandlerTest.java` (with Testcontainers)
- `invoice/application/commands/CreateInvoiceHandlerTest.java`
- `invoice/application/commands/RecordPaymentHandlerTest.java`
- `invoice/application/queries/ListInvoicesHandlerTest.java`

**Manual Verification:**
- Run all integration tests
- Verify Testcontainers setup works
- Check database state after tests

---

### Task 7.3 — API Validation 🧍‍♂️

**Goal:** Validate all endpoints using Postman.

**Acceptance Criteria:**
- [ ] All CRUD + CQRS queries functional with BasicAuth
- [ ] Response shapes match DTOs

**Execution:** 🧍‍♂️ **Manual**

**Manual Steps:**
1. Create Postman collection
2. Test all endpoints
3. Verify BasicAuth
4. Verify response shapes
5. Test error cases

---

## 🔧 Epic 8: Operational Clarity and Developer Experience Enhancements

### Task 8.1 — OpenAPI Specification ✅

**Goal:** Add OpenAPI/Swagger documentation for API transparency.

**Acceptance Criteria:**
- [x] Add `springdoc-openapi-starter-webmvc-ui` dependency
- [x] Serve OpenAPI spec at `/v3/api-docs`
- [x] Serve Swagger UI at `/swagger-ui.html`
- [x] Verify endpoints and DTOs are correctly reflected
- [x] Commit generated spec file for documentation purposes

**Execution:** ✅ **Cursor-automatable**

**Files to Create/Update:**
- `build.gradle.kts` (add springdoc dependency)
- `application.yml` (configure OpenAPI paths)
- `openapi.json` (generated spec file, committed for docs)

**Manual Verification:**
- Access Swagger UI at `http://localhost:8080/swagger-ui.html`
- Verify all endpoints are documented
- Verify DTOs match API responses

---

### Task 8.2 — Event Persistence and Debug Endpoint ⚙️

**Goal:** Persist domain events for debugging and audit purposes.

**Acceptance Criteria:**
- [x] Create `domain_events` table (id, type, payload JSON, created_at)
- [x] Modify `SimpleDomainEventPublisher` to persist events
- [x] Add repository for retrieving persisted events
- [x] Add `/api/debug/events` endpoint (restricted to `dev` profile)
- [x] Ensure event publication still functions normally

**Execution:** ⚙️ **Cursor scaffolding + manual verification**

**Files to Create/Update:**
- `shared/infrastructure/persistence/entities/DomainEventEntity.java`
- `shared/infrastructure/persistence/DomainEventJpaRepository.java`
- `shared/infrastructure/events/SimpleDomainEventPublisher.java` (enhance to persist)
- `shared/api/debug/DebugEventController.java` (dev profile only)
- `schema.sql` (add domain_events table)

**Manual Verification:**
- Create invoice → verify event persisted in `domain_events` table
- Access `/api/debug/events` in dev profile → verify events returned
- Verify endpoint not accessible in prod profile

---

### Task 8.3 — Standardized Error Envelope ✅

**Goal:** Ensure all error responses follow a consistent structure.

**Acceptance Criteria:**
- [x] Define `ApiError` DTO: `{ code: string, message: string, details?: object }`
- [x] Update `GlobalExceptionHandler` to return consistent JSON responses
- [x] Add/adjust tests to validate the unified error structure

**Execution:** ✅ **Cursor-automatable**

**Files to Create/Update:**
- `shared/api/dto/ApiError.java`
- `shared/api/exception/GlobalExceptionHandler.java` (update to use ApiError)
- `shared/api/exception/GlobalExceptionHandlerTest.java` (update tests)

**Manual Verification:**
- Test various error scenarios → verify consistent error envelope
- Verify all error responses match ApiError structure

---

### Task 8.4 — Remove Flyway ✅

**Goal:** Simplify schema management by using Spring Boot's built-in schema initialization.

**Acceptance Criteria:**
- [x] Remove Flyway dependency from `build.gradle.kts`
- [x] Delete `/resources/db/migration` directory
- [x] Create `schema.sql` in `/resources`
- [x] Configure `spring.sql.init.mode=always` in `application.yml`
- [x] Update Docker and compose configurations accordingly

**Execution:** ✅ **Cursor-automatable**

**Files to Create/Update:**
- `build.gradle.kts` (remove flyway plugin and dependency)
- `resources/schema.sql` (consolidate all table definitions)
- `application.yml` (add `spring.sql.init.mode=always`)
- `docker-compose.yml` (remove flyway-related configs if any)
- `Dockerfile` (remove flyway-related steps if any)

**Manual Verification:**
- Start application → verify schema created correctly
- Verify all tables exist and relationships work
- Run existing tests → verify they still pass

---

### Task 8.5 — Seed Data Loader ✅

**Goal:** Provide demo data for local development and testing.

**Acceptance Criteria:**
- [x] Create `CommandLineRunner` (active under `dev` profile)
- [x] Populate demo customers and invoices if DB is empty
- [x] Use command handlers for data creation (maintains domain invariants)
- [x] Validate with existing `validate-*` scripts that data loads successfully

**Status:** ✅ **COMPLETED**

**Execution:** ✅ **Cursor-automatable** - **COMPLETED**

**Files Created:**
- `shared/infrastructure/persistence/DevDataSeeder.java` (CommandLineRunner, @Profile("dev"))
- `application-dev.yml` (dev profile configuration with enhanced logging)

**Implementation Details:**
- Seeds 3 demo customers (Acme Corporation, TechCorp Solutions, Startup Inc)
- Seeds 3 demo invoices (2 for Acme, 1 for TechCorp, 1 overdue)
- Only runs when database is empty (checks customer count)
- Uses command handlers to maintain domain invariants and publish events

**Verification:**
- ✅ Seeder runs when database is empty
- ✅ Seeder skips when data exists
- ✅ Validation scripts work with seeded data

---

## ☁️ Epic 9: AWS Deployment

### Task 9.1 — AWS Aurora + Fargate Deployment 🧍‍♂️

**Goal:** Deploy to AWS infrastructure.

**Acceptance Criteria:**
- [x] CDK infrastructure code complete and ready
- [x] Environment variable configuration via `.env` file
- [x] Route53 DNS setup documented
- [x] BasicAuth credentials configuration (env vars or Secrets Manager)
- [x] Deployment checklist and documentation created
- [ ] API containerized and running on Fargate (manual deployment step)
- [ ] Connected to Aurora PostgreSQL Serverless v2 (automatic via CDK)
- [ ] Logs visible in CloudWatch (automatic via CDK)

**Execution:** 🧍‍♂️ **Manual deployment** - **INFRASTRUCTURE CODE COMPLETE**

**Infrastructure Components (CDK Stack):**
1. **Aurora Serverless v2 PostgreSQL** - Auto-scaling database (0.5-1 ACU)
2. **ECS Fargate** - Serverless container hosting (512 MB, 256 CPU)
3. **Application Load Balancer** - HTTPS endpoint with ACM certificate
4. **Route53** - DNS with A Record ALIAS to ALB (must be created separately)
5. **CloudWatch Logs** - Centralized logging
6. **Secrets Manager** - Database credentials storage

**Configuration:**
- Environment variables in `infra/cdk/.env` file
- Required: `AWS_ACCOUNT_ID`, `AWS_REGION`, `ACM_CERTIFICATE_ARN`
- Optional: `DOMAIN_NAME`, `ECR_REPOSITORY_NAME`, `ECR_IMAGE_TAG`, BasicAuth credentials

**Manual Steps Required:**
1. Create Route53 hosted zone (`invoiceme.vincentchan.cloud`)
2. Configure `.env` file with AWS account details
3. Bootstrap CDK (first time only)
4. Deploy via `cdk deploy`
5. Create A Record ALIAS pointing to ALB DNS name
6. Verify connectivity and test endpoints

**Documentation:**
- `infra/cdk/README.md` - Complete deployment guide
- `infra/cdk/DEPLOYMENT_CHECKLIST.md` - Pre-deployment checklist
- `README.md` - AWS architecture diagram and overview

---

## Epic Summary

| Epic                | Tasks | ✅ Cursor | ⚙️ Cursor+Manual | 🧍‍♂️ Manual |
| ------------------- | ----- | --------- | ---------------- | ----------- |
| 1. Setup & Infra    | 4     | 3         | 1                | 0           |
| 2. Customer Context | 4     | 3         | 1                | 0           |
| 3. Invoice Context  | 5     | 3         | 2                | 0           |
| 4. Payment Context    | 2     | 1         | 1                | 0           |
| 5. CQRS Read Side   | 3     | 1         | 2                | 0           |
| 6. Cross-Cutting    | 3     | 3         | 0                | 0           |
| 7. Testing          | 3     | 1         | 1                | 1           |
| 8. Operational Clarity | 5  | 4         | 1                | 0           |
| 9. AWS Deployment   | 1     | 0         | 0                | 1           |

**Total:**
- **30 Tasks**
- **19 Cursor-automatable (✅)**
- **9 require human verification (⚙️)**
- **2 manual-only (🧍‍♂️)**

---

## Implementation Order Recommendation

1. **Epic 1** (Setup) - Foundation for everything
2. **Epic 2** (Customer) - Simplest context, good starting point
3. **Epic 3** (Invoice) - Core domain, most complex
4. **Epic 4** (Payment) - Depends on Invoice
5. **Epic 5** (CQRS) - Read side, depends on write side
6. **Epic 6** (Cross-Cutting) - Can be done in parallel
7. **Epic 7** (Testing) - Throughout development
8. **Epic 8** (Operational Clarity) - Developer experience before deployment
9. **Epic 9** (AWS Deployment) - Final step

---

## Cursor Execution Directives

**For Cursor:**
1. Start with Epic 1 (Setup & Infrastructure)
2. Implement all ✅ tasks automatically
3. Scaffold all ⚙️ tasks (code generation + placeholders for manual verification)
4. Generate test skeletons for Epic 7
5. Ensure VSA folder structure is strictly followed
6. OpenAPI spec generation is handled in Epic 8 (Task 8.1)

**Verification Commands:**
```bash
# Build and run
./gradlew bootRun

# Run tests
./gradlew test

# Docker Compose
docker-compose up --build

# Verify Swagger
curl http://localhost:8080/swagger-ui.html
```

---

**End of Implementation Task List — ready for Cursor execution.**

