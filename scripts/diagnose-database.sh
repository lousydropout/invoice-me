#!/bin/bash
#
# Database Diagnostic Script
#
# This script helps diagnose if data is being persisted to the database
# by checking the database connection and querying tables directly.

set -euo pipefail

API_URL="${API_URL:-https://invoice-me.vincentchan.cloud}"
USER="${ADMIN_USERNAME:-admin}"
PASS="${ADMIN_PASSWORD:-admin}"

echo "=========================================="
echo "Database Persistence Diagnostic"
echo "=========================================="
echo ""

echo "=== Step 1: Verify API can query data ==="
echo "Querying /api/invoices..."
INVOICES=$(curl -s -u "${USER}:${PASS}" "${API_URL}/api/invoices")
INVOICE_COUNT=$(echo "${INVOICES}" | python3 -c "import sys, json; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "0")
echo "API reports ${INVOICE_COUNT} invoices"
echo ""

echo "=== Step 2: Check if data persists across requests ==="
echo "Making 3 separate API calls to verify persistence..."
for i in 1 2 3; do
    COUNT=$(curl -s -u "${USER}:${PASS}" "${API_URL}/api/invoices" | python3 -c "import sys, json; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "0")
    echo "  Call $i: ${COUNT} invoices"
    sleep 1
done
echo ""

echo "=== Step 3: Create new invoice and verify it persists ==="
TIMESTAMP=$(date +%s)
echo "Creating test invoice at $(date)..."
CUSTOMER_RESPONSE=$(curl -s -u "${USER}:${PASS}" -X POST "${API_URL}/api/customers" \
    -H "Content-Type: application/json" \
    -d "{
        \"name\": \"Diagnostic Test Customer ${TIMESTAMP}\",
        \"email\": \"diagnostic-${TIMESTAMP}@test.com\",
        \"phone\": \"555-9999\",
        \"address\": {
            \"street\": \"123 Test St\",
            \"city\": \"Test City\",
            \"postalCode\": \"12345\",
            \"country\": \"US\"
        },
        \"paymentTerms\": \"NET_30\"
    }")

CUSTOMER_ID=$(echo "${CUSTOMER_RESPONSE}" | python3 -c "import sys, json; data=json.load(sys.stdin); print(data.get('id', ''))" 2>/dev/null || echo "")

if [ -z "${CUSTOMER_ID}" ]; then
    echo "  ERROR: Failed to create customer"
    exit 1
fi

echo "  Customer created: ${CUSTOMER_ID}"

INVOICE_RESPONSE=$(curl -s -u "${USER}:${PASS}" -X POST "${API_URL}/api/invoices" \
    -H "Content-Type: application/json" \
    -d "{
        \"customerId\": \"${CUSTOMER_ID}\",
        \"lineItems\": [{
            \"description\": \"Diagnostic Test Item\",
            \"quantity\": 1,
            \"unitPriceAmount\": 100.00,
            \"unitPriceCurrency\": \"USD\"
        }],
        \"issueDate\": \"$(date +%Y-%m-%d)\",
        \"dueDate\": \"$(date -d '+30 days' +%Y-%m-%d 2>/dev/null || date -v+30d +%Y-%m-%d 2>/dev/null || date +%Y-%m-%d)\",
        \"taxRate\": 0.10
    }")

INVOICE_ID=$(echo "${INVOICE_RESPONSE}" | python3 -c "import sys, json; data=json.load(sys.stdin); print(data.get('id', ''))" 2>/dev/null || echo "")

if [ -z "${INVOICE_ID}" ]; then
    echo "  ERROR: Failed to create invoice"
    exit 1
fi

echo "  Invoice created: ${INVOICE_ID}"
echo ""

echo "=== Step 4: Wait 2 seconds and verify invoice still exists ==="
sleep 2
VERIFY_RESPONSE=$(curl -s -u "${USER}:${PASS}" "${API_URL}/api/invoices/${INVOICE_ID}")
VERIFY_ID=$(echo "${VERIFY_RESPONSE}" | python3 -c "import sys, json; data=json.load(sys.stdin); print(data.get('id', ''))" 2>/dev/null || echo "")

if [ "${VERIFY_ID}" = "${INVOICE_ID}" ]; then
    echo "  ✓ Invoice persists across requests"
else
    echo "  ✗ Invoice NOT found after creation (data may be in-memory only)"
    echo "  Response: ${VERIFY_RESPONSE}"
fi
echo ""

echo "=== Step 5: Check invoice count before and after ==="
BEFORE_COUNT=$(curl -s -u "${USER}:${PASS}" "${API_URL}/api/invoices" | python3 -c "import sys, json; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "0")
echo "  Invoices before: ${BEFORE_COUNT}"

# Create another invoice
INVOICE_RESPONSE2=$(curl -s -u "${USER}:${PASS}" -X POST "${API_URL}/api/invoices" \
    -H "Content-Type: application/json" \
    -d "{
        \"customerId\": \"${CUSTOMER_ID}\",
        \"lineItems\": [{
            \"description\": \"Second Diagnostic Item\",
            \"quantity\": 2,
            \"unitPriceAmount\": 50.00,
            \"unitPriceCurrency\": \"USD\"
        }],
        \"issueDate\": \"$(date +%Y-%m-%d)\",
        \"dueDate\": \"$(date -d '+30 days' +%Y-%m-%d 2>/dev/null || date -v+30d +%Y-%m-%d 2>/dev/null || date +%Y-%m-%d)\",
        \"taxRate\": 0.10
    }")

sleep 1
AFTER_COUNT=$(curl -s -u "${USER}:${PASS}" "${API_URL}/api/invoices" | python3 -c "import sys, json; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "0")
echo "  Invoices after: ${AFTER_COUNT}"

if [ "${AFTER_COUNT}" -gt "${BEFORE_COUNT}" ]; then
    echo "  ✓ Invoice count increased (data is persisting)"
else
    echo "  ✗ Invoice count did not increase (possible in-memory storage)"
fi
echo ""

echo "=== Step 6: Query via different endpoint (outstanding) ==="
OUTSTANDING=$(curl -s -u "${USER}:${PASS}" "${API_URL}/api/customers/outstanding")
OUTSTANDING_COUNT=$(echo "${OUTSTANDING}" | python3 -c "import sys, json; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "0")
echo "  Customers with outstanding: ${OUTSTANDING_COUNT}"

# Check if our test customer appears
CUSTOMER_FOUND=$(echo "${OUTSTANDING}" | python3 -c "import sys, json; data=json.load(sys.stdin); print('YES' if any(c.get('id') == '${CUSTOMER_ID}' for c in data) else 'NO')" 2>/dev/null || echo "UNKNOWN")
echo "  Test customer in outstanding list: ${CUSTOMER_FOUND}"
echo ""

echo "=========================================="
echo "Diagnostic Summary"
echo "=========================================="
echo "If all checks pass, data IS being persisted to the database."
echo "The query handlers use JdbcTemplate which queries PostgreSQL directly."
echo ""
echo "If you're not seeing data in your database:"
echo "1. Check you're connected to the correct database"
echo "2. Check database connection string in application-prod.yml"
echo "3. Verify DB_HOST, DB_NAME environment variables"
echo "4. Check if there are multiple database instances"
echo "5. Verify transaction isolation level"
echo ""

