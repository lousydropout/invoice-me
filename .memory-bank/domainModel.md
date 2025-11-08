# Domain Model & Business Requirements

**See also:** `.memory-bank/canonicalDomainModel.md` for the complete canonical domain model specification with detailed attributes, behaviors, invariants, and relationships.

## Business Scenario

> A business issues invoices to customers, collects payments (potentially through Stripe or manual entry), and needs to track who owes what, who has paid, and when.

---

## Domain Structure (DDD Classification)

### **1. Core Domain — Invoicing**

The primary business capability that provides competitive advantage.

#### **1.1 Invoice Creation & Management**

**Goal:** Generate invoices that represent the business's claim on a customer.

**Features:**
- Create new invoice (draft state)
- Add/edit/remove line items (description, quantity, unit price, tax)
- Auto-calculate subtotals, taxes, and total
- Assign customer to invoice
- Add metadata: issue date, due date, invoice number, notes, payment terms
- Generate invoice document (PDF or structured data)
- Mark as "Sent" or "Paid" (Overdue is derived state, not stored)
- Prevent edits to sent or paid invoices (immutable states)

**Required Fields:**
- Invoice number (auto-generated)
- Customer name & contact info
- Business name & contact info (the issuer)
- Issue date / Due date
- List of line items (description, qty, unit price)
- Subtotal, tax, total
- Payment terms (e.g. "Due on receipt", "Net 30")
- Notes / footer (optional)
- Status (Draft, Sent, Paid) - Note: Overdue is derived state, not stored
- Balance remaining (calculated, not stored)

**Key Entities:**
- `Invoice` (Aggregate Root)
- `LineItem` (Value Object or Entity within Invoice aggregate)

**Domain Events:**
- `InvoiceCreated`
- `InvoiceSent`
- `InvoicePaid`
- **Note:** `InvoiceOverdue` is NOT a domain event - overdue is derived state calculated at query time

---

### **2. Supporting Domain — Payments**

Essential but not core to the business model.

#### **2.1 Payment Recording**

**Goal:** Apply payments against outstanding invoices.

**Features:**
- Record a payment (manual or automatic via Stripe webhook)
- Associate payment to an invoice (1:1 for MVP; multi-invoice payments are future enhancement)
- Validate that payment amount ≤ outstanding balance
- Auto-update invoice status (if fully paid → mark as Paid)
- Support partial payments (keep running balance)
- Support refunds (optional advanced feature)
- Store transaction reference, payment method, and date

**Entities:**
- `Payment` (Entity within Invoice aggregate - NOT a separate aggregate root for MVP)
  - id, invoiceId, amount, method, transactionRef, date, status
  - **Note:** Payment is a separate subdomain (strategic DDD) but embedded as entity within Invoice aggregate (tactical DDD)

**Value Objects:**
- `Money` (value object for arithmetic & currency consistency)

**Domain Events:**
- `PaymentRecorded`
- `PaymentFailed`

#### **2.2 Stripe / Payment Processor Integration**

**Goal:** Automate invoice payment capture and reconciliation.

**Features:**
- Create Stripe PaymentIntent or Checkout Session when invoice is sent
- Handle Stripe Webhook events:
  - `invoice.payment_succeeded`
  - `invoice.payment_failed`
  - `payment_intent.canceled`
- Update internal invoice status accordingly
- Log events for auditing
- Reconcile discrepancies between internal records and Stripe data

**Infrastructure Adapter:**
- `StripeWebhookAdapter` (handles external Stripe events)
- `StripePaymentService` (Domain Service for payment processing)

---

### **3. Supporting Domain — Customer Management**

#### **3.1 Customer CRUD**

**Goal:** Maintain customer profiles and relationships.

**Features:**
- Create, update, delete customers
- Retrieve by ID
- List all customers
- Link invoices and payments to customer
- Optionally: assign payment terms or default settings per customer

**Fields:**
- id, name, email, address, phone, company name, default payment terms

**Key Entity:**
- `Customer` (Aggregate Root)

**Domain Events:**
- `CustomerCreated`
- `CustomerUpdated`

---

### **4. Generic Subdomain — Communication**

#### **4.1 Invoice Delivery & Notification**

**Goal:** Send invoices and payment reminders to customers.

**Features:**
- Send invoice via email (with PDF or link)
- Track delivery status (sent, opened, bounced)
- Send payment confirmation when invoice paid
- Send reminders for overdue invoices

**Implementation (DDD View):**
- **Domain Service:** `InvoiceNotificationService`
- **Infrastructure Adapter:** `EmailAdapter` or `NotificationAdapter`

**Domain Events:**
- `InvoiceReminderSent`
- `PaymentConfirmationSent`

---

### **5. Supporting Domain — Reporting & Queries**

#### **5.1 Invoice Queries**

**Goal:** Support data views for management and analytics.

**Features:**
- Retrieve invoice by ID
- List invoices by status (Draft / Sent / Paid) - Note: Overdue is derived at query time
- List invoices by customer
- List outstanding and overdue invoices
- Summaries: total invoiced, total paid, total outstanding
- Time-based reports (month-to-date revenue, overdue trends)

**Technical Pattern:**
- Implemented as **Query Handlers** (CQRS read side)
- May use denormalized views or projections for performance

---

### **6. Infrastructure Concern — Authentication**

**Important:** Authentication is **NOT** a domain, subdomain, or bounded context. It's a **cross-cutting infrastructure concern**.

#### **6.1 Authentication (Infrastructure Layer)**

**Goal:** Provide simple access control to protect API endpoints.

**Assumption:**
- Single-tenant system with a single business user (or small, fixed set of internal users)
- No multi-tenant user management, RBAC, or organization boundaries
- No sign-up, no password reset flow — just an admin logging in

**Implementation:**
- Spring Security Basic Auth or lightweight JWT filter
- Credentials stored in `.env` file or simple user table
- Protects all REST endpoints (`/api/invoices`, `/api/customers`, etc.)
- Config isolated in `/infrastructure/security`

**DDD Implications:**
- Auth does **NOT** appear in ubiquitous language
- Auth does **NOT** appear in event storming (no `UserLoggedIn` events, no `RegisterUser` commands)
- Auth does **NOT** create a `User` aggregate or domain entity
- Auth is a thin infrastructure shell wrapping the API boundary

**Code Organization:**
```
/src
 └── infrastructure
      └── security
           ├── BasicAuthConfig.java
           ├── SecurityFilter.java
           └── UserDetailsService.java
```

---

### **7. Cross-Cutting Concerns**

#### **7.1 Audit & Versioning**
- Log domain events (InvoiceCreated, InvoiceSent, PaymentRecorded)
- Maintain change history for invoices and payments

#### **7.2 Multi-Account Support**
- All payments go to **the same business account by default**
- Optional: multi-account support for businesses with separate bank accounts per department or subsidiary
  - Could be modeled as **BusinessAccount** aggregate (new bounded context later)

#### **7.3 Error Handling & Validation**
- Prevent inconsistent states (e.g. payment > balance)
- Handle failed Stripe events, expired sessions, or invalid webhooks

---

## Domain Summary Table

| Domain/Subdomain          | Key Entities/Aggregates                    | Core Features                       | Typical Domain Events                            |
| ------------------------- | ------------------------------------------- | ----------------------------------- | ------------------------------------------------ |
| **Invoicing (Core)**      | Invoice (contains LineItem VOs, Payment entities) | Create/Send/Mark/Update             | `InvoiceCreated`, `InvoiceSent`, `InvoicePaid`   |
| **Payments (Supporting)** | Payment (entity within Invoice aggregate)   | Record/Reconcile                    | `PaymentRecorded` (within Invoice context)       |
| **Customer Management**   | Customer (Aggregate Root)                   | CRUD                                | `CustomerCreated`, `CustomerUpdated`             |
| **Communication**         | Message/Email (Infrastructure)             | Send/Remind/Confirm                 | `InvoiceReminderSent`, `PaymentConfirmationSent` |
| **Reporting**             | — (Query/Read Model concern)                | Outstanding/Overdue/Revenue reports | —                                                |
| **Auth (Infrastructure)** | — (Not a domain concern)                    | Basic Auth / JWT (API protection)     | — (No domain events)                              |

**Note:** Payment is NOT a separate aggregate root. It's an entity within the Invoice aggregate to maintain immediate consistency and enforce payment invariants.

---

## Key DDD Concepts to Implement

1. **Aggregate Roots:**
   - `Invoice` (contains `LineItem` value objects and `Payment` entities)
   - `Customer`
   - **Note:** `User` is NOT an aggregate root. Authentication is an infrastructure concern, not a domain concern.

2. **Value Objects:**
   - `Money` (amount + currency)
   - `LineItem` (immutable value object - description, quantity, unit price, tax)
   - `Address` (for customer)

3. **Entities within Aggregates:**
   - `Payment` (entity within Invoice aggregate - has identity, tracks individual payments)
   - **Note:** Payment is a separate *subdomain* (supporting domain) but embedded as an entity within Invoice aggregate for MVP
   - Lives in its own package (`/domain/payment`) to preserve subdomain identity
   - Dependency: Invoice → Payment (never reverse)

4. **Domain Services:**
   - `InvoiceNotificationService` (sending emails)
   - `StripePaymentService` (payment processing - future, optional for MVP)

5. **Domain Events:**
   - All state changes should emit domain events
   - Events enable decoupling and audit trails
   - Examples: `InvoiceCreated`, `InvoiceSent`, `PaymentRecorded`, `InvoicePaid`

6. **CQRS Separation:**
   - **Commands:** CreateInvoice, SendInvoice, RecordPayment, etc.
   - **Queries:** GetInvoiceById, ListInvoicesByStatus, etc.
   - **Read Models:** Start with querying write model directly; add projections/views for complex aggregations later

7. **Rich Domain Models:**
   - Entities should contain business logic, not just getters/setters
   - Business rules enforced in domain layer (e.g., "cannot edit sent invoices")
   - Balance calculation lives in Invoice aggregate: `calculateBalance() = total - sum(payments)`

---

## Architectural Decisions (Validated)

### Payment-to-Invoice Relationship

**Strategic DDD View (Subdomain Level):**
- Invoice is the **core domain**
- Payment is a **supporting subdomain** (distinct in ubiquitous language)
- They share the same **bounded context** for transactional simplicity

**Tactical DDD View (Aggregate Level):**
- Payment is implemented as an **entity embedded within the Invoice aggregate**
- Payment is NOT a separate aggregate root for MVP
- This allows Invoice to:
  - Enforce invariant "payment amount ≤ outstanding balance" atomically
  - Calculate running balance internally
  - Mark itself as "Paid" synchronously
  - Remain consistent without event-based coordination

**Code Organization:**
- Keep `/domain/invoice` and `/domain/payment` as **separate directories** to preserve subdomain identity
- `Invoice` imports `Payment` (dependency direction: Invoice → Payment, never reverse)
- Example layout:
  ```
  /domain
   ├── invoice/
   │   ├── Invoice.java
   │   └── InvoiceRepository.java
   ├── payment/
   │   └── Payment.java
   └── shared/
       └── Money.java
  ```

**Future Evolution:**
- If business logic expands (multi-invoice payments, refunds, Stripe integration, async events), Payment can be promoted to its **own aggregate and bounded context**
- At that stage, consistency shifts from synchronous (method call) to **event-driven** (`PaymentRecorded → InvoicePaid`)

**Rationale:**
- Respects conceptual subdomain boundaries
- Keeps invariants local and consistent
- Minimizes premature complexity
- Simplifies later extraction into its own context

**TL;DR:** For MVP, `Payment` is a separate *subdomain* but an *embedded entity* inside the `Invoice` aggregate. It's implemented in its own package (`/domain/payment`) but lives under the same bounded context, allowing `Invoice` to maintain full payment consistency without breaking DDD principles.

### Invoice Status vs. Derived State
- **Domain States (stored):** Draft, Sent, Paid
- **Derived State (query concern):** Overdue (calculated as `dueDate < today && status != PAID`)
- **Rationale:** Keeps aggregates clean; overdue belongs in read models/projections, not domain state

### Balance Calculation
- **Location:** Invoice aggregate method `calculateBalance()`
- **Formula:** `total - sum(payments)`
- **Rationale:** Invoice "knows" its balance by definition; encapsulates invariant

### LineItem Design
- **Decision:** Value Object (immutable)
- **Rationale:**
  - Once invoice is sent, line items shouldn't mutate
  - Identity not required (equality by value suffices)
  - Can reconstruct from audit logs if needed
  - Can promote to Entity later if partial fulfillment is needed

### Business Information
- **Decision:** Configuration/settings (not a separate aggregate)
- **Rationale:** Single-tenant MVP assumption; static metadata for issuing business
- **Future:** Can elevate to Business aggregate if multi-tenant support is needed

### Invoice Number Generation
- **Decision:** Sequential, human-readable format (e.g., INV-2024-001)
- **Rationale:** Domain-friendly, easier for users to reference

### Tax Calculation
- **Decision:** Simple tax model (single rate per invoice or per line item)
- **Rationale:** Start simple; can add Tax aggregate/value object later if complexity grows

### Multi-Tenancy
- **Decision:** Single-tenant system (one business per instance)
- **Rationale:** Keeps bounded contexts smaller, simpler data isolation
- **Future:** Can add multi-tenant support with Business aggregate and tenant isolation

### Stripe Integration
- **Decision:** Skip for MVP; focus on manual payment recording
- **Rationale:** Reduces infrastructure complexity; can add Stripe adapter later
- **Future:** Externalize Payment as separate aggregate when Stripe/webhook integration is added

### CQRS Read Models
- **Decision:** Start with querying write model directly
- **Rationale:** Simpler for MVP; sufficient for basic queries
- **Future:** Add read projections/database views for complex aggregations and dashboard queries

