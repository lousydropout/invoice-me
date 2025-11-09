# Technical Specification Document (TSD)

**Version:** 1.0 (Consolidated Implementation Guide)

**Purpose:** This document provides the complete technical specification for implementing InvoiceMe, ready for Cursor scaffolding and development.

---

## 1. Project Configuration

### Environment Setup
- **Language**: Java 21
- **Build Tool**: Gradle 8.14.x
- **Framework**: Spring Boot 3.5.0
- **Database**: PostgreSQL 17.4
- **ORM**: Spring Data JPA (Hibernate)
- **Auth**: Spring Security BasicAuth
- **Testing**: JUnit 5 + Testcontainers
- **Containerization**: Docker Compose (local) / AWS Fargate + Aurora (prod)

### Project Initialization
```bash
gradle init --type java-application
```

### Dependencies (build.gradle.kts)
```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.postgresql:postgresql:42.7.3")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}
```

---

## 2. Architectural Style

### Required Pattern: Value Stream Architecture (VSA)
**Forbidden:** Horizontal Slice Architecture (HSA)

Each **bounded context** is a self-contained vertical slice with:
- `domain/` → entities, aggregates, value objects, events
- `application/` → command & query handlers, services
- `infrastructure/` → JPA entities, adapters, repository impls
- `api/` → REST controllers, DTOs

**Shared code** (cross-context) goes under `/shared/`.

### Directory Structure
```
src/
 ├── main/java/com/invoiceme/
 │    ├── customer/
 │    │    ├── domain/
 │    │    ├── application/
 │    │    ├── infrastructure/
 │    │    └── api/
 │    ├── invoice/
 │    │    ├── domain/
 │    │    ├── application/
 │    │    ├── infrastructure/
 │    │    └── api/
 │    └── payment/
 │         ├── domain/
 │         ├── application/
 │         ├── infrastructure/
 │         └── api/
 │
 └── shared/
      ├── DomainEvent.java
      ├── Money.java
      ├── Email.java
      ├── Address.java
      └── DomainEventPublisher.java
```

**Note:** Package structure uses `com.invoiceme` as base package (consistent with scaffold).

---

## 3. Domain Model Overview

### Aggregates

| Context  | Aggregate Root                      | Description                                                |
| -------- | ----------------------------------- | ---------------------------------------------------------- |
| Customer | `Customer`                          | Stores name, contact info, address, payment terms          |
| Invoice  | `Invoice`                           | Lifecycle: Draft → Sent → Paid; holds LineItems & Payments |
| Payment  | `Payment` (as entity under Invoice) | Represents recorded manual payments                        |

### Value Objects
- `LineItem` — description, quantity, unit price
- `Money` — encapsulates BigDecimal with currency and rounding logic
- `Address` — street, city, postal code, country
- `Email` — validated email format
- `InvoiceStatus` — Enum: DRAFT, SENT, PAID

### Domain Events
```java
InvoiceCreated
InvoiceUpdated
InvoiceSent
PaymentRecorded
InvoicePaid
CustomerCreated
CustomerUpdated
CustomerDeleted
```

All domain events implement:
```java
public interface DomainEvent {
    Instant occurredAt();
}
```

---

## 4. Application Layer (Commands, Queries, Services)

Each handler lives inside the bounded context's `application/` folder.

### Command Handlers

| Command                 | Handler                 | Purpose                                  |
| ----------------------- | ----------------------- | ---------------------------------------- |
| `CreateCustomerCommand` | `CreateCustomerHandler` | Creates new customer aggregate           |
| `UpdateCustomerCommand` | `UpdateCustomerHandler` | Updates existing customer                |
| `DeleteCustomerCommand` | `DeleteCustomerHandler` | Deletes customer if no invoices exist    |
| `CreateInvoiceCommand`  | `CreateInvoiceHandler`  | Creates new draft invoice                |
| `UpdateInvoiceCommand`  | `UpdateInvoiceHandler`  | Updates draft invoice line items         |
| `SendInvoiceCommand`    | `SendInvoiceHandler`    | Marks invoice as sent                    |
| `RecordPaymentCommand`  | `RecordPaymentHandler`  | Adds payment to invoice and emits events |

### Handler Pattern
```java
@Transactional
public class CreateInvoiceHandler {
    private final InvoiceRepository repo;
    private final DomainEventPublisher events;
    
    public UUID handle(CreateInvoiceCommand cmd) {
        var invoice = new Invoice(cmd.customerId(), cmd.lineItems(), cmd.issueDate(), cmd.dueDate());
        repo.save(invoice);
        events.publish(invoice.pullDomainEvents());
        return invoice.getId();
    }
}
```

### Query Handlers

| Query                        | Handler                        | Output DTO                |
| ---------------------------- | ------------------------------ | ------------------------- |
| `GetInvoiceByIdQuery`        | `GetInvoiceByIdHandler`        | `InvoiceDetailView`       |
| `ListInvoicesQuery`          | `ListInvoicesHandler`          | `InvoiceSummaryView`      |
| `ListOverdueInvoicesQuery`   | `ListOverdueInvoicesHandler`   | `InvoiceSummaryView`      |
| `ListCustomersQuery`         | `ListCustomersHandler`         | `CustomerView`            |
| `OutstandingByCustomerQuery` | `OutstandingByCustomerHandler` | `CustomerOutstandingView` |

Queries use `JdbcTemplate` or `@Query` with read-only transactions.

---

## 5. Infrastructure Layer

### Persistence

| Entity           | Table        | Notes                                       |
| ---------------- | ------------ | ------------------------------------------- |
| `CustomerEntity` | `customers`  | Primary key: UUID                           |
| `InvoiceEntity`  | `invoices`   | FK → `customer_id`; status, total, due_date |
| `LineItemEntity` | `line_items` | FK → `invoice_id`                           |
| `PaymentEntity`  | `payments`   | FK → `invoice_id`; amount, date, method     |

### Repository Interfaces
```java
public interface InvoiceRepository {
    Optional<Invoice> findById(UUID id);
    void save(Invoice invoice);
}
```

**Repository Implementations**
Located under `/infrastructure/repository/` using Spring Data JPA or custom mappers.

### Event Publication
- Implemented in `/shared/SimpleDomainEventPublisher.java`
- Uses Spring's `ApplicationEventPublisher`
- All listeners annotated with `@EventListener` or `@TransactionalEventListener(phase = AFTER_COMMIT)`

---

## 6. API Layer

Each bounded context exposes REST controllers under `/api/{context}`.

### Endpoints

| HTTP   | Path                          | Description          |
| ------ | ----------------------------- | -------------------- |
| POST   | `/api/customers`              | Create customer      |
| PUT    | `/api/customers/{id}`         | Update customer      |
| DELETE | `/api/customers/{id}`         | Delete customer      |
| GET    | `/api/customers`              | List customers       |
| POST   | `/api/invoices`               | Create draft invoice |
| PUT    | `/api/invoices/{id}`          | Update draft         |
| POST   | `/api/invoices/{id}/send`     | Mark as sent         |
| POST   | `/api/invoices/{id}/payments` | Record payment       |
| GET    | `/api/invoices`               | List all invoices    |
| GET    | `/api/invoices/{id}`          | Invoice details      |
| GET    | `/api/invoices/overdue`       | Overdue invoices     |
| GET    | `/api/customers/outstanding`  | Outstanding balances |

Controllers convert DTOs ↔ Commands/Queries.

### Example Controller
```java
@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {
    private final CreateInvoiceHandler createHandler;
    private final SendInvoiceHandler sendHandler;
    private final RecordPaymentHandler paymentHandler;
    private final GetInvoiceByIdHandler getHandler;
    
    @PostMapping
    public ResponseEntity<InvoiceResponse> create(@RequestBody @Valid CreateInvoiceRequest req) {
        UUID id = createHandler.handle(req.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(getHandler.handle(new GetInvoiceByIdQuery(id)));
    }
    
    @PostMapping("/{id}/payments")
    public ResponseEntity<Void> recordPayment(
        @PathVariable UUID id, 
        @RequestBody @Valid RecordPaymentRequest req
    ) {
        paymentHandler.handle(req.toCommand(id));
        return ResponseEntity.noContent().build();
    }
}
```

---

## 7. Data Transfer Objects (DTOs)

**Location:** `/api/{context}/dto/`

### Example: CreateInvoiceRequest
```java
public record CreateInvoiceRequest(
    UUID customerId,
    List<LineItemDto> lineItems,
    LocalDate issueDate,
    LocalDate dueDate,
    BigDecimal taxRate,
    String notes
) {
    public CreateInvoiceCommand toCommand() {
        return new CreateInvoiceCommand(customerId, lineItems, issueDate, dueDate, taxRate, notes);
    }
    
    public record LineItemDto(String description, BigDecimal quantity, BigDecimal unitPrice) {}
}
```

### Response Example: InvoiceResponse
```java
public record InvoiceResponse(
    UUID id,
    String invoiceNumber,
    UUID customerId,
    LocalDate issueDate,
    LocalDate dueDate,
    String status,
    BigDecimal total,
    BigDecimal balance,
    List<LineItemResponse> lineItems,
    List<PaymentResponse> payments
) {}
```

---

## 8. Validation & Exceptions

- Use `jakarta.validation.constraints.*` annotations in DTOs
- Global exception handler: `@ControllerAdvice` → `GlobalExceptionHandler.java`
- Map exceptions to:
  - `400 Bad Request` → Validation
  - `404 Not Found` → Entity missing
  - `409 Conflict` → Invalid state (e.g., editing SENT invoice)
  - `422 Unprocessable Entity` → Business rule violation

---

## 9. Testing Strategy

### Unit Tests
- Test aggregates directly (Invoice, Customer)
- Validate invariants (no edit after SENT, payment ≤ balance)

### Integration Tests
- Use `@SpringBootTest` + Testcontainers (Postgres)
- Validate end-to-end: Create → Send → Record Payment

### Performance Check
- CRUD operations under 200ms

---

## 10. Deployment Configuration

### Local
- Docker Compose runs Postgres and API
- Accessible via `http://localhost:8080/swagger-ui.html`

### AWS
- Docker image → pushed to ECR → deployed via ECS Fargate
- Aurora PostgreSQL (Serverless v2)
- Secrets (DB credentials, BasicAuth) via AWS Parameter Store

---

## 11. Acceptance Criteria

- ✅ VSA folder structure strictly followed (no HSA)
- ✅ All commands & queries implemented and tested
- ✅ Domain events emitted and handled synchronously
- ✅ CRUD flows (Customer → Invoice → Payment) functional
- ✅ All endpoints authenticated via BasicAuth
- ✅ Unit and integration tests pass
- ✅ Local Docker + AWS deployment verified

---

## 12. Optional Frontend

- A minimal React/Next.js client may be included to demonstrate API usage
- Must call the REST endpoints via `fetch` or `axios`
- Optional for MVP, not required for DDD evaluation

---

## 13. Cursor Execution Directives

**Implementation Steps:**

1. **Create** folder structure as per VSA (3 bounded contexts)
2. **Generate** aggregates, value objects, and event classes per this spec
3. **Scaffold** repositories, handlers, and DTOs
4. **Implement** REST controllers with validation and proper HTTP status codes
5. **Wire** synchronous event publishing
6. **Generate** Gradle build and Docker Compose files
7. **Add** Swagger/OpenAPI via `springdoc-openapi-starter-webmvc-ui`
8. **Include** unit and integration tests for core flows

**Command to verify startup:**
```bash
docker-compose up --build
```

Then visit:
```
http://localhost:8080/swagger-ui.html
```

---

## Notes

- **Package Structure**: Uses `com.invoiceme` as base package (consistent with scaffold)
- **Database Version**: PostgreSQL 17.4 (specific patch version)
- **Event Publishing**: Synchronous, in-memory with `AFTER_COMMIT` phase
- **No Outbox Pattern**: Explicitly not required for MVP
- **Shared Code**: All cross-context primitives live in `/shared/`

---

**End of Technical Specification Document — ready for Cursor scaffolding.**

