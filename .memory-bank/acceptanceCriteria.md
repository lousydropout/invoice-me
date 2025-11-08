# Acceptance Criteria

## Overview

This document outlines the acceptance criteria for InvoiceMe, based on the consolidated PRD (Version 1.2).

---

## Core Architectural Requirements

### ✅ DDD Implementation
- [ ] Aggregates implemented (Invoice, Customer)
- [ ] Entities implemented (Payment within Invoice)
- [ ] Value Objects implemented (LineItem, Money, InvoiceStatus, InvoiceNumber, Email, Address)
- [ ] Domain Events implemented (CustomerCreated, InvoiceCreated, InvoiceSent, PaymentRecorded, InvoicePaid)
- [ ] Rich domain models with business logic (not anemic entities)
- [ ] Invariants enforced in aggregates (e.g., cannot edit sent/paid invoices)

### ✅ CQRS Separation
- [ ] Commands (writes) clearly separated from Queries (reads)
- [ ] Command handlers implemented (CreateCustomer, CreateInvoice, SendInvoice, RecordPayment, etc.)
- [ ] Query handlers implemented (GetInvoiceById, ListInvoices, ListOverdueInvoices, etc.)
- [ ] Read models defined (InvoiceSummaryView, InvoiceDetailView, CustomerView)
- [ ] No queries in command handlers, no commands in query handlers

### ✅ VSA Folder Structure
- [ ] Code organized by bounded context (Invoice, Customer, Payment), not by technical layers
- [ ] Each bounded context is a vertical slice with domain, application, infrastructure, and api layers
- [ ] No global `/controllers/`, `/services/`, or `/repositories/` folders (HSA disallowed)
- [ ] Shared primitives (Money, DomainEvent) live in `/shared/`
- [ ] Package imports only go downward (api → application → domain), never sideways

---

## Functional Requirements

### ✅ Customer Management
- [ ] Create customer (POST `/api/customers`)
- [ ] Update customer (PUT `/api/customers/{id}`)
- [ ] Delete customer (DELETE `/api/customers/{id}`)
- [ ] List customers (GET `/api/customers`)
- [ ] Get customer by ID (GET `/api/customers/{id}`)
- [ ] Customer validation (unique email, required fields)

### ✅ Invoice Management
- [ ] Create invoice draft (POST `/api/invoices`)
- [ ] Update invoice draft (PUT `/api/invoices/{id}`) - only if status is DRAFT
- [ ] Send invoice (POST `/api/invoices/{id}/send`) - transitions DRAFT → SENT
- [ ] Get invoice by ID (GET `/api/invoices/{id}`)
- [ ] List all invoices (GET `/api/invoices`)
- [ ] List overdue invoices (GET `/api/invoices/overdue`)
- [ ] Invoice validation (customer exists, line items required, etc.)

### ✅ Payment Management
- [ ] Record payment (POST `/api/invoices/{id}/payments`)
- [ ] Payment validation (amount ≤ balance, invoice exists)
- [ ] Auto-update invoice status to PAID when balance = 0
- [ ] Support partial payments (keep running balance)

### ✅ Reporting & Queries
- [ ] Outstanding balances per customer (GET `/api/customers/outstanding`)
- [ ] Overdue invoices query (derived state: `dueDate < now && status != PAID`)
- [ ] Invoice summaries with customer names
- [ ] Invoice details with line items and payments

---

## Technical Requirements

### ✅ Authentication
- [ ] BasicAuth implemented (Spring Security)
- [ ] All endpoints protected (except health check)
- [ ] Credentials from environment variables
- [ ] No User aggregate, no domain events, no RBAC

### ✅ Database & Persistence
- [ ] PostgreSQL 17 (local Docker, AWS Aurora Serverless v2)
- [ ] Spring Data JPA for persistence
- [ ] Repository adapters bridge domain and JPA entities
- [ ] Optimistic locking with `@Version`
- [ ] Flyway migrations (optional, but recommended)

### ✅ Event Handling
- [ ] Domain events defined and emitted from aggregates
- [ ] `SimpleDomainEventPublisher` implemented (Spring ApplicationEventPublisher)
- [ ] Event listeners use `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`
- [ ] Synchronous, in-memory events only (no outbox, no distributed event store)
- [ ] Event logging implemented (LoggingEventListener)

### ✅ API Layer
- [ ] REST controllers for Customer and Invoice resources
- [ ] DTOs defined (CreateCustomerRequest, InvoiceResponse, etc.)
- [ ] DTO → Command mapping implemented
- [ ] OpenAPI/Swagger documentation
- [ ] Global exception handler (maps domain errors to HTTP status codes)
- [ ] Validation annotations on DTOs (`@NotNull`, `@Email`, etc.)

---

## Testing Requirements

### ✅ Unit Tests
- [ ] Domain aggregates tested (Invoice, Customer)
- [ ] Business invariants tested (cannot edit sent invoices, payment ≤ balance)
- [ ] Value objects tested (Money arithmetic, Email validation)
- [ ] All unit tests pass via `./gradlew test`

### ✅ Integration Tests
- [ ] Command handlers tested with Testcontainers (PostgreSQL)
- [ ] Query handlers tested with Testcontainers
- [ ] Repository adapters tested
- [ ] End-to-end workflows tested (Customer → Invoice → Payment)
- [ ] All integration tests pass via `./gradlew test`

### ✅ Performance Tests
- [ ] CRUD latency < 200 ms average (manual verification)
- [ ] Query performance acceptable for MVP scale

---

## Deployment Requirements

### ✅ Local Development
- [ ] Docker Compose setup works (`docker-compose up`)
- [ ] PostgreSQL 17 container starts successfully
- [ ] Application container starts successfully
- [ ] Health check endpoint responds (`GET /api/health`)
- [ ] Database connection works

### ✅ Production (AWS)
- [ ] ECS Fargate deployment works
- [ ] Aurora PostgreSQL Serverless v2 connection works
- [ ] Environment variables configured correctly
- [ ] CloudWatch logs working
- [ ] HTTPS enabled (if applicable)
- [ ] BasicAuth credentials from environment

---

## Documentation Requirements

### ✅ Code Documentation
- [ ] OpenAPI/Swagger specification complete
- [ ] README.md updated with setup instructions
- [ ] Architecture decisions documented in memory bank

### ✅ AI Usage Documentation
- [ ] AI usage documented in development logs (acceptance criteria requirement)
- [ ] Clear attribution of AI-assisted code (if applicable)

---

## User Flow Validation

### ✅ Create & Send Invoice Flow
1. Create customer → `CustomerCreated` event
2. Create invoice draft → `InvoiceCreated` event
3. Send invoice → `InvoiceSent` event
4. Verify invoice status = SENT

### ✅ Record Payment Flow
1. Record payment → `PaymentRecorded` event
2. If balance = 0 → `InvoicePaid` event
3. Verify invoice status = PAID (if fully paid)

### ✅ Overdue Report Flow
1. Query overdue invoices (GET `/api/invoices/overdue`)
2. Verify only invoices where `dueDate < now && status != PAID` are returned
3. Verify derived state calculated correctly

### ✅ Outstanding Balance Flow
1. Query outstanding balances (GET `/api/customers/outstanding`)
2. Verify aggregated totals per customer
3. Verify calculations correct

---

## Summary Checklist

| Category | Status | Notes |
|----------|--------|-------|
| DDD Implementation | ⬜ | Aggregates, entities, VOs, events |
| CQRS Separation | ⬜ | Commands vs queries clearly separated |
| VSA Structure | ⬜ | Code organized by bounded context |
| Functional Requirements | ⬜ | All user stories implemented |
| Technical Requirements | ⬜ | Auth, DB, events, API |
| Testing | ⬜ | Unit, integration, performance |
| Deployment | ⬜ | Local Docker + AWS |
| Documentation | ⬜ | Code docs + AI usage logs |

---

## Notes

- All acceptance criteria must be met for project completion
- Performance target: CRUD latency < 200 ms average
- AI usage must be documented in development logs
- All tests must pass via `./gradlew test`
- Code must follow VSA structure (no HSA allowed)

