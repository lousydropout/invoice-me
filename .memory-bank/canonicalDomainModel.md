# Canonical Domain Model (3 Subdomains)

This is the **canonical minimal DDD model** for InvoiceMe — consistent with the PRD, event storming, and CQRS setup.

---

## 1. Customer Subdomain (Supporting Domain)

### Aggregate Root: `Customer`

Represents the entity being billed — the client or organization that receives invoices.

**Attributes:**
- `id: CustomerId`
- `name: String`
- `email: Email` (VO)
- `address: Address` (VO)
- `phone: String?` (optional)
- `defaultPaymentTerms: PaymentTerms` (VO)

**Invariants:**
- Email must be valid
- Name cannot be empty

**Behaviors:**
- `updateContactInfo(Address address, Email email, String phone)`
- `setDefaultPaymentTerms(PaymentTerms terms)`

**Value Objects:**
- `Email` — validates format
- `Address` — street, city, postal code, country
- `PaymentTerms` — e.g. Net 15, Net 30, or Due on Receipt

**Events:**
- `CustomerCreated`
- `CustomerUpdated`
- `CustomerDeleted`

---

## 2. Invoice Subdomain (Core Domain)

### Aggregate Root: `Invoice`

Represents a bill sent from the business to a customer.

**Attributes:**
- `id: InvoiceId`
- `customerId: CustomerId` (reference to Customer aggregate)
- `invoiceNumber: InvoiceNumber` (VO)
- `issueDate: LocalDate`
- `dueDate: LocalDate`
- `status: InvoiceStatus` (VO or enum) — {Draft, Sent, Paid}
- `lineItems: List<LineItem>` (VO)
- `payments: List<Payment>` (Entity from Payment subdomain)
- `notes: String?` (optional)

**Invariants:**
- Only editable while status == Draft
- `sum(payments.amount) ≤ total`
- When balance == 0 → status = Paid

**Behaviors:**
- `addLineItem(LineItem item)`
- `updateLineItems(List<LineItem> items)` *(if Draft)*
- `sendInvoice()` → transitions Draft → Sent
- `recordPayment(Payment payment)` → enforces invariant, may trigger `InvoicePaid`
- `calculateSubtotal()`
- `calculateTax()`
- `calculateTotal()`
- `calculateBalance()` → `totalAmount - sum(payments.amount)`

**Value Objects:**
- `LineItem` — `description`, `quantity`, `unitPrice`, `subtotal = quantity * unitPrice`
- `InvoiceNumber` — formatted identifier (`INV-YYYY-####`)
- `InvoiceStatus` — (Draft, Sent, Paid)
- `Money` — encapsulates amount + currency, ensures arithmetic safety

**Events:**
- `InvoiceCreated`
- `InvoiceUpdated`
- `InvoiceSent`
- `PaymentRecorded`
- `InvoicePaid`

---

## 3. Payment Subdomain (Supporting Domain)

### Entity: `Payment`

Represents a monetary transaction applied to an invoice.

**For MVP:** Payment is an entity owned by Invoice (not a standalone aggregate).

**Future:** Could become its own aggregate if payments span multiple invoices or external processors (e.g., Stripe).

**Attributes:**
- `id: PaymentId`
- `invoiceId: InvoiceId` (reference to parent Invoice aggregate)
- `amount: Money`
- `paymentDate: LocalDate`
- `method: PaymentMethod` (VO or enum) — e.g. Cash, Bank Transfer, Card
- `reference: String?` (check #, transaction ID, etc.)

**Invariants:**
- `amount > 0`
- Payment cannot exceed invoice's outstanding balance

**Behaviors:**
- Mostly created via `Invoice.recordPayment()`
- If promoted to aggregate later: might emit `PaymentRecorded` independently

**Value Objects:**
- `Money` (shared with Invoice)
- `PaymentMethod`

**Events:**
- `PaymentRecorded` (for now, emitted by Invoice aggregate)

---

## 4. Relationships Overview

```
Customer (1) ────< (many) Invoice (1) ────< (many) Payment

                               │

                               └── contains LineItems (Value Objects)
```

- Each **Customer** can have multiple **Invoices**
- Each **Invoice** can have multiple **Payments**
- Payments are owned by the Invoice aggregate (MVP scope)
- Invoice contains LineItems as Value Objects

---

## 5. Derived State

**Invoice Balance:**
- Formula: `totalAmount - sum(payments.amount)`
- Calculated by `Invoice.calculateBalance()` method

**Overdue:**
- Derived query: `(dueDate < today && status != PAID)`
- NOT stored or emitted as an event in MVP
- Calculated at query time in read models

---

## 6. Domain Event Summary

| Domain Event      | Source Aggregate | Triggered By                        | Purpose                |
| ----------------- | ---------------- | ----------------------------------- | ---------------------- |
| `CustomerCreated` | Customer         | `CreateCustomer`                    | New customer added     |
| `CustomerUpdated` | Customer         | `UpdateCustomer`                    | Customer info modified |
| `CustomerDeleted` | Customer         | `DeleteCustomer`                    | Customer removed       |
| `InvoiceCreated`  | Invoice          | `CreateInvoice`                     | Invoice drafted        |
| `InvoiceUpdated`  | Invoice          | `UpdateInvoice`                     | Draft modified         |
| `InvoiceSent`     | Invoice          | `SendInvoice`                       | Status changed to Sent |
| `PaymentRecorded` | Invoice          | `RecordPayment`                     | Payment applied        |
| `InvoicePaid`     | Invoice          | `RecordPayment` (when balance == 0) | Invoice settled        |

---

## 7. Aggregate Boundaries

| Aggregate  | Owns                | References | Emits Events                                                |
| ---------- | ------------------- | ---------- | ----------------------------------------------------------- |
| `Customer` | none                | none       | CustomerCreated, Updated, Deleted                           |
| `Invoice`  | LineItems (VO), Payments (Entity) | CustomerId | InvoiceCreated, Updated, Sent, PaymentRecorded, InvoicePaid |
| `Payment`  | none (entity only)  | InvoiceId  | (emitted by parent Invoice)                                 |

---

## 8. CQRS Command Summary

| Command          | Aggregate | Resulting Events                            |
| ---------------- | --------- | ------------------------------------------- |
| `CreateCustomer` | Customer  | `CustomerCreated`                           |
| `UpdateCustomer` | Customer  | `CustomerUpdated`                           |
| `DeleteCustomer` | Customer  | `CustomerDeleted`                           |
| `CreateInvoice`  | Invoice   | `InvoiceCreated`                           |
| `UpdateInvoice`  | Invoice   | `InvoiceUpdated`                            |
| `SendInvoice`    | Invoice   | `InvoiceSent`                               |
| `RecordPayment`  | Invoice   | `PaymentRecorded`, optionally `InvoicePaid` |

---

## 9. Directory Skeleton (Vertical Slice Architecture - VSA)

**REQUIRED:** Code must be organized by bounded context (vertical slices), not by technical layers.

```
/src
 ├── invoice/                    # Invoice bounded context (vertical slice)
 │    ├── domain/
 │    │    ├── Invoice.java (aggregate root)
 │    │    ├── LineItem.java (VO)
 │    │    ├── events/
 │    │    │    ├── InvoiceCreated.java
 │    │    │    ├── InvoiceUpdated.java
 │    │    │    ├── InvoiceSent.java
 │    │    │    ├── PaymentRecorded.java
 │    │    │    └── InvoicePaid.java
 │    │    ├── valueobjects/
 │    │    │    ├── InvoiceStatus.java
 │    │    │    └── InvoiceNumber.java
 │    │    └── InvoiceRepository.java (domain interface)
 │    │
 │    ├── application/
 │    │    ├── commands/
 │    │    │    ├── CreateInvoiceHandler.java
 │    │    │    ├── UpdateInvoiceHandler.java
 │    │    │    ├── SendInvoiceHandler.java
 │    │    │    └── RecordPaymentHandler.java
 │    │    ├── queries/
 │    │    │    ├── GetInvoiceByIdHandler.java
 │    │    │    ├── ListInvoicesHandler.java
 │    │    │    └── ListOverdueInvoicesHandler.java
 │    │    └── bus/
 │    │         └── DomainEventPublisher.java (interface)
 │    │
 │    ├── infrastructure/
 │    │    ├── persistence/
 │    │    │    ├── InvoiceJpaRepository.java
 │    │    │    ├── InvoiceRepositoryAdapter.java
 │    │    │    ├── entities/
 │    │    │    │    ├── InvoiceEntity.java
 │    │    │    │    ├── LineItemEntity.java
 │    │    │    │    └── PaymentEntity.java
 │    │    │    └── mappers/
 │    │    │         └── InvoiceMapper.java
 │    │    └── events/
 │    │         └── InvoiceEventListener.java
 │    │
 │    └── api/
 │         ├── InvoiceController.java
 │         └── dto/
 │              ├── CreateInvoiceRequest.java
 │              ├── UpdateInvoiceRequest.java
 │              ├── InvoiceResponse.java
 │              └── RecordPaymentRequest.java
 │
 ├── customer/                   # Customer bounded context (vertical slice)
 │    ├── domain/
 │    │    ├── Customer.java (aggregate root)
 │    │    ├── events/
 │    │    │    ├── CustomerCreated.java
 │    │    │    ├── CustomerUpdated.java
 │    │    │    └── CustomerDeleted.java
 │    │    ├── valueobjects/
 │    │    │    ├── Email.java
 │    │    │    ├── Address.java
 │    │    │    └── PaymentTerms.java
 │    │    └── CustomerRepository.java (domain interface)
 │    │
 │    ├── application/
 │    │    ├── commands/
 │    │    │    ├── CreateCustomerHandler.java
 │    │    │    ├── UpdateCustomerHandler.java
 │    │    │    └── DeleteCustomerHandler.java
 │    │    └── queries/
 │    │         ├── GetCustomerByIdHandler.java
 │    │         └── ListCustomersHandler.java
 │    │
 │    ├── infrastructure/
 │    │    └── persistence/
 │    │         ├── CustomerJpaRepository.java
 │    │         ├── CustomerRepositoryAdapter.java
 │    │         ├── entities/
 │    │         │    └── CustomerEntity.java
 │    │         └── mappers/
 │    │              └── CustomerMapper.java
 │    │
 │    └── api/
 │         ├── CustomerController.java
 │         └── dto/
 │              ├── CreateCustomerRequest.java
 │              ├── UpdateCustomerRequest.java
 │              └── CustomerResponse.java
 │
 ├── payment/                    # Payment bounded context (vertical slice)
 │    ├── domain/
 │    │    ├── Payment.java (entity, owned by Invoice)
 │    │    ├── PaymentMethod.java (VO or enum)
 │    │    └── (Note: PaymentRepository not needed - Invoice manages payments)
 │    │
 │    ├── application/
 │    │    └── (Note: Payment commands/queries handled by Invoice slice)
 │    │
 │    ├── infrastructure/
 │    │    └── (Note: Payment persistence handled by Invoice slice)
 │    │
 │    └── api/
 │         └── (Note: Payment endpoints in InvoiceController)
 │
 └── shared/                     # Shared primitives across bounded contexts
      ├── domain/
      │    ├── DomainEvent.java (interface)
      │    └── Money.java (value object)
      ├── application/
      │    ├── ApplicationError.java
      │    └── bus/
      │         └── DomainEventPublisher.java (interface)
      └── infrastructure/
           ├── events/
           │    ├── SimpleDomainEventPublisher.java
           │    └── LoggingEventListener.java
           └── security/
                └── BasicAuthConfig.java
```

**Key VSA Principles:**
- Each bounded context is a self-contained vertical slice
- No global `/controllers`, `/services`, or `/repositories` folders (HSA disallowed)
- Package imports only go downward (api → application → domain), never sideways
- Shared primitives live in `/shared/`
- Each slice is independently understandable and testable

---

## 10. Summary

| Category          | Core Elements                                                                              |
| ----------------- | ------------------------------------------------------------------------------------------ |
| **Aggregates**    | `Invoice`, `Customer`                                                                      |
| **Entities**      | `Payment` (embedded in Invoice)                                                            |
| **Value Objects** | `LineItem`, `Money`, `InvoiceStatus`, `InvoiceNumber`, `PaymentMethod`, `Email`, `Address`, `PaymentTerms` |
| **Derived State** | `Overdue`, `Balance`                                                                       |
| **Events**        | `CustomerCreated`, `CustomerUpdated`, `CustomerDeleted`, `InvoiceCreated`, `InvoiceUpdated`, `InvoiceSent`, `PaymentRecorded`, `InvoicePaid` |

---

## 11. Key Design Principles

1. **Payment is embedded in Invoice** (MVP) - maintains immediate consistency
2. **LineItem is a Value Object** - immutable, equality by value
3. **Money is a shared Value Object** - used by Invoice and Payment
4. **Status transitions are explicit** - Draft → Sent → Paid
5. **Balance is calculated, not stored** - `calculateBalance()` method
6. **Overdue is derived at query time** - not a domain event or stored state
7. **Customer is referenced by ID** - Invoice holds `customerId`, not Customer entity
8. **All invariants enforced in aggregates** - business rules live in domain layer

---

## 12. Future Evolution Paths

**Payment as Separate Aggregate:**
- When: Multi-invoice payments, Stripe integration, async webhooks
- How: Extract Payment to own aggregate, use domain events for consistency
- Events: `PaymentRecorded` emitted by Payment aggregate, `InvoicePaid` via event handler

**LineItem as Entity:**
- When: Partial fulfillment, line-item-level payments
- How: Add identity to LineItem, track fulfillment status
- Impact: More complex Invoice aggregate, additional invariants

**Business Aggregate:**
- When: Multi-tenant support needed
- How: Add Business aggregate, link Customers/Invoices to Business
- Impact: Tenant isolation, separate bounded context

