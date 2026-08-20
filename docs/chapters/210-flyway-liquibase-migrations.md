---
chapter: 210
topic: Database Migrations — Flyway & Liquibase, Zero-Downtime DDL, Rollback Strategy
prerequisite_chapters: [10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200]
reference_system_node: Payment Service & Merchant Ledger ↔ PostgreSQL payment_db (MerchantPayoutProfile, flyway_schema_history, Expand-Contract Pattern, Non-Blocking DDL)
---

# Chapter 210: Database Migrations — Flyway & Liquibase, Zero-Downtime DDL, Rollback Strategy

## 1. Concept

In modern continuous delivery (CD) pipelines, backend applications are deployed dozens of times per day across Kubernetes clusters. While application code can be rolled forward or backward in seconds, **database schemas are stateful and persistent**. 

Using `spring.jpa.hibernate.ddl-auto=update` in production is catastrophic: Hibernate cannot rename columns, cannot delete obsolete columns, cannot apply non-blocking index creation, and frequently attempts conflicting DDL across concurrent pod startups.

**Flyway** and **Liquibase** provide deterministic, version-controlled database schema migrations. Every schema modification is captured in an immutable migration script, executed in strict sequence, and tracked in a metadata table (`flyway_schema_history` / `DATABASECHANGELOG`).

However, automated migrations alone do not guarantee zero downtime. If a developer renames or drops a column in a single migration, **rolling deployments and blue-green deployments will instantly break**, because old application pods and new application pods must run concurrently against the same database during the deployment window.

```
+-------------------------------------------------------------------------------------------------+
|                                 The Golden Rule of Database Migrations                          |
|                                                                                                 |
|  Database changes must ALWAYS be BACKWARD COMPATIBLE with the currently running application     |
|  version. Every breaking change must be decomposed into a 3-Phase Expand-Contract Rollout.     |
+-------------------------------------------------------------------------------------------------+
```

---

## 2. Internal Working

### Flyway Migration Lifecycle & `flyway_schema_history`

When Spring Boot boots with Flyway enabled:

```
Spring Boot Startup ──► Flyway.migrate()
                             │
                             ▼
     1. Acquire Migration Lock (pg_advisory_lock in PostgreSQL)
                             │
                             ▼
     2. Create/Read flyway_schema_history Table
                             │
                             ▼
     3. Scan Classpath for Migration Scripts (V*__*.sql, R__*.sql)
                             │
                             ▼
     4. Validate Checksums (CRC32) of Already Applied Scripts
                             │
            ┌────────────────┴────────────────┐
            ▼ (Mismatch)                      ▼ (Valid)
   Throw FlywayValidateException    5. Execute Pending Migrations
   (Aborts Startup!)                in Sequential Order
                                              │
                                              ▼
                                    6. Record Success in Metadata Table
                                              │
                                              ▼
                                    7. Release Advisory Lock
```

#### Migration Script Naming Conventions
- **Versioned Migrations (`V1__...sql`, `V2__...sql`)**: Executed exactly once. Never modified after being merged to `main`.
- **Undo Migrations (`U1__...sql`)**: Commercial Flyway feature for down-migrations (anti-pattern in cloud architectures).
- **Repeatable Migrations (`R__...sql`)**: Re-executed whenever their checksum changes (ideal for views, stored procedures, and triggers).

---

### The 3-Phase Expand-Contract (Parallel Run) Pattern

To rename or restructure a database column without taking downtime during a Kubernetes rolling update:

```
Phase 1: EXPAND (Release N)
┌─────────────────────────────────────────────────────────────┐
│ 1. DDL: Add new column (nullable): ALTER TABLE ADD COLUMN   │
│ 2. App Code: DUAL-WRITE to both old and new columns.        │
│ 3. App Code: READ from new column with fallback to old.     │
└─────────────────────────────────────────────────────────────┘
                             │
                             ▼ (Deploy Release N + Run Backfill Job)
Phase 2: BACKFILL (Background Job)
┌─────────────────────────────────────────────────────────────┐
│ 1. Async Batch Job: UPDATE table SET new_col = old_col      │
│    WHERE new_col IS NULL (Chunked batch updates).           │
└─────────────────────────────────────────────────────────────┘
                             │
                             ▼ (Verify 100% Backfilled)
Phase 3: CONTRACT (Release N+1)
┌─────────────────────────────────────────────────────────────┐
│ 1. App Code: Remove read fallback; read ONLY from new col.  │
│ 2. DDL: Add NOT NULL / CHECK constraints to new column.     │
│ 3. DDL: Drop old column in subsequent release: DROP COLUMN  │
└─────────────────────────────────────────────────────────────┘
```

---

### Non-Blocking DDL in PostgreSQL

DDL statements in PostgreSQL acquire table-level locks. If an `ALTER TABLE` requires an **`AccessExclusiveLock`**, it blocks all concurrent `SELECT`, `INSERT`, `UPDATE`, and `DELETE` queries on that table.

#### Lock Queue Head-of-Line Blocking
If an `ALTER TABLE` waits for a long-running 10-second `SELECT` query to finish, the `ALTER TABLE` enters the lock wait queue. **Every subsequent `SELECT` query behind it in the queue is blocked**, instantly exhausting application connection pools and triggering an outage!

```
[Running Query: SELECT * (Takes 10s)]
   ▲
   │ (Blocks)
[Pending DDL: ALTER TABLE ... (Waiting for AccessExclusiveLock)]
   ▲
   │ (Blocks ALL subsequent queries!)
[Blocked Queries: SELECT ..., INSERT ..., UPDATE ...] ──► Connection Pool Starvation!
```

#### The Rules of Non-Blocking DDL

| DDL Operation | Dangerous Approach | Production-Safe Non-Blocking Approach |
|---|---|---|
| **Create Index** | `CREATE INDEX idx ON orders (user_id)` *(Acquires ShareLock, blocks writes)* | `CREATE INDEX CONCURRENTLY idx ON orders (user_id)` *(No write locks)* |
| **Add Column with Default** | `ALTER TABLE orders ADD COLUMN status VARCHAR DEFAULT 'NEW'` *(Pre-PG11 rewrote entire table)* | In PostgreSQL 11+: Safe constant-time catalog update for non-volatile defaults. |
| **Add Foreign Key / Check Constraint** | `ALTER TABLE orders ADD CONSTRAINT fk_user ...` *(Scans and locks entire table)* | `ALTER TABLE orders ADD CONSTRAINT fk_user ... NOT VALID;`<br>`ALTER TABLE orders VALIDATE CONSTRAINT fk_user;` *(Validates without table lock)* |
| **Set Lock Timeout** | Running DDL without timeout | `SET lock_timeout = '2s';`<br>Fails fast instead of creating a blocking queue! |

---

### Safe Rollback Strategy: Why "Roll Forward" Wins

In distributed enterprise architectures, running down-migrations (`DROP TABLE`, `DROP COLUMN`) during an incident is extremely risky:
1. **Data Loss**: Rolling back DDL drops data written by the new application version during its execution window.
2. **Dual-Version Incompatibility**: Down-migrations break newly deployed pods that are still in-flight.

> [!IMPORTANT]
> **Production Rollback Mandate**:
> Never execute destructive down-migrations in production. If a release fails, roll back the **application container image** (which is safe because schema changes were backward-compatible in the Expand phase), and apply a new **forward migration (`V_next__...sql`)** to revert schema changes cleanly.

---

## 3. Enterprise Scenario: FinFlow Merchant Payout Engine

In the **FinFlow Payout Subsystem**:

```
Merchant Payout Service (20 pods in Kubernetes) ──► PostgreSQL (payment_db)
      │
      ├── Legacy Schema: merchant_payout_profiles (legacy_bank_account)
      └── Target Schema: merchant_payout_profiles (iban, swift_routing_code)
```

- **Deployment Mechanism**: Kubernetes Rolling Update (`maxSurge: 25%`, `maxUnavailable: 0`).
- **Rolling Window**: 15 minutes where Pods running Version 1.0 (Old) and Version 2.0 (New) run simultaneously against `payment_db`.
- **Traffic**: 4,000 req/sec peak.

---

## 4. Incorrect Implementation

Below is the dangerous approach where a developer renames a column directly in a single migration, instantly breaking old pods during deployment:

```sql
-- DANGEROUS MIGRATION: V2__breaking_column_rename.sql
-- Renames column in place without Expand-Contract!
ALTER TABLE merchant_payout_profiles RENAME COLUMN legacy_bank_account TO iban;
```

```java
package com.finflow.chapter210.incorrect;

import com.finflow.chapter210.domain.MerchantPayoutProfile;
import com.finflow.chapter210.repository.MerchantPayoutProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * INCORRECT IMPLEMENTATION:
 * 1. Hard assumption that old/new columns are immediately consistent without Expand-Contract.
 * 2. Unsafe reads failing immediately with NullPointerException during rolling updates.
 */
@Service
public class BreakingMigrationServiceIncorrect {

    private final MerchantPayoutProfileRepository repository;

    public BreakingMigrationServiceIncorrect(MerchantPayoutProfileRepository repository) {
        this.repository = repository;
    }

    /**
     * Anti-Pattern: Hard assumption that new column is always populated immediately.
     * Crashes on legacy un-backfilled records during rolling deployments!
     */
    @Transactional(readOnly = true)
    public String getIbanUnsafe(String merchantId) {
        MerchantPayoutProfile profile = repository.findByMerchantId(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + merchantId));

        // CRASH: On un-backfilled legacy records, getIban() returns NULL -> NullPointerException!
        return profile.getIban().toUpperCase();
    }
}
```

---

## 5. Production Incident

### Incident Timeline

| Time (UTC) | Event / Telemetry |
|---|---|
| **15:00:00** | CD pipeline initiates Kubernetes rolling update for Payment Service (v1.0 $\to$ v2.0). |
| **15:00:05** | Pod 1 boots, executes Flyway migration `V2__breaking_column_rename.sql`, renaming `legacy_bank_account` to `iban`. |
| **15:00:10** | Remaining 19 Pods running v1.0 attempt to execute payment queries: `SELECT legacy_bank_account FROM merchant_payout_profiles`. |
| **15:00:15** | PostgreSQL throws `PSQLException: ERROR: column "legacy_bank_account" does not exist`. |
| **15:00:30** | 95% of customer payout requests routed to v1.0 pods fail with HTTP 500. |
| **15:02:00** | PagerDuty fires SEV-1 Alert: `PayoutService_5xx_Error_Spike (95% failure rate)`. $8.2M in merchant disbursements stalled. |
| **15:05:00** | Engineers attempt to roll back Kubernetes deployment to v1.0, but v1.0 pods fail to start because the database schema was already irreversibly renamed! |
| **15:18:00** | Emergency forward patch deployed: Database column renamed back, Expand-Contract dual-write code applied. |
| **15:25:00** | 100% of payout requests succeed. Outage resolved. |

---

## 6. Logs & Diagnostics

### 1. Old Pod SQL Crash Log During Rolling Deployment
```text
2026-08-20T15:00:15.112Z ERROR [payment-service,trace_id=1a2b3c,span_id=4d5e6f] 1 --- [http-nio-8080-exec-14] o.h.engine.jdbc.spi.SqlExceptionHelper   : ERROR: column m1_0.legacy_bank_account does not exist
  Position: 42

org.springframework.dao.InvalidDataAccessResourceUsageException: could not execute query [select m1_0.id,m1_0.legacy_bank_account,m1_0.merchant_id from merchant_payout_profiles m1_0 where m1_0.merchant_id=?]
	at org.springframework.orm.jpa.vendor.HibernateJpaDialect.convertHibernateAccessException(HibernateJpaDialect.java:279)
	at org.springframework.orm.jpa.vendor.HibernateJpaDialect.translateExceptionIfPossible(HibernateJpaDialect.java:241)
Caused by: org.postgresql.util.PSQLException: ERROR: column m1_0.legacy_bank_account does not exist
```

### 2. Flyway Checksum Mismatch Error (Editing Applied Scripts)
```text
2026-08-20T15:08:22.401Z ERROR [payment-service,,] 1 --- [main] o.s.boot.SpringApplication : Application run failed

org.flywaydb.core.api.exception.FlywayValidateException: Validate failed: Migrations have failed validation
Migration checksum mismatch for migration version 2
-> Applied to database : 1421095821
-> Resolved locally    : 894120512. Either revert the changes to the migration, or run repair to update the schema history.
```

---

## 7. Root Cause Analysis

```
+-------------------------------------------------------------------------------------------------+
|                               Breaking Migration Root Cause Chain                               |
|                                                                                                 |
|  1. Destructive DDL applied in single release (RENAME COLUMN / DROP COLUMN)                     |
|     └── Database column modified instantly across the entire shared database.                   |
|                                                                                                 |
|  2. Rolling Deployment Version Incompatibility                                                 |
|     ├── 19 Old Pods (v1.0) continue running for 15 minutes during gradual container replacement.|
|     └── Old Pods execute SQL queries referencing the old column name -> SQL Crash on 95% load!  |
|                                                                                                 |
|  3. Rollback Trap                                                                               |
|     └── Kubernetes image rollback to v1.0 fails because database schema is no longer compatible |
|         with v1.0 code, creating an unrecoverable deployment deadlock!                          |
+-------------------------------------------------------------------------------------------------+
```

---

## 8. Debugging Process

```
[1. Alert Triage] Inspect 5xx error surge during Kubernetes rollout window
       │
[2. Migration History Inspection] Query flyway_schema_history for recent migration state
       │
[3. Lock Queue Check] Query pg_locks to verify no DDL is blocking live traffic
       │
[4. Repair Checksums] If local migration was altered in development, run flyway:repair
       │
[5. Forward Remediation] Apply Expand-Contract dual-write code and roll forward
```

### Step 1: Query Flyway Schema History
```sql
SELECT installed_rank, version, description, type, script, checksum, installed_on, execution_time, success 
FROM flyway_schema_history 
ORDER BY installed_rank DESC;
```

### Step 2: Repair Flyway Checksum Table
If an applied migration script comment or formatting was modified:
```bash
mvn flyway:repair
# Or via Spring Boot startup property:
# spring.flyway.repair-on-error: true (development only!)
```

---

## 9. Correct Implementation

### 1. Flyway Versioned Migration Scripts

#### `V1__init_merchant_payout_schema.sql`
```sql
CREATE TABLE merchant_payout_profiles (
    id VARCHAR(36) NOT NULL,
    merchant_id VARCHAR(64) NOT NULL,
    payout_currency VARCHAR(3) NOT NULL,
    legacy_bank_account VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_merchant_payout UNIQUE (merchant_id)
);
```

#### `V2__expand_iban_routing_columns.sql` (Phase 1: Expand)
```sql
-- Phase 1 (Expand): Add nullable new columns for international IBAN and SWIFT routing
ALTER TABLE merchant_payout_profiles ADD COLUMN iban VARCHAR(34);
ALTER TABLE merchant_payout_profiles ADD COLUMN swift_routing_code VARCHAR(11);
```

#### `V3__add_performance_indexes.sql`
```sql
CREATE INDEX idx_payout_status_created ON merchant_payout_profiles (status, created_at);
```

#### `R__merchant_payout_views.sql` (Repeatable Migration)
```sql
CREATE OR REPLACE VIEW v_active_merchant_payouts AS
SELECT 
    id,
    merchant_id,
    payout_currency,
    status,
    COALESCE(iban, legacy_bank_account) AS effective_account_number,
    swift_routing_code,
    created_at
FROM merchant_payout_profiles
WHERE status = 'ACTIVE';
```

---

### 2. Expand-Contract Dual-Write Service: `ExpandContractPayoutService.java`

```java
package com.finflow.chapter210.correct;

import com.finflow.chapter210.domain.MerchantPayoutProfile;
import com.finflow.chapter210.repository.MerchantPayoutProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class ExpandContractPayoutService {

    private final MerchantPayoutProfileRepository repository;

    public ExpandContractPayoutService(MerchantPayoutProfileRepository repository) {
        this.repository = repository;
    }

    /**
     * Dual-Write: Writes to both new column (iban) and legacy column (legacy_bank_account).
     * Guarantees zero downtime across old and new pods during rolling updates.
     */
    @Transactional
    public MerchantPayoutProfile registerPayoutProfile(String merchantId, String currency, String accountNumber, String swiftCode) {
        MerchantPayoutProfile profile = new MerchantPayoutProfile(
                UUID.randomUUID().toString(),
                merchantId,
                currency,
                accountNumber, // Dual-write legacy column
                accountNumber, // Dual-write new column
                swiftCode,
                "ACTIVE",
                Instant.now()
        );
        return repository.save(profile);
    }

    /**
     * Resilient Read Fallback: Reads new column; if null (pre-migration row), falls back to legacy.
     */
    @Transactional(readOnly = true)
    public String resolveEffectiveAccountNumber(String merchantId) {
        MerchantPayoutProfile profile = repository.findByMerchantId(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + merchantId));

        if (profile.getIban() != null && !profile.getIban().isBlank()) {
            return profile.getIban();
        }
        return profile.getLegacyBankAccount();
    }
}
```

---

### 3. Programmatic Migration Health Service: `FlywayMigrationInfoService.java`

```java
package com.finflow.chapter210.correct;

import com.finflow.chapter210.dto.MigrationInfoSummary;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class FlywayMigrationInfoService {

    private final Flyway flyway;

    public FlywayMigrationInfoService(Flyway flyway) {
        this.flyway = flyway;
    }

    public List<MigrationInfoSummary> getMigrationHistory() {
        MigrationInfo[] all = flyway.info().all();
        return Arrays.stream(all)
                .map(info -> new MigrationInfoSummary(
                        info.getVersion() != null ? info.getVersion().getVersion() : "REPEATABLE",
                        info.getDescription(),
                        info.getType().name(),
                        info.getScript(),
                        info.getState().name(),
                        info.getChecksum()
                ))
                .toList();
    }

    public boolean isSchemaUpToDate() {
        MigrationInfo current = flyway.info().current();
        return current != null && current.getState().isApplied();
    }
}
```

---

## 10. Performance Comparison

Comparison during a 20-pod Kubernetes rolling update under 4,000 req/sec load.

| Metric | Destructive Migration (In-place Rename) | Zero-Downtime Expand-Contract Pattern |
|---|---|---|
| **Deployment Success Rate** | 5.0% *(95% 500 errors on old pods)* | **100.0% (Zero errors)** |
| **Table Lock Hold Time** | Indefinite (blocked behind queries) | **< 2ms (Lock timeout protected)** |
| **Old Pod Compatibility** | Broken immediately | **100% Backward Compatible** |
| **Rollback Capability** | Broken (Database corrupted for v1.0) | **Instant Container Rollback Safe** |
| **Data Loss During Deployment** | $8.2M stalled disbursements | **$0.00 Lost** |
| **Schema Drift / Checksum Errors** | Common | **Zero (Flyway validated)** |

---

## 11. Best Practices

### The Do's
- **DO use Expand-Contract for all breaking changes**: Add column (nullable) $\to$ Dual write $\to$ Backfill $\to$ Drop old column in next release.
- **DO set `spring.jpa.hibernate.ddl-auto=validate` in production**: Ensure Hibernate validates entity mappings against Flyway migrations without executing raw DDL.
- **DO set short lock timeouts in DDL scripts**: `SET lock_timeout = '2s';` prevents DDL from creating blocking queues in PostgreSQL.
- **DO use Repeatable Migrations (`R__*.sql`) for views and functions**: Keeps SQL view definitions idempotent and clean.
- **DO baseline existing legacy databases**: Use `spring.flyway.baseline-on-migrate=true` when introducing Flyway to an existing system.

### The Don'ts
- **DON'T edit already-applied migration scripts**: Modifying committed migration files changes the CRC32 checksum and causes `FlywayValidateException`.
- **DON'T create indexes without `CONCURRENTLY` in PostgreSQL**: `CREATE INDEX` locks the table against writes for the entire duration of index creation.
- **DON'T drop columns in the same release that stops reading them**: Old pods still in-flight will immediately crash.
- **DON'T execute long-running data migrations inside Flyway DDL**: Huge `UPDATE table SET ...` statements block application startup; execute backfills via background batch workers.

---

## 12. Common Mistakes

### Mistake 1: Modifying a Merged Flyway Script
A developer edits `V2__add_column.sql` to add a comment or change a type.
**Why it fails**: When deployed, Flyway calculates the CRC32 checksum of `V2`, discovers it does not match the checksum stored in `flyway_schema_history`, and aborts application startup with `FlywayValidateException`.
**Production Fix**: Create a new versioned migration script (`V4__fix_column_type.sql`).

### Mistake 2: The Missing `CONCURRENTLY` Index Lock Storm
Running `CREATE INDEX idx_orders_created ON orders (created_at);` on a 50-million-row table.
**Why it fails**: PostgreSQL acquires a `ShareLock`, blocking all `INSERT`, `UPDATE`, and `DELETE` queries on `orders` for 8 minutes, knocking the application offline.
**Production Fix**: Run `CREATE INDEX CONCURRENTLY` (outside of a multi-statement transaction).

---

## 13. Interview Questions

### Junior Tier
**Q: Why should production Spring Boot applications use Flyway or Liquibase instead of `spring.jpa.hibernate.ddl-auto=update`?**
> **Answer**: `hibernate.ddl-auto=update` cannot safely handle non-trivial schema evolution: it cannot rename columns, cannot delete obsolete columns, cannot create partial/covering/concurrent indexes, and has no audit history. Under high-availability multi-instance deployments, concurrent pod startups with `ddl-auto=update` execute conflicting DDL simultaneously, causing race conditions and schema corruption. Flyway provides immutable, version-controlled, auditable, and deterministic SQL migrations.

### Mid Tier
**Q: Explain the 3-Phase Expand-Contract (Parallel Run) pattern and why it is required for zero-downtime rolling deployments.**
> **Answer**: During a Kubernetes rolling update or blue-green deployment, old application pods (Version N) and new application pods (Version N+1) run concurrently against the database for minutes. In Expand-Contract:
> 1. **Phase 1 (Expand)**: Add the new column as nullable in the database. Application code dual-writes to both old and new columns and reads from new with fallback to old.
> 2. **Phase 2 (Backfill)**: An asynchronous batch worker populates the new column for historical rows.
> 3. **Phase 3 (Contract)**: In the subsequent release, the application reads exclusively from the new column, and the old column is safely dropped.

### Senior Tier
**Q: How does Lock Queue Head-of-Line blocking occur during DDL in PostgreSQL, and how do you prevent it from taking down production?**
> **Answer**: In PostgreSQL, `ALTER TABLE` requires an `AccessExclusiveLock`. If a long-running query (e.g. a 10s analytical `SELECT`) is currently executing, the `ALTER TABLE` must wait in the lock queue. Crucially, all subsequent queries (`SELECT`, `INSERT`, `UPDATE`) queue *behind* the waiting `ALTER TABLE` in the lock queue, blocking all database traffic. To prevent this, every DDL migration must configure a strict lock timeout: `SET lock_timeout = '2s';`. If the lock cannot be acquired in 2 seconds, the DDL aborts and fails fast without blocking incoming traffic.

### Staff Tier
**Q: How does Flyway prevent concurrent migration execution when 20 microservice pods boot simultaneously?**
> **Answer**: Flyway utilizes database-specific locking mechanisms before reading `flyway_schema_history`. In PostgreSQL, Flyway acquires an **Advisory Lock** via `pg_advisory_lock(checksum)` using a unique 64-bit key derived from the schema history table name. The first booting pod acquires the advisory lock and executes pending migrations. The remaining 19 pods block and poll until the lock is released, verify that all migrations are marked `SUCCESS` in `flyway_schema_history`, and proceed with application startup.

### Principal Tier
**Q: Design a zero-downtime schema evolution strategy to change a primary key column from `INT` to `BIGINT` on a 200-million-row financial ledger table without locking the table or taking downtime.**
> **Answer**: Altering an existing column type from `INT` to `BIGINT` in PostgreSQL rewrites the entire table heap under an `AccessExclusiveLock` (hours of downtime). A Principal-level solution uses **Shadow Column Migration with Trigger-Assisted Dual-Write**:
> 1. **Expand**: Add a shadow column: `ALTER TABLE ledger ADD COLUMN id_v2 BIGINT;` (instant catalog update).
> 2. **Database Trigger**: Create a row-level `BEFORE INSERT OR UPDATE` trigger copying `NEW.id` to `NEW.id_v2`.
> 3. **Chunked Backfill Worker**: Run a background batch script updating historical rows in small batches: `UPDATE ledger SET id_v2 = id WHERE id_v2 IS NULL AND id BETWEEN ? AND ?`.
> 4. **Concurrent Indexing**: Build shadow indexes: `CREATE UNIQUE INDEX CONCURRENTLY idx_ledger_id_v2 ON ledger (id_v2);`.
> 5. **Swap & Contract**: In a brief transaction with `lock_timeout = '2s'`, drop the trigger, drop the old primary key constraint, and rename `id_v2` to `id`.

---

## 14. Hands-on Exercise

### Objective
Implement a safe Expand-Contract column rename from `legacy_bank_account` to `iban` in FinFlow:
1. Write Flyway migration scripts adding nullable `iban`.
2. Implement dual-write and read-fallback in `ExpandContractPayoutService`.
3. Verify that legacy un-backfilled records resolve seamlessly without `NullPointerException`.

### Solution

#### Step 1: Flyway Migration `V2__expand_iban.sql`
```sql
ALTER TABLE merchant_payout_profiles ADD COLUMN iban VARCHAR(34);
```

#### Step 2: Service Layer Implementation
```java
@Service
public class PayoutService {

    private final MerchantPayoutProfileRepository repository;

    public PayoutService(MerchantPayoutProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void updateAccount(String merchantId, String newAccount) {
        MerchantPayoutProfile profile = repository.findByMerchantId(merchantId).orElseThrow();
        // Dual-write
        profile.setLegacyBankAccount(newAccount);
        profile.setIban(newAccount);
        repository.save(profile);
    }

    @Transactional(readOnly = true)
    public String getEffectiveAccount(String merchantId) {
        MerchantPayoutProfile profile = repository.findByMerchantId(merchantId).orElseThrow();
        // Resilient read fallback
        return profile.getIban() != null ? profile.getIban() : profile.getLegacyBankAccount();
    }
}
```

---

## 15. Advanced Challenge: Trigger-Assisted Zero-Downtime Column Type Migration

### Enterprise Problem Statement
Migrate `merchant_id` from `VARCHAR(32)` to `UUID` on a 50-million-row table while handling 3,000 writes/sec without table locks.

### Enterprise Solution

```sql
-- Step 1: Add shadow column
ALTER TABLE merchant_payout_profiles ADD COLUMN merchant_uuid UUID;

-- Step 2: Create trigger function for automatic dual-write
CREATE OR REPLACE FUNCTION sync_merchant_uuid()
RETURNS TRIGGER AS $$
BEGIN
    NEW.merchant_uuid = NEW.merchant_id::UUID;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sync_merchant_uuid
BEFORE INSERT OR UPDATE ON merchant_payout_profiles
FOR EACH ROW EXECUTE FUNCTION sync_merchant_uuid();

-- Step 3: Build concurrent index on shadow column
CREATE INDEX CONCURRENTLY idx_payout_merchant_uuid ON merchant_payout_profiles (merchant_uuid);

-- Step 4: Background batch backfill (executed via Spring Batch worker)
-- UPDATE merchant_payout_profiles SET merchant_uuid = merchant_id::UUID WHERE merchant_uuid IS NULL AND id BETWEEN ...
```

---

## 16. Production Checklist

Before approving any pull request containing database migrations:

- [ ] **`ddl-auto=validate` Enforced**: Verify `spring.jpa.hibernate.ddl-auto` is set to `validate`.
- [ ] **Expand-Contract Pattern Applied**: Confirm no columns or tables are renamed/dropped in a single release.
- [ ] **Lock Timeout in DDL Scripts**: Ensure all DDL scripts begin with `SET lock_timeout = '2s';`.
- [ ] **`CONCURRENTLY` for Index Creation**: Verify PostgreSQL indexes are created using `CREATE INDEX CONCURRENTLY`.
- [ ] **No Destructive Down-Migrations**: Ensure rollbacks are executed by reverting application code and applying forward migrations.
- [ ] **No Heavy Data Backfills in Flyway**: Confirm data migrations $> 100,000$ rows are offloaded to background batch jobs.
- [ ] **Repeatable Migrations Idempotent**: Verify all `R__*.sql` scripts use `CREATE OR REPLACE` or `DROP IF EXISTS`.
