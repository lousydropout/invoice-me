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
      ddl-auto: validate     # Use 'validate' with Flyway migrations
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
    open-in-view: false
  flyway:
    enabled: true
```

With that, Spring Boot automatically discovers your `JpaRepository` and connects to your `postgres` service (same host name via Docker network).

### Production (AWS Aurora Serverless)

When deploying:

* Use an RDS/Aurora PostgreSQL cluster
* Replace environment variables:

  ```
  SPRING_DATASOURCE_URL=jdbc:postgresql://<aurora-endpoint>:5432/invoiceme
  SPRING_DATASOURCE_USERNAME=<aws_secret_username>
  SPRING_DATASOURCE_PASSWORD=<aws_secret_password>
  ```

* No code changes — Spring abstracts this fully

**Production Configuration:**

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  datasource:
    hikari:
      maximum-pool-size: 5
      connection-timeout: 30000
```

Aurora Serverless v2 supports Postgres 14–17 compatible dialects — Hibernate 6 handles this fine.

---

## Database Schema (Flyway Migrations)

Use Flyway for schema versioning. Example migration:

**`V1__init.sql`**

```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE customers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    phone TEXT,
    street TEXT,
    city TEXT,
    postal_code TEXT,
    country TEXT,
    payment_terms TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE invoices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id UUID NOT NULL REFERENCES customers(id),
    invoice_number TEXT NOT NULL UNIQUE,
    issue_date DATE NOT NULL,
    due_date DATE NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('DRAFT', 'SENT', 'PAID')),
    total NUMERIC(18,2) NOT NULL CHECK (total > 0),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE line_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    description TEXT NOT NULL,
    quantity NUMERIC(18,2) NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(18,2) NOT NULL CHECK (unit_price > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    amount NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    payment_date DATE NOT NULL,
    method TEXT NOT NULL,
    reference TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_invoices_customer_id ON invoices(customer_id);
CREATE INDEX idx_invoices_status ON invoices(status);
CREATE INDEX idx_line_items_invoice_id ON line_items(invoice_id);
CREATE INDEX idx_payments_invoice_id ON payments(invoice_id);
```

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
| **Database**       | PostgreSQL (local via Docker, AWS via Aurora)                           | PostgreSQL 17-compatible |

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
- [ ] Create adapter implementations (CustomerRepositoryAdapter, InvoiceRepositoryAdapter)
- [ ] Configure PostgreSQL connection (local and production)
- [ ] Create Flyway migrations for schema
- [ ] Add optimistic locking (`@Version`) to entities
- [ ] Configure H2 for integration tests
- [ ] Test adapter implementations with real database

