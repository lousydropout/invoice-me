# Architecture Decisions

## Authentication Strategy

### Decision
Authentication is **NOT** a domain concern. It's a **cross-cutting infrastructure concern** implemented at the API boundary.

### Rationale
- **Single-tenant system**: One business user (or small, fixed set of internal users)
- **Simple scope**: Just need to "keep out strangers" - no multi-tenant user management, RBAC, or organization boundaries
- **DDD alignment**: Auth doesn't belong in the domain model, ubiquitous language, or event storming
- **PRD compliance**: Satisfies "Authentication required" without introducing unnecessary domain complexity

### Implementation
- **Technology**: Spring Security Basic Auth or lightweight JWT filter
- **Storage**: Credentials in `.env` file or simple user table
- **Scope**: Protects all REST endpoints (`/api/invoices`, `/api/customers`, etc.)
- **Location**: `/infrastructure/security` (isolated from domain)

### What This Means
- ❌ **No** `User` aggregate root
- ❌ **No** `UserLoggedIn` domain events
- ❌ **No** `RegisterUser` commands
- ❌ **No** RBAC or role system
- ❌ **No** multi-tenant user management
- ✅ **Yes** Simple Basic Auth protecting API endpoints
- ✅ **Yes** Infrastructure layer concern only
- ✅ **Yes** DDD-neutral (doesn't pollute domain model)

### Code Organization
```
/src
 └── infrastructure
      └── security
           ├── BasicAuthConfig.java
           ├── SecurityFilter.java
           └── UserDetailsService.java
```

### Example Configuration
```yaml
spring.security.user.name=admin
spring.security.user.password=${ADMIN_PASSWORD}
```

### Context Map Position
Auth wraps the API boundary but never reaches into bounded contexts:

```
                 ┌────────────────────────┐
                 │   Infrastructure Auth  │
                 │ (BasicAuth Filter, etc)│
                 └─────────────┬──────────┘
                               │
                     (protects all APIs)
                               │
      ┌────────────────────────▼────────────────────────┐
      │                Invoicing (Core)                 │
      └───────────────┬───────────┬───────────────┬────┘
                      │           │               │
                Customers     Payments       Business Config
                      │           │               │
                  Notifications   │             Reporting
                      │           │               │
                   Accounting Integration (future)
```

Auth never reaches into any of those boxes; it wraps them at the API boundary.

---

## Payment-to-Invoice Relationship

### Decision
Payment is a separate *subdomain* (strategic DDD) but embedded as an *entity* within the Invoice aggregate (tactical DDD).

### Rationale
- **Strategic**: Payment is a supporting subdomain (distinct in ubiquitous language)
- **Tactical**: Embedded entity maintains immediate consistency and enforces invariants
- **Future-proof**: Can be extracted to separate aggregate/bounded context when needed

### Implementation
- Payment lives in `/domain/payment` package (preserves subdomain identity)
- Invoice imports Payment (dependency: Invoice → Payment, never reverse)
- Payment is NOT a separate aggregate root for MVP

---

## Invoice Status vs. Derived State

### Decision
- **Domain States (stored)**: Draft, Sent, Paid
- **Derived State (query concern)**: Overdue (calculated as `dueDate < today && status != PAID`)

### Rationale
Keeps aggregates clean; overdue belongs in read models/projections, not domain state.

---

## Balance Calculation

### Decision
Balance calculation lives in Invoice aggregate method `calculateBalance()`.

### Formula
`total - sum(payments)`

### Rationale
Invoice "knows" its balance by definition; encapsulates invariant.

---

## LineItem Design

### Decision
LineItem is a Value Object (immutable).

### Rationale
- Once invoice is sent, line items shouldn't mutate
- Identity not required (equality by value suffices)
- Can reconstruct from audit logs if needed
- Can promote to Entity later if partial fulfillment is needed

---

## Business Information

### Decision
Business information is configuration/settings (not a separate aggregate).

### Rationale
Single-tenant MVP assumption; static metadata for issuing business.

### Future
Can elevate to Business aggregate if multi-tenant support is needed.

---

## Invoice Number Generation

### Decision
Sequential, human-readable format (e.g., INV-2024-001).

### Rationale
Domain-friendly, easier for users to reference.

---

## Tax Calculation

### Decision
Simple tax model (single rate per invoice or per line item).

### Rationale
Start simple; can add Tax aggregate/value object later if complexity grows.

---

## Multi-Tenancy

### Decision
Single-tenant system (one business per instance).

### Rationale
Keeps bounded contexts smaller, simpler data isolation.

### Future
Can add multi-tenant support with Business aggregate and tenant isolation.

---

## Stripe Integration

### Decision
Skip for MVP; focus on manual payment recording.

### Rationale
Reduces infrastructure complexity; can add Stripe adapter later.

### Future
Externalize Payment as separate aggregate when Stripe/webhook integration is added.

---

## CQRS Read Models

### Decision
Start with querying write model directly.

### Rationale
Simpler for MVP; sufficient for basic queries.

### Future
Add read projections/database views for complex aggregations and dashboard queries.

---

## Domain Event Publishing Strategy

### Decision
**REQUIRED:** Synchronous, in-memory events only. No outbox pattern, no distributed event store.

### Rationale
- **PRD Non-Goal**: Explicitly states no outbox pattern or distributed event store
- **Simplicity**: Synchronous events sufficient for MVP
- **Transaction Safety**: Use `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` to ensure events published after transaction commit
- **Performance**: In-memory events are fast and sufficient for MVP scale

### Implementation
- **Publisher**: `SimpleDomainEventPublisher` using Spring's `ApplicationEventPublisher`
- **Transaction Context**: `AFTER_COMMIT` phase ensures events published after transaction commit
- **Listeners**: Use `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` for all event listeners
- **No External Queues**: No Kafka, RabbitMQ, SQS, or message brokers
- **No Outbox Table**: No `domain_events` table for MVP

### What This Means
- ✅ **Yes** Synchronous event publishing within same process
- ✅ **Yes** Events published after transaction commit
- ✅ **Yes** In-memory event handling (logging, simulated emails)
- ❌ **No** Outbox pattern (explicitly non-goal)
- ❌ **No** Distributed event store (explicitly non-goal)
- ❌ **No** Asynchronous message brokers (explicitly non-goal)

### Code Example
```java
@Component
public class SimpleDomainEventPublisher implements DomainEventPublisher {
    private final ApplicationEventPublisher springPublisher;
    
    @Override
    public void publish(List<? extends DomainEvent> events) {
        events.forEach(springPublisher::publishEvent);
    }
}

@Component
public class LoggingEventListener {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvoiceSent(InvoiceSent event) {
        log.info("Invoice {} sent", event.invoiceId());
    }
}
```

---

## Vertical Slice Architecture (VSA)

### Decision
**REQUIRED:** InvoiceMe backend **MUST** follow Vertical Slice Architecture (VSA), not Horizontal Slice Architecture (HSA).

### Rationale
- VSA mirrors the domain structure defined in DDD
- Organizes codebase by business capability (bounded context), not by technical layer
- Keeps the system modular, maintainable, and aligned with business capabilities
- Each slice is independently understandable and testable as a small feature module

### Implementation
- Each **bounded context** (Invoice, Customer, Payment) is a *vertical slice* containing its own:
  - `domain/` - Aggregates, Entities, Value Objects, Events
  - `application/` - Command Handlers, Query Handlers, Services
  - `infrastructure/` - JPA Repositories, Mappers, Adapters
  - `api/` - REST Controllers, DTOs

### What This Means
- ❌ **No** global `/controllers`, `/services`, or `/repositories` folders (HSA disallowed)
- ✅ **Yes** Each bounded context is self-contained
- ✅ **Yes** Package imports only go downward (api → application → domain)
- ✅ **Yes** Shared primitives (Money, DomainEvent) live in `/shared/`
- ✅ **Yes** Each slice is a "mini-application" with its own repository interfaces, DTOs, and handlers

### Code Organization
```
src/
 ├── invoice/        # Invoice bounded context (vertical slice)
 │    ├── domain/
 │    ├── application/
 │    ├── infrastructure/
 │    └── api/
 ├── customer/       # Customer bounded context (vertical slice)
 │    ├── domain/
 │    ├── application/
 │    ├── infrastructure/
 │    └── api/
 ├── payment/       # Payment bounded context (vertical slice)
 │    ├── domain/
 │    └── (Note: Payment commands/queries handled by Invoice slice)
 └── shared/        # Shared primitives
      ├── domain/
      ├── application/
      └── infrastructure/
```

