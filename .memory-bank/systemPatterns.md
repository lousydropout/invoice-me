# System Patterns

## Architecture Decisions
- **Minimal API Server**: Lightweight CRUD API, no complex business logic
- **Backend-only**: No frontend components
- **Container-first**: Docker Compose for local, ECS Fargate for production
- **Database migrations**: Flyway for version-controlled schema changes
- **Environment-based configuration**: Use environment variables for deployment flexibility

## Code Conventions
- Java 21 language features
- Spring Boot 3.5 conventions
- RESTful API design
- Standard Spring Boot package structure

## Design Patterns
- Repository pattern (via Spring Data JPA)
- REST Controller pattern
- Dependency Injection (Spring)

## Best Practices
- Keep it simple - avoid overcomplication
- Focus on containerization and deployment patterns
- Use environment variables for configuration (DB_HOST, DB_USER, etc.)
- Maintain clean separation between local (Docker Compose) and cloud (RDS) database configs
- Health checks for deployment verification

