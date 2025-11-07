# System Patterns

## Architecture Decisions
- **Minimal API Server**: Lightweight CRUD API, no complex business logic
- **Backend-only**: No frontend components
- **Container-first**: Docker Compose for local, ECS Fargate for production
- **Database migrations**: Flyway for version-controlled schema changes
- **Environment-based configuration**: Use environment variables for deployment flexibility

## Code Conventions
- Java 21 language features (records for DTOs)
- Spring Boot 3.5 conventions
- RESTful API design
- Standard Spring Boot package structure
- Java records for all DTOs (CreateInvoiceRequest, UpdateInvoiceRequest, InvoiceResponse)
- Optional<T> for nullable fields in UpdateInvoiceRequest (PATCH semantics)
- Lombok for entity boilerplate (@Getter, @Setter, @RequiredArgsConstructor, @Slf4j)
- Snake_case column names in database (Postgres convention)

## Design Patterns
- Repository pattern (via Spring Data JPA)
- REST Controller pattern
- Dependency Injection (Spring)
- Mapper pattern (InvoiceMapper for entity-to-DTO conversion)
- Global exception handling (@RestControllerAdvice)
- Service layer with transaction management (@Transactional)

## Best Practices
- Keep it simple - avoid overcomplication
- Focus on containerization and deployment patterns
- Use environment variables for configuration (DB_HOST, DB_USER, etc.)
- Maintain clean separation between local (Docker Compose) and cloud (RDS) database configs
- Health checks for deployment verification
- @PrePersist/@PreUpdate for automatic timestamp management
- JPA validation mode (ddl-auto: validate) - schema managed by Flyway
- spring.jpa.open-in-view=false for better performance
- Multi-stage Docker builds for smaller image size
- Named volumes for Postgres data persistence
- Service health checks in Docker Compose (depends_on with condition: service_healthy)

