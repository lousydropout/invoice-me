#!/bin/bash
#
# Customer API Validation and Repository Persistence Test Script
#
# This script validates:
# 1. Input validation (missing fields, invalid formats, etc.)
# 2. Customer CRUD operations via API
# 3. Repository persistence and database consistency
#
# Preconditions:
# - Backend API and PostgreSQL running locally via Docker
# - Database 'invoiceme' accessible at localhost:5432
# - BasicAuth configured (default: admin/admin, can be overridden via env vars)

set -euo pipefail

# Configuration
API_URL="${API_URL:-http://localhost:8080}"
USER="${ADMIN_USERNAME:-admin}"
PASS="${ADMIN_PASSWORD:-admin}"
DB_USER="${POSTGRES_USER:-invoiceme}"
DB_NAME="${POSTGRES_DB:-invoiceme}"
TEST_EMAIL="repo@test.com"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Helper functions
log_info() {
    echo -e "${GREEN}✓${NC} $1"
}

log_error() {
    echo -e "${RED}✗${NC} $1"
}

log_test() {
    echo -e "\n${YELLOW}=== $1 ===${NC}"
}

# Check if services are running
check_services() {
    log_test "Checking Services"
    
    if ! curl -s -f "${API_URL}/api/health" > /dev/null 2>&1; then
        log_error "API is not accessible at ${API_URL}"
        exit 1
    fi
    log_info "API is accessible"
    
    if ! docker exec invoiceme-postgres psql -U "${DB_USER}" -d "${DB_NAME}" -c "SELECT 1;" > /dev/null 2>&1; then
        log_error "Database is not accessible"
        exit 1
    fi
    log_info "Database is accessible"
}

# Clean up test data
cleanup() {
    log_test "Cleaning Up Test Data"
    docker exec invoiceme-postgres psql -U "${DB_USER}" -d "${DB_NAME}" -c "DELETE FROM customers WHERE email = '${TEST_EMAIL}';" > /dev/null 2>&1 || true
    log_info "Test data cleaned"
}

# Validation Tests
test_validation() {
    log_test "Validation Tests"
    
    # Test 1: Reject missing name
    log_test "Test 1: Reject missing name"
    RESPONSE=$(curl -s -u "${USER}:${PASS}" -H "Content-Type: application/json" \
        -d '{"email":"good@email.com","phone":"123","address":{"street":"A","city":"B","postalCode":"C","country":"D"},"paymentTerms":"NET_30"}' \
        "${API_URL}/api/customers")
    if echo "${RESPONSE}" | grep -q "Name is required"; then
        log_info "Missing name validation - PASS"
    else
        log_error "Missing name validation - FAIL"
        echo "Response: ${RESPONSE}"
        return 1
    fi
    
    # Test 2: Reject invalid email
    log_test "Test 2: Reject invalid email"
    RESPONSE=$(curl -s -u "${USER}:${PASS}" -H "Content-Type: application/json" \
        -d '{"name":"Bad Email","email":"not-an-email","phone":"123","address":{"street":"A","city":"B","postalCode":"C","country":"D"},"paymentTerms":"NET_30"}' \
        "${API_URL}/api/customers")
    if echo "${RESPONSE}" | grep -q "Email must be valid"; then
        log_info "Invalid email validation - PASS"
    else
        log_error "Invalid email validation - FAIL"
        echo "Response: ${RESPONSE}"
        return 1
    fi
    
    # Test 3: Reject unsupported payment term
    log_test "Test 3: Reject unsupported payment term (NET_10)"
    RESPONSE=$(curl -s -u "${USER}:${PASS}" -H "Content-Type: application/json" \
        -d '{"name":"Acme","email":"acme@email.com","phone":"123","address":{"street":"A","city":"B","postalCode":"C","country":"D"},"paymentTerms":"NET_10"}' \
        "${API_URL}/api/customers")
    if echo "${RESPONSE}" | grep -q "Invalid payment terms"; then
        log_info "Invalid payment terms validation - PASS"
    else
        log_error "Invalid payment terms validation - FAIL"
        echo "Response: ${RESPONSE}"
        return 1
    fi
    
    # Test 4: Reject blank name on update
    log_test "Test 4: Reject blank name on update"
    # First create a customer
    CREATE_RESPONSE=$(curl -s -u "${USER}:${PASS}" -H "Content-Type: application/json" \
        -d "{\"name\":\"Temp Customer\",\"email\":\"temp@test.com\",\"phone\":\"123\",\"address\":{\"street\":\"A\",\"city\":\"B\",\"postalCode\":\"C\",\"country\":\"D\"},\"paymentTerms\":\"NET_30\"}" \
        "${API_URL}/api/customers")
    TEMP_ID=$(echo "${CREATE_RESPONSE}" | grep -o '"id":"[^"]*"' | cut -d'"' -f4)
    
    # Try to update with blank name
    UPDATE_RESPONSE=$(curl -s -u "${USER}:${PASS}" -X PUT -H "Content-Type: application/json" \
        -d "{\"name\":\"\",\"email\":\"temp@test.com\",\"phone\":\"123\",\"address\":{\"street\":\"A\",\"city\":\"B\",\"postalCode\":\"C\",\"country\":\"D\"},\"paymentTerms\":\"NET_30\"}" \
        "${API_URL}/api/customers/${TEMP_ID}")
    if echo "${UPDATE_RESPONSE}" | grep -q "Name is required"; then
        log_info "Blank name update validation - PASS"
    else
        log_error "Blank name update validation - FAIL"
        echo "Response: ${UPDATE_RESPONSE}"
        return 1
    fi
    
    # Clean up temp customer
    curl -s -u "${USER}:${PASS}" -X DELETE "${API_URL}/api/customers/${TEMP_ID}" > /dev/null || true
}

# Repository Persistence Tests
test_repository_persistence() {
    log_test "Repository Persistence Tests"
    
    # Step 1: Create a new customer via API
    log_test "Step 1: Create customer via API"
    CREATE_RESPONSE=$(curl -s -u "${USER}:${PASS}" -H "Content-Type: application/json" \
        -d "{\"name\":\"Repo Test\",\"email\":\"${TEST_EMAIL}\",\"phone\":\"555-0001\",
             \"address\":{\"street\":\"10 Repo Way\",\"city\":\"Codeville\",\"postalCode\":\"10010\",\"country\":\"US\"},
             \"paymentTerms\":\"NET_30\"}" \
        -w "\nHTTP_CODE:%{http_code}" \
        "${API_URL}/api/customers")
    
    HTTP_CODE=$(echo "${CREATE_RESPONSE}" | grep "HTTP_CODE" | cut -d: -f2)
    CUSTOMER_ID=$(echo "${CREATE_RESPONSE}" | grep -o '"id":"[^"]*"' | cut -d'"' -f4)
    
    if [ "${HTTP_CODE}" = "201" ] && [ -n "${CUSTOMER_ID}" ]; then
        log_info "Customer created - ID: ${CUSTOMER_ID}"
    else
        log_error "Customer creation failed - HTTP: ${HTTP_CODE}"
        echo "Response: ${CREATE_RESPONSE}"
        return 1
    fi
    
    # Step 2: Verify record exists in database
    log_test "Step 2: Verify record in database"
    DB_RESULT=$(docker exec invoiceme-postgres psql -U "${DB_USER}" -d "${DB_NAME}" -t -c \
        "SELECT id, name, email, phone FROM customers WHERE email = '${TEST_EMAIL}';")
    
    if echo "${DB_RESULT}" | grep -q "${CUSTOMER_ID}"; then
        log_info "Customer found in database"
        echo "  ${DB_RESULT}"
    else
        log_error "Customer not found in database"
        return 1
    fi
    
    # Step 3: Retrieve via GET endpoint
    log_test "Step 3: Retrieve customer via GET endpoint"
    GET_RESPONSE=$(curl -s -u "${USER}:${PASS}" "${API_URL}/api/customers/${CUSTOMER_ID}")
    
    if echo "${GET_RESPONSE}" | grep -q "${TEST_EMAIL}"; then
        log_info "Customer retrieved via API"
    else
        log_error "Customer retrieval failed"
        echo "Response: ${GET_RESPONSE}"
        return 1
    fi
    
    # Step 4: Update the customer
    log_test "Step 4: Update customer"
    UPDATE_HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -u "${USER}:${PASS}" -X PUT \
        -H "Content-Type: application/json" \
        -d "{\"name\":\"Repo Test Updated\",\"email\":\"${TEST_EMAIL}\",\"phone\":\"555-0002\",
             \"address\":{\"street\":\"20 Persistence Blvd\",\"city\":\"Codeville\",\"postalCode\":\"10020\",\"country\":\"US\"},
             \"paymentTerms\":\"NET_45\"}" \
        "${API_URL}/api/customers/${CUSTOMER_ID}")
    
    if [ "${UPDATE_HTTP_CODE}" = "204" ]; then
        log_info "Customer updated successfully"
    else
        log_error "Customer update failed - HTTP: ${UPDATE_HTTP_CODE}"
        return 1
    fi
    
    # Step 5: Re-query database
    log_test "Step 5: Verify update in database"
    DB_RESULT=$(docker exec invoiceme-postgres psql -U "${DB_USER}" -d "${DB_NAME}" -t -c \
        "SELECT name, phone FROM customers WHERE email = '${TEST_EMAIL}';")
    
    if echo "${DB_RESULT}" | grep -q "Repo Test Updated" && echo "${DB_RESULT}" | grep -q "555-0002"; then
        log_info "Database reflects update"
        echo "  ${DB_RESULT}"
    else
        log_error "Database update verification failed"
        echo "  ${DB_RESULT}"
        return 1
    fi
    
    # Step 6: Delete the customer
    log_test "Step 6: Delete customer"
    DELETE_HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -u "${USER}:${PASS}" -X DELETE \
        "${API_URL}/api/customers/${CUSTOMER_ID}")
    
    if [ "${DELETE_HTTP_CODE}" = "204" ]; then
        log_info "Customer deleted successfully"
    else
        log_error "Customer deletion failed - HTTP: ${DELETE_HTTP_CODE}"
        return 1
    fi
    
    # Step 7: Verify deletion
    log_test "Step 7: Verify deletion in database"
    DB_COUNT=$(docker exec invoiceme-postgres psql -U "${DB_USER}" -d "${DB_NAME}" -t -c \
        "SELECT COUNT(*) FROM customers WHERE email = '${TEST_EMAIL}';" | tr -d ' ')
    
    if [ "${DB_COUNT}" = "0" ]; then
        log_info "Customer deleted from database"
    else
        log_error "Customer still exists in database (count: ${DB_COUNT})"
        return 1
    fi
}

# Main execution
main() {
    echo "=========================================="
    echo "Customer API Validation Test Suite"
    echo "=========================================="
    echo "API URL: ${API_URL}"
    echo "User: ${USER}"
    echo ""
    
    # Run tests
    check_services
    cleanup
    
    if test_validation; then
        log_info "All validation tests passed"
    else
        log_error "Validation tests failed"
        exit 1
    fi
    
    if test_repository_persistence; then
        log_info "All repository persistence tests passed"
    else
        log_error "Repository persistence tests failed"
        exit 1
    fi
    
    cleanup
    
    echo ""
    echo "=========================================="
    log_info "All tests completed successfully!"
    echo "=========================================="
}

# Run main function
main "$@"

