# System Patterns

**See also:**
- `.memory-bank/canonicalDomainModel.md` - Complete canonical domain model specification
- `.memory-bank/applicationLayer.md` - Application service layer patterns and implementation (Commands)
- `.memory-bank/queryLayer.md` - Query layer patterns and implementation (CQRS Read Side)
- `.memory-bank/eventHandling.md` - Domain event handling patterns (publishing, listeners, outbox)
- `.memory-bank/infrastructureLayer.md` - Infrastructure layer patterns (repositories, JPA, PostgreSQL)
- `.memory-bank/apiLayer.md` - API/Interface layer patterns (REST controllers, DTOs, OpenAPI)

## Architecture Principles (DDD)

### Domain-Driven Design (DDD)
- **Rich Domain Models**: Entities contain business logic, not just getters/setters
- **Aggregate Roots**: Invoice (contains Payment entities), Customer
- **Authentication**: NOT a domain concern - it's infrastructure (Spring Security Basic Auth/JWT)
- **Subdomains vs Aggregates**: 
  - **Strategic (Subdomain Level)**: Invoice (core), Payment (supporting), Customer (supporting) are distinct subdomains
  - **Tactical (Aggregate Level)**: Payment is embedded as entity within Invoice aggregate for MVP
  - **Code Organization**: Separate packages (`/domain/invoice`, `/domain/payment`) preserve subdomain identity
- **Value Objects**: Money, Address, LineItem (immutable, equality by value)
- **Domain Services**: Business logic that doesn't naturally fit in entities (e.g., InvoiceNotificationService)
- **Domain Events**: All state changes emit events (InvoiceCreated, PaymentRecorded, etc.)
  - Events are immutable records describing past occurrences
  - Aggregates raise events via `pullDomainEvents()` pattern
  - Application layer publishes events after saving aggregates
  - Infrastructure layer handles events via listeners
- **Bounded Contexts**: Invoice and Payment share same bounded context for transactional simplicity (MVP)
- **See**: `.memory-bank/eventHandling.md` for complete event handling patterns

### Command Query Responsibility Segregation (CQRS)
- **Commands** (Write Operations):
  - CreateCustomerCommand → CreateCustomerHandler
  - UpdateCustomerCommand → UpdateCustomerHandler
  - CreateInvoiceCommand → CreateInvoiceHandler
  - UpdateInvoiceCommand → UpdateInvoiceHandler
  - SendInvoiceCommand → SendInvoiceHandler
  - RecordPaymentCommand → RecordPaymentHandler
- **Queries** (Read Operations):
  - GetInvoiceById → GetInvoiceByIdHandler
  - ListInvoices → ListInvoicesHandler
  - ListInvoicesByCustomer → ListInvoicesByCustomerHandler
  - ListOverdueInvoices → ListOverdueInvoicesHandler
  - ListCustomers → ListCustomersHandler
  - OutstandingByCustomer → OutstandingByCustomerHandler
  - Derived: `isOverdue` calculated at query time (due_date < CURRENT_DATE && status != PAID)
  - Derived: `balance` calculated at query time (total - SUM(payments))
- **Separation**: Commands and Queries use different handlers and may use different data models
- **Read Models**: Optimized projections (InvoiceSummaryView, InvoiceDetailView, CustomerView)
- **Implementation**: Direct SQL queries via JdbcTemplate for performance, JPA for simple queries
- **See**: `.memory-bank/eventStorming.md` for complete command-handler matrix
- **See**: `.memory-bank/queryLayer.md` for complete query layer patterns

### Vertical Slice Architecture (VSA) - **REQUIRED**

**Critical Requirement:** InvoiceMe backend **MUST** follow Vertical Slice Architecture (VSA), not Horizontal Slice Architecture (HSA).

**Key Principles:**
- Organize codebase **by business capability (bounded context)**, not by technical layer
- Each **bounded context** (Invoice, Customer, Payment) is a *vertical slice* containing its own `domain`, `application`, `infrastructure`, and `api` layers
- **Avoid global** `/controllers`, `/services`, or `/repositories` folders — these are HSA and disallowed
- Each slice should be independently understandable and testable as a small feature module

**Required Structure:**
```
src/
 ├── invoice/
 │    ├── domain/              # Aggregates, Entities, Value Objects, Events
 │    ├── application/         # Command Handlers, Query Handlers, Services
 │    ├── infrastructure/      # JPA Repositories, Mappers, Adapters
 │    └── api/                 # REST Controllers, DTOs
 │
 ├── customer/
 │    ├── domain/
 │    ├── application/
 │    ├── infrastructure/
 │    └── api/
 │
 └── payment/
      ├── domain/
      ├── application/
      ├── infrastructure/
      └── api/
```

**Guidelines:**
- All aggregates, events, and handlers related to a bounded context stay within that slice
- Shared primitives (e.g. `Money`, `DomainEvent`) may live under `src/shared/`
- Ensure package imports only go *downward* (api → application → domain), never sideways between contexts
- Each vertical slice should be a "mini-application" with its own repository interfaces, DTOs, and handlers

**Rationale:**
VSA mirrors the domain structure defined in DDD. It keeps the system modular, maintainable, and aligned with business capabilities.

### Clean Architecture Layers
1. **Domain Layer**: Entities, Value Objects, Domain Services, Domain Events (no dependencies)
2. **Application Layer**: Use Cases, Command/Query Handlers, Application Services
3. **Infrastructure Layer**: Repositories (JPA), External Services (Stripe, Email), Database

## Architecture Decisions
- **Backend-only**: No frontend components (API-first)
- **Container-first**: Docker Compose for local, ECS Fargate for production
- **Database migrations**: Flyway for version-controlled schema changes
- **Environment-based configuration**: Use environment variables for deployment flexibility
- **Event-Driven**: Domain events for decoupling and audit trails

## Code Conventions
- Java 21 language features (records for DTOs, pattern matching)
- Spring Boot 3.5 conventions
- RESTful API design
- **Rich Domain Models**: Entities with business methods, not just data containers
- Java records for DTOs (Commands, Queries, Responses)
- Optional<T> for nullable fields in update operations
- Lombok for entity boilerplate (minimal use, prefer explicit code for domain logic)
- Snake_case column names in database (Postgres convention)
- Immutable value objects where possible

## Design Patterns

### Domain Patterns
- **Aggregate Root**: Invoice (contains Payment entities), Customer (enforce invariants, transactional boundaries)
- **Subdomain Organization**: Payment is separate subdomain but embedded entity in Invoice aggregate (preserves conceptual boundaries while maintaining consistency)
- **Value Object**: Money, Address, LineItem (immutable, equality by value)
- **Domain Service**: Business logic that spans aggregates (InvoiceNotificationService)
- **Domain Event**: InvoiceCreated, InvoiceSent, PaymentRecorded, InvoicePaid, CustomerCreated, CustomerUpdated (decoupling, audit)
- **Policy**: Reactive rules triggered by events (e.g., NotifyCustomerByEmail triggered by InvoiceSent)
- **Derived State**: Calculated at query time (e.g., `isOverdue` = `dueDate < today && status != PAID`)

### Application Patterns
- **Command Handler**: Process commands (CreateInvoiceHandler, SendInvoiceHandler, etc.)
  - Implements `CommandHandler<T>` interface with single `handle()` method
  - Load aggregate from repository (domain interface)
  - Call aggregate method (command)
  - Save aggregate
  - Publish domain events via `DomainEventPublisher`
  - `@Transactional` annotation ensures atomic write + event publishing
  - One transaction per command = one aggregate boundary
- **Query Handler**: Process queries (GetInvoiceByIdHandler, ListInvoicesHandler, etc.)
  - Read-only transactions (`@Transactional(readOnly = true)`)
  - Direct SQL queries via JdbcTemplate for performance
  - Calculate derived fields at query time (e.g., `isOverdue`, `balance`)
  - Return read model DTOs (InvoiceSummaryView, InvoiceDetailView, CustomerView)
  - No domain aggregate loading - only read data projections
- **Policy Handler**: React to domain events (NotifyCustomerByEmailPolicy)
  - Listen to domain events via `@EventListener`
  - Execute reactive business logic
  - May emit system events for tracking
- **Event Publishing**: Domain events flow from aggregates → application → infrastructure
  - MVP: In-memory publisher using Spring's `ApplicationEventPublisher`
  - Production: Outbox pattern for transactional safety and reliable delivery
  - Events published after aggregate save for consistency
  - **See**: `.memory-bank/eventHandling.md` for complete event handling patterns
- **Repository Pattern**: Domain interfaces defined in domain layer, JPA implementations in infrastructure
- **Port Pattern**: External concerns (Clock, Email) as interfaces (ports) implemented in infrastructure
- **Error Handling**: `ApplicationError` with static factories, mapped to HTTP status codes via `@ControllerAdvice`
- **See**: `.memory-bank/applicationLayer.md` for complete application layer patterns

### Infrastructure Patterns
- **Repository Adapter Pattern**: Adapters bridge domain repository interfaces and Spring Data JPA
  - Domain interfaces defined in domain layer
  - JPA entities in infrastructure layer
  - Adapters translate between domain models and entities via mappers
  - Preserves DDD purity (domain doesn't depend on infrastructure)
- **Mapper Pattern**: Convert between domain Value Objects and entity primitives
  - CustomerMapper, InvoiceMapper
  - Handle nested entities (LineItems, Payments) within aggregates
  - Preserve aggregate boundaries
- **Spring Data JPA**: Automatic repository implementations
  - CustomerJpaRepository, InvoiceJpaRepository extend JpaRepository
  - Custom query methods as needed
- **Adapter Pattern**: StripeWebhookAdapter, EmailAdapter (external service integration)
- **Authentication**: Spring Security Basic Auth or JWT filter (cross-cutting concern, NOT domain)
  - Protects API endpoints at infrastructure layer
  - Single-tenant: simple credentials (`.env` or user table)
  - No User aggregate, no RBAC, no domain events
  - Config location: `/infrastructure/security`
- **Database Configuration**: PostgreSQL (local Docker, AWS Aurora Serverless)
  - Flyway for schema migrations
  - H2 for integration tests
  - Optimistic locking with `@Version`
- **See**: `.memory-bank/infrastructureLayer.md` for complete infrastructure patterns

## Best Practices

### Domain Layer
- Entities enforce business rules (e.g., "cannot edit sent invoices")
- Value objects are immutable
- Domain events are part of the domain model
- No infrastructure dependencies in domain layer

### Application Layer
- Thin layer that orchestrates domain operations
- Commands/Queries are explicit and focused
- Handlers coordinate domain services and repositories

### Infrastructure Layer
- Implements interfaces defined in domain/application layers
- Handles external integrations (Stripe, Email, Database)
- JPA entities are separate from domain entities (or use mapping)

### API/Interface Layer
- **REST Controllers**: Thin controllers that delegate to application handlers
- **DTOs**: Request and response objects (Java records) that isolate API from domain
- **Command Mapping**: DTOs convert to commands via helper methods
- **OpenAPI Documentation**: Self-documenting API via Swagger/OpenAPI
- **Error Handling**: Global exception handler maps domain errors to HTTP status codes
- **Authentication**: BasicAuth protects all endpoints at infrastructure layer
- **See**: `.memory-bank/apiLayer.md` for complete API layer patterns

### Package Structure (Vertical Slice Architecture - VSA)

**REQUIRED:** Code must be organized by bounded context (vertical slices), not by technical layers.

- **Each bounded context is a vertical slice** containing its own `domain`, `application`, `infrastructure`, and `api` layers
- **No global folders** like `/controllers`, `/services`, or `/repositories` (HSA disallowed)
- **Example structure:**
  ```
  /src
   ├── invoice/              # Invoice bounded context (vertical slice)
   │    ├── domain/
   │    │    ├── Invoice.java (aggregate root)
   │    │    ├── InvoiceRepository.java (interface)
   │    │    └── events/
   │    ├── application/
   │    │    ├── commands/
   │    │    └── queries/
   │    ├── infrastructure/
   │    │    └── persistence/
   │    └── api/
   │
   ├── customer/             # Customer bounded context (vertical slice)
   │    ├── domain/
   │    │    ├── Customer.java (aggregate root)
   │    │    └── CustomerRepository.java (interface)
   │    ├── application/
   │    ├── infrastructure/
   │    └── api/
   │
   ├── payment/              # Payment bounded context (vertical slice)
   │    ├── domain/
   │    │    └── Payment.java (entity, owned by Invoice)
   │    └── (Note: Payment commands/queries handled by Invoice slice)
   │
   └── shared/               # Shared primitives across bounded contexts
        ├── domain/
        │    └── Money.java (value object)
        ├── application/
        │    └── bus/
        │         └── DomainEventPublisher.java (interface)
        └── infrastructure/
             └── events/
                  └── SimpleDomainEventPublisher.java
  ```
- **Dependency direction**: 
  - Within slice: api → application → domain (downward only)
  - Between slices: Invoice → Payment (never reverse)
  - Shared primitives: imported by all slices
- **Rationale**: VSA mirrors the domain structure defined in DDD. It keeps the system modular, maintainable, and aligned with business capabilities.

### General
- Use environment variables for configuration (DB_HOST, DB_USER, etc.)
- Maintain clean separation between local (Docker Compose) and cloud (RDS) database configs
- Health checks for deployment verification
- @PrePersist/@PreUpdate for automatic timestamp management
- JPA validation mode (ddl-auto: validate) - schema managed by Flyway
- spring.jpa.open-in-view=false for better performance
- Multi-stage Docker builds for smaller image size
- Named volumes for Postgres data persistence
- Service health checks in Docker Compose (depends_on with condition: service_healthy)
- Integration tests for complete workflows (Customer → Invoice → Payment)

