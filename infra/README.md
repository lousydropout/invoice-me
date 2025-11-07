# Infrastructure Setup Guide

This directory contains templates and configuration files for deploying InvoiceMe API to AWS ECS Fargate.

## Files

- `ecs-task-def.json` - ECS Fargate task definition template

## Manual Setup Steps

### Prerequisites

1. **AWS Account** with appropriate permissions
2. **RDS PostgreSQL 17** database instance
3. **ECR Repository** created (see GitHub Actions workflow setup)
4. **IAM Roles** configured for ECS tasks

### 1. Update ECS Task Definition

Before using `ecs-task-def.json`, replace the following placeholders:

- `ACCOUNT_ID` - Your 12-digit AWS account ID
- `REGION` - Your AWS region (e.g., `us-east-1`, `eu-west-1`)
- `your-rds-endpoint.region.rds.amazonaws.com` - Your RDS endpoint
- Database credentials (DB_NAME, DB_USER) - Update as needed

### 2. Create IAM Roles

#### Task Execution Role (`ecsTaskExecutionRole`)

Required permissions:
- `ecr:GetAuthorizationToken`
- `ecr:BatchCheckLayerAvailability`
- `ecr:GetDownloadUrlForLayer`
- `ecr:BatchGetImage`
- `logs:CreateLogStream`
- `logs:PutLogEvents`
- `secretsmanager:GetSecretValue` (if using Secrets Manager for DB password)

#### Task Role (`ecsTaskRole`)

Add permissions based on your application needs (e.g., S3 access, other AWS services).

### 3. Create CloudWatch Log Group

```bash
aws logs create-log-group --log-group-name /ecs/invoiceme-api --region REGION
```

### 4. Store Database Password in Secrets Manager (Optional but Recommended)

```bash
aws secretsmanager create-secret \
  --name invoiceme/db-password \
  --secret-string "your-database-password" \
  --region REGION
```

If not using Secrets Manager, you can add `DB_PASSWORD` directly to the `environment` array in the task definition (not recommended for production).

### 5. Create ECS Cluster

```bash
aws ecs create-cluster --cluster-name invoiceme-cluster --region REGION
```

### 6. Register Task Definition

```bash
aws ecs register-task-definition \
  --cli-input-json file://ecs-task-def.json \
  --region REGION
```

### 7. Create ECS Service

After registering the task definition, create an ECS service:

```bash
aws ecs create-service \
  --cluster invoiceme-cluster \
  --service-name invoiceme-api \
  --task-definition invoiceme-api \
  --desired-count 1 \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[subnet-xxx],securityGroups=[sg-xxx],assignPublicIp=ENABLED}" \
  --region REGION
```

**Note**: Replace `subnet-xxx` and `sg-xxx` with your actual subnet IDs and security group IDs. Ensure:
- Security group allows inbound traffic on port 8080 (or configure via ALB)
- Subnets are in the same VPC as your RDS instance
- RDS security group allows inbound PostgreSQL traffic from ECS security group

### 8. Network Configuration

- Ensure ECS tasks can reach RDS (same VPC or VPC peering)
- Configure security groups appropriately
- Consider using Application Load Balancer (ALB) for public access instead of assigning public IPs

## Security Best Practices

1. **Never commit** actual credentials or account IDs to version control
2. Use AWS Secrets Manager for sensitive values like database passwords
3. Use IAM roles with least privilege principles
4. Enable VPC Flow Logs for network monitoring
5. Use private subnets for ECS tasks when possible
6. Enable encryption at rest for RDS and ECR

