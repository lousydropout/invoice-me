# **Product Requirements Document (PRD): InvoiceMe — AI-Assisted Full-Stack ERP Assessment**

## **1\. Introduction and Project Goal**

### **1.1 Project Goal**

The goal of the InvoiceMe assessment is to challenge candidates to build a small, production-quality ERP-style invoicing system. This project is designed to explicitly demonstrate mastery of modern software architecture principles (Domain-Driven Design, CQRS, Vertical Slice Architecture) alongside the intelligent and efficient use of AI-assisted development tools.

### **1.2 Context**

This assessment mirrors real-world Software-as-a-Service (SaaS) ERP development, concentrating on core business domains: **Customers, Invoices, and Payments**. Success requires architectural clarity, separation of concerns, and code quality that aligns with enterprise-level, scalable systems.

## **2\. Business Functionality (Domain Model)**

### **2.1 Problem Statement**

While AI tools accelerate code generation, they do not inherently guarantee sound system design. The challenge is to prove that the candidate can provide the architectural guidance necessary to build a correct, maintainable, and scalable application structure, using AI as an accelerator rather than a primary designer.

### **2.2 Core Functional Requirements**

The system must implement the following core operations, ensuring a clean separation between **Commands** (mutations) and **Queries** (reads) as per the CQRS principle:

| Domain Entity | Commands (Write Operations) | Queries (Read Operations) |
| ----- | ----- | ----- |
| **Customer** | Create, Update, Delete Customer | Retrieve Customer by ID, List all Customers |
| **Invoice** | Create (Draft), Update, Mark as Sent, Record Payment | Retrieve Invoice by ID, List Invoices by Status/Customer |
| **Payment** | Record Payment (Applies to Invoice) | Retrieve Payment by ID, List Payments for an Invoice |

### **2.3 Invoice Lifecycle and Logic**

* **Line Items:** Each Invoice **MUST** support the association of multiple Line Items (describing services/products, quantity, and unit price).  
* **Lifecycle:** Implement the following state transitions:  
  Draft → Sent → Paid.  
* **Balance Calculation:** Implement robust logic for calculating the running Invoice balance and correctly applying Payments against that balance.

### **2.4 User Management**

* **Authentication:** Basic authentication functionality (e.g., a simple Login screen) is required to secure access to the application data.

## **3\. Architecture and Technical Requirements**

### **3.1 Architectural Principles (Mandatory)**

The application architecture is the core of the assessment and **MUST** adhere to the following principles:

1. **Domain-Driven Design (DDD):** Model the core entities (Customer, Invoice, Payment) as true Domain Objects with rich behavior.  
2. **Command Query Responsibility Segregation (CQRS):** Implement a clean separation between write operations (Commands) and read operations (Queries).  
3. **Vertical Slice Architecture (VSA):** Organize the code around features or use cases (vertical slices) rather than technical layers (horizontal slicing).  
4. **Layer Separation:** Maintain clear boundaries between the **Domain**, **Application**, and **Infrastructure** layers (Clean Architecture).

### **3.2 Technical Stack**

* **Back-End (API):** Java with Spring Boot. Must expose **RESTful APIs**.  
* **Front-End (UI):** TypeScript with React.js or Next.js. Must adhere to **MVVM (Model-View-ViewModel)** principles for UI logic.  
* **Database:** PostgreSQL is preferred for production readiness simulation; however, an in-memory database (H2 or SQLite) is permitted for testing and rapid development.  
* **Cloud Platforms:** Deployment target flexibility: **AWS or Azure**.

### **3.3 Performance Benchmarks**

* **API Latency:** API response times for standard CRUD operations **MUST be under 200ms** in a local testing environment.  
* **UI Experience:** Smooth and responsive UI interactions without noticeable lag.

## **4\. Code Quality and AI Acceleration**

### **4.1 Code Quality Standards (Mandatory)**

* **Structure:** Code must be modular, readable, and well-documented.  
* **Data Transfer:** Use explicit **DTOs (Data Transfer Objects)** and mappers for boundary crossing (API to Application Layer).  
* **Domain Events:** The use of optional **Domain Events** is encouraged to demonstrate advanced DDD modeling.  
* **Consistency:** Consistent naming conventions and clean code organization are required throughout the repository.

### **4.2 Testing (Mandatory)**

* **Integration Tests:** **MUST** implement integration tests to verify end-to-end functionality across key modules (e.g., the complete Customer Payment flow).

### **4.3 AI Tool Utilization**

Candidates are encouraged to use AI tools (Cursor, Copilot, v0.dev, Locofy) to accelerate development. The evaluation will measure **how effectively and intelligently** these tools were utilized to achieve high-quality, architecturally sound results.

## **5\. Project Deliverables and Constraints**

### **5.1 Time Constraint**

* **Recommended Completion Time:** 5–7 days.

### **5.2 Submission Requirements**

1. **Code Repository:** Complete, functional code repository (GitHub preferred).  
2. **Demo:** A video or live presentation demonstrating the core functional flow (Customer creation, Invoice creation with line items, Payment application).  
3. **Brief Technical Writeup (1-2 pages):** Documenting the chosen architecture (DDD boundaries, CQRS implementation, VSA structure), design decisions, and database schema.  
4. **AI Tool Documentation:** Detailed documentation of the specific AI tools used, including example prompts and a justification for how they accelerated development while maintaining architectural quality.  
5. **Test Cases and Validation Results:** Evidence of passing integration tests.

