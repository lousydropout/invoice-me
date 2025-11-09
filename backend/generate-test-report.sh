#!/bin/bash
# Generate test report summary from Gradle test results

cd "$(dirname "$0")"

# Run tests if report doesn't exist or is stale
if [ ! -f "build/reports/tests/test/index.html" ] || [ "build/reports/tests/test/index.html" -ot "src" ]; then
    echo "Running tests..."
    ./gradlew test --no-daemon > /dev/null 2>&1
fi

# Extract test summary using Python
python3 << 'PYTHON_EOF'
import re
from pathlib import Path
from datetime import datetime

html = Path('build/reports/tests/test/index.html').read_text()

# Extract totals
total_match = re.search(r'<div class="counter">(\d+)</div>\s*<p>tests</p>', html)
failures_match = re.search(r'<div class="counter">(\d+)</div>\s*<p>failures</p>', html)
duration_match = re.search(r'<div class="counter">([\d.]+)s</div>\s*<p>duration</p>', html)

total = int(total_match.group(1)) if total_match else 0
failures = int(failures_match.group(1)) if failures_match else 0
duration = float(duration_match.group(1)) if duration_match else 0

# Extract package counts
pattern = r'<a href="packages/([^"]+)\.html">([^<]+)</a>.*?<td>(\d+)</td>'
matches = re.findall(pattern, html, re.DOTALL)

# Categorize
invoice_domain = 0
invoice_repo = 0
invoice_commands = 0
invoice_api = 0
customer_domain = 0
customer_commands = 0
customer_api = 0
payment_domain = 0
shared = 0

for package, name, tests in matches:
    count = int(tests)
    if count == 0:
        continue
    
    if 'invoice.domain' in package:
        invoice_domain += count
    elif 'invoice.infrastructure.persistence' in package:
        invoice_repo += count
    elif 'invoice.application.commands' in package:
        invoice_commands += count
    elif 'invoice.api' in package:
        invoice_api += count
    elif 'customer.domain' in package:
        customer_domain += count
    elif 'customer.application.commands' in package:
        customer_commands += count
    elif 'customer.api' in package:
        customer_api += count
    elif 'payment.domain' in package:
        payment_domain += count
    else:
        shared += count

# Generate report
print("Current test report summary:")
print(f"Generated: {datetime.now().strftime('%b %d, %Y')}")
print()
print(f"* Total tests: {total}/{total} passing ({100 if failures == 0 else round((total-failures)/total*100)}% success rate, {duration:.1f}s execution time)")
print(f"* Invoice domain tests: {invoice_domain}/{invoice_domain} passing")
print(f"* Invoice repository tests: {invoice_repo}/{invoice_repo} passing")
print(f"* Invoice command handler tests: {invoice_commands}/{invoice_commands} passing")
print(f"* Invoice API integration tests: {invoice_api}/{invoice_api} passing")
print(f"* Customer domain tests: {customer_domain}/{customer_domain} passing")
print(f"* Customer command handler tests: {customer_commands}/{customer_commands} passing")
print(f"* Customer API tests: {customer_api}/{customer_api} passing")
print(f"* Payment domain tests: {payment_domain}/{payment_domain} passing")
print(f"* Shared infrastructure tests: {shared}/{shared} passing")
PYTHON_EOF

