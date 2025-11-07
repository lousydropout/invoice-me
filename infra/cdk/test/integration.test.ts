import { execSync } from 'child_process';

/**
 * Integration tests for InvoiceMe API deployed via CDK
 * 
 * These tests:
 * 1. Fetch the API URL from the CDK stack outputs
 * 2. Test all API endpoints against the deployed infrastructure
 * 
 * Prerequisites:
 * - Stack must be deployed: `cdk deploy`
 * - AWS credentials configured
 * - Stack name: InvoiceMeStack
 * - AWS CLI installed
 */

const STACK_NAME = 'InvoiceMeStack';
const REGION = 'us-east-1';
let apiUrl: string;
let healthCheckUrl: string;

/**
 * Fetch stack outputs from CloudFormation using AWS CLI
 */
async function getStackOutputs(): Promise<{ apiUrl: string; healthCheckUrl: string }> {
  try {
    const command = `aws cloudformation describe-stacks --stack-name ${STACK_NAME} --region ${REGION} --query 'Stacks[0].Outputs' --output json`;
    const output = execSync(command, { encoding: 'utf-8' });
    const outputs = JSON.parse(output) as Array<{ OutputKey: string; OutputValue: string }>;

    const apiUrlOutput = outputs.find((o) => o.OutputKey === 'ApiUrl');
    const healthCheckUrlOutput = outputs.find((o) => o.OutputKey === 'HealthCheckUrl');

    if (!apiUrlOutput || !healthCheckUrlOutput) {
      throw new Error(
        `Stack outputs not found. Expected ApiUrl and HealthCheckUrl. ` +
        `Found outputs: ${outputs.map((o) => o.OutputKey).join(', ')}`
      );
    }

    return {
      apiUrl: apiUrlOutput.OutputValue,
      healthCheckUrl: healthCheckUrlOutput.OutputValue,
    };
  } catch (error: any) {
    if (error.message.includes('does not exist')) {
      throw new Error(`Stack ${STACK_NAME} not found. Deploy it first with: cdk deploy`);
    }
    throw error;
  }
}

/**
 * Make HTTP request helper
 */
async function httpRequest(
  url: string,
  options: RequestInit = {}
): Promise<Response> {
  const response = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
  });
  return response;
}

// Jest globals are available in the test environment
describe('InvoiceMe API Integration Tests', () => {
  beforeAll(async () => {
    // Fetch API URL from stack outputs
    const outputs = await getStackOutputs();
    apiUrl = outputs.apiUrl;
    healthCheckUrl = outputs.healthCheckUrl;
    console.log(`Testing API at: ${apiUrl}`);
    console.log(`Health check URL: ${healthCheckUrl}`);
  }, 30000); // 30 second timeout for stack lookup

  describe('Health Check', () => {
    test('should return 200 OK', async () => {
      const response = await httpRequest(healthCheckUrl);
      expect(response.status).toBe(200);
    });

    test('should return valid health status', async () => {
      const response = await httpRequest(healthCheckUrl);
      const data = await response.json() as { status: string };
      expect(data).toHaveProperty('status');
      expect(data.status).toBe('ok');
    });
  });

  describe('Invoice CRUD Operations', () => {
    let createdInvoiceId: string;

    test('POST /api/invoices - should create a new invoice', async () => {
      const invoiceData = {
        customerName: 'Test Customer',
        amount: '99.99',
      };

      const response = await httpRequest(`${apiUrl}/invoices`, {
        method: 'POST',
        body: JSON.stringify(invoiceData),
      });

      expect(response.status).toBe(201);
      const invoice = await response.json() as { id: string; customerName: string; amount: number | string; status: string };
      expect(invoice).toHaveProperty('id');
      expect(invoice.customerName).toBe(invoiceData.customerName);
      // Amount might be returned as number or string, so convert to string for comparison
      expect(String(invoice.amount)).toBe(invoiceData.amount);
      expect(invoice.status).toBe('DRAFT');
      createdInvoiceId = invoice.id;
    });

    test('GET /api/invoices - should list all invoices', async () => {
      const response = await httpRequest(`${apiUrl}/invoices`);
      expect(response.status).toBe(200);
      const invoices = await response.json() as Array<unknown>;
      expect(Array.isArray(invoices)).toBe(true);
      expect(invoices.length).toBeGreaterThan(0);
    });

    test('GET /api/invoices/{id} - should get invoice by ID', async () => {
      expect(createdInvoiceId).toBeDefined();
      const response = await httpRequest(`${apiUrl}/invoices/${createdInvoiceId}`);
      expect(response.status).toBe(200);
      const invoice = await response.json() as { id: string; customerName: string };
      expect(invoice.id).toBe(createdInvoiceId);
      expect(invoice.customerName).toBe('Test Customer');
    });

    test('PATCH /api/invoices/{id} - should update invoice', async () => {
      expect(createdInvoiceId).toBeDefined();
      const updateData = {
        status: 'SENT',
        amount: '149.99',
      };

      const response = await httpRequest(`${apiUrl}/invoices/${createdInvoiceId}`, {
        method: 'PATCH',
        body: JSON.stringify(updateData),
      });

      expect(response.status).toBe(200);
      const invoice = await response.json() as { status: string; amount: number | string; customerName: string };
      expect(invoice.status).toBe('SENT');
      // Amount might be returned as number or string, so convert to string for comparison
      expect(String(invoice.amount)).toBe('149.99');
      expect(invoice.customerName).toBe('Test Customer'); // Should remain unchanged
    });

    test('DELETE /api/invoices/{id} - should delete invoice', async () => {
      expect(createdInvoiceId).toBeDefined();
      const response = await httpRequest(`${apiUrl}/invoices/${createdInvoiceId}`, {
        method: 'DELETE',
      });

      expect(response.status).toBe(204);
    });

    test('GET /api/invoices/{id} - should return 404 after deletion', async () => {
      expect(createdInvoiceId).toBeDefined();
      const response = await httpRequest(`${apiUrl}/invoices/${createdInvoiceId}`);
      expect(response.status).toBe(404);
    });
  });

  describe('Error Handling', () => {
    test('GET /api/invoices/{id} - should return 404 for non-existent invoice', async () => {
      const fakeId = '00000000-0000-0000-0000-000000000000';
      const response = await httpRequest(`${apiUrl}/invoices/${fakeId}`);
      expect(response.status).toBe(404);
    });

    test('POST /api/invoices - should return 400 for invalid data', async () => {
      const invalidData = {
        customerName: '', // Empty name should fail validation
        amount: '-10', // Negative amount should fail validation
      };

      const response = await httpRequest(`${apiUrl}/invoices`, {
        method: 'POST',
        body: JSON.stringify(invalidData),
      });

      expect(response.status).toBe(400);
    });
  });
});

