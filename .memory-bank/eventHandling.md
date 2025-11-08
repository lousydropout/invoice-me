# Domain Event Handling

## Overview

Domain events are how the domain "speaks" to the outside world. They flow from the domain layer through the application layer to the infrastructure layer, where they trigger side effects like sending emails, updating read models, or syncing to external systems.

---

## The Goal

In DDD:

> The domain emits events to describe what happened,
> the application layer publishes them,
> and the infrastructure layer handles them (or forwards them elsewhere).

We want to design that pipeline cleanly, so that:
* The domain stays pure (no external dependencies)
* The application coordinates event publishing
* The infrastructure executes side effects in a reliable way (transactionally consistent with Postgres)

---

## Event Flow in InvoiceMe

Let's walk through what happens when a command runs:

```
User calls API  →  Handler executes  →  Aggregate emits events
                                         ↓
                                   Application publishes events
                                         ↓
                              Infrastructure handles (or stores) events
```

**Example:**

`RecordPaymentCommand` → `Invoice.recordPayment()` → emits `PaymentRecorded`, maybe `InvoicePaid` →

Application layer saves the invoice → publishes those domain events →

Infrastructure picks them up to:
* Send email (future NotificationService)
* Update reporting projections
* Log or export to an external system (future AccountingAdapter)

---

## Domain Event Definition

Each event is a **small immutable object** describing something that happened in the past, with just enough data for listeners to react.

### DomainEvent Marker Interface

```java
package app.domain.shared;

import java.time.Instant;

public interface DomainEvent {
    Instant occurredAt();
}
```

### Example Event: PaymentRecorded

```java
package app.domain.invoice.events;

import java.time.Instant;
import java.util.UUID;

public record PaymentRecorded(
    UUID invoiceId,
    UUID customerId,
    double amount,
    Instant occurredAt
) implements DomainEvent {
    @Override
    public Instant occurredAt() { return occurredAt; }
}
```

### Example Event: InvoiceSent

```java
package app.domain.invoice.events;

import java.time.Instant;
import java.util.UUID;

public record InvoiceSent(
    UUID invoiceId,
    UUID customerId,
    Instant occurredAt
) implements DomainEvent {
    @Override
    public Instant occurredAt() { return occurredAt; }
}
```

### Example Event: InvoicePaid

```java
package app.domain.invoice.events;

import java.time.Instant;
import java.util.UUID;

public record InvoicePaid(
    UUID invoiceId,
    UUID customerId,
    Instant occurredAt
) implements DomainEvent {
    @Override
    public Instant occurredAt() { return occurredAt; }
}
```

---

## Aggregates Raising Events

Inside your aggregate root (e.g., `Invoice`):

```java
public class Invoice {
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    public void recordPayment(Payment payment) {
        // domain logic...
        domainEvents.add(new PaymentRecorded(id, customerId, payment.amount(), Instant.now()));
        
        if (calculateBalance().isZero()) {
            domainEvents.add(new InvoicePaid(id, customerId, Instant.now()));
        }
    }

    public void send() {
        // domain logic...
        domainEvents.add(new InvoiceSent(id, customerId, Instant.now()));
    }

    public List<DomainEvent> pullDomainEvents() {
        var eventsToPublish = List.copyOf(domainEvents);
        domainEvents.clear();
        return eventsToPublish;
    }
}
```

This keeps your events inside the aggregate until the handler saves and publishes them.

**Key Pattern:**
- Events are collected in a list during aggregate operations
- `pullDomainEvents()` returns a copy and clears the list
- Handler calls `pullDomainEvents()` after saving the aggregate

---

## Application Layer — Publishing Events

We'll define a **DomainEventPublisher** interface in the application layer.

Handlers use it right after saving aggregates.

### DomainEventPublisher Interface

```java
package app.application.bus;

import app.domain.shared.DomainEvent;
import java.util.List;

public interface DomainEventPublisher {
    void publish(List<? extends DomainEvent> events);
}
```

### Handler Usage

```java
@Service
@Transactional
public class RecordPaymentHandler implements CommandHandler<RecordPaymentCommand> {
    private final InvoiceRepository repo;
    private final DomainEventPublisher events;

    public void handle(RecordPaymentCommand cmd) {
        var invoice = repo.findById(cmd.invoiceId());
        invoice.recordPayment(...);
        repo.save(invoice);
        events.publish(invoice.pullDomainEvents());  // Publish after save
    }
}
```

**Important:** Events are published **after** the aggregate is saved, ensuring transactional consistency.

---

## Infrastructure Implementation Options

**PRD Requirement:** Synchronous, in-memory events only. No outbox pattern, no distributed event store.

### **In-Memory Publisher (Required for MVP)**
* Events are handled synchronously within the same process
* Uses Spring's `ApplicationEventPublisher` with `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`
* Ensures event publication happens after transaction commit
* Great for local dev and MVP when side effects are lightweight (logging, projections)
* **This is the only implementation required for MVP**

### **Outbox Pattern (Explicitly Non-Goal)**
* **NOT required for MVP** - explicitly stated in PRD non-goals
* Future enhancement if distributed event store is needed
* Would persist events in a table (`domain_events`) inside the same Postgres transaction
* A background worker would poll and publish asynchronously
* **Not part of current scope**

---

## Implementing the MVP Version (In-Memory) - **REQUIRED**

**PRD Requirement:** Synchronous, in-memory events with `AFTER_COMMIT` transaction context.

### SimpleDomainEventPublisher

```java
package app.shared.infrastructure.events;

import app.shared.application.bus.DomainEventPublisher;
import app.shared.domain.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SimpleDomainEventPublisher implements DomainEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(SimpleDomainEventPublisher.class);
    private final ApplicationEventPublisher springPublisher;

    public SimpleDomainEventPublisher(ApplicationEventPublisher springPublisher) {
        this.springPublisher = springPublisher;
    }

    @Override
    public void publish(List<? extends DomainEvent> events) {
        events.forEach(e -> {
            log.debug("Publishing domain event: {}", e.getClass().getSimpleName());
            springPublisher.publishEvent(e);
        });
    }
}
```

This delegates to Spring's built-in event bus.

### Event Listeners (Infrastructure Layer) - **AFTER_COMMIT Required**

```java
package app.shared.infrastructure.events;

import app.invoice.domain.events.InvoiceSent;
import app.invoice.domain.events.InvoicePaid;
import app.invoice.domain.events.PaymentRecorded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class LoggingEventListener {
    private static final Logger log = LoggerFactory.getLogger(LoggingEventListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvoiceSent(InvoiceSent event) {
        log.info("Invoice {} sent to customer {}", event.invoiceId(), event.customerId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvoicePaid(InvoicePaid event) {
        log.info("Invoice {} fully paid", event.invoiceId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentRecorded(PaymentRecorded event) {
        log.info("Payment of {} recorded for invoice {}", event.amount(), event.invoiceId());
    }
}
```

**Key Points:**
- Events published synchronously within the same process
- `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` ensures events are handled after transaction commit
- No external queues, no outbox pattern, no distributed event store
- Perfect for MVP when side effects are lightweight (logging, projections)

You can add other listeners, like:
* `EmailNotificationListener` (logs simulated emails, no real delivery)
* `ProjectionUpdaterListener` (updates read models)

Each subscribes to a different event type using `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`.

---

## Outbox Pattern (Future Enhancement - NOT Required for MVP)

**PRD Non-Goal:** Outbox pattern is explicitly NOT required for MVP. This section is for future reference only.

When you're ready to move to distributed event store (future enhancement):

### Step 1: Create a `domain_events` table in Postgres

```sql
CREATE TABLE domain_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed BOOLEAN DEFAULT FALSE,
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_domain_events_unprocessed ON domain_events(processed, occurred_at) WHERE processed = FALSE;
```

### Step 2: Outbox Entity

```java
package app.infrastructure.persistence.entities;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "domain_events")
public class OutboxEntity {
    @Id
    private UUID id;
    
    @Column(name = "aggregate_id")
    private UUID aggregateId;
    
    @Column(name = "aggregate_type")
    private String aggregateType;
    
    @Column(name = "event_type")
    private String eventType;
    
    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;
    
    @Column(name = "occurred_at")
    private Instant occurredAt;
    
    @Column(name = "processed")
    private Boolean processed = false;
    
    @Column(name = "processed_at")
    private Instant processedAt;
    
    // getters, setters, markProcessed() method
}
```

### Step 3: In Repository Adapter

When you save an aggregate, serialize and persist its domain events in that same transaction:

```java
@Repository
@Transactional
public class InvoiceRepositoryAdapter implements InvoiceRepository {
    private final InvoiceJpaRepository jpa;
    private final InvoiceMapper mapper;
    private final OutboxRepository outboxRepo;
    private final ObjectMapper objectMapper;

    @Override
    public void save(Invoice invoice) {
        var entity = mapper.toEntity(invoice);
        jpa.save(entity);
        
        // Persist events in same transaction
        invoice.pullDomainEvents().forEach(event -> {
            var outboxEntity = toOutboxEntity(event, invoice.getId());
            outboxRepo.save(outboxEntity);
        });
    }
    
    private OutboxEntity toOutboxEntity(DomainEvent event, UUID aggregateId) {
        var entity = new OutboxEntity();
        entity.setId(UUID.randomUUID());
        entity.setAggregateId(aggregateId);
        entity.setAggregateType("Invoice");
        entity.setEventType(event.getClass().getSimpleName());
        entity.setPayload(objectMapper.writeValueAsString(event));
        entity.setOccurredAt(event.occurredAt());
        return entity;
    }
}
```

### Step 4: Background Processor

A scheduled Spring component (or AWS Lambda) reads unprocessed events and republishes them:

```java
package app.infrastructure.events;

import app.domain.shared.DomainEvent;
import app.infrastructure.persistence.entities.OutboxEntity;
import app.infrastructure.persistence.repositories.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxRepository repo;
    private final ApplicationEventPublisher springPublisher;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 5000)  // Every 5 seconds
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEntity> events = repo.findUnprocessed();
        
        for (var e : events) {
            try {
                DomainEvent event = deserialize(e);
                springPublisher.publishEvent(event);
                e.markProcessed();
                e.setProcessedAt(Instant.now());
                repo.save(e);
                log.debug("Published and marked processed: {}", e.getId());
            } catch (Exception ex) {
                log.error("Failed to publish event {}", e.getId(), ex);
                // Don't mark as processed - will retry on next run
            }
        }
    }
    
    private DomainEvent deserialize(OutboxEntity entity) {
        // Deserialize JSON payload to DomainEvent based on event_type
        // Implementation depends on your serialization strategy
        return objectMapper.readValue(entity.getPayload(), DomainEvent.class);
    }
}
```

That gives you **transactional safety** and **reliable delivery** — ideal when you deploy to Aurora.

---

## Event–Handler Mapping for InvoiceMe

| Event             | Typical Handler(s)                                             | Layer          |
| ----------------- | -------------------------------------------------------------- | -------------- |
| `CustomerCreated` | None yet (maybe analytics later)                               | —              |
| `CustomerUpdated` | None yet                                                       | —              |
| `CustomerDeleted` | None yet                                                       | —              |
| `InvoiceCreated`  | ProjectionUpdater (reporting)                                  | Infrastructure |
| `InvoiceUpdated`  | ProjectionUpdater (reporting)                                  | Infrastructure |
| `InvoiceSent`     | EmailNotificationListener, ProjectionUpdater                  | Infrastructure |
| `PaymentRecorded` | ProjectionUpdater (balance), AccountingAdapter (future)        | Infrastructure |
| `InvoicePaid`     | EmailNotificationListener (payment receipt), AccountingAdapter | Infrastructure |

Each of those listeners implements `@EventListener` or a custom `DomainEventHandler`.

---

## Why This Fits DDD

| DDD Principle                                                 | Implementation                                                 |
| ------------------------------------------------------------- | -------------------------------------------------------------- |
| **Events come from domain logic, not infrastructure**         | Aggregates raise them directly                                 |
| **Events express business facts, not technical side effects** | `InvoicePaid` instead of `InvoiceStatusChanged`                |
| **Event publication is part of application workflow**         | `handler → save → publish()`                                   |
| **Infrastructure reacts, not domain**                         | Email, projections, integrations are listeners, not aggregates |

---

## Package Structure (Vertical Slice Architecture - VSA)

**REQUIRED:** Code must be organized by bounded context (vertical slices), not by technical layers.

```
/src/main/java/app
 ├── invoice/                    # Invoice bounded context (vertical slice)
 │    ├── domain/
 │    │    └── events/
 │    │         ├── InvoiceCreated.java
 │    │         ├── InvoiceSent.java
 │    │         ├── PaymentRecorded.java
 │    │         └── InvoicePaid.java
 │    ├── application/
 │    │    └── bus/
 │    │         └── DomainEventPublisher.java (interface)
 │    └── infrastructure/
 │         └── events/
 │              └── InvoiceEventListener.java (if context-specific)
 │
 ├── customer/                   # Customer bounded context (vertical slice)
 │    ├── domain/
 │    │    └── events/
 │    │         ├── CustomerCreated.java
 │    │         ├── CustomerUpdated.java
 │    │         └── CustomerDeleted.java
 │    └── application/
 │         └── bus/
 │              └── DomainEventPublisher.java (interface - may reference shared)
 │
 └── shared/                     # Shared across bounded contexts
      ├── domain/
      │    └── DomainEvent.java (interface)
      ├── application/
      │    └── bus/
      │         └── DomainEventPublisher.java (interface)
      └── infrastructure/
           └── events/
                ├── SimpleDomainEventPublisher.java
                ├── OutboxPublisher.java (future)
                ├── LoggingEventListener.java
                ├── EmailNotificationListener.java (future)
                └── ProjectionUpdaterListener.java (future)
```

**Key VSA Principles:**
- Domain events live in each bounded context's `domain/events/` folder
- Event publishers may be context-specific or shared
- Event listeners in shared infrastructure handle cross-cutting concerns
- Outbox pattern (if used) lives in shared infrastructure

---

## Summary

| Layer                          | Responsibility                                   | Example Classes                                                        |
| ------------------------------ | ------------------------------------------------ | ---------------------------------------------------------------------- |
| **Domain**                     | Define events and emit them from aggregates      | `InvoiceSent`, `PaymentRecorded`, `InvoicePaid`                        |
| **Application**                | Publish domain events after saving aggregates    | `DomainEventPublisher`, `RecordPaymentHandler`                         |
| **Infrastructure**             | Handle events synchronously (in-memory)           | `LoggingEventListener`, `EmailNotificationListener` (simulated)       |
| **Database (Postgres/Aurora)** | Not used for event storage (MVP)                | Events handled in-memory only                                          |

**MVP Setup (Required):**
- Use Spring's `ApplicationEventPublisher` for synchronous, in-memory events
- Use `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` for event listeners
- No outbox pattern, no distributed event store
- Events published within same transaction context (AFTER_COMMIT)

**Future Enhancement (Not Required):**
- Outbox pattern for distributed event store (if needed later)

---

## Key Design Principles

1. **Domain Events are Immutable**: Events are records (immutable) describing past occurrences
2. **Events Come from Domain**: Aggregates raise events, not infrastructure
3. **Pull Pattern**: Aggregates expose `pullDomainEvents()` to extract events after save
4. **Publish After Save**: Events are published after aggregate is persisted
5. **Infrastructure Handles Side Effects**: Email, projections, integrations are listeners
6. **Transactional Safety**: Outbox pattern ensures events are persisted in same transaction
7. **Reliable Delivery**: Outbox pattern enables exactly-once delivery guarantees

---

## Implementation Checklist

### MVP (In-Memory)
- [ ] Create `DomainEvent` marker interface
- [ ] Create domain event records (InvoiceSent, PaymentRecorded, InvoicePaid, etc.)
- [ ] Add `pullDomainEvents()` method to aggregates
- [ ] Implement `SimpleDomainEventPublisher` using Spring's `ApplicationEventPublisher`
- [ ] Create event listeners (LoggingEventListener)
- [ ] Update handlers to publish events after saving aggregates
- [ ] Test event flow end-to-end

### Production (Outbox Pattern)
- [ ] Create `domain_events` table via Flyway migration
- [ ] Create `OutboxEntity` JPA entity
- [ ] Create `OutboxRepository`
- [ ] Update repository adapters to persist events in outbox
- [ ] Implement `OutboxPublisher` scheduled task
- [ ] Add error handling and retry logic
- [ ] Test transactional consistency
- [ ] Monitor outbox processing

---

## Future Enhancements

* **Event Sourcing**: Store all events as source of truth (advanced)
* **Event Replay**: Rebuild aggregates from events for debugging/audit
* **Event Versioning**: Handle schema evolution of events
* **Distributed Events**: Publish to SNS/SQS/Kafka for microservices
* **Event Store**: Dedicated event database (EventStore, PostgreSQL with JSONB)

