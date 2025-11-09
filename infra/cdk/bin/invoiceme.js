#!/usr/bin/env node
"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || function (mod) {
    if (mod && mod.__esModule) return mod;
    var result = {};
    if (mod != null) for (var k in mod) if (k !== "default" && Object.prototype.hasOwnProperty.call(mod, k)) __createBinding(result, mod, k);
    __setModuleDefault(result, mod);
    return result;
};
Object.defineProperty(exports, "__esModule", { value: true });
require("source-map-support/register");
const dotenv = __importStar(require("dotenv"));
const cdk = __importStar(require("aws-cdk-lib"));
const invoiceme_stack_1 = require("../lib/invoiceme-stack");
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
new invoiceme_stack_1.InvoiceMeStack(app, 'InvoiceMeStack', {
    env: {
        account: account,
        region: region,
    },
    description: 'InvoiceMe API infrastructure: ECS Fargate + Aurora Serverless PostgreSQL',
});
//# sourceMappingURL=data:application/json;base64,eyJ2ZXJzaW9uIjozLCJmaWxlIjoiaW52b2ljZW1lLmpzIiwic291cmNlUm9vdCI6IiIsInNvdXJjZXMiOlsiaW52b2ljZW1lLnRzIl0sIm5hbWVzIjpbXSwibWFwcGluZ3MiOiI7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7O0FBQ0EsdUNBQXFDO0FBQ3JDLCtDQUFpQztBQUNqQyxpREFBbUM7QUFDbkMsNERBQXdEO0FBRXhELDRDQUE0QztBQUM1QyxNQUFNLENBQUMsTUFBTSxFQUFFLENBQUM7QUFFaEI7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7R0FtQkc7QUFFSCxNQUFNLEdBQUcsR0FBRyxJQUFJLEdBQUcsQ0FBQyxHQUFHLEVBQUUsQ0FBQztBQUUxQixrRUFBa0U7QUFDbEUsTUFBTSxPQUFPLEdBQUcsT0FBTyxDQUFDLEdBQUcsQ0FBQyxjQUFjLElBQUksY0FBYyxDQUFDO0FBQzdELE1BQU0sTUFBTSxHQUFHLE9BQU8sQ0FBQyxHQUFHLENBQUMsVUFBVSxJQUFJLFdBQVcsQ0FBQztBQUVyRCxJQUFJLENBQUMsT0FBTyxDQUFDLEdBQUcsQ0FBQyxjQUFjLElBQUksQ0FBQyxPQUFPLENBQUMsR0FBRyxDQUFDLFVBQVUsRUFBRSxDQUFDO0lBQzNELE9BQU8sQ0FBQyxJQUFJLENBQUMsNkVBQTZFLENBQUMsQ0FBQztBQUM5RixDQUFDO0FBRUQsSUFBSSxnQ0FBYyxDQUFDLEdBQUcsRUFBRSxnQkFBZ0IsRUFBRTtJQUN4QyxHQUFHLEVBQUU7UUFDSCxPQUFPLEVBQUUsT0FBTztRQUNoQixNQUFNLEVBQUUsTUFBTTtLQUNmO0lBQ0QsV0FBVyxFQUFFLDBFQUEwRTtDQUN4RixDQUFDLENBQUMiLCJzb3VyY2VzQ29udGVudCI6WyIjIS91c3IvYmluL2VudiBub2RlXG5pbXBvcnQgJ3NvdXJjZS1tYXAtc3VwcG9ydC9yZWdpc3Rlcic7XG5pbXBvcnQgKiBhcyBkb3RlbnYgZnJvbSAnZG90ZW52JztcbmltcG9ydCAqIGFzIGNkayBmcm9tICdhd3MtY2RrLWxpYic7XG5pbXBvcnQgeyBJbnZvaWNlTWVTdGFjayB9IGZyb20gJy4uL2xpYi9pbnZvaWNlbWUtc3RhY2snO1xuXG4vLyBMb2FkIGVudmlyb25tZW50IHZhcmlhYmxlcyBmcm9tIC5lbnYgZmlsZVxuZG90ZW52LmNvbmZpZygpO1xuXG4vKipcbiAqIENESyBBcHAgRW50cnkgUG9pbnRcbiAqIFxuICogVGhpcyBzdGFjayB3aWxsIGNyZWF0ZSBiaWxsYWJsZSBBV1MgcmVzb3VyY2VzIGluY2x1ZGluZzpcbiAqIC0gVXNlcyBkZWZhdWx0IFZQQyAobm8gTkFUIEdhdGV3YXkgY29zdHMpXG4gKiAtIEF1cm9yYSBTZXJ2ZXJsZXNzIFBvc3RncmVTUUwgY2x1c3RlciAoYXV0by1zY2FsaW5nKVxuICogLSBFQ1MgRmFyZ2F0ZSB0YXNrc1xuICogLSBBcHBsaWNhdGlvbiBMb2FkIEJhbGFuY2VyXG4gKiAtIENsb3VkV2F0Y2ggTG9nc1xuICogXG4gKiBCZWZvcmUgZGVwbG95aW5nOlxuICogMS4gQ29weSAuZW52LmV4YW1wbGUgdG8gLmVudiBhbmQgY29uZmlndXJlIHZhbHVlc1xuICogMi4gRW5zdXJlIEFXUyBDTEkgaXMgY29uZmlndXJlZCB3aXRoIGFwcHJvcHJpYXRlIGNyZWRlbnRpYWxzXG4gKiAzLiBFbnN1cmUgZGVmYXVsdCBWUEMgZXhpc3RzIGluIHRoZSB0YXJnZXQgcmVnaW9uXG4gKiA0LiBSdW4gYGNkayBib290c3RyYXAgYXdzOi8vPEFDQ09VTlRfSUQ+LzxSRUdJT04+YCBpZiB0aGlzIGlzIHlvdXIgZmlyc3QgQ0RLIGRlcGxveW1lbnRcbiAqIDUuIFZlcmlmeSB0aGUgYWNjb3VudCBJRCBhbmQgcmVnaW9uIG1hdGNoIHlvdXIgQVdTIGFjY291bnRcbiAqIFxuICogVG8gZGVwbG95OiBjZGsgZGVwbG95XG4gKiBUbyBkZXN0cm95OiBjZGsgZGVzdHJveVxuICovXG5cbmNvbnN0IGFwcCA9IG5ldyBjZGsuQXBwKCk7XG5cbi8vIEFXUyBBY2NvdW50IGFuZCBSZWdpb24gY29uZmlndXJhdGlvbiBmcm9tIGVudmlyb25tZW50IHZhcmlhYmxlc1xuY29uc3QgYWNjb3VudCA9IHByb2Nlc3MuZW52LkFXU19BQ0NPVU5UX0lEIHx8ICc5NzE0MjI3MTc0NDYnO1xuY29uc3QgcmVnaW9uID0gcHJvY2Vzcy5lbnYuQVdTX1JFR0lPTiB8fCAndXMtZWFzdC0xJztcblxuaWYgKCFwcm9jZXNzLmVudi5BV1NfQUNDT1VOVF9JRCB8fCAhcHJvY2Vzcy5lbnYuQVdTX1JFR0lPTikge1xuICBjb25zb2xlLndhcm4oJ1dhcm5pbmc6IEFXU19BQ0NPVU5UX0lEIG9yIEFXU19SRUdJT04gbm90IHNldCBpbiAuZW52IGZpbGUuIFVzaW5nIGRlZmF1bHRzLicpO1xufVxuXG5uZXcgSW52b2ljZU1lU3RhY2soYXBwLCAnSW52b2ljZU1lU3RhY2snLCB7XG4gIGVudjoge1xuICAgIGFjY291bnQ6IGFjY291bnQsXG4gICAgcmVnaW9uOiByZWdpb24sXG4gIH0sXG4gIGRlc2NyaXB0aW9uOiAnSW52b2ljZU1lIEFQSSBpbmZyYXN0cnVjdHVyZTogRUNTIEZhcmdhdGUgKyBBdXJvcmEgU2VydmVybGVzcyBQb3N0Z3JlU1FMJyxcbn0pO1xuXG4iXX0=