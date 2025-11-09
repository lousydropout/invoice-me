#!/usr/bin/env node
import 'source-map-support/register';
import * as dotenv from 'dotenv';
import * as cdk from 'aws-cdk-lib';
import { InvoiceMeStack } from '../lib/invoiceme-stack';

// Load environment variables from .env file
dotenv.config();

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
 * 1. Copy .env.example to .env and configure values
 * 2. Ensure AWS CLI is configured with appropriate credentials
 * 3. Ensure default VPC exists in the target region
 * 4. Run `cdk bootstrap aws://<ACCOUNT_ID>/<REGION>` if this is your first CDK deployment
 * 5. Verify the account ID and region match your AWS account
 * 
 * To deploy: cdk deploy
 * To destroy: cdk destroy
 */

const app = new cdk.App();

// AWS Account and Region configuration from environment variables
const account = process.env.AWS_ACCOUNT_ID || '971422717446';
const region = process.env.AWS_REGION || 'us-east-1';

if (!process.env.AWS_ACCOUNT_ID || !process.env.AWS_REGION) {
  console.warn('Warning: AWS_ACCOUNT_ID or AWS_REGION not set in .env file. Using defaults.');
}

new InvoiceMeStack(app, 'InvoiceMeStack', {
  env: {
    account: account,
    region: region,
  },
  description: 'InvoiceMe API infrastructure: ECS Fargate + Aurora Serverless PostgreSQL',
});

