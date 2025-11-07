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
- Spring Boot Starter Data JPA (database access)
- PostgreSQL Driver (database connectivity)
- Flyway (database migrations)
- Spring Boot Starter Actuator (health checks)

## Development Environment
- Java 21 JDK
- Gradle
- Docker and Docker Compose
- Local Postgres via Docker Compose

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
- `GET /api/health` - Health check endpoint
  - Returns: `{status:"ok"}`
- Invoice CRUD endpoints (to be defined)
  - Basic CRUD operations for invoice entities

## Database Schema
- Database name: `invoiceme`
- Invoice table structure (to be defined via Flyway migrations)
- Connection string format:
  - Local: `jdbc:postgresql://postgres:5432/invoiceme`
  - AWS: `jdbc:postgresql://${DB_HOST}:5432/${DB_NAME}`

