#!/bin/bash

set -e

BASE_URL="http://localhost:8080"
AUTH="admin:admin"

echo "=== Query API Validation (Task 5.3) ==="
echo ""

# Step 0: Clear database tables
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
    "name": "Test Customer",
    "email": "test@example.com",
    "phone": "555-1234",
    "address": {
      "street": "123 Main St",
      "city": "City",
      "postalCode": "12345",
      "country": "US"
    },
    "paymentTerms": "NET_30"
  }')

CUSTOMER_ID=$(echo "$CUSTOMER_RESPONSE" | grep -o '"id":"[^"]*"' | cut -d'"' -f4)
if [ -z "$CUSTOMER_ID" ]; then
  echo "   Error creating customer: $CUSTOMER_RESPONSE"
  exit 1
fi
echo "   Customer ID: $CUSTOMER_ID"
echo ""

# Step 2: Create invoice
echo "2. Creating invoice..."
INVOICE_RESPONSE=$(curl -s -u "$AUTH" -X POST "$BASE_URL/api/invoices" \
  -H "Content-Type: application/json" \
  -d "{
    \"customerId\": \"$CUSTOMER_ID\",
    \"lineItems\": [
      {
        \"description\": \"Service A\",
        \"quantity\": 5,
        \"unitPriceAmount\": 100.0,
        \"unitPriceCurrency\": \"USD\"
      }
    ],
    \"issueDate\": \"$(date +%Y-%m-%d)\",
    \"dueDate\": \"$(date -d '+30 days' +%Y-%m-%d 2>/dev/null || date -v+30d +%Y-%m-%d 2>/dev/null || date +%Y-%m-%d)\",
    \"taxRate\": 0.10,
    \"notes\": \"Test invoice\"
  }")

INVOICE_ID=$(echo "$INVOICE_RESPONSE" | grep -o '"id":"[^"]*"' | cut -d'"' -f4)
if [ -z "$INVOICE_ID" ]; then
  echo "   Error creating invoice: $INVOICE_RESPONSE"
  exit 1
fi
echo "   Invoice ID: $INVOICE_ID"
echo ""

# Step 3: Send invoice
echo "3. Sending invoice..."
SEND_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -u "$AUTH" -X POST "$BASE_URL/api/invoices/$INVOICE_ID/send")
echo "   Send Status: $SEND_STATUS"
echo ""

# Step 4: Test GET /api/invoices (list all)
echo "4. Testing GET /api/invoices (list all)..."
LIST_RESPONSE=$(curl -s -u "$AUTH" "$BASE_URL/api/invoices")
LIST_COUNT=$(echo "$LIST_RESPONSE" | grep -o '"id"' | wc -l)
echo "   Found $LIST_COUNT invoice(s)"
echo "$LIST_RESPONSE" | head -c 200
echo "..."
echo ""

# Step 5: Test GET /api/invoices/{id} (details)
echo "5. Testing GET /api/invoices/$INVOICE_ID (details)..."
DETAIL_RESPONSE=$(curl -s -u "$AUTH" "$BASE_URL/api/invoices/$INVOICE_ID")
echo "$DETAIL_RESPONSE" | head -c 300
echo "..."
echo ""

# Step 6: Create overdue invoice
echo "6. Creating overdue invoice..."
OVERDUE_INVOICE_RESPONSE=$(curl -s -u "$AUTH" -X POST "$BASE_URL/api/invoices" \
  -H "Content-Type: application/json" \
  -d "{
    \"customerId\": \"$CUSTOMER_ID\",
    \"lineItems\": [
      {
        \"description\": \"Service B\",
        \"quantity\": 2,
        \"unitPriceAmount\": 50.0,
        \"unitPriceCurrency\": \"USD\"
      }
    ],
    \"issueDate\": \"$(date -d '-10 days' +%Y-%m-%d 2>/dev/null || date -v-10d +%Y-%m-%d 2>/dev/null || date +%Y-%m-%d)\",
    \"dueDate\": \"$(date -d '-5 days' +%Y-%m-%d 2>/dev/null || date -v-5d +%Y-%m-%d 2>/dev/null || date +%Y-%m-%d)\",
    \"taxRate\": 0.10,
    \"notes\": \"Overdue invoice\"
  }")

OVERDUE_INVOICE_ID=$(echo "$OVERDUE_INVOICE_RESPONSE" | grep -o '"id":"[^"]*"' | cut -d'"' -f4)
if [ -z "$OVERDUE_INVOICE_ID" ]; then
  echo "   Error creating overdue invoice: $OVERDUE_INVOICE_RESPONSE"
  exit 1
fi
curl -s -o /dev/null -u "$AUTH" -X POST "$BASE_URL/api/invoices/$OVERDUE_INVOICE_ID/send"
echo "   Overdue Invoice ID: $OVERDUE_INVOICE_ID"
echo ""

# Step 7: Test GET /api/invoices/overdue
echo "7. Testing GET /api/invoices/overdue..."
OVERDUE_RESPONSE=$(curl -s -u "$AUTH" "$BASE_URL/api/invoices/overdue")
OVERDUE_COUNT=$(echo "$OVERDUE_RESPONSE" | grep -o '"id"' | wc -l)
echo "   Found $OVERDUE_COUNT overdue invoice(s)"
echo "$OVERDUE_RESPONSE" | head -c 200
echo "..."
echo ""

# Step 8: Record payment
echo "8. Recording payment..."
PAYMENT_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -u "$AUTH" -X POST "$BASE_URL/api/invoices/$INVOICE_ID/payments" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 250.0,
    "currency": "USD",
    "paymentDate": "'$(date +%Y-%m-%d)'",
    "method": "BANK_TRANSFER",
    "reference": "TXN001"
  }')
echo "   Payment Status: $PAYMENT_STATUS"
echo ""

# Step 9: Test GET /api/customers/outstanding
echo "9. Testing GET /api/customers/outstanding..."
OUTSTANDING_RESPONSE=$(curl -s -u "$AUTH" "$BASE_URL/api/customers/outstanding")
OUTSTANDING_COUNT=$(echo "$OUTSTANDING_RESPONSE" | grep -o '"id"' | wc -l)
echo "   Found $OUTSTANDING_COUNT customer(s) with outstanding balances"
echo "$OUTSTANDING_RESPONSE"
echo ""

echo "=== Validation Complete ==="

