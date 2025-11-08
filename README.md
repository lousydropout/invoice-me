# InvoiceMe API

A minimal Spring Boot 3.5 API server for invoice management, focused on containerization and deployment patterns.

## Tech Stack

- Java 21
- Spring Boot 3.5
- Gradle (Kotlin DSL)
- PostgreSQL
- Flyway (database migrations)
- Docker & Docker Compose

## Local Development

### Prerequisites

- Docker and Docker Compose
- Java 21 (optional, for local builds)

### Running with Docker Compose

1. Start the services:
```bash
docker compose up --build
```

This will:
- Start a PostgreSQL 16 container on port 5432
- Build and start the API container on port 8080
- Wait for Postgres to be healthy before starting the API

2. Verify the health endpoint:
```bash
curl http://localhost:8080/api/health
```

Expected response:
```json
{"status":"ok"}
```

### Local Build (Optional)

If you want to build and test locally without Docker:

```bash
cd backend
./gradlew clean test bootJar
```

## API Endpoints

### Health Check

```bash
curl http://localhost:8080/api/health
```

### Invoice CRUD Operations

#### Create Invoice

```bash
curl -X POST http://localhost:8080/api/invoices \
  -H "Content-Type: application/json" \
  -d '{"customerName":"Alice","amount":"123.45"}'
```

#### List All Invoices

```bash
curl http://localhost:8080/api/invoices
```

#### Get Invoice by ID

```bash
curl http://localhost:8080/api/invoices/{id}
```

Replace `{id}` with the UUID from the create response.

#### Update Invoice (PATCH)

```bash
curl -X PATCH http://localhost:8080/api/invoices/{id} \
  -H "Content-Type: application/json" \
  -d '{"status":"SENT"}'
```

You can update any combination of fields:
- `customerName` (string)
- `amount` (decimal, must be positive)
- `status` (DRAFT, SENT, PAID, CANCELED)

#### Delete Invoice

```bash
curl -X DELETE http://localhost:8080/api/invoices/{id}
```

## Testing

Run tests locally:

```bash
cd backend
./gradlew test
```

## Project Structure

```
backend/
├── src/
│   ├── main/
│   │   ├── java/com/invoiceme/api/
│   │   │   ├── controller/     # REST controllers
│   │   │   ├── domain/         # Entities and enums
│   │   │   ├── dto/            # Request/Response DTOs
│   │   │   ├── exception/      # Exception handlers
│   │   │   ├── mapper/         # Entity-DTO mappers
│   │   │   ├── repository/     # JPA repositories
│   │   │   └── service/        # Business logic
│   │   └── resources/
│   │       ├── application.yml          # Default config
│   │       ├── application-prod.yml    # Production config
│   │       └── db/migration/           # Flyway migrations
│   └── test/
└── Dockerfile
```

## Database

- **Local**: PostgreSQL 16 via Docker Compose
- **Connection**: `jdbc:postgresql://postgres:5432/invoiceme`
- **Credentials**: `invoiceme` / `invoiceme`
- **Migrations**: Managed by Flyway

## API Documentation

The API is automatically documented using OpenAPI 3.0 specification:

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs
- **Exported Spec**: `backend/openapi.json`

The Swagger UI provides an interactive interface to explore and test all API endpoints. The OpenAPI specification is auto-generated from the codebase and includes all endpoints, request/response schemas, and validation rules.

### Accessing Swagger UI

1. Start the application (see Local Development section)
2. Navigate to http://localhost:8080/swagger-ui.html
3. Swagger UI is publicly accessible (no authentication required)
4. API endpoints still require BasicAuth (admin:admin)

### OpenAPI Specification

The OpenAPI JSON specification is available at `/v3/api-docs` and is also exported to `backend/openapi.json` for version control and documentation purposes.

## Future: AWS Deployment

The application is prepared for AWS ECS Fargate + RDS deployment:

- Production profile uses environment variables:
  - `DB_HOST`
  - `DB_PORT`
  - `DB_NAME`
  - `DB_USER`
  - `DB_PASSWORD`

Set `SPRING_PROFILES_ACTIVE=prod` to use the production configuration.

