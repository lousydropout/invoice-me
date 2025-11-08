# Memory Bank Index

This directory contains the complete architectural analysis and implementation guidance for the InvoiceMe project.

## Quick Reference

### Core Documents (Start Here)

1. **`.memory-bank/projectbrief.md`** - Project overview, goals, and requirements (PRD v1.2)
2. **`.memory-bank/technicalSpecification.md`** - **Complete technical specification for implementation (TSD)**
3. **`.memory-bank/canonicalDomainModel.md`** - Complete domain model specification (3 subdomains)
4. **`.memory-bank/architectureDecisions.md`** - All validated architectural decisions
5. **`.memory-bank/acceptanceCriteria.md`** - Complete acceptance criteria checklist

### Implementation Guides (By Layer)

6. **`.memory-bank/applicationLayer.md`** - Command handlers and application service patterns
7. **`.memory-bank/queryLayer.md`** - Query handlers and CQRS read side patterns
8. **`.memory-bank/eventHandling.md`** - Domain event publishing and handling
9. **`.memory-bank/infrastructureLayer.md`** - Repository adapters, JPA entities, PostgreSQL
10. **`.memory-bank/apiLayer.md`** - REST controllers, DTOs, OpenAPI specification

### Analysis & Patterns

11. **`.memory-bank/eventStorming.md`** - Event storming (visual narrative + command-handler matrix)
12. **`.memory-bank/systemPatterns.md`** - Architecture principles and design patterns
13. **`.memory-bank/domainModel.md`** - Detailed domain structure and business requirements
14. **`.memory-bank/activeContext.md`** - Current state, what's missing, next steps
15. **`.memory-bank/techContext.md`** - Technology stack and configuration
16. **`.memory-bank/vsaArchitecture.md`** - Vertical Slice Architecture detailed guide

### Progress Tracking

17. **`.memory-bank/implementationTasks.md`** - **Complete implementation task list (8 epics, 25 tasks)**
18. **`.memory-bank/progress.md`** - Implementation progress log

---

## Key Architectural Decisions (Summary)

### Domain Model
- **3 Subdomains**: Customer (supporting), Invoice (core), Payment (supporting)
- **2 Aggregate Roots**: Invoice, Customer
- **Payment**: Separate subdomain but embedded entity within Invoice aggregate (MVP)
- **LineItem**: Value Object (immutable)
- **Status**: Draft, Sent, Paid (stored) | Overdue (derived at query time)

### Vertical Slice Architecture (VSA) - **REQUIRED**
- **MUST** follow Vertical Slice Architecture, not Horizontal Slice Architecture
- Organize by bounded context (Invoice, Customer, Payment), not by technical layers
- Each bounded context is a vertical slice with its own domain, application, infrastructure, and api layers
- **No global** `/controllers`, `/services`, or `/repositories` folders (HSA disallowed)
- Shared primitives (Money, DomainEvent) live in `/shared/`
- Package imports only go downward (api → application → domain), never sideways

### Authentication
- **NOT a domain concern** - Infrastructure layer only
- Basic Auth or JWT filter
- No User aggregate, no domain events, no RBAC
- Single-tenant system

### CQRS
- **Commands**: Write operations via command handlers
- **Queries**: Read operations via query handlers (direct SQL for performance)
- **Read Models**: Optimized projections (InvoiceSummaryView, InvoiceDetailView)

### Event Handling
- **MVP (Required)**: Synchronous, in-memory publisher (Spring ApplicationEventPublisher)
- **Transaction Context**: `AFTER_COMMIT` phase ensures events published after transaction commit
- **Pattern**: Aggregates raise events → Application publishes → Infrastructure handles (synchronously)
- **Non-Goal**: No outbox pattern, no distributed event store, no message brokers

### Infrastructure
- **Repositories**: Domain interfaces, infrastructure implementations
- **Adapters**: Bridge domain and JPA entities via mappers
- **Database**: PostgreSQL 17 (local Docker, AWS Aurora Serverless v2)
- **Migrations**: Flyway (optional)
- **Testing**: Testcontainers for integration tests

---

## Implementation Roadmap

**See:** `.memory-bank/implementationTasks.md` for complete, detailed task breakdown.

### Quick Overview (8 Epics)

1. **Epic 1: Setup & Infrastructure** - Project initialization, Docker, BasicAuth, shared kernel
2. **Epic 2: Customer Context** - Customer aggregate, repository, commands, REST API
3. **Epic 3: Invoice Context** - Invoice aggregate, LineItem, Payment, commands, REST API, events
4. **Epic 4: Payment Context** - Payment entity, manual payment command
5. **Epic 5: CQRS Read Side** - Read models, query handlers, read API
6. **Epic 6: Cross-Cutting** - Logging, error handling, validation
7. **Epic 7: Testing** - Unit tests, integration tests, API validation
8. **Epic 8: Deployment** - AWS Aurora + Fargate

**Total:** 25 tasks (15 automatable, 8 require verification, 2 manual)

---

## Document Relationships

```
projectbrief.md
    ↓
canonicalDomainModel.md ←→ architectureDecisions.md
    ↓                           ↓
eventStorming.md          systemPatterns.md
    ↓                           ↓
applicationLayer.md ←→ queryLayer.md ←→ eventHandling.md
    ↓                  ↓                  ↓
infrastructureLayer.md ←→ apiLayer.md
    ↓
activeContext.md (current state)
```

---

## Consistency Checklist

✅ **Payment**: Consistently documented as entity within Invoice aggregate (not separate aggregate root)
✅ **Overdue**: Consistently documented as derived state (not stored, not domain event)
✅ **Authentication**: Consistently documented as infrastructure concern (not domain)
✅ **Status Values**: Draft, Sent, Paid (Overdue is derived)
✅ **Aggregate Roots**: Invoice, Customer (Payment is entity, not aggregate)
✅ **Event Flow**: Domain → Application → Infrastructure (synchronous, in-memory, AFTER_COMMIT)
✅ **CQRS**: Commands (writes) vs Queries (reads) clearly separated
✅ **VSA**: Code organized by bounded context (vertical slices), not by technical layers
✅ **Stack Versions**: Java 21, Spring Boot 3.5.x, Gradle 8.14.x, PostgreSQL 17
✅ **Event Publishing**: Synchronous, in-memory only (no outbox, no distributed event store)
✅ **Testing**: Testcontainers for integration tests
✅ **Performance**: CRUD latency < 200 ms average
✅ **AI Usage**: Documented in development logs (acceptance criteria)

---

## Notes

- All documents prioritize information from the current analysis session
- Scaffold state is clearly marked as "to be refactored"
- Future evolution paths are documented but not required for MVP
- All architectural decisions are validated and consistent across documents

