# Technical Context

## Technology Stack
- **Language**: Java 21
- **Framework**: Spring Boot 3.5.x
- **Build Tool**: Gradle 8.14.x
- **Database**: PostgreSQL 17.4 (specific patch version for TSD)
- **ORM**: Spring Data JPA (Hibernate)
- **Migrations**: Flyway (optional)
- **Containerization**: Docker, Docker Compose
- **Testing**: JUnit 5 + Testcontainers (for integration tests)
- **Auth**: Spring Security BasicAuth
- **Future Cloud**: AWS ECS Fargate, Aurora PostgreSQL Serverless v2

## Dependencies
- Spring Boot Starter Web (REST API)
- Spring Boot Starter Validation (request validation)
- Spring Boot Starter Data JPA (database access)
- Spring Boot Starter Actuator (health checks)
- PostgreSQL Driver (database connectivity)
- Flyway Core + Flyway Database PostgreSQL (database migrations)
- Lombok (code generation)
- H2 Database (test runtime)

## Development Environment
- Java 21 JDK
- Gradle 8.14.x (Kotlin DSL)
- Docker and Docker Compose
- Local Postgres 17 via Docker Compose
- Named volume `pgdata` for Postgres data persistence
- Testcontainers for integration tests

## Deployment

### Local Development
- Docker Compose spins up:
  - Postgres service
  - Application container
- Database connection: `jdbc:postgresql://postgres:5432/invoiceme`
- Health check: `curl localhost:8080/api/health` → `{status:"ok"}`

### AWS Target (Production)
- **Compute**: ECS Fargate (containerized application)
- **Database**: Aurora PostgreSQL Serverless v2
- **Configuration**: Environment variables
  - `DB_HOST` - Aurora endpoint
  - `DB_USER` - Database username
  - `DB_PASSWORD` - Database password
  - `DB_NAME` - Database name
- **Monitoring**: CloudWatch logs (event & request logging)
- **Security**: HTTPS, BasicAuth credentials from env (no public endpoints)
- **Infrastructure**: Managed via AWS CDK

## API Documentation

**Note:** The current scaffold has basic Invoice CRUD. The actual DDD implementation will have:
- Customer, Invoice, and Payment resources
- CQRS separation (Commands vs Queries)
- Rich domain models with business logic
- Domain events for side effects

**See:** `.memory-bank/apiLayer.md` for complete API specification

### Current Scaffold Endpoints (To Be Refactored)

#### Health
- `GET /api/health` - Health check endpoint
  - Returns: `{"status":"ok"}`

#### Invoice CRUD (Scaffold - Anemic)
- `POST /api/invoices` - Create invoice
  - Body: `{"customerName":"string","amount":"decimal"}`
  - Returns: InvoiceResponse with generated UUID
  - Validation: customerName (not blank), amount (positive)

- `GET /api/invoices` - List all invoices
  - Returns: Array of InvoiceResponse

- `GET /api/invoices/{id}` - Get invoice by UUID
  - Returns: InvoiceResponse or 404

- `PATCH /api/invoices/{id}` - Update invoice (partial)
  - Body: `{"customerName?":"string","amount?":"decimal","status?":"DRAFT|SENT|PAID|CANCELED"}`
  - All fields optional (uses Optional<T> in DTO)
  - Only provided fields are updated
  - Returns: Updated InvoiceResponse

- `DELETE /api/invoices/{id}` - Delete invoice
  - Returns: 204 No Content or 404

### Error Responses
- `400 Bad Request` - Validation errors (includes field-level messages)
- `404 Not Found` - Invoice not found
- `500 Internal Server Error` - Server errors

## Database Schema

**Note:** Current scaffold has basic invoice table. Actual DDD implementation will have:
- `customers` table (Customer aggregate)
- `invoices` table (Invoice aggregate)
- `line_items` table (LineItem value objects within Invoice)
- `payments` table (Payment entities within Invoice aggregate)
- Optional: `domain_events` table (for outbox pattern)

**See:** `.memory-bank/infrastructureLayer.md` for complete schema specification

### Current Scaffold Schema (To Be Refactored)
- Database name: `invoiceme`
- Invoice table structure (via Flyway migration V1__init.sql):
  - `id` UUID PRIMARY KEY (auto-generated)
  - `customer_name` TEXT NOT NULL (will become `customer_id` reference)
  - `amount` NUMERIC(18,2) NOT NULL CHECK (amount > 0) (will be calculated from line items)
  - `status` TEXT NOT NULL (enum: DRAFT, SENT, PAID - CANCELED removed, Overdue is derived)
  - `created_at` TIMESTAMPTZ NOT NULL DEFAULT now()
  - `updated_at` TIMESTAMPTZ NOT NULL DEFAULT now()
- Connection string format:
  - Local: `jdbc:postgresql://postgres:5432/invoiceme`
  - AWS: `jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}`

## Project Structure

**Current Scaffold Structure (To Be Refactored):**
```
backend/
├── src/main/java/com/invoiceme/api/
│   ├── controller/     # HealthController, InvoiceController
│   ├── domain/         # Invoice entity, InvoiceStatus enum (anemic)
│   ├── dto/            # CreateInvoiceRequest, UpdateInvoiceRequest, InvoiceResponse
│   ├── exception/      # GlobalExceptionHandler
│   ├── mapper/         # InvoiceMapper
│   ├── repository/     # InvoiceRepository
│   └── service/        # InvoiceService (@Slf4j logging)
```

**Target DDD Structure:**
```
backend/
├── src/main/java/app/
│   ├── domain/          # Aggregates, Value Objects, Domain Events
│   │   ├── customer/   # Customer aggregate
│   │   ├── invoice/    # Invoice aggregate
│   │   ├── payment/    # Payment entity (within Invoice)
│   │   └── shared/     # Money, DomainEvent interface
│   ├── application/     # Commands, Queries, Handlers
│   │   ├── commands/   # Command handlers
│   │   ├── queries/    # Query handlers
│   │   └── bus/        # DomainEventPublisher
│   ├── infrastructure/ # Repositories, JPA, Adapters
│   │   ├── persistence/# Repository adapters, JPA entities
│   │   ├── events/     # Event listeners, publishers
│   │   └── security/   # Basic Auth config
│   └── api/            # REST Controllers, DTOs
│       ├── customer/   # CustomerController
│       └── invoice/    # InvoiceController
└── src/main/resources/
    ├── application.yml
    ├── application-prod.yml
    ├── openapi.yaml    # OpenAPI specification
    └── db/migration/   # Flyway migrations
```

**See:** `.memory-bank/canonicalDomainModel.md` and `.memory-bank/infrastructureLayer.md` for complete structure

