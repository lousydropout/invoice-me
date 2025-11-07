# Active Context

## Current Focus
AWS infrastructure deployed successfully. CDK stack `InvoiceMeStack` is live with Aurora Serverless PostgreSQL, ECS Fargate service, and Application Load Balancer.

## Recent Changes
- Complete Spring Boot 3.5 application implemented with Java 21
- Gradle Kotlin DSL build configuration with all dependencies
- Invoice domain model: Entity, Repository, Service, Controller, DTOs (Java records)
- Health endpoint at `/api/health`
- Full CRUD API for invoices with PATCH support for partial updates
- GlobalExceptionHandler with 400/404/500 error handling
- Flyway migration V1__init.sql with invoice table schema
- Docker Compose setup with Postgres 17 and multi-stage Dockerfile
- AWS CDK infrastructure deployed (Aurora Serverless v2, ECS Fargate, ALB)
- ECR image integration with proper permissions

## AWS Deployment

**Stack**: `InvoiceMeStack`  
**Region**: `us-east-1`  
**Status**: Deployed

**Endpoints**:
- ALB DNS: `Invoic-Invoi-Gz9hboCJo4NZ-1292342723.us-east-1.elb.amazonaws.com`
- API URL: `http://Invoic-Invoi-Gz9hboCJo4NZ-1292342723.us-east-1.elb.amazonaws.com/api`
- Health Check: `http://Invoic-Invoi-Gz9hboCJo4NZ-1292342723.us-east-1.elb.amazonaws.com/api/health`

**Infrastructure**:
- Aurora Serverless v2 PostgreSQL (15.4 → upgraded to 17.4)
- ECS Fargate cluster with auto-scaling
- Application Load Balancer (public)
- Default VPC with public subnets
- CloudWatch Logs integration

## Open Questions
- None - deployment successful

## Next Steps
1. Test deployed API endpoints via ALB
2. Verify database connectivity from ECS tasks
3. Monitor CloudWatch logs for application health
4. Test invoice CRUD operations on deployed infrastructure

