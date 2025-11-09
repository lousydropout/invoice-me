# Infrastructure Layer (Persistence)

## Overview

The infrastructure layer bridges your **domain** (pure Java logic) and the **database** (PostgreSQL). Repositories persist aggregate roots, not child entities directly.

---

## Repository Role Recap

A **Repository** in DDD is a persistence abstraction for an *aggregate root*.

You never load or save child entities directly — always through the aggregate.

| Aggregate Root | Repository Interface    | Backed Table(s)                      |
| -------------- | ----------------------- | ------------------------------------ |
| `Customer`     | `CustomerRepository`    | `customers`                          |
| `Invoice`      | `InvoiceRepository`     | `invoices`, `line_items`, `payments` |
| (Payment)      | none (owned by Invoice) | part of `payments` table             |

---

## Package Layout (Vertical Slice Architecture - VSA)

**REQUIRED:** Code must be organized by bounded context (vertical slices), not by technical layers.

```
/src/main/java/app
 ├── invoice/                    # Invoice bounded context (vertical slice)
 │    ├── domain/
 │    │    ├── InvoiceRepository.java (domain interface)
 │    │    └── InvoiceId.java
 │    └── infrastructure/
 │         └── persistence/
 │              ├── InvoiceJpaRepository.java
 │              ├── InvoiceRepositoryAdapter.java
 │              ├── entities/
 │              │     ├── InvoiceEntity.java
 │              │     ├── LineItemEntity.java
 │              │     └── PaymentEntity.java
 │              └── mappers/
 │                   └── InvoiceMapper.java
 │
 ├── customer/                   # Customer bounded context (vertical slice)
 │    ├── domain/
 │    │    └── CustomerRepository.java (domain interface)
 │    └── infrastructure/
 │         └── persistence/
 │              ├── CustomerJpaRepository.java
 │              ├── CustomerRepositoryAdapter.java
 │              ├── entities/
 │              │     └── CustomerEntity.java
 │              └── mappers/
 │                   └── CustomerMapper.java
 │
 ├── payment/                    # Payment bounded context (vertical slice)
 │    ├── domain/
 │    │    └── Payment.java (entity, owned by Invoice)
 │    └── infrastructure/
 │         └── (Note: Payment persistence handled by Invoice slice)
 │
 └── shared/                     # Shared across bounded contexts
      ├── domain/
      │    └── Money.java (value object)
      └── infrastructure/
           └── config/
                └── JpaConfig.java
```

**Key VSA Principles:**
- Each bounded context has its own `infrastructure/persistence/` folder
- No global `/infrastructure/persistence/` folder (HSA disallowed)
- Repository interfaces defined in each context's `domain/` folder
- Repository implementations in each context's `infrastructure/` folder
- Shared primitives (Money) live in `/shared/`

---

## Domain-Side Repository Interfaces (Pure Abstraction)

### CustomerRepository

```java
package app.domain.customer;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository {
    Optional<Customer> findById(UUID id);
    void save(Customer customer);
    void delete(UUID id);
    boolean existsByEmail(String email);
}
```

### InvoiceRepository

```java
package app.domain.invoice;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository {
    Optional<Invoice> findById(UUID id);
    void save(Invoice invoice);
    void delete(UUID id);
    UUID nextIdentity();
}
```

**Note:** These belong in your **domain** because they describe how the domain expects to persist data — without depending on *how*.

---

## Infrastructure Implementations (Using JPA)

Spring Data JPA automatically implements interfaces that extend `JpaRepository`.

To preserve DDD purity, we wrap the Spring interfaces in adapters that implement your domain `CustomerRepository` and `InvoiceRepository`.

---

## JPA Entities

### CustomerEntity

```java
@Entity
@Table(name = "customers")
public class CustomerEntity {
    @Id
    private UUID id;
    
    private String name;
    private String email;
    private String phone;
    private String street;
    private String city;
    private String postalCode;
    private String country;
    private String paymentTerms;
}
```

### InvoiceEntity

```java
@Entity
@Table(name = "invoices")
public class InvoiceEntity {
    @Id
    private UUID id;
    
    private UUID customerId;
    private String invoiceNumber;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private String status; // DRAFT, SENT, PAID
    private BigDecimal total;
    private String notes;
    
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LineItemEntity> lineItems = new ArrayList<>();
    
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentEntity> payments = new ArrayList<>();
}
```

### LineItemEntity

```java
@Entity
@Table(name = "line_items")
public class LineItemEntity {
    @Id
    @GeneratedValue
    private UUID id;
    
    private String description;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private InvoiceEntity invoice;
}
```

### PaymentEntity

```java
@Entity
@Table(name = "payments")
public class PaymentEntity {
    @Id
    @GeneratedValue
    private UUID id;
    
    private BigDecimal amount;
    private LocalDate paymentDate;
    private String method;
    private String reference;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private InvoiceEntity invoice;
}
```

---

## Spring Data JPA Interfaces

```java
public interface CustomerJpaRepository extends JpaRepository<CustomerEntity, UUID> {
    boolean existsByEmail(String email);
}

public interface InvoiceJpaRepository extends JpaRepository<InvoiceEntity, UUID> {
    // Custom query examples
    List<InvoiceEntity> findByCustomerId(UUID customerId);
}
```

---

## Adapter Implementations

Adapters translate between your **domain model** and **JPA entities** via simple mappers.

### CustomerRepositoryAdapter

```java
@Repository
public class CustomerRepositoryAdapter implements CustomerRepository {
    private final CustomerJpaRepository jpa;
    private final CustomerMapper mapper;

    public CustomerRepositoryAdapter(CustomerJpaRepository jpa, CustomerMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public Optional<Customer> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public void save(Customer customer) {
        jpa.save(mapper.toEntity(customer));
    }

    @Override
    public void delete(UUID id) {
        jpa.deleteById(id);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpa.existsByEmail(email);
    }
}
```

### InvoiceRepositoryAdapter

```java
@Repository
public class InvoiceRepositoryAdapter implements InvoiceRepository {
    private final InvoiceJpaRepository jpa;
    private final InvoiceMapper mapper;

    public InvoiceRepositoryAdapter(InvoiceJpaRepository jpa, InvoiceMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public Optional<Invoice> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public void save(Invoice invoice) {
        jpa.save(mapper.toEntity(invoice));
    }

    @Override
    public void delete(UUID id) {
        jpa.deleteById(id);
    }

    @Override
    public UUID nextIdentity() {
        return UUID.randomUUID();
    }
}
```

---

## Mappers

Keep these simple and symmetrical:

```java
@Component
public class CustomerMapper {
    public CustomerEntity toEntity(Customer domain) { ... }
    public Customer toDomain(CustomerEntity entity) { ... }
}

@Component
public class InvoiceMapper {
    public InvoiceEntity toEntity(Invoice domain) { ... }
    public Invoice toDomain(InvoiceEntity entity) { ... }
}
```

**Mapping Responsibilities:**
- Convert domain Value Objects to entity fields (e.g., `Email` → `String`)
- Convert entity fields to domain Value Objects
- Handle nested entities (LineItems, Payments) within Invoice aggregate
- Preserve aggregate boundaries (never expose child entities directly)

---

## PostgreSQL Configuration

### Local Development (Docker Compose)

**`src/main/resources/application.yml`**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/invoiceme
    username: invoiceme
    password: invoiceme
  jpa:
    hibernate:
      ddl-auto: validate     # Use 'validate' with schema.sql
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
    open-in-view: false
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
```

With that, Spring Boot automatically discovers your `JpaRepository` and connects to your `postgres` service (same host name via Docker network). The schema is initialized from `schema.sql` on startup.

### Production (AWS Aurora Serverless v2)

**Infrastructure**: Managed via AWS CDK (TypeScript) in `infra/cdk/`

**Deployment Configuration:**

* Aurora Serverless v2 PostgreSQL 17.4 cluster
* Environment variables configured via CDK:
  - `DB_HOST` - Aurora cluster endpoint (auto-configured)
  - `DB_PORT` - `5432`
  - `DB_NAME` - `invoiceme`
  - `DB_USER` - From AWS Secrets Manager
  - `DB_PASSWORD` - From AWS Secrets Manager
  - `SPRING_PROFILES_ACTIVE` - `prod`

**Production Configuration (`application-prod.yml`):**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT:5432}/${DB_NAME}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
```

**AWS Infrastructure Components:**
- **Aurora Serverless v2**: Auto-scaling PostgreSQL (0.5-1 ACU)
- **ECS Fargate**: Container hosting (512 MB, 256 CPU)
- **Application Load Balancer**: HTTPS endpoint with ACM certificate
- **Route53**: DNS with A Record ALIAS to ALB (`invoiceme.vincentchan.cloud`)
- **CloudWatch Logs**: Application logging
- **Secrets Manager**: Database credentials storage

**CDK Configuration:**
- Environment variables in `infra/cdk/.env` file
- Required: `AWS_ACCOUNT_ID`, `AWS_REGION`, `ACM_CERTIFICATE_ARN`
- Optional: `DOMAIN_NAME`, `ECR_REPOSITORY_NAME`, `ECR_IMAGE_TAG`, BasicAuth credentials

Aurora Serverless v2 supports PostgreSQL 17.4 — Hibernate 6 handles this fine.

---

## Database Schema (Spring Boot SQL Initialization)

**Current Implementation**: Spring Boot SQL initialization using `schema.sql`
- Single consolidated schema file: `backend/src/main/resources/schema.sql`
- Automatically executed on startup when `spring.sql.init.mode=always`
- Replaces Flyway migrations for simplified schema management

**Schema File**: `backend/src/main/resources/schema.sql`

The schema file includes:
- `customers` table (Customer aggregate)
- `invoices` table (Invoice aggregate)
- `line_items` table (LineItem value objects)
- `payments` table (Payment entities)
- `domain_events` table (event persistence for debugging/audit)
- All indexes and foreign key constraints
- UUID extension enabled

See `backend/src/main/resources/schema.sql` for the complete schema definition.

---

## Optimistic Locking

Add `@Version` to entities for optimistic locking:

```java
@Entity
@Table(name = "invoices")
public class InvoiceEntity {
    @Id
    private UUID id;
    
    @Version
    private Long version;  // Optimistic locking
    
    // ... rest of fields
}
```

This prevents concurrent modification conflicts.

---

## Testing Strategy

### Unit Tests (Domain)
- Test domain logic without database
- Use in-memory implementations or mocks

### Integration Tests
- Use H2 in-memory database for fast tests
- Or use Testcontainers with PostgreSQL for realistic tests
- Test adapter implementations with real database

### Test Configuration

**`src/test/resources/application-test.yml`**

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect
```

---

## Summary

| Layer              | Responsibility                                                          | Framework                |
| ------------------ | ----------------------------------------------------------------------- | ------------------------ |
| **Domain**         | Define repository contracts (`InvoiceRepository`, `CustomerRepository`) | Pure Java                |
| **Infrastructure** | Implement via adapters using Spring Data JPA                            | Spring Boot, Hibernate   |
| **Database**       | PostgreSQL 17.4 (local via Docker, AWS via Aurora)                     | PostgreSQL 17.4-compatible |

### Key Principles

1. **Repository interfaces in domain** - Define contracts without implementation details
2. **Adapters bridge domain and infrastructure** - Isolate database details from domain
3. **Mappers convert entities ↔ domain** - Handle Value Object ↔ primitive conversions
4. **Aggregate boundaries preserved** - Never load/save child entities directly
5. **Database-agnostic domain** - Switching from local Postgres → Aurora = no code change
6. **Testable domain logic** - Unit-test domain without touching database

---

## Implementation Checklist

- [ ] Create repository interfaces in domain layer
- [ ] Create JPA entities in infrastructure layer
- [ ] Create Spring Data JPA interfaces
- [ ] Create mapper components (CustomerMapper, InvoiceMapper)
- [x] Create adapter implementations (CustomerRepositoryAdapter, InvoiceRepositoryAdapter)
- [x] Configure PostgreSQL connection (local and production)
- [x] Create Spring Boot SQL initialization schema (`schema.sql`)
- [x] Add optimistic locking (`@Version`) to entities
- [ ] Configure H2 for integration tests
- [ ] Test adapter implementations with real database

