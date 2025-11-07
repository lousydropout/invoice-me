# InvoiceMe CDK Infrastructure

AWS CDK TypeScript infrastructure for deploying InvoiceMe API to ECS Fargate with Aurora Serverless PostgreSQL.

## Architecture

This stack provisions:

- **Default VPC** (uses existing default VPC with public subnets)
- **Aurora Serverless v2 PostgreSQL** cluster in public subnet (auto-scaling, not publicly accessible)
- **ECS Fargate** cluster and service running the InvoiceMe API container in public subnets
- **Application Load Balancer** (ALB) for public HTTP access
- **Security Groups** configured for secure communication:
  - ALB accepts HTTP traffic from internet
  - ECS tasks accept traffic from ALB only
  - Aurora accepts PostgreSQL traffic from ECS tasks only
- **Public IPs** assigned to ECS tasks for ECR image pulls (no NAT Gateways needed)

## Prerequisites

Before deploying, ensure you have:

1. **Node.js** ≥ 18.0.0 installed
   ```bash
   node --version
   ```

2. **AWS CDK CLI** v2 installed globally
   ```bash
   npm install -g aws-cdk
   cdk --version
   ```

3. **AWS CLI** configured with appropriate credentials
   ```bash
   aws configure
   aws sts get-caller-identity  # Verify your credentials
   ```

4. **AWS Account** with:
   - Default VPC available (created automatically in most AWS accounts)
   - Permissions to create:
     - Aurora Serverless clusters
     - ECS clusters, services, and task definitions
     - Application Load Balancers
     - Security Groups
     - CloudWatch Log Groups
     - Secrets Manager secrets
     - IAM roles and policies

## Deployment Steps

### 1. Install Dependencies

```bash
cd infra/cdk
npm install
```

### 2. Verify Configuration

Before deploying, verify the account ID and region in `bin/invoiceme.ts`:

- Account ID: `971422717446` (extracted from ECR URL)
- Region: `us-east-1`

**Important**: Ensure these match your AWS account before proceeding.

### 3. Bootstrap CDK (First Time Only)

If this is your first CDK deployment in this account/region, bootstrap CDK:

```bash
cdk bootstrap aws://971422717446/us-east-1
```

This creates the CDK bootstrap stack with an S3 bucket and IAM roles needed for deployments.

### 4. Synthesize CloudFormation Template (Optional)

Preview the CloudFormation template that will be created:

```bash
cdk synth
```

### 5. Review Changes

Before deploying, review what will be created:

```bash
cdk diff
```

### 6. Deploy the Stack

Deploy the infrastructure:

```bash
cdk deploy
```

**Note**: This will create billable AWS resources:
- Aurora Serverless v2 cluster (pay per ACU-hour, auto-scales 0.5-1 ACU for dev)
- ECS Fargate tasks (pay per use)
- Application Load Balancer (pay per use)
- CloudWatch Logs (pay per GB stored/transferred)

**Cost Savings**: 
- Using the default VPC eliminates NAT Gateway costs (~$64/month savings)
- Aurora Serverless v2 scales down when idle, reducing costs during low usage

The deployment will take approximately 10-15 minutes, primarily due to Aurora cluster creation.

### 7. Access the API

After deployment completes, the stack outputs will display:

- **AlbDnsName**: DNS name of the load balancer
- **ApiUrl**: Base URL for the API (e.g., `http://<alb-dns>/api`)
- **HealthCheckUrl**: Health check endpoint (e.g., `http://<alb-dns>/api/health`)

Test the health endpoint:

```bash
curl http://<alb-dns-name>/api/health
```

Expected response:
```json
{"status":"ok"}
```

## Configuration

### Environment Variables

The ECS task is configured with the following environment variables:

- `DB_HOST`: Automatically set to Aurora cluster endpoint
- `DB_PORT`: `5432`
- `DB_NAME`: `invoiceme`
- `DB_USER`: Retrieved from AWS Secrets Manager (Aurora-generated secret)
- `DB_PASSWORD`: Retrieved from AWS Secrets Manager (Aurora-generated secret)
- `SPRING_PROFILES_ACTIVE`: `prod`

### Database Credentials

Aurora credentials are automatically generated and stored in AWS Secrets Manager:
- Secret name: `invoiceme/rds/credentials`
- The ECS task has permissions to read this secret

To retrieve credentials manually:

```bash
aws secretsmanager get-secret-value \
  --secret-id invoiceme/rds/credentials \
  --region us-east-1 \
  --query SecretString \
  --output text | jq .
```

### Container Image

The stack uses the ECR image:
```
971422717446.dkr.ecr.us-east-1.amazonaws.com/vincent-chan/invoice-me:latest
```

Ensure this image exists and is accessible from the ECS task execution role.

## Cleanup

To destroy all resources and avoid ongoing charges:

```bash
cdk destroy
```

**Warning**: This will delete:
- Aurora cluster and all data (unless deletion protection is enabled)
- All ECS tasks and services
- Load balancer
- VPC and networking resources
- CloudWatch log groups

**Note**: If you set `deletionProtection: true` on the Aurora cluster, you must disable it manually before destroying the stack.

## Troubleshooting

### ECS Tasks Not Starting

1. Check CloudWatch Logs:
   ```bash
   aws logs tail /ecs/invoiceme-api --follow
   ```

2. Verify ECR image exists and is accessible:
   ```bash
   aws ecr describe-images \
     --repository-name vincent-chan/invoice-me \
     --region us-east-1
   ```

3. Check ECS service events:
   ```bash
   aws ecs describe-services \
     --cluster invoiceme-cluster \
     --services invoiceme-service \
     --region us-east-1 \
     --query 'services[0].events'
   ```

### Database Connection Issues

1. Verify Aurora cluster is running:
   ```bash
   aws rds describe-db-clusters \
     --db-cluster-identifier invoiceme-stack-invoicemedatabase* \
     --region us-east-1
   ```

2. Check security group rules allow traffic from ECS security group to Aurora on port 5432

3. Verify ECS task can access Secrets Manager:
   ```bash
   aws iam get-role-policy \
     --role-name <task-execution-role-name> \
     --policy-name <policy-name>
   ```

### Health Check Failures

1. Verify the health endpoint is accessible:
   ```bash
   curl http://<alb-dns>/api/health
   ```

2. Check target group health:
   ```bash
   aws elbv2 describe-target-health \
     --target-group-arn <target-group-arn> \
     --region us-east-1
   ```

3. Ensure the application is listening on port 8080

## Security Considerations

- Aurora cluster is in public subnet but not publicly accessible (security groups restrict access)
- ECS tasks run in public subnets with public IPs (needed for ECR access)
- Security groups follow least privilege principles:
  - Aurora only accepts traffic from ECS security group
  - ECS only accepts traffic from ALB security group
  - ALB accepts HTTP traffic from internet
- Database credentials are stored in AWS Secrets Manager
- All traffic between ECS and Aurora is within the VPC

## Cost Optimization

For development/testing, consider:

1. Using default VPC eliminates NAT Gateway costs (~$64/month savings) ✅
2. Aurora Serverless v2 auto-scales and can scale down to 0.5 ACU when idle ✅
3. Set up scheduled scaling to stop/start resources during off-hours
4. Enable Aurora deletion protection only in production

## Manual Actions Required

Before first deployment:

1. ✅ Verify AWS account ID in `bin/invoiceme.ts` matches your account
2. ✅ Ensure AWS CLI is configured with appropriate credentials
3. ✅ Run `cdk bootstrap` if this is your first CDK deployment
4. ✅ Review `cdk diff` output before deploying
5. ✅ Confirm you understand the billable resources being created

## Stack Outputs

After deployment, the following outputs are available:

- `AlbDnsName`: DNS name of the Application Load Balancer
- `ApiUrl`: Base URL for the InvoiceMe API
- `HealthCheckUrl`: Health check endpoint URL

### Current Deployment

**Stack Name**: `InvoiceMeStack`  
**Region**: `us-east-1`

**Deployed Endpoints**:
- **ALB DNS**: `Invoic-Invoi-Gz9hboCJo4NZ-1292342723.us-east-1.elb.amazonaws.com`
- **API URL**: `http://Invoic-Invoi-Gz9hboCJo4NZ-1292342723.us-east-1.elb.amazonaws.com/api`
- **Health Check**: `http://Invoic-Invoi-Gz9hboCJo4NZ-1292342723.us-east-1.elb.amazonaws.com/api/health`

View outputs:

```bash
aws cloudformation describe-stacks \
  --stack-name InvoiceMeStack \
  --region us-east-1 \
  --query 'Stacks[0].Outputs'
```

## Integration Testing

Integration tests are available to test the deployed API using the stack's `ApiUrl` output.

### Prerequisites

1. Install dependencies:
   ```bash
   npm install
   ```

2. Ensure the stack is deployed:
   ```bash
   cdk deploy
   ```

3. AWS credentials must be configured (same as for CDK deployment)

### Running Tests

Run all integration tests:

```bash
npm run test:integration
```

Or run all tests:

```bash
npm test
```

### What the Tests Do

The integration tests (`test/integration.test.ts`):

1. **Fetch API URL from Stack Outputs**: Automatically retrieves the `ApiUrl` and `HealthCheckUrl` from the CloudFormation stack outputs
2. **Test Health Endpoint**: Verifies the `/api/health` endpoint returns 200 OK
3. **Test Invoice CRUD Operations**:
   - Create invoice (POST)
   - List all invoices (GET)
   - Get invoice by ID (GET)
   - Update invoice (PATCH)
   - Delete invoice (DELETE)
4. **Test Error Handling**: Verifies 404 and 400 error responses

### Test Structure

```typescript
// Tests automatically fetch ApiUrl from InvoiceMeStack outputs
const outputs = await getStackOutputs();
apiUrl = outputs.apiUrl; // http://<alb-dns>/api
healthCheckUrl = outputs.healthCheckUrl; // http://<alb-dns>/api/health
```

The tests use the AWS SDK to query CloudFormation stack outputs, so they always test against the currently deployed stack.

