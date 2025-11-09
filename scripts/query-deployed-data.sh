#!/bin/bash
#
# Query Deployed API Data
#
# This script queries all available data from the deployed API
# to verify what's actually stored in the database.

set -euo pipefail

API_URL="${API_URL:-https://invoice-me.vincentchan.cloud}"
USER="${ADMIN_USERNAME:-admin}"
PASS="${ADMIN_PASSWORD:-admin}"

echo "=========================================="
echo "Deployed API Data Query"
echo "=========================================="
echo "API URL: ${API_URL}"
echo ""

echo "=== Customers (via /outstanding endpoint) ==="
CUSTOMERS=$(curl -s -u "${USER}:${PASS}" "${API_URL}/api/customers/outstanding")
CUSTOMER_COUNT=$(echo "${CUSTOMERS}" | python3 -c "import sys, json; print(len(json.load(sys.stdin)))")
echo "Found ${CUSTOMER_COUNT} customers with outstanding balances"
echo "${CUSTOMERS}" | python3 -m json.tool
echo ""

echo "=== Invoices ==="
INVOICES=$(curl -s -u "${USER}:${PASS}" "${API_URL}/api/invoices")
INVOICE_COUNT=$(echo "${INVOICES}" | python3 -c "import sys, json; print(len(json.load(sys.stdin)))")
echo "Found ${INVOICE_COUNT} invoices"
echo "${INVOICES}" | python3 -m json.tool
echo ""

echo "=== Overdue Invoices ==="
OVERDUE=$(curl -s -u "${USER}:${PASS}" "${API_URL}/api/invoices/overdue")
OVERDUE_COUNT=$(echo "${OVERDUE}" | python3 -c "import sys, json; print(len(json.load(sys.stdin)))")
echo "Found ${OVERDUE_COUNT} overdue invoices"
echo "${OVERDUE}" | python3 -m json.tool
echo ""

echo "=== Sample Invoice Detail (first invoice) ==="
FIRST_INVOICE_ID=$(echo "${INVOICES}" | python3 -c "import sys, json; data=json.load(sys.stdin); print(data[0]['id'] if data else '')")
if [ -n "${FIRST_INVOICE_ID}" ]; then
    curl -s -u "${USER}:${PASS}" "${API_URL}/api/invoices/${FIRST_INVOICE_ID}" | python3 -m json.tool | head -60
    echo ""
    echo "... (truncated)"
fi

echo ""
echo "=========================================="
echo "Summary"
echo "=========================================="
echo "Customers: ${CUSTOMER_COUNT}"
echo "Invoices: ${INVOICE_COUNT}"
echo "Overdue: ${OVERDUE_COUNT}"
echo ""
echo "Note: /api/customers returns empty because listAll() is not yet implemented"
echo "      (see CustomerController.java line 75-79)"
echo "      Use /api/customers/outstanding or /api/customers/{id} instead"

