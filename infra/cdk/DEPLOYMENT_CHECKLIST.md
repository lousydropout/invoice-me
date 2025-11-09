# Task 9.1: AWS Aurora + Fargate Deployment Checklist

## Overview

The CDK infrastructure code is **already complete** and ready for deployment. This document outlines what needs to be configured and verified before deploying.

## Infrastructure Components

The CDK stack (`InvoiceMeStack`) provisions:

1. **Aurora Serverless v2 PostgreSQL** cluster
   - Auto-scaling: 0.5-1 ACU (Aurora Capacity Units)
   - PostgreSQL 17.4
   - Credentials stored in AWS Secrets Manager
   - Deployed in public subnets (not publicly accessible)

2. **ECS Fargate** cluster and service
   - Container image: `971422717446.dkr.ecr.us-east-1.amazonaws.com/vincent-chan/invoice-me:latest`
   - Resources: 512 MB memory, 256 CPU units
   - Logs to CloudWatch: `/ecs/invoiceme-api`

3. **Application Load Balancer (ALB)**
   - HTTP (port 80) → redirects to HTTPS
   - HTTPS (port 443) → forwards to ECS service
   - Health check: `/api/health`
   - ACM Certificate: Pre-configured ARN

4. **Security Groups**
   - ALB: Accepts HTTP/HTTPS from internet
   - ECS: Accepts traffic from ALB only (port 8080)
   - Aurora: Accepts PostgreSQL from ECS only (port 5432)

5. **CloudWatch Logs**
   - Log group: `/ecs/invoiceme-api`
   - Retention: 7 days (for cost savings)

## Pre-Deployment Checklist

### ✅ 1. Configure Environment Variables

**Action Required:**
- [ ] Copy `.env.example` to `.env`:
  ```bash
  cd infra/cdk
  cp .env.example .env
  ```
- [ ] Edit `.env` and configure the following values:
  - `AWS_ACCOUNT_ID`: Your AWS account ID (default: `971422717446`)
  - `AWS_REGION`: Your AWS region (default: `us-east-1`)
  - `DOMAIN_NAME`: Your domain name (default: `invoice-me.vincentchan.cloud`)
  - `ECR_REPOSITORY_NAME`: Your ECR repository name (default: `vincent-chan/invoice-me`)
  - `ECR_IMAGE_TAG`: Docker image tag to deploy (default: `latest`)
  - `ACM_CERTIFICATE_ARN`: Your ACM certificate ARN for HTTPS (required)

**Note**: The `.env` file is gitignored and will not be committed to version control.

### ✅ 2. AWS Account Configuration

**Action Required:**
- [ ] Verify `AWS_ACCOUNT_ID` in `.env` matches your AWS account
- [ ] Verify `AWS_REGION` in `.env` is correct for your deployment

### ✅ 3. AWS CLI Configuration

**Action Required:**
- [ ] Install AWS CLI: `aws --version`
- [ ] Configure credentials: `aws configure`
- [ ] Verify credentials: `aws sts get-caller-identity`
- [ ] Ensure credentials have permissions for:
  - RDS (Aurora Serverless)
  - ECS (Fargate, clusters, services)
  - EC2 (VPC, security groups)
  - ELB (Application Load Balancer)
  - CloudWatch Logs
  - Secrets Manager
  - ECR (read access to repository)

### ✅ 3. Default VPC Verification

**Action Required:**
- [ ] Verify default VPC exists in target region:
  ```bash
  aws ec2 describe-vpcs --filters "Name=isDefault,Values=true" --region us-east-1
  ```
- [ ] If default VPC doesn't exist, create one or modify CDK stack to use custom VPC

### ✅ 4. CDK Bootstrap (First Time Only)

**Action Required:**
- [ ] Bootstrap CDK if first deployment in account/region:
  ```bash
  cd infra/cdk
  # Load .env and bootstrap
  source .env 2>/dev/null || true
  cdk bootstrap aws://${AWS_ACCOUNT_ID:-971422717446}/${AWS_REGION:-us-east-1}
  
  # Or specify directly
  cdk bootstrap aws://971422717446/us-east-1
  ```

### ✅ 5. ECR Repository Verification

**Configuration:**
- Repository: Set via `ECR_REPOSITORY_NAME` in `.env` (default: `vincent-chan/invoice-me`)
- Image tag: Set via `ECR_IMAGE_TAG` in `.env` (default: `latest`)

**Action Required:**
- [ ] Verify ECR repository exists (use values from your `.env`):
  ```bash
  # Load .env variables
  source .env 2>/dev/null || true
  aws ecr describe-repositories \
    --repository-names ${ECR_REPOSITORY_NAME:-vincent-chan/invoice-me} \
    --region ${AWS_REGION:-us-east-1}
  ```
- [ ] Verify image exists with the specified tag:
  ```bash
  aws ecr describe-images \
    --repository-name ${ECR_REPOSITORY_NAME:-vincent-chan/invoice-me} \
    --image-ids imageTag=${ECR_IMAGE_TAG:-latest} \
    --region ${AWS_REGION:-us-east-1}
  ```
- [ ] If repository/image doesn't exist, build and push:
  ```bash
  # Build Docker image
  cd backend
  docker build -t vincent-chan/invoice-me:latest .
  
  # Tag for ECR
  docker tag vincent-chan/invoice-me:latest \
    971422717446.dkr.ecr.us-east-1.amazonaws.com/vincent-chan/invoice-me:latest
  
  # Login to ECR
  aws ecr get-login-password --region us-east-1 | \
    docker login --username AWS --password-stdin \
    971422717446.dkr.ecr.us-east-1.amazonaws.com
  
  # Push image
  docker push 971422717446.dkr.ecr.us-east-1.amazonaws.com/vincent-chan/invoice-me:latest
  ```

### ✅ 6. ACM Certificate Verification

**Configuration:**
- Certificate ARN: Set via `ACM_CERTIFICATE_ARN` in `.env` (required)

**Action Required:**
- [ ] Verify certificate ARN is set in `.env` file
- [ ] Verify certificate exists and is validated:
  ```bash
  # Load .env variables
  source .env 2>/dev/null || true
  aws acm describe-certificate \
    --certificate-arn ${ACM_CERTIFICATE_ARN} \
    --region ${AWS_REGION:-us-east-1}
  ```
- [ ] If certificate doesn't exist or domain differs:
  - Create new certificate in ACM
  - Update ARN in `lib/invoiceme-stack.ts` (line 256)
  - Or use HTTP only (remove HTTPS listener)

### ✅ 7. Environment Variables Configuration

**Already Configured in CDK Stack:**

The ECS task definition automatically sets:
- `DB_HOST`: Aurora cluster endpoint (auto-populated)
- `DB_PORT`: `5432`
- `DB_NAME`: `invoiceme`
- `DB_USER`: From Secrets Manager (Aurora-generated)
- `DB_PASSWORD`: From Secrets Manager (Aurora-generated)
- `SPRING_PROFILES_ACTIVE`: `prod`

**BasicAuth Credentials:**

BasicAuth credentials can be configured in two ways:

1. **Via `.env` file (simpler, but less secure):**
   - Set `SPRING_SECURITY_USER_NAME` in `.env`
   - Set `SPRING_SECURITY_USER_PASSWORD` in `.env`
   - These will be passed as environment variables to the container

2. **Via AWS Secrets Manager (recommended for production):**
   - Create a secret in Secrets Manager with keys `username` and `password`
   - Update the CDK stack to reference the secret (see commented code in `lib/invoiceme-stack.ts`)
   - This is more secure as credentials are not in environment variables

**Action Required:**
- [ ] Configure BasicAuth credentials in `.env` file:
  ```bash
  SPRING_SECURITY_USER_NAME=admin
  SPRING_SECURITY_USER_PASSWORD=your-secure-password
  ```
- [ ] Or (recommended for production) store credentials in AWS Secrets Manager and update CDK stack

### ✅ 8. CDK Dependencies

**Action Required:**
- [ ] Install CDK dependencies:
  ```bash
  cd infra/cdk
  npm install
  ```

## Deployment Steps

### Step 1: Review Changes

```bash
cd infra/cdk
cdk diff
```

Review the CloudFormation changes that will be created.

### Step 2: Synthesize Template (Optional)

```bash
cdk synth
```

This generates the CloudFormation template without deploying.

### Step 3: Deploy Stack

```bash
cdk deploy
```

**Expected Duration:** 10-15 minutes (primarily Aurora cluster creation)

**What Happens:**
1. Aurora Serverless v2 cluster is created
2. Database credentials are generated and stored in Secrets Manager
3. ECS cluster is created
4. CloudWatch log group is created
5. ECS task definition is registered
6. Application Load Balancer is created
7. ECS service is created and tasks start
8. Stack outputs are displayed

### Step 4: Verify Deployment

After deployment completes, the stack outputs will show:

- **AlbDnsName**: DNS name of the load balancer
- **ApiUrl**: Base URL for the API
- **HealthCheckUrl**: Health check endpoint URL

**Test Health Endpoint:**
```bash
# Get ALB DNS name from stack outputs
ALB_DNS=$(aws cloudformation describe-stacks \
  --stack-name InvoiceMeStack \
  --region us-east-1 \
  --query 'Stacks[0].Outputs[?OutputKey==`AlbDnsName`].OutputValue' \
  --output text)

# Test health endpoint
curl https://${ALB_DNS}/api/health
```

Expected response:
```json
{"status":"ok"}
```

**Test API Endpoint (with BasicAuth):**
```bash
curl -u admin:admin https://${ALB_DNS}/api/invoices
```

## Post-Deployment Verification

### ✅ 1. ECS Service Status

```bash
aws ecs describe-services \
  --cluster invoiceme-cluster \
  --services invoiceme-service \
  --region us-east-1 \
  --query 'services[0].{status:status,runningCount:runningCount,desiredCount:desiredCount}'
```

Expected: `runningCount` should equal `desiredCount` (1)

### ✅ 2. CloudWatch Logs

```bash
aws logs tail /ecs/invoiceme-api --follow --region us-east-1
```

Check for:
- Application startup logs
- Database connection success
- No errors or exceptions

### ✅ 3. Database Connectivity

```bash
# Get database endpoint
DB_ENDPOINT=$(aws rds describe-db-clusters \
  --db-cluster-identifier invoiceme-stack-invoicemedatabase* \
  --region us-east-1 \
  --query 'DBClusters[0].Endpoint' \
  --output text)

# Get database credentials from Secrets Manager
DB_CREDS=$(aws secretsmanager get-secret-value \
  --secret-id invoiceme/rds/credentials \
  --region us-east-1 \
  --query SecretString \
  --output text)

# Test connection (requires psql or use AWS RDS Query Editor)
echo $DB_CREDS | jq .
```

### ✅ 4. ALB Target Health

```bash
# Get target group ARN
TG_ARN=$(aws elbv2 describe-target-groups \
  --names invoiceme-stack-invoicemetargetgroup* \
  --region us-east-1 \
  --query 'TargetGroups[0].TargetGroupArn' \
  --output text)

# Check target health
aws elbv2 describe-target-health \
  --target-group-arn $TG_ARN \
  --region us-east-1
```

Expected: Targets should be `healthy`

### ✅ 5. API Functionality Test

```bash
# Test health endpoint
curl https://${ALB_DNS}/api/health

# Test invoice creation (requires BasicAuth)
curl -u admin:admin \
  -X POST https://${ALB_DNS}/api/invoices \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "test-id",
    "lineItems": [{"description": "Test", "quantity": 1, "unitPriceAmount": 10, "unitPriceCurrency": "USD"}],
    "issueDate": "2025-01-01",
    "dueDate": "2025-01-31",
    "taxRate": 0.1
  }'
```

## Cost Estimation

**Development/Testing Environment:**
- Aurora Serverless v2: ~$0.12/hour (0.5-1 ACU, scales down when idle)
- ECS Fargate: ~$0.04/hour (512 MB, 256 CPU)
- Application Load Balancer: ~$0.0225/hour + data transfer
- CloudWatch Logs: ~$0.50/GB stored

**Monthly Estimate (24/7):**
- Aurora: ~$87/month (if always running at 0.5 ACU)
- ECS: ~$29/month
- ALB: ~$16/month
- **Total: ~$132/month** (plus data transfer and CloudWatch)

**Cost Savings:**
- Using default VPC eliminates NAT Gateway costs (~$64/month)
- Aurora Serverless v2 auto-scales down when idle
- Consider stopping resources during off-hours for additional savings

## Troubleshooting

### ECS Tasks Not Starting

1. **Check CloudWatch Logs:**
   ```bash
   aws logs tail /ecs/invoiceme-api --follow
   ```

2. **Check ECS Service Events:**
   ```bash
   aws ecs describe-services \
     --cluster invoiceme-cluster \
     --services invoiceme-service \
     --region us-east-1 \
     --query 'services[0].events[0:5]'
   ```

3. **Common Issues:**
   - ECR image not found → Build and push image
   - Secrets Manager access denied → Check IAM permissions
   - Database connection failed → Check security groups and Aurora endpoint

### Database Connection Issues

1. **Verify Aurora Cluster Status:**
   ```bash
   aws rds describe-db-clusters \
     --db-cluster-identifier invoiceme-stack-invoicemedatabase* \
     --region us-east-1
   ```

2. **Check Security Group Rules:**
   - ECS security group should allow outbound to Aurora
   - Aurora security group should allow inbound from ECS on port 5432

3. **Test Connection from ECS Task:**
   - Use AWS Systems Manager Session Manager to connect to ECS task
   - Or check CloudWatch Logs for connection errors

### Health Check Failures

1. **Verify Health Endpoint:**
   ```bash
   curl https://${ALB_DNS}/api/health
   ```

2. **Check Target Group Health:**
   ```bash
   aws elbv2 describe-target-health \
     --target-group-arn $TG_ARN \
     --region us-east-1
   ```

3. **Common Issues:**
   - Application not listening on port 8080
   - Health endpoint path incorrect
   - Security group blocking ALB → ECS traffic

## Cleanup

To destroy all resources and avoid ongoing charges:

```bash
cd infra/cdk
cdk destroy
```

**Warning:** This will delete:
- Aurora cluster and all data
- All ECS tasks and services
- Load balancer
- CloudWatch log groups
- Security groups

**Note:** If `deletionProtection: true` is set on Aurora, disable it first:
```bash
aws rds modify-db-cluster \
  --db-cluster-identifier <cluster-id> \
  --no-deletion-protection \
  --region us-east-1
```

## Summary

**What's Already Configured:**
- ✅ Complete CDK stack code
- ✅ Aurora Serverless v2 configuration
- ✅ ECS Fargate service configuration
- ✅ Application Load Balancer setup
- ✅ Security groups and networking
- ✅ CloudWatch logging
- ✅ Database credentials via Secrets Manager
- ✅ Environment variables for database connection
- ✅ Environment variable configuration via `.env` file

**What Needs to Be Done:**
- ⚠️ Copy `.env.example` to `.env` and configure values
- ⚠️ Verify AWS account ID and region in `.env`
- ⚠️ Configure AWS CLI credentials
- ⚠️ Bootstrap CDK (first time only)
- ⚠️ Verify/upload ECR image
- ⚠️ Verify ACM certificate ARN in `.env`
- ⚠️ Deploy stack via `cdk deploy`
- ⚠️ Verify deployment and test endpoints

**Estimated Time:** 30-45 minutes (including Aurora cluster creation)

