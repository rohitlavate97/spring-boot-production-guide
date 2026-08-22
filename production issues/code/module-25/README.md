# Module 25: Database Migrations: Flyway, Locks & Zero-Downtime

## Overview
This module explores enterprise database schema evolution with Flyway, deep-diving into the 4-phase Expand and Contract pattern, avoiding PostgreSQL `AccessExclusiveLock` table rewrites, mitigating Flyway schema history lock deadlocks in Kubernetes rolling deployments, and enforcing safe DDL practices.

## Key Scenarios Covered
1. **The Flyway Schema Table Lock Deadlock:**
   - Why spinning up multiple Kubernetes pods simultaneously causes migration lock contention and failed rollouts.
   - Solving by running Flyway as a single-replica Kubernetes Pre-Sync Job / InitContainer.
2. **The `AccessExclusiveLock` Table Rewrite Outage:**
   - Why adding non-nullable columns or changing column types without safety rules locks entire tables, exhausting HikariCP pools.
   - Setting aggressive lock timeouts (`SET lock_timeout = '2s'`) and safe DDL constructs.
3. **The Expand and Contract Pattern (Parallel Run):**
   - **Phase 1 (Expand):** Add new column as nullable; dual-write from application code.
   - **Phase 2 (Backfill):** Migrate historical data in small, non-blocking batches.
   - **Phase 3 (Switch Reads):** Switch application queries to the new column.
   - **Phase 4 (Contract):** Drop deprecated columns after older application versions are fully decommissioned.
4. **Flyway Production Safety Configuration:**
   - Disabling `flywayClean` (`clean-disabled: true`) to protect against accidental database drops.

## Project Structure
- `src/main/resources/db/migration/`:
  - `V1__init_schema.sql` (Initial accounts table).
  - `V2__expand_new_columns.sql` (Phase 1 Expand migration).
- `src/main/java/.../model/`:
  - `AccountEntity.java` (JPA Entity with expanded columns).
- `src/main/java/.../service/`:
  - `ExpandContractMigrationService.java` (Dual-write, batch backfill, read fallback).
- `src/main/java/.../controller/`:
  - `MigrationDiagnosticsController.java` (REST endpoints for migration status, dual-write, and backfill batches).
- `src/test/java/.../`:
  - `ExpandContractMigrationTest.java`
  - `FlywaySafetyConfigurationTest.java`
  - `MigrationDiagnosticsControllerTest.java`
  - `Module25IntegrationTest.java`

## How to Run Tests
```bash
mvn clean test
```

## Complete Documentation
For the full deep-dive technical incident guide, see [Module 25 Documentation](../../docs/module-25-database-migrations-flyway-locks-zero-downtime.md).
