# Active Context

## Current Focus
**Transitioning from scaffold to DDD implementation**

The repository currently contains a scaffolded "hello-world" Spring Boot application with basic Invoice CRUD. This scaffold provides the foundation (Java 21, Spring Boot 3.5, PostgreSQL, Docker, AWS infrastructure) but uses anemic domain models and traditional layered architecture.

**Next Phase:** Implement the actual business domain following DDD principles:
- Rich domain models (Invoice, Payment, Customer with business logic)
- CQRS separation (Commands vs Queries)
- Vertical Slice Architecture (organize by features)
- Domain events for audit and decoupling
- Line items support for invoices
- Payment processing and balance tracking
- Stripe integration

## Current State (Scaffold)

### What Exists
- Spring Boot 3.5 application with Java 21
- Basic Invoice entity (anemic - just getters/setters)
- Invoice CRUD endpoints (Create, Read, Update, Delete)
- Health endpoint at `/api/health`
- GlobalExceptionHandler with error handling
- Flyway migration V1__init.sql (basic invoice table)
- Docker Compose setup with Postgres 17
- AWS CDK infrastructure deployed (Aurora Serverless v2, ECS Fargate, ALB)
- ECR image integration

### What's Missing (Per Domain Model)
- **Customer** entity (currently only customerName string)
- **Payment** entity and payment processing
- **LineItem** support for invoices
- **Rich domain models** with business logic
- **CQRS** separation (Commands/Queries)
- **Vertical Slice Architecture** (currently layered)
- **Domain events** (InvoiceCreated, PaymentRecorded, etc.)
- **Stripe integration** for payment processing
- **Email notifications** for invoice delivery
- **Authentication** (infrastructure layer - Spring Security Basic Auth, NOT a domain concern)
- **Integration tests** for complete workflows

## AWS Deployment (Scaffold Infrastructure)

**Stack**: `InvoiceMeStack`  
**Region**: `us-east-1`  
**Status**: Deployed (ready for actual application)

**Infrastructure**:
- Aurora Serverless v2 PostgreSQL (17.4)
- ECS Fargate cluster with auto-scaling
- Application Load Balancer (public)
- Default VPC with public subnets
- CloudWatch Logs integration

## Analysis Complete

**Event Storming Documentation:** See `.memory-bank/eventStorming.md` for:
- Visual narrative flow (chronological business story)
- Command-handler matrix (architectural mapping)
- Complete domain events catalog
- Policy handlers and derived state patterns

**Architecture Decisions:** See `.memory-bank/architectureDecisions.md` for:
- Authentication strategy (infrastructure, NOT domain)
- Payment-to-Invoice relationship
- All validated architectural decisions

**Canonical Domain Model:** See `.memory-bank/canonicalDomainModel.md` for:
- Complete domain model specification (3 subdomains: Customer, Invoice, Payment)
- Detailed attributes, behaviors, invariants for each aggregate
- Value objects, entities, relationships, and events
- Directory structure and implementation guidance

**Application Layer:** See `.memory-bank/applicationLayer.md` for:
- Command contracts and handler implementations
- Transaction boundaries and event publishing patterns
- Repository interfaces (domain)
- Error handling and validation strategy
- Controller wiring and testing approach

**Infrastructure Layer:** See `.memory-bank/infrastructureLayer.md` for:
- Repository adapter implementations (JPA)
- Entity-to-domain mapping patterns
- PostgreSQL configuration (local and AWS Aurora)
- Flyway migration strategy
- Testing with H2 and Testcontainers

**API/Interface Layer:** See `.memory-bank/apiLayer.md` for:
- REST endpoint design and DTOs
- Controller implementations and patterns
- HTTP semantics and status codes
- OpenAPI/Swagger specification
- JSON examples and request/response shapes

**Query Layer (CQRS Read Side):** See `.memory-bank/queryLayer.md` for:
- Read model DTOs and projections
- Query handler implementations (JdbcTemplate, JPA)
- Direct SQL queries optimized for Postgres
- Derived state calculations (balance, overdue)
- Database indexes for query performance

**Domain Event Handling:** See `.memory-bank/eventHandling.md` for:
- Domain event definitions and marker interface
- Aggregate event raising pattern (pullDomainEvents)
- Application layer event publishing
- Infrastructure event listeners
- In-memory publisher (MVP) vs Outbox pattern (production)
- Event-handler mapping and side effects

**Key Commands Identified:**
- CreateCustomer, UpdateCustomer, DeleteCustomer
- CreateInvoice, UpdateInvoice, SendInvoice
- RecordPayment

**Domain Events:**
- CustomerCreated, CustomerUpdated, CustomerDeleted
- InvoiceCreated, InvoiceUpdated, InvoiceSent, InvoicePaid
- PaymentRecorded

**Policies:**
- NotifyCustomerByEmail (triggered by InvoiceSent)

**Derived State:**
- `isOverdue` (calculated at query time: `dueDate < today && status != PAID`)

## Open Questions
- Implementation order: Start with Core Domain (Invoicing) or build all aggregates in parallel?
- Stripe integration: When to integrate? (After core domain is solid)
- Frontend: Backend-only for now, or plan for React/Next.js later?

## Next Steps

**See:** `.memory-bank/implementationTasks.md` for complete, actionable task list.

### Implementation Roadmap (8 Epics, 25 Tasks)

1. **Epic 1: Project Setup & Infrastructure** (4 tasks)
   - Initialize Spring Boot project with VSA structure
   - Configure Docker Compose
   - Set up BasicAuth
   - Create shared kernel (DomainEvent, Money, Email, Address)

2. **Epic 2: Customer Context** (4 tasks)
   - Domain model (Customer aggregate, events)
   - Repository & JPA adapter
   - Command handlers (CRUD)
   - REST API endpoints

3. **Epic 3: Invoice Context** (5 tasks)
   - Domain model (Invoice aggregate, LineItem, Payment, events)
   - Repository & persistence mapping
   - Command handlers (Create, Update, Send, RecordPayment)
   - REST API endpoints
   - Domain event publisher

4. **Epic 4: Payment Context** (2 tasks)
   - Domain model (Payment entity)
   - Manual payment command

5. **Epic 5: CQRS Read Side** (3 tasks)
   - Define read models (DTOs)
   - Query handlers (SQL/JPA)
   - Read API endpoints

6. **Epic 6: Cross-Cutting Concerns** (3 tasks)
   - Logging (structured logs)
   - Global error handling
   - DTO validation

7. **Epic 7: Testing & Validation** (3 tasks)
   - Unit tests (domain invariants)
   - Integration tests (Testcontainers)
   - API validation (Postman)

8. **Epic 8: Deployment** (1 task)
   - AWS Aurora + Fargate deployment

### Task Breakdown
- **15 tasks** fully automatable by Cursor (✅)
- **8 tasks** require human verification (⚙️)
- **2 tasks** manual-only (🧍‍♂️)

### Quick Start
1. Begin with Epic 1 (Setup & Infrastructure)
2. Follow VSA folder structure strictly
3. Implement Customer context first (simplest)
4. Then Invoice context (core domain)
5. Add Payment, CQRS, cross-cutting, testing, deployment

