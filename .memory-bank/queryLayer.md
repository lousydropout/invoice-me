# Query Layer (CQRS Read Side)

## Overview

The query side is the "Q" in **CQRS** (Command–Query Responsibility Segregation). It handles all read operations, returning data without modifying state.

---

## What the Read Side Is

In CQRS, you separate:

* **Commands (writes)** → change the system state, emit events
* **Queries (reads)** → return data, never modify state

**Why?**

Because the best model for writing (aggregates, invariants, transactions) isn't always the best for reading (joins, filters, sorting).

So we design **read models** — optimized, denormalized projections of your data that are safe and fast to query.

---

## Read Model Scope for InvoiceMe

Since we're focusing on **Customer**, **Invoice**, and **Payment**, the read side only needs to support a few core queries.

| Use Case                             | Query                    | Output                                           |
| ------------------------------------ | ------------------------ | ------------------------------------------------ |
| View one invoice                     | `GetInvoiceById`         | Full invoice details                             |
| List all invoices                    | `ListInvoices`           | Simplified invoice summary list                  |
| List invoices by customer            | `ListInvoicesByCustomer` | Summaries filtered by `customerId`               |
| View all customers                   | `ListCustomers`          | Basic customer info                              |
| Find overdue invoices                | `ListOverdueInvoices`    | Derived query (due date < now && status != PAID) |
| Get outstanding balance per customer | `OutstandingByCustomer`  | Aggregated totals                                |
| Get payments for an invoice          | `ListPaymentsByInvoice`  | Payment entries linked to invoice                |

That's plenty for a first-pass CQRS setup.

---

## Read Model Pattern in DDD Terms

Each read model is a **projection**:

It consumes domain events or queries the database directly.

**For MVP:** We'll use **direct SQL / JPA queries on Postgres tables** — no event projection layer yet.

**Future:** You can switch to async projections (views updated by domain events).

---

## Package Layout (Vertical Slice Architecture - VSA)

**REQUIRED:** Code must be organized by bounded context (vertical slices), not by technical layers.

```
/src/main/java/app
 ├── invoice/                    # Invoice bounded context (vertical slice)
 │    └── application/
 │         └── queries/
 │              ├── GetInvoiceByIdHandler.java
 │              ├── ListInvoicesHandler.java
 │              ├── ListInvoicesByCustomerHandler.java
 │              ├── ListOverdueInvoicesHandler.java
 │              └── dto/
 │                   ├── InvoiceSummaryView.java
 │                   ├── InvoiceDetailView.java
 │                   └── PaymentView.java
 │
 ├── customer/                   # Customer bounded context (vertical slice)
 │    └── application/
 │         └── queries/
 │              ├── GetCustomerByIdHandler.java
 │              ├── ListCustomersHandler.java
 │              ├── OutstandingByCustomerHandler.java
 │              └── dto/
 │                   └── CustomerView.java
 │
 └── shared/                     # Shared across bounded contexts
      └── application/
           └── queries/
                └── (shared query utilities if needed)
```

**Key VSA Principles:**
- Each bounded context has its own `application/queries/` folder
- No global `/application/queries/` folder (HSA disallowed)
- Query handlers and read model DTOs live within their bounded context

---

## DTOs for Read Models

These are **immutable projections** used for responses.

They aren't JPA entities — just read-optimized views.

### InvoiceSummaryView

```java
public record InvoiceSummaryView(
    UUID id,
    String invoiceNumber,
    UUID customerId,
    String customerName,
    String status,
    LocalDate issueDate,
    LocalDate dueDate,
    BigDecimal total,
    BigDecimal balance
) {}
```

### InvoiceDetailView

```java
public record InvoiceDetailView(
    UUID id,
    String invoiceNumber,
    String customerName,
    String customerEmail,
    LocalDate issueDate,
    LocalDate dueDate,
    String status,
    BigDecimal subtotal,
    BigDecimal tax,
    BigDecimal total,
    BigDecimal balance,
    String notes,
    List<LineItemView> lineItems,
    List<PaymentView> payments
) {
    public record LineItemView(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal subtotal) {}
    public record PaymentView(BigDecimal amount, LocalDate paymentDate, String method, String reference) {}
}
```

### CustomerView

```java
public record CustomerView(
    UUID id,
    String name,
    String email,
    String phone,
    String country,
    BigDecimal outstandingBalance
) {}
```

---

## Query Handlers (Application Layer)

You can implement these with:
* **Spring Data JPA** (EntityManager), or
* **JdbcTemplate / @Query** for custom SQL

Both styles are shown below.

---

## Query Handler Implementations

### GetInvoiceByIdHandler (via JPA)

```java
@Service
@Transactional(readOnly = true)
public class GetInvoiceByIdHandler {
  private final EntityManager em;

  public GetInvoiceByIdHandler(EntityManager em) { 
    this.em = em; 
  }

  public InvoiceDetailView handle(UUID invoiceId) {
    var query = em.createQuery("""
      SELECT new app.application.queries.dto.InvoiceDetailView(
        i.id, i.invoiceNumber, c.name, c.email, i.issueDate, i.dueDate, i.status,
        i.subtotal, i.tax, i.total, (i.total - COALESCE(SUM(p.amount), 0)), i.notes,
        CAST(NULL AS list), CAST(NULL AS list)
      )
      FROM InvoiceEntity i
      JOIN CustomerEntity c ON c.id = i.customerId
      LEFT JOIN PaymentEntity p ON p.invoice.id = i.id
      WHERE i.id = :id
      GROUP BY i.id, c.name, c.email
      """, InvoiceDetailView.class);

    query.setParameter("id", invoiceId);
    return query.getSingleResult();
  }
}
```

**Note:** For nested line items and payments, you can fetch separately and inject them into the `InvoiceDetailView` constructor.

### ListInvoicesHandler (via JdbcTemplate)

```java
@Service
@Transactional(readOnly = true)
public class ListInvoicesHandler {
  private final JdbcTemplate jdbc;

  public ListInvoicesHandler(JdbcTemplate jdbc) { 
    this.jdbc = jdbc; 
  }

  public List<InvoiceSummaryView> handle() {
    String sql = """
      SELECT i.id, i.invoice_number, i.customer_id, c.name AS customer_name,
             i.status, i.issue_date, i.due_date, i.total,
             (i.total - COALESCE(SUM(p.amount), 0)) AS balance
      FROM invoices i
      JOIN customers c ON i.customer_id = c.id
      LEFT JOIN payments p ON p.invoice_id = i.id
      GROUP BY i.id, c.name
      ORDER BY i.issue_date DESC
      """;

    return jdbc.query(sql, (rs, n) -> new InvoiceSummaryView(
        UUID.fromString(rs.getString("id")),
        rs.getString("invoice_number"),
        UUID.fromString(rs.getString("customer_id")),
        rs.getString("customer_name"),
        rs.getString("status"),
        rs.getDate("issue_date").toLocalDate(),
        rs.getDate("due_date").toLocalDate(),
        rs.getBigDecimal("total"),
        rs.getBigDecimal("balance")
    ));
  }
}
```

This is pure SQL, runs directly against Postgres, and is blazing fast for read-only queries.

### ListOverdueInvoicesHandler

```java
@Service
@Transactional(readOnly = true)
public class ListOverdueInvoicesHandler {
  private final JdbcTemplate jdbc;

  public ListOverdueInvoicesHandler(JdbcTemplate jdbc) { 
    this.jdbc = jdbc; 
  }

  public List<InvoiceSummaryView> handle() {
    String sql = """
      SELECT i.id, i.invoice_number, i.customer_id, c.name AS customer_name,
             i.status, i.issue_date, i.due_date, i.total,
             (i.total - COALESCE(SUM(p.amount), 0)) AS balance
      FROM invoices i
      JOIN customers c ON i.customer_id = c.id
      LEFT JOIN payments p ON p.invoice_id = i.id
      WHERE i.status != 'PAID' AND i.due_date < CURRENT_DATE
      GROUP BY i.id, c.name
      ORDER BY i.due_date ASC
      """;

    return jdbc.query(sql, (rs, n) -> new InvoiceSummaryView(
        UUID.fromString(rs.getString("id")),
        rs.getString("invoice_number"),
        UUID.fromString(rs.getString("customer_id")),
        rs.getString("customer_name"),
        rs.getString("status"),
        rs.getDate("issue_date").toLocalDate(),
        rs.getDate("due_date").toLocalDate(),
        rs.getBigDecimal("total"),
        rs.getBigDecimal("balance")
    ));
  }
}
```

### OutstandingByCustomerHandler

```java
@Service
@Transactional(readOnly = true)
public class OutstandingByCustomerHandler {
  private final JdbcTemplate jdbc;

  public OutstandingByCustomerHandler(JdbcTemplate jdbc) { 
    this.jdbc = jdbc; 
  }

  public List<CustomerView> handle() {
    String sql = """
      SELECT c.id, c.name, c.email, c.phone, c.country,
             COALESCE(SUM(i.total - COALESCE(p.total_paid, 0)), 0) AS outstanding_balance
      FROM customers c
      LEFT JOIN invoices i ON i.customer_id = c.id
      LEFT JOIN (
        SELECT invoice_id, SUM(amount) AS total_paid
        FROM payments
        GROUP BY invoice_id
      ) p ON p.invoice_id = i.id
      GROUP BY c.id
      ORDER BY c.name
      """;

    return jdbc.query(sql, (rs, n) -> new CustomerView(
        UUID.fromString(rs.getString("id")),
        rs.getString("name"),
        rs.getString("email"),
        rs.getString("phone"),
        rs.getString("country"),
        rs.getBigDecimal("outstanding_balance")
    ));
  }
}
```

### ListInvoicesByCustomerHandler

```java
@Service
@Transactional(readOnly = true)
public class ListInvoicesByCustomerHandler {
  private final JdbcTemplate jdbc;

  public ListInvoicesByCustomerHandler(JdbcTemplate jdbc) { 
    this.jdbc = jdbc; 
  }

  public List<InvoiceSummaryView> handle(UUID customerId) {
    String sql = """
      SELECT i.id, i.invoice_number, i.customer_id, c.name AS customer_name,
             i.status, i.issue_date, i.due_date, i.total,
             (i.total - COALESCE(SUM(p.amount), 0)) AS balance
      FROM invoices i
      JOIN customers c ON i.customer_id = c.id
      LEFT JOIN payments p ON p.invoice_id = i.id
      WHERE i.customer_id = ?
      GROUP BY i.id, c.name
      ORDER BY i.issue_date DESC
      """;

    return jdbc.query(sql, (rs, n) -> new InvoiceSummaryView(
        UUID.fromString(rs.getString("id")),
        rs.getString("invoice_number"),
        UUID.fromString(rs.getString("customer_id")),
        rs.getString("customer_name"),
        rs.getString("status"),
        rs.getDate("issue_date").toLocalDate(),
        rs.getDate("due_date").toLocalDate(),
        rs.getBigDecimal("total"),
        rs.getBigDecimal("balance")
    ), customerId);
  }
}
```

### ListCustomersHandler

```java
@Service
@Transactional(readOnly = true)
public class ListCustomersHandler {
  private final JdbcTemplate jdbc;

  public ListCustomersHandler(JdbcTemplate jdbc) { 
    this.jdbc = jdbc; 
  }

  public List<CustomerView> handle() {
    String sql = """
      SELECT c.id, c.name, c.email, c.phone, c.country,
             COALESCE(SUM(i.total - COALESCE(p.total_paid, 0)), 0) AS outstanding_balance
      FROM customers c
      LEFT JOIN invoices i ON i.customer_id = c.id
      LEFT JOIN (
        SELECT invoice_id, SUM(amount) AS total_paid
        FROM payments
        GROUP BY invoice_id
      ) p ON p.invoice_id = i.id
      GROUP BY c.id
      ORDER BY c.name
      """;

    return jdbc.query(sql, (rs, n) -> new CustomerView(
        UUID.fromString(rs.getString("id")),
        rs.getString("name"),
        rs.getString("email"),
        rs.getString("phone"),
        rs.getString("country"),
        rs.getBigDecimal("outstanding_balance")
    ));
  }
}
```

---

## Why This Works Well with Postgres

Postgres excels at:
* joins
* aggregations (`SUM`, `COALESCE`)
* date filters
* indexing (e.g., `CREATE INDEX ON invoices(due_date)`)

So we let Postgres do the heavy lifting — your read models can stay *thin* and fast.

No need for ORM overhead when projecting views.

### Optional: Postgres Views

You could even create Postgres **views** for some queries later:

```sql
CREATE VIEW overdue_invoices AS
SELECT * FROM invoices
WHERE status != 'PAID' AND due_date < CURRENT_DATE;
```

and query that from `JdbcTemplate`.

---

## Controller Endpoints for Queries

Example additions to your existing REST API:

```java
@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceQueryController {
  private final GetInvoiceByIdHandler getById;
  private final ListInvoicesHandler listAll;
  private final ListOverdueInvoicesHandler listOverdue;
  private final ListInvoicesByCustomerHandler listByCustomer;

  @GetMapping
  public List<InvoiceSummaryView> list() {
    return listAll.handle();
  }

  @GetMapping("/{id}")
  public InvoiceDetailView get(@PathVariable UUID id) {
    return getById.handle(id);
  }

  @GetMapping("/overdue")
  public List<InvoiceSummaryView> overdue() {
    return listOverdue.handle();
  }

  @GetMapping(params = "customerId")
  public List<InvoiceSummaryView> listByCustomer(@RequestParam UUID customerId) {
    return listByCustomer.handle(customerId);
  }
}
```

### Customer Query Controller

```java
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerQueryController {
  private final ListCustomersHandler listAll;
  private final OutstandingByCustomerHandler outstanding;

  @GetMapping
  public List<CustomerView> list() {
    return listAll.handle();
  }

  @GetMapping("/outstanding")
  public List<CustomerView> outstanding() {
    return outstanding.handle();
  }
}
```

---

## Summary Table

| Query                    | Input        | Output                     | Backed By                        |
| ------------------------ | ------------ | -------------------------- | -------------------------------- |
| `GetInvoiceById`         | `invoiceId`  | `InvoiceDetailView`        | `SELECT ... JOIN ...`            |
| `ListInvoices`           | —            | `List<InvoiceSummaryView>` | `SELECT ... ORDER BY issue_date` |
| `ListInvoicesByCustomer` | `customerId` | `List<InvoiceSummaryView>` | `WHERE customer_id = ?`          |
| `ListOverdueInvoices`    | —            | `List<InvoiceSummaryView>` | `WHERE due_date < CURRENT_DATE`  |
| `ListCustomers`          | —            | `List<CustomerView>`       | `SELECT * FROM customers`        |
| `OutstandingByCustomer`  | —            | `List<CustomerView>`       | aggregate join query             |

---

## Key Design Principles

1. **Read-Only Transactions**: All query handlers use `@Transactional(readOnly = true)`
2. **Direct SQL for Performance**: Use `JdbcTemplate` for complex queries, JPA for simple ones
3. **Denormalized Views**: Read models include joined data (customer name in invoice view)
4. **Derived State**: Calculate `balance` and `isOverdue` at query time, not stored
5. **Immutable Projections**: Read model DTOs are records (immutable)
6. **No Domain Dependencies**: Query handlers don't load aggregates, only read data
7. **Postgres-Optimized**: Leverage Postgres strengths (joins, aggregations, indexes)

---

## Optional Future Evolution

Later, when your system grows:

* Replace these SQL queries with **event-driven projections** updated in near-real time
* Store projections in a read-optimized schema or cache (Redis, ElasticSearch, etc.)
* Add pagination, filters, and sorting (Postgres + `Pageable` support from Spring Data)
* Create materialized views for expensive aggregations
* Add full-text search capabilities
* Implement read replicas for scaling

But for now — your **CQRS read side** is simple, fast, and production-ready with Postgres.

---

## Implementation Checklist

- [ ] Create read model DTOs (InvoiceSummaryView, InvoiceDetailView, CustomerView)
- [ ] Implement query handlers using JdbcTemplate or EntityManager
- [ ] Add `@Transactional(readOnly = true)` to all query handlers
- [ ] Create query controller endpoints
- [ ] Add database indexes for common query patterns (customer_id, due_date, status)
- [ ] Test query performance with realistic data volumes
- [ ] Consider creating Postgres views for complex queries
- [ ] Add pagination support if needed
- [ ] Document query endpoints in OpenAPI spec

---

## Database Indexes for Query Performance

Add these indexes to optimize read queries:

```sql
-- For ListInvoicesByCustomer
CREATE INDEX idx_invoices_customer_id ON invoices(customer_id);

-- For ListOverdueInvoices
CREATE INDEX idx_invoices_due_date ON invoices(due_date);
CREATE INDEX idx_invoices_status ON invoices(status);

-- Composite index for common query pattern
CREATE INDEX idx_invoices_customer_status ON invoices(customer_id, status);

-- For payment aggregations
CREATE INDEX idx_payments_invoice_id ON payments(invoice_id);
```

