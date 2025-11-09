#!/bin/bash
#
# Database Contents Verification Script
#
# This script helps verify what's actually in the database by querying
# the API and showing you what tables and data exist.

set -euo pipefail

API_URL="${API_URL:-https://invoice-me.vincentchan.cloud}"
USER="${ADMIN_USERNAME:-admin}"
PASS="${ADMIN_PASSWORD:-admin}"

echo "=========================================="
echo "Database Contents Verification"
echo "=========================================="
echo ""
echo "This script queries the API to show what data exists."
echo "If you're not seeing this data in your database, check:"
echo "  1. Database connection string (DB_HOST, DB_NAME)"
echo "  2. Schema name (might be 'public' or another schema)"
echo "  3. Table names (case-sensitive in PostgreSQL)"
echo ""

echo "=== Current Data Counts ==="
echo ""

# Count customers via outstanding endpoint
CUSTOMERS=$(curl -s -u "${USER}:${PASS}" "${API_URL}/api/customers/outstanding")
CUSTOMER_COUNT=$(echo "${CUSTOMERS}" | python3 -c "import sys, json; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "0")
echo "Customers: ${CUSTOMER_COUNT}"

# Count invoices
INVOICES=$(curl -s -u "${USER}:${PASS}" "${API_URL}/api/invoices")
INVOICE_COUNT=$(echo "${INVOICES}" | python3 -c "import sys, json; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "0")
echo "Invoices: ${INVOICE_COUNT}"

# Count payments (by checking invoice details)
PAYMENT_COUNT=0
if [ "${INVOICE_COUNT}" -gt 0 ]; then
    FIRST_INVOICE_ID=$(echo "${INVOICES}" | python3 -c "import sys, json; data=json.load(sys.stdin); print(data[0]['id'] if data else '')" 2>/dev/null || echo "")
    if [ -n "${FIRST_INVOICE_ID}" ]; then
        INVOICE_DETAIL=$(curl -s -u "${USER}:${PASS}" "${API_URL}/api/invoices/${FIRST_INVOICE_ID}")
        PAYMENT_COUNT=$(echo "${INVOICE_DETAIL}" | python3 -c "import sys, json; data=json.load(sys.stdin); print(len(data.get('payments', [])))" 2>/dev/null || echo "0")
    fi
fi
echo "Payments (sample from first invoice): ${PAYMENT_COUNT}"
echo ""

echo "=== Sample Data ==="
echo ""
echo "First 3 customers:"
echo "${CUSTOMERS}" | python3 -m json.tool | head -30
echo ""

echo "First 3 invoices:"
echo "${INVOICES}" | python3 -m json.tool | head -40
echo ""

echo "=== Database Connection Info ==="
echo ""
echo "To verify data in your database, connect and run:"
echo ""
echo "  -- Check current schema"
echo "  SELECT current_schema();"
echo ""
echo "  -- List all tables"
echo "  SELECT table_name FROM information_schema.tables"
echo "    WHERE table_schema = 'public'"
echo "    ORDER BY table_name;"
echo ""
echo "  -- Count records"
echo "  SELECT 'customers' as table_name, COUNT(*) as count FROM customers"
echo "  UNION ALL"
echo "  SELECT 'invoices', COUNT(*) FROM invoices"
echo "  UNION ALL"
echo "  SELECT 'line_items', COUNT(*) FROM line_items"
echo "  UNION ALL"
echo "  SELECT 'payments', COUNT(*) FROM payments"
echo "  UNION ALL"
echo "  SELECT 'domain_events', COUNT(*) FROM domain_events;"
echo ""
echo "  -- Check for flyway table (leftover from previous deployment)"
echo "  SELECT * FROM flyway_schema_history LIMIT 5;"
echo ""
echo "  -- Verify data exists"
echo "  SELECT id, name, email FROM customers LIMIT 5;"
echo "  SELECT id, invoice_number, status FROM invoices LIMIT 5;"
echo ""

echo "=== About flyway_schema_history ==="
echo ""
echo "The 'flyway_schema_history' table is a leftover from a previous deployment"
echo "that used Flyway. Your current codebase uses Spring Boot SQL initialization"
echo "instead (schema.sql). This table doesn't affect your application."
echo ""
echo "To remove it (optional):"
echo "  DROP TABLE IF EXISTS flyway_schema_history;"
echo ""

