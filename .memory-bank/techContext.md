# Technical Context

## Technology Stack
- **Language**: Java 21
- **Framework**: Spring Boot 3.5.0
- **Build Tool**: Gradle 8.14.x (Kotlin DSL)
- **Database**: PostgreSQL 17.4
- **ORM**: Spring Data JPA (Hibernate)
- **Schema Management**: Spring Boot SQL initialization (`schema.sql`)
- **Containerization**: Docker, Docker Compose
- **Testing**: JUnit 5 + Testcontainers (for integration tests)
- **Auth**: Spring Security BasicAuth (configurable via environment variables)
- **API Documentation**: OpenAPI 3.0 / Swagger UI (springdoc-openapi-starter-webmvc-ui:2.7.0)
- **Cloud**: AWS ECS Fargate + Aurora Serverless v2 (CDK infrastructure ready)

## Dependencies
- Spring Boot Starter Web (REST API)
- Spring Boot Starter Validation (request validation)
- Spring Boot Starter Data JPA (database access)
- Spring Boot Starter Actuator (health checks)
- Spring Boot Starter Security (BasicAuth)
- PostgreSQL Driver 42.7.3 (database connectivity)
- springdoc-openapi-starter-webmvc-ui:2.7.0 (OpenAPI/Swagger documentation)
- Testcontainers PostgreSQL (integration tests)
- Lombok (code generation)
- H2 Database (test runtime)
- JaCoCo (code coverage reporting)

## Development Environment
- Java 21 JDK
- Gradle 8.14.x (Kotlin DSL)
- Docker and Docker Compose
- Local Postgres 17 via Docker Compose
- Named volume `pgdata` for Postgres data persistence
- Testcontainers for integration tests
- Dev profile with seed data (`SPRING_PROFILES_ACTIVE=dev`)

## Deployment

### Local Development
- Docker Compose spins up:
  - Postgres service
  - Application container
- Database connection: `jdbc:postgresql://postgres:5432/invoiceme`
- Health check: `curl localhost:8080/api/health` → `{status:"ok"}`

### AWS Target (Production)
- **Infrastructure**: AWS CDK (TypeScript) in `infra/cdk/`
- **Compute**: ECS Fargate (containerized application)
  - Auto-scaling Fargate tasks
  - 512 MB memory, 256 CPU units per task
  - Container images from ECR
- **Database**: Aurora Serverless v2 PostgreSQL 17.4
  - Auto-scaling: 0.5-1 ACU (Aurora Capacity Units)
  - High availability across multiple AZs
  - Credentials stored in AWS Secrets Manager
- **Networking**: 
  - Application Load Balancer (ALB) with HTTPS
  - Route53 hosted zone (`invoiceme.vincentchan.cloud`) with A Record ALIAS to ALB
  - ACM certificate for SSL/TLS termination
  - Default VPC with public subnets
  - Security groups for traffic control
- **Configuration**: Environment variables (from `.env` file or Secrets Manager)
  - `DB_HOST` - Aurora endpoint (auto-configured by CDK)
  - `DB_USER` - Database username (from Secrets Manager)
  - `DB_PASSWORD` - Database password (from Secrets Manager)
  - `DB_NAME` - Database name (`invoiceme`)
  - `SPRING_PROFILES_ACTIVE` - `prod` for production
  - `SPRING_SECURITY_USER_NAME` - BasicAuth username (optional)
  - `SPRING_SECURITY_USER_PASSWORD` - BasicAuth password (optional)
- **Monitoring**: CloudWatch Logs (`/ecs/invoiceme-api`)
- **Security**: 
  - HTTPS via ALB with ACM certificate
  - BasicAuth credentials (configurable via env or Secrets Manager)
  - Security groups restrict access (ALB → ECS → Aurora)
- **CDK Configuration**: 
  - Environment variables in `infra/cdk/.env` file
  - Required: `AWS_ACCOUNT_ID`, `AWS_REGION`, `ACM_CERTIFICATE_ARN`
  - Optional: `DOMAIN_NAME`, `ECR_REPOSITORY_NAME`, `ECR_IMAGE_TAG`, BasicAuth credentials

## API Documentation

**Complete Implementation:**
- Customer, Invoice, and Payment resources
- CQRS separation (Commands vs Queries)
- Rich domain models with business logic
- Domain events for side effects
- OpenAPI 3.0 / Swagger UI documentation

**See:** `.memory-bank/apiLayer.md` for complete API specification

**API Endpoints:**
- Customer CRUD: `POST`, `GET`, `GET/{id}`, `PUT/{id}`, `DELETE/{id}`
- Invoice CRUD: `POST`, `GET`, `GET/{id}`, `PUT/{id}`, `POST/{id}/send`
- Payment: `POST /api/invoices/{id}/payments`
- Queries: `GET /api/invoices`, `GET /api/invoices/overdue`, `GET /api/customers/outstanding`

**Error Responses:**
- Standardized `ApiError` DTO with `code`, `message`, and optional `details`
- `400 Bad Request` - Validation errors
- `404 Not Found` - Resource not found
- `422 Unprocessable Entity` - Business rule violations
- `500 Internal Server Error` - Server errors

## Database Schema

**Schema Management**: Spring Boot SQL initialization using `schema.sql`
- Single consolidated schema file: `backend/src/main/resources/schema.sql`
- Automatically executed on startup when `spring.sql.init.mode=always`
- Includes all tables: `customers`, `invoices`, `line_items`, `payments`, `domain_events`
- All indexes and foreign key constraints defined

**Tables**:
- `customers` table (Customer aggregate)
- `invoices` table (Invoice aggregate)
- `line_items` table (LineItem value objects within Invoice)
- `payments` table (Payment entities within Invoice aggregate)
- `domain_events` table (event persistence for debugging/audit)

**See:** `.memory-bank/infrastructureLayer.md` for complete schema specification

## Project Structure

**Current Implementation Structure (VSA - Vertical Slice Architecture):**
```
backend/
├── src/main/java/com/invoiceme/
│   ├── customer/              # Customer bounded context (vertical slice)
│   │   ├── domain/           # Customer aggregate, value objects, events
│   │   ├── application/      # Command/query handlers
│   │   ├── infrastructure/   # Repository adapters, JPA entities
│   │   └── api/              # REST controllers, DTOs
│   ├── invoice/              # Invoice bounded context (vertical slice)
│   │   ├── domain/           # Invoice aggregate, LineItem, Payment, events
│   │   ├── application/      # Command/query handlers
│   │   ├── infrastructure/   # Repository adapters, JPA entities
│   │   └── api/              # REST controllers, DTOs
│   ├── payment/              # Payment bounded context
│   │   └── domain/           # Payment entity
│   └── shared/               # Shared across bounded contexts
│       ├── domain/           # Money, Address, Email, DomainEvent
│       ├── application/      # DomainEventPublisher
│       ├── infrastructure/   # Event persistence, security config
│       └── api/              # Global exception handler, DTOs
└── src/main/resources/
    ├── application.yml
    ├── application-prod.yml
    ├── application-dev.yml
    └── schema.sql            # Spring Boot SQL initialization
```

**See:** `.memory-bank/canonicalDomainModel.md` and `.memory-bank/infrastructureLayer.md` for complete structure

