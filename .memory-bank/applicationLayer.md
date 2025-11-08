# Application Service Layer (Commands)

## Scope

**Commands only (write side).** Queries live in a read service; we'll ignore them here.

---

## Folder Layout (Vertical Slice Architecture - VSA)

**REQUIRED:** Code must be organized by bounded context (vertical slices), not by technical layers.

```
/src/main/java/app
  /invoice                    # Invoice bounded context (vertical slice)
    /application
      /commands
        CreateInvoiceCommand.java
        UpdateInvoiceCommand.java
        SendInvoiceCommand.java
        RecordPaymentCommand.java
        CreateInvoiceHandler.java
        UpdateInvoiceHandler.java
        SendInvoiceHandler.java
        RecordPaymentHandler.java
      /queries
        GetInvoiceByIdHandler.java
        ListInvoicesHandler.java
        ListOverdueInvoicesHandler.java
      /bus
        DomainEventPublisher.java (interface)
  
  /customer                   # Customer bounded context (vertical slice)
    /application
      /commands
        CreateCustomerCommand.java
        UpdateCustomerCommand.java
        DeleteCustomerCommand.java
        CreateCustomerHandler.java
        UpdateCustomerHandler.java
        DeleteCustomerHandler.java
      /queries
        GetCustomerByIdHandler.java
        ListCustomersHandler.java
  
  /shared                     # Shared across bounded contexts
    /application
      /errors
        ApplicationError.java
        ErrorCodes.java
      /ports
        EmailPort.java (optional - future: notifications)
        ClockPort.java (for deterministic time in tests)
      /bus
        DomainEventPublisher.java (interface - may be shared)
```

**Note:** Each bounded context contains its own `application` layer. Shared concerns (ApplicationError, ports) live in `/shared/application/`.

---

## Command Contracts (Inputs the API will map to)

### Customer Commands

```java
public record CreateCustomerCommand(
  String name,
  String email,
  String street, String city, String postalCode, String country,
  String phone,
  String defaultPaymentTerms // e.g. "NET_30"
) {}

public record UpdateCustomerCommand(
  UUID customerId,
  String name,
  String email,
  String street, String city, String postalCode, String country,
  String phone,
  String defaultPaymentTerms
) {}

public record DeleteCustomerCommand(UUID customerId) {}
```

### Invoice Commands

```java
public record CreateInvoiceCommand(
  UUID customerId,
  List<LineItemDto> lineItems,
  LocalDate issueDate,
  LocalDate dueDate,
  BigDecimal taxRatePercent,          // MVP: simple % tax
  String notes
) {
  public record LineItemDto(String description, BigDecimal quantity, BigDecimal unitPrice) {}
}

public record UpdateInvoiceCommand(
  UUID invoiceId,
  List<CreateInvoiceCommand.LineItemDto> lineItems,
  LocalDate dueDate,
  BigDecimal taxRatePercent,
  String notes
) {}

public record SendInvoiceCommand(UUID invoiceId) {}

public record RecordPaymentCommand(
  UUID invoiceId,
  BigDecimal amount,
  LocalDate paymentDate,
  String method,            // CASH, BANK_TRANSFER, CARD
  String reference          // optional
) {}
```

---

## Handler Interfaces & Transaction Boundary

Each handler is a stateless service with a single `handle()` method. Handlers:

* Load aggregate via a repository (domain interface)
* Invoke a domain method
* Persist aggregate
* Publish domain events

**Transactional consistency per command = one aggregate.**

```java
public interface CommandHandler<C> {
  void handle(C command);
}
```

Annotate handlers with `@Transactional` at the application layer (Spring). That ensures atomic write + event outbox append if you're doing outbox; for MVP, a simple in-memory publisher is fine.

---

## Repositories (Domain Interfaces Used by Handlers)

```java
// domain/customer
public interface CustomerRepository {
  Customer findById(CustomerId id);
  void save(Customer c);
  void delete(CustomerId id);
  boolean existsByEmail(Email email);
}

// domain/invoice
public interface InvoiceRepository {
  Invoice findById(InvoiceId id);
  InvoiceId nextIdentity();
  void save(Invoice invoice);
}

// domain/customer
public interface CustomerReadRepository { // optional for validations
  boolean exists(CustomerId id);
}
```

Infrastructure provides the JPA implementations.

---

## Event Publishing (Application Port)

```java
public interface DomainEventPublisher {
  void publish(List<? extends DomainEvent> events);
}
```

Aggregates expose `pullDomainEvents()` (then clear).

---

## Sample Handler Implementations

### CreateInvoiceHandler

```java
@Service
@Transactional
public class CreateInvoiceHandler implements CommandHandler<CreateInvoiceCommand> {
  private final InvoiceRepository invoices;
  private final CustomerReadRepository customers;
  private final DomainEventPublisher events;
  private final ClockPort clock;

  public void handle(CreateInvoiceCommand cmd) {
    if (!customers.exists(new CustomerId(cmd.customerId())))
      throw ApplicationError.notFound("customer");

    var id = invoices.nextIdentity();
    var lineItems = cmd.lineItems().stream()
       .map(li -> new LineItem(li.description(), li.quantity(), li.unitPrice()))
       .toList();

    var invoice = Invoice.create(
       id,
       new CustomerId(cmd.customerId()),
       lineItems,
       cmd.issueDate() != null ? cmd.issueDate() : clock.today(),
       cmd.dueDate(),
       new TaxRate(cmd.taxRatePercent()),
       cmd.notes()
    );

    invoices.save(invoice);
    events.publish(invoice.pullDomainEvents());
  }
}
```

### RecordPaymentHandler

```java
@Service
@Transactional
public class RecordPaymentHandler implements CommandHandler<RecordPaymentCommand> {
  private final InvoiceRepository invoices;
  private final DomainEventPublisher events;

  public void handle(RecordPaymentCommand cmd) {
    var invoice = invoices.findById(new InvoiceId(cmd.invoiceId()));
    var payment = Payment.of(
        new Money(cmd.amount()),
        cmd.paymentDate(),
        PaymentMethod.from(cmd.method()),
        cmd.reference()
    );

    invoice.recordPayment(payment);      // enforces amount ≤ balance, may mark Paid
    invoices.save(invoice);
    events.publish(invoice.pullDomainEvents());  // PaymentRecorded (+ InvoicePaid?)
  }
}
```

### SendInvoiceHandler

```java
@Service
@Transactional
public class SendInvoiceHandler implements CommandHandler<SendInvoiceCommand> {
  private final InvoiceRepository invoices;
  private final DomainEventPublisher events;

  public void handle(SendInvoiceCommand cmd) {
    var invoice = invoices.findById(new InvoiceId(cmd.invoiceId()));
    invoice.send();               // Draft → Sent; guards invalid transitions
    invoices.save(invoice);
    events.publish(invoice.pullDomainEvents());  // InvoiceSent
  }
}
```

### Create/Update/Delete Customer Handlers

* Validate inputs (email format), ensure uniqueness if required
* Map DTO → VOs (`Email`, `Address`, `PaymentTerms`)
* Persist + publish events

---

## Input Validation (Where It Lives)

* **Syntactic** (non-null, formats): in controller/DTO or a validator before handler
* **Business invariants** (state transitions, overpayment): inside aggregates

Fail fast with clear error mapping (`ApplicationError` → HTTP 4xx/409).

---

## Idempotency & Concurrency

* Use **optimistic locking** on aggregates (JPA `@Version`)
* For public APIs that might retry (not in MVP), accept an `Idempotency-Key` header and store command results keyed by it (infra concern)
* Payment recording is guarded by invariant; a second identical command will 409 on version mismatch or violate invariant (choose behavior)

---

## Error Mapping (Minimal)

```java
public class ApplicationError extends RuntimeException {
  private final String code;
  
  // static factories:
  public static ApplicationError notFound(String entity) { ... }      // 404
  public static ApplicationError conflict(String reason) { ... }      // 409
  public static ApplicationError validation(String msg) { ... }       // 422
}
```

A Spring `@ControllerAdvice` maps these to HTTP status codes.

---

## Controller → Handler Wiring (Sketch)

```java
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {
  private final CreateInvoiceHandler create;
  private final UpdateInvoiceHandler update;
  private final SendInvoiceHandler send;
  private final RecordPaymentHandler record;

  @PostMapping
  public void create(@RequestBody CreateInvoiceDto dto) {
    create.handle(map(dto));
  }

  @PostMapping("/{id}/send")
  public void send(@PathVariable UUID id) {
    send.handle(new SendInvoiceCommand(id));
  }

  @PostMapping("/{id}/payments")
  public void record(@PathVariable UUID id, @RequestBody RecordPaymentDto dto) {
    record.handle(new RecordPaymentCommand(id, dto.amount(), dto.date(), dto.method(), dto.reference()));
  }
}
```

Keep mapping logic thin (or use MapStruct). DTOs shouldn't leak into domain.

---

## Testing Strategy (Fast and Focused)

### Unit Tests (Domain)
Pure tests on `Invoice` aggregate:
* Cannot edit after Sent
* `recordPayment` enforces `amount ≤ balance`
* Emits `InvoicePaid` when balance zero

### Application Tests (Slice)
Handler tests with fake repos + fake publisher:
* `RecordPaymentHandler` saves and publishes `PaymentRecorded` (+ `InvoicePaid`)
* `SendInvoiceHandler` fails if already Sent/Paid

### Integration Tests
Via controllers against in-memory DB (H2) to prove wiring.

Use a **`FakeEventPublisher`** in tests to assert emitted events.

---

## Ports (Optional)

* `ClockPort` (already shown) for deterministic dates
* `EmailPort` if you decide to notify on `InvoiceSent` later (still out of MVP scope)

---

## Summary (Clipboard-Ready)

* Application layer = thin orchestrators (`CommandHandler#handle`)
* Each handler = transactional boundary per aggregate
* Repositories are domain interfaces; infra provides implementations
* Domain raises events; application publishes them
* Validation split: syntax at edge, invariants in domain
* Keep it boring, predictable, and testable

---

## Key Design Principles

1. **One Transaction Per Command**: Each handler is transactional, operates on one aggregate
2. **Domain Interfaces**: Repositories are defined in domain, implemented in infrastructure
3. **Event Pull Pattern**: Aggregates expose `pullDomainEvents()`, handlers publish them
4. **Validation Split**: Syntactic validation at API edge, business invariants in domain
5. **Thin Handlers**: Handlers orchestrate, don't contain business logic
6. **Ports for External Concerns**: Clock, Email, etc. are ports (interfaces) implemented in infrastructure
7. **Error Handling**: Application errors map to HTTP status codes via `@ControllerAdvice`

---

## Implementation Checklist

- [ ] Create command records for all write operations
- [ ] Implement `CommandHandler<T>` interface
- [ ] Create handler implementations for each command
- [ ] Define repository interfaces in domain layer
- [ ] Implement `DomainEventPublisher` interface
- [ ] Create `ApplicationError` with static factories
- [ ] Implement `ClockPort` for deterministic time
- [ ] Wire controllers to handlers
- [ ] Add `@Transactional` to all handlers
- [ ] Create unit tests for domain logic
- [ ] Create application tests with fake repos/publishers
- [ ] Create integration tests with H2 database

