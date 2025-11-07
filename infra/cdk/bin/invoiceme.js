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
const cdk = __importStar(require("aws-cdk-lib"));
const invoiceme_stack_1 = require("../lib/invoiceme-stack");
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
new invoiceme_stack_1.InvoiceMeStack(app, 'InvoiceMeStack', {
    env: {
        account: account,
        region: region,
    },
    description: 'InvoiceMe API infrastructure: ECS Fargate + Aurora Serverless PostgreSQL',
});
//# sourceMappingURL=data:application/json;base64,eyJ2ZXJzaW9uIjozLCJmaWxlIjoiaW52b2ljZW1lLmpzIiwic291cmNlUm9vdCI6IiIsInNvdXJjZXMiOlsiaW52b2ljZW1lLnRzIl0sIm5hbWVzIjpbXSwibWFwcGluZ3MiOiI7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7O0FBQ0EsdUNBQXFDO0FBQ3JDLGlEQUFtQztBQUNuQyw0REFBd0Q7QUFFeEQ7Ozs7Ozs7Ozs7Ozs7Ozs7OztHQWtCRztBQUVILE1BQU0sR0FBRyxHQUFHLElBQUksR0FBRyxDQUFDLEdBQUcsRUFBRSxDQUFDO0FBRTFCLHVDQUF1QztBQUN2QyxrRkFBa0Y7QUFDbEYsTUFBTSxPQUFPLEdBQUcsY0FBYyxDQUFDO0FBQy9CLE1BQU0sTUFBTSxHQUFHLFdBQVcsQ0FBQztBQUUzQixJQUFJLGdDQUFjLENBQUMsR0FBRyxFQUFFLGdCQUFnQixFQUFFO0lBQ3hDLEdBQUcsRUFBRTtRQUNILE9BQU8sRUFBRSxPQUFPO1FBQ2hCLE1BQU0sRUFBRSxNQUFNO0tBQ2Y7SUFDRCxXQUFXLEVBQUUsMEVBQTBFO0NBQ3hGLENBQUMsQ0FBQyIsInNvdXJjZXNDb250ZW50IjpbIiMhL3Vzci9iaW4vZW52IG5vZGVcbmltcG9ydCAnc291cmNlLW1hcC1zdXBwb3J0L3JlZ2lzdGVyJztcbmltcG9ydCAqIGFzIGNkayBmcm9tICdhd3MtY2RrLWxpYic7XG5pbXBvcnQgeyBJbnZvaWNlTWVTdGFjayB9IGZyb20gJy4uL2xpYi9pbnZvaWNlbWUtc3RhY2snO1xuXG4vKipcbiAqIENESyBBcHAgRW50cnkgUG9pbnRcbiAqIFxuICogVGhpcyBzdGFjayB3aWxsIGNyZWF0ZSBiaWxsYWJsZSBBV1MgcmVzb3VyY2VzIGluY2x1ZGluZzpcbiAqIC0gVXNlcyBkZWZhdWx0IFZQQyAobm8gTkFUIEdhdGV3YXkgY29zdHMpXG4gKiAtIEF1cm9yYSBTZXJ2ZXJsZXNzIFBvc3RncmVTUUwgY2x1c3RlciAoYXV0by1zY2FsaW5nKVxuICogLSBFQ1MgRmFyZ2F0ZSB0YXNrc1xuICogLSBBcHBsaWNhdGlvbiBMb2FkIEJhbGFuY2VyXG4gKiAtIENsb3VkV2F0Y2ggTG9nc1xuICogXG4gKiBCZWZvcmUgZGVwbG95aW5nOlxuICogMS4gRW5zdXJlIEFXUyBDTEkgaXMgY29uZmlndXJlZCB3aXRoIGFwcHJvcHJpYXRlIGNyZWRlbnRpYWxzXG4gKiAyLiBFbnN1cmUgZGVmYXVsdCBWUEMgZXhpc3RzIGluIHRoZSB0YXJnZXQgcmVnaW9uXG4gKiAzLiBSdW4gYGNkayBib290c3RyYXAgYXdzOi8vOTcxNDIyNzE3NDQ2L3VzLWVhc3QtMWAgaWYgdGhpcyBpcyB5b3VyIGZpcnN0IENESyBkZXBsb3ltZW50XG4gKiA0LiBWZXJpZnkgdGhlIGFjY291bnQgSUQgYW5kIHJlZ2lvbiBtYXRjaCB5b3VyIEFXUyBhY2NvdW50XG4gKiBcbiAqIFRvIGRlcGxveTogY2RrIGRlcGxveVxuICogVG8gZGVzdHJveTogY2RrIGRlc3Ryb3lcbiAqL1xuXG5jb25zdCBhcHAgPSBuZXcgY2RrLkFwcCgpO1xuXG4vLyBBV1MgQWNjb3VudCBhbmQgUmVnaW9uIGNvbmZpZ3VyYXRpb25cbi8vIEFjY291bnQgSUQgZXh0cmFjdGVkIGZyb20gRUNSIFVSTDogOTcxNDIyNzE3NDQ2LmRrci5lY3IudXMtZWFzdC0xLmFtYXpvbmF3cy5jb21cbmNvbnN0IGFjY291bnQgPSAnOTcxNDIyNzE3NDQ2JztcbmNvbnN0IHJlZ2lvbiA9ICd1cy1lYXN0LTEnO1xuXG5uZXcgSW52b2ljZU1lU3RhY2soYXBwLCAnSW52b2ljZU1lU3RhY2snLCB7XG4gIGVudjoge1xuICAgIGFjY291bnQ6IGFjY291bnQsXG4gICAgcmVnaW9uOiByZWdpb24sXG4gIH0sXG4gIGRlc2NyaXB0aW9uOiAnSW52b2ljZU1lIEFQSSBpbmZyYXN0cnVjdHVyZTogRUNTIEZhcmdhdGUgKyBBdXJvcmEgU2VydmVybGVzcyBQb3N0Z3JlU1FMJyxcbn0pO1xuXG4iXX0=