# Vertical Slice Architecture (VSA) - Required

## Critical Requirement

**InvoiceMe backend MUST follow Vertical Slice Architecture (VSA), not Horizontal Slice Architecture (HSA).**

This is a mandatory architectural requirement from the PRD.

---

## What is VSA?

Vertical Slice Architecture organizes code **by business capability (bounded context)**, not by technical layer.

Each **bounded context** (Invoice, Customer, Payment) is a *vertical slice* containing its own:
- `domain/` - Aggregates, Entities, Value Objects, Events
- `application/` - Command Handlers, Query Handlers, Services
- `infrastructure/` - JPA Repositories, Mappers, Adapters
- `api/` - REST Controllers, DTOs

---

## What is HSA (Disallowed)?

Horizontal Slice Architecture organizes code by technical layer:
- Global `/controllers/` folder
- Global `/services/` folder
- Global `/repositories/` folder
- Global `/domain/` folder

**This structure is disallowed for InvoiceMe.**

---

## Required VSA Structure

```
src/
 ├── invoice/                    # Invoice bounded context (vertical slice)
 │    ├── domain/
 │    │    ├── Invoice.java (aggregate root)
 │    │    ├── InvoiceRepository.java (domain interface)
 │    │    ├── events/
 │    │    │    ├── InvoiceCreated.java
 │    │    │    ├── InvoiceSent.java
 │    │    │    ├── PaymentRecorded.java
 │    │    │    └── InvoicePaid.java
 │    │    └── valueobjects/
 │    │         ├── InvoiceStatus.java
 │    │         └── InvoiceNumber.java
 │    │
 │    ├── application/
 │    │    ├── commands/
 │    │    │    ├── CreateInvoiceHandler.java
 │    │    │    ├── UpdateInvoiceHandler.java
 │    │    │    ├── SendInvoiceHandler.java
 │    │    │    └── RecordPaymentHandler.java
 │    │    ├── queries/
 │    │    │    ├── GetInvoiceByIdHandler.java
 │    │    │    ├── ListInvoicesHandler.java
 │    │    │    └── ListOverdueInvoicesHandler.java
 │    │    └── bus/
 │    │         └── DomainEventPublisher.java (interface)
 │    │
 │    ├── infrastructure/
 │    │    └── persistence/
 │    │         ├── InvoiceJpaRepository.java
 │    │         ├── InvoiceRepositoryAdapter.java
 │    │         ├── entities/
 │    │         │     ├── InvoiceEntity.java
 │    │         │     ├── LineItemEntity.java
 │    │         │     └── PaymentEntity.java
 │    │         └── mappers/
 │    │              └── InvoiceMapper.java
 │    │
 │    └── api/
 │         ├── InvoiceController.java
 │         └── dto/
 │              ├── CreateInvoiceRequest.java
 │              ├── UpdateInvoiceRequest.java
 │              ├── InvoiceResponse.java
 │              └── RecordPaymentRequest.java
 │
 ├── customer/                   # Customer bounded context (vertical slice)
 │    ├── domain/
 │    │    ├── Customer.java (aggregate root)
 │    │    ├── CustomerRepository.java (domain interface)
 │    │    ├── events/
 │    │    │    ├── CustomerCreated.java
 │    │    │    ├── CustomerUpdated.java
 │    │    │    └── CustomerDeleted.java
 │    │    └── valueobjects/
 │    │         ├── Email.java
 │    │         ├── Address.java
 │    │         └── PaymentTerms.java
 │    │
 │    ├── application/
 │    │    ├── commands/
 │    │    │    ├── CreateCustomerHandler.java
 │    │    │    ├── UpdateCustomerHandler.java
 │    │    │    └── DeleteCustomerHandler.java
 │    │    └── queries/
 │    │         ├── GetCustomerByIdHandler.java
 │    │         └── ListCustomersHandler.java
 │    │
 │    ├── infrastructure/
 │    │    └── persistence/
 │    │         ├── CustomerJpaRepository.java
 │    │         ├── CustomerRepositoryAdapter.java
 │    │         ├── entities/
 │    │         │     └── CustomerEntity.java
 │    │         └── mappers/
 │    │              └── CustomerMapper.java
 │    │
 │    └── api/
 │         ├── CustomerController.java
 │         └── dto/
 │              ├── CreateCustomerRequest.java
 │              ├── UpdateCustomerRequest.java
 │              └── CustomerResponse.java
 │
 ├── payment/                    # Payment bounded context (vertical slice)
 │    ├── domain/
 │    │    ├── Payment.java (entity, owned by Invoice)
 │    │    └── PaymentMethod.java (VO or enum)
 │    │
 │    ├── application/
 │    │    └── (Note: Payment commands/queries handled by Invoice slice)
 │    │
 │    ├── infrastructure/
 │    │    └── (Note: Payment persistence handled by Invoice slice)
 │    │
 │    └── api/
 │         └── (Note: Payment endpoints in InvoiceController)
 │
 └── shared/                     # Shared primitives across bounded contexts
      ├── domain/
      │    ├── DomainEvent.java (interface)
      │    └── Money.java (value object)
      │
      ├── application/
      │    ├── errors/
      │    │    ├── ApplicationError.java
      │    │    └── ErrorCodes.java
      │    ├── ports/
      │    │    ├── EmailPort.java (optional)
      │    │    └── ClockPort.java
      │    └── bus/
      │         └── DomainEventPublisher.java (interface)
      │
      └── infrastructure/
           ├── events/
           │    ├── SimpleDomainEventPublisher.java
           │    ├── OutboxPublisher.java (future)
           │    ├── LoggingEventListener.java
           │    └── EmailNotificationListener.java (future)
           └── security/
                └── BasicAuthConfig.java
```

---

## Key VSA Principles

### 1. Bounded Context = Vertical Slice
- Each bounded context (Invoice, Customer, Payment) is a self-contained vertical slice
- All code related to a bounded context lives within that slice
- No code "leaks" into global folders

### 2. No Global Technical Folders
- ❌ **No** global `/controllers/` folder
- ❌ **No** global `/services/` folder
- ❌ **No** global `/repositories/` folder
- ❌ **No** global `/domain/` folder
- ✅ **Yes** Each bounded context has its own folders

### 3. Dependency Direction
- **Within slice**: api → application → domain (downward only)
- **Between slices**: Invoice → Payment (never reverse)
- **Shared primitives**: Imported by all slices from `/shared/`

### 4. Shared Concerns
- Shared primitives (Money, DomainEvent) live in `/shared/domain/`
- Shared application concerns (ApplicationError, ports) live in `/shared/application/`
- Shared infrastructure (event publishers, security) lives in `/shared/infrastructure/`

### 5. Independence
- Each vertical slice should be independently understandable
- Each slice should be independently testable
- Each slice is a "mini-application" with its own repository interfaces, DTOs, and handlers

---

## Payment Bounded Context Special Case

**Note:** Payment is a separate subdomain (strategic DDD) but embedded as an entity within Invoice aggregate (tactical DDD).

**VSA Implementation:**
- Payment has its own `/payment/domain/` folder (preserves subdomain identity)
- Payment commands/queries are handled by Invoice slice (since Payment is owned by Invoice)
- Payment persistence is handled by Invoice slice (PaymentEntity in Invoice infrastructure)
- Payment endpoints are in InvoiceController (since payments are part of Invoice aggregate)

This maintains:
- ✅ Subdomain identity (Payment has its own domain package)
- ✅ Aggregate boundaries (Invoice manages Payment)
- ✅ VSA structure (each bounded context is a slice)

---

## Benefits of VSA

1. **Aligns with DDD**: Structure mirrors domain model
2. **Modularity**: Each slice is self-contained
3. **Maintainability**: Easy to find code related to a bounded context
4. **Testability**: Each slice can be tested independently
5. **Scalability**: Easy to extract slices to separate services later
6. **Team Organization**: Teams can own entire slices

---

## Migration from HSA to VSA

If starting from scaffold (which uses HSA):
1. Create bounded context folders (`/invoice/`, `/customer/`, `/payment/`)
2. Move domain code to each context's `domain/` folder
3. Move application code to each context's `application/` folder
4. Move infrastructure code to each context's `infrastructure/` folder
5. Move API code to each context's `api/` folder
6. Extract shared primitives to `/shared/`
7. Remove global technical folders

---

## Implementation Checklist

- [ ] Create bounded context folders (`/invoice/`, `/customer/`, `/payment/`)
- [ ] Create vertical slice structure (domain, application, infrastructure, api) for each context
- [ ] Move domain code to context-specific folders
- [ ] Move application code to context-specific folders
- [ ] Move infrastructure code to context-specific folders
- [ ] Move API code to context-specific folders
- [ ] Extract shared primitives to `/shared/`
- [ ] Remove global technical folders (if migrating from HSA)
- [ ] Verify dependency direction (downward only within slices)
- [ ] Verify no sideways dependencies between slices (except shared)

---

## Summary

| Aspect | VSA (Required) | HSA (Disallowed) |
|--------|----------------|------------------|
| **Organization** | By bounded context | By technical layer |
| **Structure** | `/invoice/domain/`, `/invoice/application/` | `/domain/invoice/`, `/application/invoice/` |
| **Global Folders** | None (except `/shared/`) | `/controllers/`, `/services/`, `/repositories/` |
| **Independence** | Each slice is self-contained | Layers depend on each other |
| **Alignment** | Mirrors DDD structure | Separates domain from infrastructure |

**VSA is REQUIRED for InvoiceMe backend implementation.**

