#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { InvoiceMeStack } from '../lib/invoiceme-stack';

/**
 * CDK App Entry Point
 * 
 * This stack will create billable AWS resources including:
 * - Uses default VPC (no NAT Gateway costs)
 * - Aurora Serverless PostgreSQL cluster (auto-scaling)
 * - ECS Fargate tasks
 * - Application Load Balancer
 * - CloudWatch Logs
 * 
 * Before deploying:
 * 1. Ensure AWS CLI is configured with appropriate credentials
 * 2. Ensure default VPC exists in the target region
 * 3. Run `cdk bootstrap aws://971422717446/us-east-1` if this is your first CDK deployment
 * 4. Verify the account ID and region match your AWS account
 * 
 * To deploy: cdk deploy
 * To destroy: cdk destroy
 */

const app = new cdk.App();

// AWS Account and Region configuration
// Account ID extracted from ECR URL: 971422717446.dkr.ecr.us-east-1.amazonaws.com
const account = '971422717446';
const region = 'us-east-1';

new InvoiceMeStack(app, 'InvoiceMeStack', {
  env: {
    account: account,
    region: region,
  },
  description: 'InvoiceMe API infrastructure: ECS Fargate + Aurora Serverless PostgreSQL',
});

