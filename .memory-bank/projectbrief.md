# Project Brief

## Overview
InvoiceMe is a minimal API server proof-of-concept focused on practicing Java 21 + Spring Boot 3.5 setup and deployment patterns. The project emphasizes containerization, local Docker Compose workflows, and future AWS ECS Fargate + RDS deployment.

## Goals
- Practice Java 21 + Spring Boot 3.5 setup and deployment patterns
- Focus on containerization and local Docker Compose (Postgres + app)
- Prepare for future AWS ECS Fargate + RDS deployment
- Maintain a lightweight, focused codebase without overcomplication

## Key Requirements
- Lightweight CRUD API with dummy routes for invoices
- Health endpoint for verification
- Backend-only (no frontend)
- No complex business logic
- Uses Gradle, Spring Boot, JPA, Flyway, and Postgres
- Docker Compose for local development
- Environment variable configuration for deployment flexibility

## Constraints
- **No frontend** - Backend API only
- **No complex business logic** - Keep it simple
- **No billing, auth, or multi-tenant ERP logic** - Avoid overcomplication
- **No OAuth or payment integrations** - Not in scope
- **No managed infrastructure (CDK) yet** - Future phase

## Success Criteria
- Local development: Docker Compose spins up Postgres and app container
- Application connects to Postgres via `jdbc:postgresql://postgres:5432/invoiceme`
- Health endpoint verified: `curl localhost:8080/api/health` returns `{status:"ok"}`
- Ready for future AWS deployment (ECS Fargate + RDS)
- Clean Spring Boot containerization and deployment readiness

