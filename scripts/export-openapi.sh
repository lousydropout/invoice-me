#!/bin/bash

# Script to export OpenAPI JSON specification
# Usage: ./scripts/export-openapi.sh

set -e

BASE_URL="http://localhost:8080"
OUTPUT_FILE="backend/openapi.json"

echo "=== Exporting OpenAPI Specification ==="
echo ""

# Check if API is running
if ! curl -s -f "$BASE_URL/actuator/health" > /dev/null 2>&1; then
    echo "Error: API is not running at $BASE_URL"
    echo "Please start the application first:"
    echo "  docker compose up -d"
    echo "  or"
    echo "  cd backend && ./gradlew bootRun"
    exit 1
fi

echo "Fetching OpenAPI specification from $BASE_URL/v3/api-docs..."
curl -s "$BASE_URL/v3/api-docs" | jq '.' > "$OUTPUT_FILE"

if [ $? -eq 0 ]; then
    echo "✓ OpenAPI specification exported to $OUTPUT_FILE"
    echo ""
    echo "File size: $(wc -c < "$OUTPUT_FILE" | xargs) bytes"
    echo "Endpoints found: $(jq '.paths | length' "$OUTPUT_FILE")"
else
    echo "Error: Failed to export OpenAPI specification"
    exit 1
fi

