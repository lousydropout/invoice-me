# Technical Context

## Technology Stack
- **Language**: Java 21
- **Framework**: Spring Boot 3.5
- **Build Tool**: Gradle
- **Database**: PostgreSQL
- **ORM**: Spring Data JPA
- **Migrations**: Flyway
- **Containerization**: Docker, Docker Compose
- **Future Cloud**: AWS ECS Fargate, RDS

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
- Gradle 8.14 (Kotlin DSL)
- Docker and Docker Compose
- Local Postgres 17 via Docker Compose
- Named volume `pgdata` for Postgres data persistence

## Deployment

### Local Development
- Docker Compose spins up:
  - Postgres service
  - Application container
- Database connection: `jdbc:postgresql://postgres:5432/invoiceme`
- Health check: `curl localhost:8080/api/health` → `{status:"ok"}`

### AWS Target (Future Phase)
- **Compute**: ECS Fargate (containerized application)
- **Database**: RDS PostgreSQL instance
- **Configuration**: Environment variables
  - `DB_HOST` - RDS endpoint
  - `DB_USER` - Database username
  - `DB_PASSWORD` - Database password
  - `DB_NAME` - Database name
- **Infrastructure**: Managed infrastructure (CDK) - future phase

## API Documentation

### Endpoints

#### Health
- `GET /api/health` - Health check endpoint
  - Returns: `{"status":"ok"}`

#### Invoice CRUD
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
- Database name: `invoiceme`
- Invoice table structure (via Flyway migration V1__init.sql):
  - `id` UUID PRIMARY KEY (auto-generated)
  - `customer_name` TEXT NOT NULL
  - `amount` NUMERIC(18,2) NOT NULL CHECK (amount > 0)
  - `status` TEXT NOT NULL (enum: DRAFT, SENT, PAID, CANCELED)
  - `created_at` TIMESTAMPTZ NOT NULL DEFAULT now()
  - `updated_at` TIMESTAMPTZ NOT NULL DEFAULT now()
- Connection string format:
  - Local: `jdbc:postgresql://postgres:5432/invoiceme`
  - AWS: `jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}`

## Project Structure
```
backend/
├── src/main/java/com/invoiceme/api/
│   ├── controller/     # HealthController, InvoiceController
│   ├── domain/         # Invoice entity, InvoiceStatus enum
│   ├── dto/            # CreateInvoiceRequest, UpdateInvoiceRequest, InvoiceResponse (Java records)
│   ├── exception/      # GlobalExceptionHandler
│   ├── mapper/         # InvoiceMapper
│   ├── repository/     # InvoiceRepository
│   └── service/        # InvoiceService (@Slf4j logging)
├── src/main/resources/
│   ├── application.yml          # Default config (localhost Postgres)
│   ├── application-prod.yml    # Production config (env vars)
│   └── db/migration/            # V1__init.sql
└── Dockerfile                    # Multi-stage build (Gradle 8.14 + JRE 21)
```

