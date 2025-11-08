# API/Interface Layer (REST Controllers & DTOs)

## Overview

The interface layer is the API that external clients (React/Next.js frontend, Postman) will hit. It stays true to DDD structure while providing practical REST endpoints backed by PostgreSQL.

---

## What DTOs Are

DTO stands for **Data Transfer Object**.

It's a plain Java record or class used to **carry data across boundaries** — for example, from:
* the REST controller → the application service (command handler), or
* the domain aggregate → the REST response.

DTOs isolate the outside world (JSON shape, HTTP requests) from your **domain model** (Invoice, Customer, Payment).

They prevent your aggregates from leaking into the API and allow independent versioning.

---

## REST Resource Design (Endpoints)

For our 3 core subdomains:

| Resource             | Endpoint                             | Methods                        | Description                          |
| -------------------- | ------------------------------------ | ------------------------------ | ------------------------------------ |
| **Customer**         | `/api/customers`                     | `GET`, `POST`, `PUT`, `DELETE` | Manage customer records              |
| **Invoice**          | `/api/invoices`                      | `GET`, `POST`, `PUT`, `DELETE` | Create and manage invoices           |
| **Invoice Payments** | `/api/invoices/{invoiceId}/payments` | `POST`                         | Record a payment for a given invoice |

You can extend with `/api/customers/{id}/invoices` or `/api/invoices?status=Sent` later, but those are read-model queries, not core writes.

---

## DTOs per Resource

We'll define **Request DTOs** (input payloads) and **Response DTOs** (what the API returns).

Use Java **records** for brevity — they're immutable, serializable, and map cleanly to JSON.

### Customer DTOs

```java
// For POST /api/customers
public record CreateCustomerRequest(
    String name,
    String email,
    String phone,
    String street,
    String city,
    String postalCode,
    String country,
    String paymentTerms
) {}

// For PUT /api/customers/{id}
public record UpdateCustomerRequest(
    String name,
    String email,
    String phone,
    String street,
    String city,
    String postalCode,
    String country,
    String paymentTerms
) {}

// For responses (GET)
public record CustomerResponse(
    UUID id,
    String name,
    String email,
    String phone,
    String street,
    String city,
    String postalCode,
    String country,
    String paymentTerms
) {}
```

### Invoice DTOs

```java
// POST /api/invoices
public record CreateInvoiceRequest(
    UUID customerId,
    List<LineItemDto> lineItems,
    LocalDate issueDate,
    LocalDate dueDate,
    BigDecimal taxRate,
    String notes
) {
    public record LineItemDto(String description, BigDecimal quantity, BigDecimal unitPrice) {}
}

// PUT /api/invoices/{id}
public record UpdateInvoiceRequest(
    List<CreateInvoiceRequest.LineItemDto> lineItems,
    LocalDate dueDate,
    BigDecimal taxRate,
    String notes
) {}

// GET /api/invoices/{id}
public record InvoiceResponse(
    UUID id,
    String invoiceNumber,
    UUID customerId,
    LocalDate issueDate,
    LocalDate dueDate,
    String status,
    BigDecimal subtotal,
    BigDecimal tax,
    BigDecimal total,
    BigDecimal balance,
    String notes,
    List<LineItemResponse> lineItems,
    List<PaymentResponse> payments
) {
    public record LineItemResponse(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal subtotal) {}
    public record PaymentResponse(BigDecimal amount, LocalDate paymentDate, String method, String reference) {}
}
```

### Payment DTO

```java
// POST /api/invoices/{invoiceId}/payments
public record RecordPaymentRequest(
    BigDecimal amount,
    LocalDate paymentDate,
    String method,
    String reference
) {}

// Included inside InvoiceResponse as PaymentResponse
```

---

## REST Controller Contracts

Each controller:
* Accepts DTOs (mapped from JSON via Spring)
* Converts them into commands
* Invokes your handler (application service)
* Returns DTOs or HTTP 204 (no content) for writes

### InvoiceController Example

```java
@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {
    private final CreateInvoiceHandler createHandler;
    private final UpdateInvoiceHandler updateHandler;
    private final SendInvoiceHandler sendHandler;
    private final RecordPaymentHandler paymentHandler;
    private final InvoiceQueryService queryService;

    @PostMapping
    public ResponseEntity<InvoiceResponse> create(@RequestBody CreateInvoiceRequest req) {
        UUID id = createHandler.handle(req.toCommand());       // returns new invoice ID
        InvoiceResponse resp = queryService.getInvoiceById(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable UUID id, @RequestBody UpdateInvoiceRequest req) {
        updateHandler.handle(req.toCommand(id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/send")
    public ResponseEntity<Void> send(@PathVariable UUID id) {
        sendHandler.handle(new SendInvoiceCommand(id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<Void> recordPayment(@PathVariable UUID id, @RequestBody RecordPaymentRequest req) {
        paymentHandler.handle(req.toCommand(id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public InvoiceResponse getById(@PathVariable UUID id) {
        return queryService.getInvoiceById(id);
    }
}
```

*(Use `@RequiredArgsConstructor` from Lombok for constructor injection.)*

---

## Mapping DTOs → Commands

Keep a thin mapping layer to decouple the JSON shape from domain commands.

Add simple helper methods in DTOs or use a separate mapper class.

Example:

```java
public record RecordPaymentRequest(BigDecimal amount, LocalDate paymentDate, String method, String reference) {
    public RecordPaymentCommand toCommand(UUID invoiceId) {
        return new RecordPaymentCommand(invoiceId, amount, paymentDate, method, reference);
    }
}
```

This avoids directly constructing commands in the controller.

---

## HTTP Semantics & Status Codes

| Operation         | Method                             | Response                 | Status           |
| ----------------- | ---------------------------------- | ------------------------ | ---------------- |
| Create Customer   | POST `/api/customers`              | `CustomerResponse`       | `201 Created`    |
| Update Customer   | PUT `/api/customers/{id}`          | —                        | `204 No Content` |
| Create Invoice    | POST `/api/invoices`               | `InvoiceResponse`        | `201 Created`    |
| Update Invoice    | PUT `/api/invoices/{id}`           | —                        | `204 No Content` |
| Send Invoice      | POST `/api/invoices/{id}/send`     | —                        | `204 No Content` |
| Record Payment    | POST `/api/invoices/{id}/payments` | —                        | `204 No Content` |
| Get Invoice       | GET `/api/invoices/{id}`           | `InvoiceResponse`        | `200 OK`         |
| Get All Customers | GET `/api/customers`               | `List<CustomerResponse>` | `200 OK`         |

All endpoints are authenticated via BasicAuth (from infrastructure security config).

---

## JSON Shape Examples

### Create Invoice (POST)

```json
{
  "customerId": "e6c3d3ae-47f5-44da-9620-6b8fcbad2db8",
  "lineItems": [
    { "description": "Web design", "quantity": 10, "unitPrice": 150.0 },
    { "description": "Hosting", "quantity": 1, "unitPrice": 50.0 }
  ],
  "issueDate": "2025-11-01",
  "dueDate": "2025-11-30",
  "taxRate": 10.0,
  "notes": "Thanks for your business!"
}
```

### Invoice Response (GET)

```json
{
  "id": "b3f8a8d4-8829-4b7b-8af2-6065b2e11a6f",
  "invoiceNumber": "INV-2025-0007",
  "customerId": "e6c3d3ae-47f5-44da-9620-6b8fcbad2db8",
  "issueDate": "2025-11-01",
  "dueDate": "2025-11-30",
  "status": "SENT",
  "subtotal": 1550.0,
  "tax": 155.0,
  "total": 1705.0,
  "balance": 1705.0,
  "notes": "Thanks for your business!",
  "lineItems": [
    { "description": "Web design", "quantity": 10, "unitPrice": 150.0, "subtotal": 1500.0 },
    { "description": "Hosting", "quantity": 1, "unitPrice": 50.0, "subtotal": 50.0 }
  ],
  "payments": []
}
```

---

## Translation Chain

The DTOs don't know or care that Postgres is underneath.

When you call your repository adapter's `save(invoice)`, Spring Data JPA translates it into the right SQL for your Postgres schema.

So even though your API is JSON/REST, and your domain is pure Java, Spring handles the translation chain:

```
HTTP JSON
   ↓
DTO
   ↓
Command
   ↓
Domain Aggregate (Invoice)
   ↓
Repository Adapter
   ↓
Spring Data JPA
   ↓
PostgreSQL
```

---

## OpenAPI 3.1 (Swagger) Specification

The OpenAPI specification file can live at `backend/src/main/resources/openapi.yaml` or be served dynamically via Springdoc.

### Installation

```gradle
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0'
```

### Access

Launch your app and open:
```
http://localhost:8080/swagger-ui.html
```

You'll see a live interactive interface of all endpoints, requests, and responses.

### OpenAPI YAML

The complete OpenAPI 3.1 specification should be placed at `backend/src/main/resources/openapi.yaml`.

**Key features:**
* All endpoints documented (Customer, Invoice, Payment)
* Request/response schemas with examples
* BasicAuth security scheme applied globally
* UUID, Money, Date value types defined
* LineItem and Payment nested objects
* Proper HTTP status codes (201, 204, 200, 404, 409)

**OpenAPI Specification Structure:**
- `info`: API metadata (title, description, version)
- `servers`: Local development and production URLs
- `security`: BasicAuth scheme definition
- `components/schemas`: All DTOs and value types
- `paths`: All REST endpoints with request/response definitions

You can import this `openapi.yaml` into:
* **Postman** (Collections → Import)
* **Insomnia**
* **Stoplight / Redocly** for documentation
* **Swagger UI** (via Springdoc OpenAPI)

**Note:** The full OpenAPI YAML specification is provided in the user's analysis. It should be saved as `backend/src/main/resources/openapi.yaml` for Springdoc to automatically serve it.

---

## Package Structure (Vertical Slice Architecture - VSA)

**REQUIRED:** Code must be organized by bounded context (vertical slices), not by technical layers.

```
/src/main/java/app
 ├── invoice/                    # Invoice bounded context (vertical slice)
 │    └── api/
 │         ├── InvoiceController.java
 │         └── dto/
 │              ├── CreateInvoiceRequest.java
 │              ├── UpdateInvoiceRequest.java
 │              ├── InvoiceResponse.java
 │              └── RecordPaymentRequest.java
 │
 ├── customer/                   # Customer bounded context (vertical slice)
 │    └── api/
 │         ├── CustomerController.java
 │         └── dto/
 │              ├── CreateCustomerRequest.java
 │              ├── UpdateCustomerRequest.java
 │              └── CustomerResponse.java
 │
 └── shared/                     # Shared across bounded contexts
      └── api/
           └── exception/
                └── GlobalExceptionHandler.java

/src/main/resources/
 └── openapi.yaml
```

**Key VSA Principles:**
- Each bounded context has its own `api/` folder
- No global `/api/` folder (HSA disallowed)
- Controllers and DTOs live within their bounded context
- Shared concerns (GlobalExceptionHandler) live in `/shared/api/`

---

## Summary

| Layer                    | Example                                   | Role                                 |
| ------------------------ | ----------------------------------------- | ------------------------------------ |
| **API (Interface)**      | `InvoiceController`                       | Exposes REST endpoints               |
| **DTOs**                 | `CreateInvoiceRequest`, `InvoiceResponse` | JSON boundary objects                |
| **Application Layer**    | `CreateInvoiceHandler`                    | Use-case orchestration               |
| **Domain Layer**         | `Invoice` aggregate                       | Business logic & invariants          |
| **Infrastructure Layer** | `InvoiceRepositoryAdapter`                | Persists aggregates via JPA/Postgres |

---

## Key Design Principles

1. **DTOs isolate API from domain** - JSON shape independent of domain model
2. **Thin controllers** - Controllers delegate to handlers, don't contain business logic
3. **Command mapping** - DTOs convert to commands via helper methods
4. **RESTful semantics** - Proper HTTP methods and status codes
5. **OpenAPI documentation** - Self-documenting API via Swagger
6. **Authentication** - BasicAuth protects all endpoints
7. **Error handling** - Global exception handler maps domain errors to HTTP status codes

---

## Implementation Checklist

- [ ] Create DTO records for all requests and responses
- [ ] Implement controllers for Customer and Invoice resources
- [ ] Add DTO → Command mapping methods
- [ ] Configure OpenAPI/Swagger documentation
- [ ] Add global exception handler for error mapping
- [ ] Test endpoints with Postman/Swagger UI
- [ ] Verify JSON serialization/deserialization
- [ ] Ensure BasicAuth protection on all endpoints

