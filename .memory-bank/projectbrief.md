# Project Brief

## Overview
InvoiceMe is a production-quality ERP-style invoicing system built with Java 21 and Spring Boot 3.5. The project demonstrates mastery of Domain-Driven Design (DDD), CQRS, and Vertical Slice Architecture principles while using AI-assisted development tools effectively.

**Version:** 1.2 (Consolidated DDD & Assessment Spec)

**Current State:** The repository contains a scaffolded "hello-world" Spring Boot application with basic Invoice CRUD operations. This scaffold provides the foundation (Java 21, Spring Boot 3.5, PostgreSQL, Docker) but does not yet implement the actual business domain.

**Target State:** A fully-featured invoicing system with rich domain models, proper DDD boundaries, CQRS separation, and all required business capabilities.

**Stack Versions:**
- Java: **21**
- Spring Boot: **3.5.x**
- Gradle: **8.14.x**
- PostgreSQL: **17**

## Business Scenario
> A business issues invoices to customers, collects payments (potentially through Stripe or manual entry), and needs to track who owes what, who has paid, and when.

## Goals
- Implement a production-quality invoicing system following DDD principles
- Demonstrate CQRS (Command Query Responsibility Segregation)
- Organize code using Vertical Slice Architecture (VSA) - **REQUIRED**
- Build rich domain models with business logic (not anemic entities)
- Support customer management, invoice lifecycle, and payment tracking
- Maintain clean architecture boundaries (Domain, Application, Infrastructure)
- Achieve CRUD latency < 200 ms average
- Document AI usage in development logs

## Non-Goals
- **No** third-party payment gateways (Stripe optional, not required)
- **No** asynchronous message brokers (Kafka, RabbitMQ, SQS)
- **No** multi-tenant logic
- **No** outbox pattern or distributed event store (synchronous in-memory events only)
- **No** real email delivery (listeners log simulated emails)

## Key Requirements

### Core Domain — Invoicing
- Invoice creation with line items (description, quantity, unit price, tax)
- Auto-calculation of subtotals, taxes, and totals
- Invoice lifecycle: Draft → Sent → Paid (Overdue is derived state, not stored)
- Immutable states (cannot edit sent/paid invoices)
- Invoice metadata: issue date, due date, invoice number, payment terms

### Supporting Domain — Payments
- Record payments (manual or via Stripe webhook)
- Apply payments to invoices with balance tracking
- Support partial payments
- Auto-update invoice status when fully paid
- Stripe integration for payment processing

### Supporting Domain — Customer Management
- Customer CRUD operations
- Link invoices and payments to customers
- Customer default settings (payment terms, etc.)

### Generic Subdomain — Communication
- Email notifications (invoice delivery, payment confirmations, reminders)
- Track delivery status

### Generic Subdomain — Reporting & Queries
- Query invoices by status, customer, date range
- Outstanding/overdue invoice reports (overdue calculated at query time)
- Revenue summaries and analytics

### Infrastructure Concern — Authentication
**Note:** Authentication is NOT a domain concern. It's a cross-cutting infrastructure concern.
- Simple Basic Auth or JWT filter protecting API endpoints
- Single-tenant: credentials in `.env` or simple user table
- No User aggregate, no domain events, no RBAC
- See `.memory-bank/architectureDecisions.md` for details

## Technical Constraints
- **Backend:** Java 21 + Spring Boot 3.5 (required)
- **Database:** PostgreSQL (preferred) or H2 for testing
- **Architecture:** Must follow DDD, CQRS, and VSA principles
- **Deployment:** AWS ECS Fargate + RDS (infrastructure already scaffolded)

## Success Criteria (Acceptance Criteria)
✅ DDD implemented (aggregates, entities, value objects, domain events)
✅ CQRS separation (commands vs. queries)
✅ VSA folder structure enforced
✅ BasicAuth authentication
✅ Local Docker + Aurora deployment works
✅ Full user flows tested end-to-end
✅ CRUD latency under 200 ms
✅ AI usage documented in development logs

## Testing Requirements
- **Unit Tests**: Domain aggregates and invariants (JUnit 5)
- **Integration Tests**: Command handlers + Postgres via Testcontainers (Spring Boot Test)
- **End-to-End Flow**: Customer → Invoice → Payment lifecycle (Manual or Postman)
- **Performance Test**: CRUD latency < 200 ms average (Manual verification)
- All tests must pass via `./gradlew test`

