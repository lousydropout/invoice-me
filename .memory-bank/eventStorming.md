# Event Storming & Use Cases

## Overview

Event storming provides two complementary perspectives on the same domain logic:

1. **Visual Narrative** - How the business story unfolds (chronological flow)
2. **Command-Handler Matrix** - How that story maps to actual architecture

They're the same content, just two perspectives: *one for people, one for code.*

**Note:** Authentication does **NOT** appear in event storming. Auth is an infrastructure concern (Spring Security Basic Auth/JWT) that protects API endpoints but is not part of the domain model, ubiquitous language, or business events.

---

## 1. Visual Event-Storming Diagram (Chronological Flow)

A Markdown-friendly linear diagram showing the business narrative over time.

**Legend:**
- 🟨 Actor
- 🔵 Command
- 🟢 Aggregate / Domain Service
- 🟧 Event
- 💗 Policy (reactive rule)

```
🟨 Business User

  🔵 CreateCustomer

    🟢 Customer

      🟧 CustomerCreated

  🔵 CreateInvoice

    🟢 Invoice

      🟧 InvoiceCreated

  🔵 UpdateInvoice

    🟢 Invoice

      🟧 InvoiceUpdated

  🔵 SendInvoice

    🟢 Invoice

      🟧 InvoiceSent

        💗 Policy: NotifyCustomerByEmail

          🟢 InvoiceNotificationService

            🟧 InvoiceEmailDispatched (optional system event)

  🔵 RecordPayment

    🟢 Invoice

      🟧 PaymentRecorded

        💗 if balance == 0 → 🟧 InvoicePaid

🟨 System Clock / Query Layer

  💗 Derived Condition: (dueDate < today && status != PAID)

      → computed field `isOverdue` (no stored event)
```

### What This Shows

- **Time flows left → right**
- Each command causes one or more domain events
- Some events trigger policies (automated reactions)
- "Overdue" doesn't appear as a domain event — it's calculated at read time
- This is the *business narrative view*: what the system *does* over time

---

## 2. Command–Handler Matrix (Architectural View)

Same logic, but in a table that maps commands → handlers → aggregates → events.

This is what you'll use when structuring `/application/commands`.

### Commands (Write Operations)

| Command          | Actor         | Aggregate | Handler Class (App Layer) | Domain Events Emitted                          | Policies Triggered      |
| ---------------- | ------------- | --------- | ------------------------- | ---------------------------------------------- | ----------------------- |
| `CreateCustomer` | Business User | Customer  | `CreateCustomerHandler`   | `CustomerCreated`                              | —                       |
| `UpdateCustomer` | Business User | Customer  | `UpdateCustomerHandler`   | `CustomerUpdated`                              | —                       |
| `DeleteCustomer` | Business User | Customer  | `DeleteCustomerHandler`   | `CustomerDeleted`                              | —                       |
| `CreateInvoice`  | Business User | Invoice   | `CreateInvoiceHandler`    | `InvoiceCreated`                               | —                       |
| `UpdateInvoice`  | Business User | Invoice   | `UpdateInvoiceHandler`    | `InvoiceUpdated`                               | —                       |
| `SendInvoice`    | Business User | Invoice   | `SendInvoiceHandler`      | `InvoiceSent`                                  | `NotifyCustomerByEmail` |
| `RecordPayment`  | Business User | Invoice   | `RecordPaymentHandler`    | `PaymentRecorded`, `InvoicePaid` (conditional) | —                       |

### Derived / System-level (Non-Command) Items

| Trigger                               | Type          | Handler                      | Outcome                  |
| ------------------------------------- | ------------- | ---------------------------- | ------------------------ |
| `(dueDate < today && status != PAID)` | Derived Query | `InvoiceQueryService`        | Field `isOverdue = true` |
| `InvoiceSent`                         | Policy        | `InvoiceNotificationService` | `InvoiceEmailDispatched` |

---

## Key Observations

### Code Organization

- Each **command handler** belongs in `/application/commands/` and calls methods on its aggregate
- Each **event** belongs to `/domain/events/`
- Each **policy** lives in `/application/policies/` (or `/application/eventhandlers/`)
- Aggregates (`Invoice`, `Customer`) belong to `/domain/`
- Queries and derived conditions belong to the read side of CQRS

### Event Flow Patterns

1. **Direct Command → Event**: Most commands directly emit a domain event
2. **Conditional Events**: `RecordPayment` may emit `InvoicePaid` if balance reaches zero
3. **Policy-Driven Events**: `InvoiceSent` triggers `NotifyCustomerByEmail` policy, which may emit `InvoiceEmailDispatched`
4. **Derived State**: `isOverdue` is calculated at query time, not stored as an event

### Domain Events Catalog

**Customer Domain:**
- `CustomerCreated`
- `CustomerUpdated`
- `CustomerDeleted`

**Invoice Domain:**
- `InvoiceCreated`
- `InvoiceUpdated`
- `InvoiceSent`
- `PaymentRecorded` (within Invoice context)
- `InvoicePaid` (conditional, when balance reaches zero)

**System/Infrastructure Events:**
- `InvoiceEmailDispatched` (optional, for tracking email delivery)

### Policies (Reactive Rules)

1. **NotifyCustomerByEmail** (triggered by `InvoiceSent`)
   - Handler: `InvoiceNotificationService`
   - Action: Send invoice via email
   - May emit: `InvoiceEmailDispatched` (for audit/tracking)

2. **MarkInvoiceAsPaid** (triggered by `PaymentRecorded` when balance == 0)
   - Handler: `Invoice` aggregate method
   - Action: Update invoice status to PAID
   - Emits: `InvoicePaid`

### Derived State (Query Side)

**Overdue Calculation:**
- **Condition**: `dueDate < today && status != PAID`
- **Location**: Query/Read Model
- **Implementation**: Computed field `isOverdue` in query results
- **Note**: Not a domain event; calculated at read time

---

## Implementation Mapping

### Package Structure

```
/domain
  ├── invoice/
  │   ├── Invoice.java
  │   └── InvoiceRepository.java
  ├── payment/
  │   └── Payment.java
  ├── customer/
  │   ├── Customer.java
  │   └── CustomerRepository.java
  └── events/
      ├── InvoiceCreated.java
      ├── InvoiceSent.java
      ├── PaymentRecorded.java
      ├── InvoicePaid.java
      ├── CustomerCreated.java
      └── CustomerUpdated.java

/application
  ├── commands/
  │   ├── CreateCustomerHandler.java
  │   ├── UpdateCustomerHandler.java
  │   ├── CreateInvoiceHandler.java
  │   ├── UpdateInvoiceHandler.java
  │   ├── SendInvoiceHandler.java
  │   └── RecordPaymentHandler.java
  ├── policies/
  │   └── NotifyCustomerByEmailPolicy.java
  └── queries/
      └── InvoiceQueryService.java

/domain/services
  └── InvoiceNotificationService.java
```

### Handler Responsibilities

**Command Handlers:**
- Load aggregate from repository
- Call aggregate method (command)
- Save aggregate
- Publish domain events

**Policy Handlers:**
- Listen to domain events
- Execute reactive business logic
- May emit system events for tracking

**Query Handlers:**
- Read from write model (MVP)
- Calculate derived fields (e.g., `isOverdue`)
- Return DTOs/View Models

