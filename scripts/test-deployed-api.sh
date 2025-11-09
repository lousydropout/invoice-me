#!/bin/bash
#
# Deployed API Test Script
#
# Tests the deployed InvoiceMe API at https://invoice-me.vincentchan.cloud
# This script performs end-to-end API tests without database access.
#
# Usage:
#   ./scripts/test-deployed-api.sh
#   API_URL=https://invoice-me.vincentchan.cloud ADMIN_USERNAME=admin ADMIN_PASSWORD=admin ./scripts/test-deployed-api.sh

set -euo pipefail

# Configuration
API_URL="${API_URL:-https://invoice-me.vincentchan.cloud}"
USER="${ADMIN_USERNAME:-admin}"
PASS="${ADMIN_PASSWORD:-admin}"
SKIP_CLEANUP="${SKIP_CLEANUP:-false}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test counters
TESTS_PASSED=0
TESTS_FAILED=0

# Helper functions
log_info() {
    echo -e "${GREEN}✓${NC} $1"
    ((TESTS_PASSED++)) || true
}

log_error() {
    echo -e "${RED}✗${NC} $1"
    ((TESTS_FAILED++)) || true
}

log_test() {
    echo -e "\n${YELLOW}=== $1 ===${NC}"
}

log_step() {
    echo -e "${BLUE}→${NC} $1"
}

# Test health check
test_health_check() {
    log_test "Health Check"
    
    log_step "Testing GET /api/health"
    RESPONSE=$(curl -s -f "${API_URL}/api/health" 2>&1)
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${API_URL}/api/health")
    
    if [ "${HTTP_CODE}" = "200" ]; then
        if echo "${RESPONSE}" | grep -q '"status":"ok"'; then
            log_info "Health check passed (HTTP ${HTTP_CODE})"
            echo "  Response: ${RESPONSE}"
        else
            log_error "Health check returned unexpected response: ${RESPONSE}"
            return 1
        fi
    else
        log_error "Health check failed (HTTP ${HTTP_CODE})"
        return 1
    fi
}

# Test customer CRUD
test_customer_crud() {
    log_test "Customer CRUD Operations"
    local CUSTOMER_ID=""
    local TIMESTAMP=$(date +%s)
    local TEST_EMAIL="test-${TIMESTAMP}@deployed-test.com"
    
    # Create customer
    log_step "Creating customer..."
    CREATE_RESPONSE=$(curl -s -u "${USER}:${PASS}" -X POST "${API_URL}/api/customers" \
        -H "Content-Type: application/json" \
        -d "{
            \"name\": \"Deployed Test Customer ${TIMESTAMP}\",
            \"email\": \"${TEST_EMAIL}\",
            \"phone\": \"555-0001\",
            \"address\": {
                \"street\": \"123 Test St\",
                \"city\": \"Test City\",
                \"postalCode\": \"12345\",
                \"country\": \"US\"
            },
            \"paymentTerms\": \"NET_30\"
        }" \
        -w "\nHTTP_CODE:%{http_code}")
    
    HTTP_CODE=$(echo "${CREATE_RESPONSE}" | grep "HTTP_CODE" | cut -d: -f2)
    CUSTOMER_ID=$(echo "${CREATE_RESPONSE}" | grep -o '"id":"[^"]*"' | cut -d'"' -f4 || echo "")
    
    if [ "${HTTP_CODE}" = "201" ] && [ -n "${CUSTOMER_ID}" ]; then
        log_info "Customer created (ID: ${CUSTOMER_ID})"
    else
        log_error "Customer creation failed (HTTP ${HTTP_CODE})"
        echo "  Response: ${CREATE_RESPONSE}"
        return 1
    fi
    
    # Get customer
    log_step "Retrieving customer..."
    GET_RESPONSE=$(curl -s -u "${USER}:${PASS}" "${API_URL}/api/customers/${CUSTOMER_ID}")
    GET_HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -u "${USER}:${PASS}" "${API_URL}/api/customers/${CUSTOMER_ID}")
    
    if [ "${GET_HTTP_CODE}" = "200" ] && echo "${GET_RESPONSE}" | grep -q "${TEST_EMAIL}"; then
        log_info "Customer retrieved successfully"
    else
        log_error "Customer retrieval failed (HTTP ${GET_HTTP_CODE})"
        return 1
    fi
    
    # Update customer
    log_step "Updating customer..."
    UPDATE_HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -u "${USER}:${PASS}" -X PUT \
        -H "Content-Type: application/json" \
        -d "{
            \"name\": \"Updated Test Customer ${TIMESTAMP}\",
            \"email\": \"${TEST_EMAIL}\",
            \"phone\": \"555-0002\",
            \"address\": {
                \"street\": \"456 Updated Ave\",
                \"city\": \"Updated City\",
                \"postalCode\": \"54321\",
                \"country\": \"US\"
            },
            \"paymentTerms\": \"NET_45\"
        }" \
        "${API_URL}/api/customers/${CUSTOMER_ID}")
    
    if [ "${UPDATE_HTTP_CODE}" = "204" ]; then
        log_info "Customer updated successfully"
    else
        log_error "Customer update failed (HTTP ${UPDATE_HTTP_CODE})"
        return 1
    fi
    
    # List customers
    log_step "Listing customers..."
    LIST_HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -u "${USER}:${PASS}" "${API_URL}/api/customers")
    
    if [ "${LIST_HTTP_CODE}" = "200" ]; then
        log_info "Customer list retrieved successfully"
    else
        log_error "Customer list failed (HTTP ${LIST_HTTP_CODE})"
        return 1
    fi
    
    # Delete customer (unless SKIP_CLEANUP is set)
    if [ "${SKIP_CLEANUP}" != "true" ]; then
        log_step "Deleting customer..."
        DELETE_HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -u "${USER}:${PASS}" -X DELETE \
            "${API_URL}/api/customers/${CUSTOMER_ID}")
        
        if [ "${DELETE_HTTP_CODE}" = "204" ]; then
            log_info "Customer deleted successfully"
        else
            log_error "Customer deletion failed (HTTP ${DELETE_HTTP_CODE})"
            return 1
        fi
        
        # Verify deletion
        log_step "Verifying deletion..."
        VERIFY_HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -u "${USER}:${PASS}" "${API_URL}/api/customers/${CUSTOMER_ID}")
        
        if [ "${VERIFY_HTTP_CODE}" = "404" ]; then
            log_info "Customer deletion verified (404 as expected)"
        else
            log_error "Customer still exists (HTTP ${VERIFY_HTTP_CODE})"
            return 1
        fi
    else
        log_info "Skipping cleanup - Customer ID: ${CUSTOMER_ID} (available for inspection)"
    fi
}

# Test invoice lifecycle
test_invoice_lifecycle() {
    log_test "Invoice Lifecycle Operations"
    local CUSTOMER_ID=""
    local INVOICE_ID=""
    local TIMESTAMP=$(date +%s)
    local TEST_EMAIL="invoice-test-${TIMESTAMP}@deployed-test.com"
    
    # Create customer for invoice
    log_step "Creating customer for invoice test..."
    CUSTOMER_RESPONSE=$(curl -s -u "${USER}:${PASS}" -X POST "${API_URL}/api/customers" \
        -H "Content-Type: application/json" \
        -d "{
            \"name\": \"Invoice Test Customer ${TIMESTAMP}\",
            \"email\": \"${TEST_EMAIL}\",
            \"phone\": \"555-1000\",
            \"address\": {
                \"street\": \"789 Invoice St\",
                \"city\": \"Invoice City\",
                \"postalCode\": \"99999\",
                \"country\": \"US\"
            },
            \"paymentTerms\": \"NET_30\"
        }")
    
    CUSTOMER_ID=$(echo "${CUSTOMER_RESPONSE}" | grep -o '"id":"[^"]*"' | cut -d'"' -f4 || echo "")
    
    if [ -z "${CUSTOMER_ID}" ]; then
        log_error "Failed to create customer for invoice test"
        return 1
    fi
    
    # Create invoice
    log_step "Creating invoice..."
    INVOICE_RESPONSE=$(curl -s -u "${USER}:${PASS}" -X POST "${API_URL}/api/invoices" \
        -H "Content-Type: application/json" \
        -d "{
            \"customerId\": \"${CUSTOMER_ID}\",
            \"lineItems\": [{
                \"description\": \"Test Service\",
                \"quantity\": 10,
                \"unitPriceAmount\": 50.00,
                \"unitPriceCurrency\": \"USD\"
            }],
            \"issueDate\": \"$(date +%Y-%m-%d)\",
            \"dueDate\": \"$(date -d '+30 days' +%Y-%m-%d 2>/dev/null || date -v+30d +%Y-%m-%d 2>/dev/null || date +%Y-%m-%d)\",
            \"taxRate\": 0.10,
            \"notes\": \"Deployed API test invoice\"
        }" \
        -w "\nHTTP_CODE:%{http_code}")
    
    HTTP_CODE=$(echo "${INVOICE_RESPONSE}" | grep "HTTP_CODE" | cut -d: -f2)
    INVOICE_ID=$(echo "${INVOICE_RESPONSE}" | grep -o '"id":"[^"]*"' | cut -d'"' -f4 || echo "")
    
    if [ "${HTTP_CODE}" = "201" ] && [ -n "${INVOICE_ID}" ]; then
        log_info "Invoice created (ID: ${INVOICE_ID})"
    else
        log_error "Invoice creation failed (HTTP ${HTTP_CODE})"
        echo "  Response: ${INVOICE_RESPONSE}"
        # Clean up customer
        curl -s -u "${USER}:${PASS}" -X DELETE "${API_URL}/api/customers/${CUSTOMER_ID}" > /dev/null || true
        return 1
    fi
    
    # Get invoice
    log_step "Retrieving invoice..."
    GET_HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -u "${USER}:${PASS}" "${API_URL}/api/invoices/${INVOICE_ID}")
    
    if [ "${GET_HTTP_CODE}" = "200" ]; then
        log_info "Invoice retrieved successfully"
    else
        log_error "Invoice retrieval failed (HTTP ${GET_HTTP_CODE})"
    fi
    
    # Send invoice
    log_step "Sending invoice..."
    SEND_HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -u "${USER}:${PASS}" -X POST \
        "${API_URL}/api/invoices/${INVOICE_ID}/send")
    
    if [ "${SEND_HTTP_CODE}" = "204" ]; then
        log_info "Invoice sent successfully"
    else
        log_error "Invoice send failed (HTTP ${SEND_HTTP_CODE})"
    fi
    
    # Record payment
    log_step "Recording payment..."
    PAYMENT_HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -u "${USER}:${PASS}" -X POST \
        -H "Content-Type: application/json" \
        -d "{
            \"amount\": 275.00,
            \"currency\": \"USD\",
            \"paymentDate\": \"$(date +%Y-%m-%d)\",
            \"method\": \"BANK_TRANSFER\",
            \"reference\": \"TEST-${TIMESTAMP}\"
        }" \
        "${API_URL}/api/invoices/${INVOICE_ID}/payments")
    
    if [ "${PAYMENT_HTTP_CODE}" = "201" ] || [ "${PAYMENT_HTTP_CODE}" = "204" ]; then
        log_info "Payment recorded successfully (HTTP ${PAYMENT_HTTP_CODE})"
    else
        log_error "Payment recording failed (HTTP ${PAYMENT_HTTP_CODE})"
    fi
    
    # List invoices
    log_step "Listing invoices..."
    LIST_HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -u "${USER}:${PASS}" "${API_URL}/api/invoices")
    
    if [ "${LIST_HTTP_CODE}" = "200" ]; then
        log_info "Invoice list retrieved successfully"
    else
        log_error "Invoice list failed (HTTP ${LIST_HTTP_CODE})"
    fi
    
    # Clean up (unless SKIP_CLEANUP is set)
    if [ "${SKIP_CLEANUP}" != "true" ]; then
        log_step "Cleaning up test data..."
        curl -s -u "${USER}:${PASS}" -X DELETE "${API_URL}/api/customers/${CUSTOMER_ID}" > /dev/null || true
        log_info "Test cleanup completed"
    else
        log_info "Skipping cleanup - Test data preserved:"
        echo "  Customer ID: ${CUSTOMER_ID}"
        echo "  Invoice ID: ${INVOICE_ID}"
        echo "  View customer: ${API_URL}/api/customers/${CUSTOMER_ID}"
        echo "  View invoice: ${API_URL}/api/invoices/${INVOICE_ID}"
    fi
}

# Test authentication
test_authentication() {
    log_test "Authentication Tests"
    
    log_step "Testing unauthenticated request (should fail)..."
    UNAUTH_HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${API_URL}/api/customers")
    
    if [ "${UNAUTH_HTTP_CODE}" = "401" ]; then
        log_info "Unauthenticated request correctly rejected (401)"
    else
        log_error "Unauthenticated request should return 401, got ${UNAUTH_HTTP_CODE}"
    fi
    
    log_step "Testing invalid credentials (should fail)..."
    INVALID_HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -u "wrong:password" "${API_URL}/api/customers")
    
    if [ "${INVALID_HTTP_CODE}" = "401" ]; then
        log_info "Invalid credentials correctly rejected (401)"
    else
        log_error "Invalid credentials should return 401, got ${INVALID_HTTP_CODE}"
    fi
}

# Test validation
test_validation() {
    log_test "Input Validation Tests"
    
    log_step "Testing missing required field..."
    VALIDATION_RESPONSE=$(curl -s -u "${USER}:${PASS}" -X POST "${API_URL}/api/customers" \
        -H "Content-Type: application/json" \
        -d '{
            "email": "test@example.com",
            "phone": "123",
            "address": {"street": "A", "city": "B", "postalCode": "C", "country": "D"},
            "paymentTerms": "NET_30"
        }' \
        -w "\nHTTP_CODE:%{http_code}")
    
    HTTP_CODE=$(echo "${VALIDATION_RESPONSE}" | grep "HTTP_CODE" | cut -d: -f2)
    
    if [ "${HTTP_CODE}" = "400" ] || [ "${HTTP_CODE}" = "422" ]; then
        log_info "Missing field validation works (HTTP ${HTTP_CODE})"
    else
        log_error "Missing field validation failed (HTTP ${HTTP_CODE})"
    fi
}

# Main execution
main() {
    echo "=========================================="
    echo "Deployed API Test Suite"
    echo "=========================================="
    echo "API URL: ${API_URL}"
    echo "User: ${USER}"
    echo "Skip Cleanup: ${SKIP_CLEANUP}"
    echo ""
    
    # Run tests
    test_health_check
    test_authentication
    test_validation
    test_customer_crud
    test_invoice_lifecycle
    
    # Summary
    echo ""
    echo "=========================================="
    echo "Test Summary"
    echo "=========================================="
    echo -e "${GREEN}Passed: ${TESTS_PASSED}${NC}"
    echo -e "${RED}Failed: ${TESTS_FAILED}${NC}"
    echo ""
    
    if [ "${TESTS_FAILED}" -eq 0 ]; then
        echo -e "${GREEN}✓ All tests passed!${NC}"
        exit 0
    else
        echo -e "${RED}✗ Some tests failed${NC}"
        exit 1
    fi
}

# Run main function
main "$@"

