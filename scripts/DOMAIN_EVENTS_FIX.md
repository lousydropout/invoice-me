# Domain Events Table Missing - Production Fix

## Problem

The production database is missing the `domain_events` table, causing `INTERNAL_SERVER_ERROR` when the application tries to persist domain events:

```
ERROR: relation "domain_events" does not exist
```

## Root Cause

The `domain_events` table is defined in `schema.sql`, but:
1. The production database was likely initialized before this table was added to the schema
2. Spring Boot's `spring.sql.init.mode: always` may not re-run schema scripts if it detects existing tables
3. The `CREATE TABLE IF NOT EXISTS` in `schema.sql` should work, but the script may not be executing

## Solution

### Immediate Fix: Run Migration Script

Run the migration script to create the missing table:

```bash
./scripts/run-migration-domain-events.sh
```

Or manually connect to the database and run:

```bash
psql -h <DB_HOST> -U <DB_USER> -d invoiceme -f scripts/migrate-add-domain-events-table.sql
```

The migration script is idempotent and safe to run multiple times.

### Long-term Fix: Improved Error Handling

The code has been updated to:
1. **Isolate persistence failures**: Domain event persistence now runs in a separate transaction (`REQUIRES_NEW`) so failures don't affect the main business transaction
2. **Graceful degradation**: If the table is missing, the error is logged as a warning but doesn't break the application
3. **Better logging**: Changed from `ERROR` to `WARN` level for persistence failures

## Files Changed

1. **`scripts/migrate-add-domain-events-table.sql`** - Migration script to create the table
2. **`scripts/run-migration-domain-events.sh`** - Helper script to run the migration
3. **`backend/src/main/java/com/invoiceme/shared/infrastructure/events/SimpleDomainEventPublisher.java`** - Improved error handling

## Verification

After running the migration, verify the table exists:

```sql
SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE table_name = 'domain_events'
ORDER BY ordinal_position;
```

You should see:
- `id` (uuid)
- `type` (text)
- `payload` (jsonb)
- `created_at` (timestamptz)

## Prevention

To prevent this in the future:
1. Always run schema migrations as part of deployment
2. Consider using a proper migration tool (Flyway, Liquibase) for production
3. The improved error handling ensures the app continues working even if the table is missing

