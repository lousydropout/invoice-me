#!/bin/bash

set -e

BASE_URL="http://localhost:8080"
AUTH="admin:admin"

echo "=== Payment API Validation ==="
echo ""

# Step 0: Clear database tables to avoid duplicate key issues
echo "0. Clearing database tables..."
docker compose exec -T postgres psql -U invoiceme -d invoiceme <<EOF
TRUNCATE TABLE payments CASCADE;
TRUNCATE TABLE line_items CASCADE;
TRUNCATE TABLE invoices CASCADE;
TRUNCATE TABLE customers CASCADE;
EOF
echo "   Database cleared"
echo ""

# Step 1: Create customer
echo "1. Creating customer..."
CUSTOMER_RESPONSE=$(curl -s -u "$AUTH" -X POST "$BASE_URL/api/customers" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Customer '$(date +%s)'",
    "email": "test'$(date +%s)'@example.com",
    "address": {
      "street": "123 Main St",
      "city": "City",
      "postalCode": "12345",
      "country": "US"
    },
    "phone": "555-1234",
    "paymentTerms": "NET_30"
  }')

CUSTOMER_ID=$(echo "$CUSTOMER_RESPONSE" | grep -o '"id":"[^"]*"' | cut -d'"' -f4)
echo "   Customer ID: $CUSTOMER_ID"
echo ""

# Step 2: Create invoice
echo "2. Creating invoice..."
INVOICE_RESPONSE=$(curl -s -u "$AUTH" -X POST "$BASE_URL/api/invoices" \
  -H "Content-Type: application/json" \
  -d "{
    \"customerId\": \"$CUSTOMER_ID\",
    \"lineItems\": [{
      \"description\": \"Design\",
      \"quantity\": 5,
      \"unitPriceAmount\": 100,
      \"unitPriceCurrency\": \"USD\"
    }],
    \"issueDate\": \"2025-11-08\",
    \"dueDate\": \"2025-11-15\",
    \"taxRate\": 0.10
  }")

INVOICE_ID=$(echo "$INVOICE_RESPONSE" | grep -o '"id":"[^"]*"' | cut -d'"' -f4)
echo "   Invoice ID: $INVOICE_ID"
echo ""

# Step 3: Send invoice
echo "3. Sending invoice..."
SEND_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -u "$AUTH" -X POST "$BASE_URL/api/invoices/$INVOICE_ID/send")
echo "   Send Status: $SEND_STATUS"
echo ""

# Step 4: Record payment
echo "4. Recording payment (250.00)..."
PAYMENT_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -u "$AUTH" -X POST "$BASE_URL/api/invoices/$INVOICE_ID/payments" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 250.00,
    "currency": "USD",
    "paymentDate": "2025-11-08",
    "method": "BANK_TRANSFER",
    "reference": "TXN001"
  }')
echo "   Payment Status: $PAYMENT_STATUS"
echo ""

# Step 5: Verify invoice
echo "5. Verifying invoice..."
INVOICE_DETAIL=$(curl -s -u "$AUTH" "$BASE_URL/api/invoices/$INVOICE_ID")
echo "$INVOICE_DETAIL" | python3 -m json.tool | head -50
echo ""

# Step 6: Test overpayment (should fail with 422)
echo "6. Testing overpayment (should return 422)..."
OVERPAYMENT_RESPONSE=$(curl -s -u "$AUTH" -X POST "$BASE_URL/api/invoices/$INVOICE_ID/payments" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 999999.00,
    "currency": "USD",
    "paymentDate": "2025-11-08",
    "method": "BANK_TRANSFER"
  }' -w "\nHTTP_STATUS:%{http_code}")

HTTP_STATUS=$(echo "$OVERPAYMENT_RESPONSE" | grep "HTTP_STATUS" | cut -d: -f2)
echo "   Overpayment Status: $HTTP_STATUS"
echo "$OVERPAYMENT_RESPONSE" | grep -v "HTTP_STATUS" | python3 -m json.tool
echo ""

echo "=== Validation Complete ==="

