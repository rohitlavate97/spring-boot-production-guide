# Module 25: Database Migrations: Flyway, Locks & Zero-Downtime

## Issue 25.1: Flyway Migration Lock Outages, `AccessExclusiveLock` Table Rewrites, and Rolling Deployment Column Mismatches

---

### 1. Scenario

During a major core banking release on the **FinFlow Merchant Accounts & Global Ledger**:
1. An engineer added a Flyway migration script `V14__add_merchant_risk_tier.sql` containing:
   `ALTER TABLE accounts ADD COLUMN risk_tier VARCHAR(32) NOT NULL DEFAULT 'STANDARD';`
2. In production, the `accounts` table held **50 million records**. The DDL statement acquired a PostgreSQL **`AccessExclusiveLock`**, blocking all read and write traffic on the table.
3. Every active API transaction queued up waiting for the lock. Within 2.5 seconds, all 100 HikariCP connections in the pool were **completely exhausted**, causing cascading `Connection is not available, request timed out after 30000ms` errors and taking down the entire clearing engine (**The Table Lock Outage**).
4. Simultaneously, a column rename was deployed: `V15__rename_account_number_to_uuid.sql`. The migration script dropped `account_number` and added `account_uuid`.
5. Kubernetes initiated a standard rolling deployment over 10 minutes. While 4 new Version 2 pods were running, 6 old Version 1 pods were still serving live customer traffic behind the load balancer.
6. The old Version 1 pods queried `SELECT account_number FROM accounts`, throwing catastrophic **`PSQLException: column "account_number" does not exist` on 60% of user requests** (**The Breaking Rolling Deployment Disaster**).
7. In addition, 10 pods scaling up simultaneously all attempted to acquire the Flyway lock on `flyway_schema_history`. When Pod 1 timed out on its Kubernetes startup probe and was restarted by Kubelet, the migration lock was left in an orphaned state, permanently blocking all other pods from booting.

---

### 2. Symptoms

```text
1. Immediate HikariCP Connection Pool Exhaustion on Migration:
   HikariPool-1 - Connection is not available, request timed out after 30000ms.
   PostgreSQL pg_stat_activity shows hundreds of SELECT/UPDATE queries blocked behind an ALTER TABLE.

2. Breaking Schema Errors During Rolling Deployments:
   org.postgresql.util.PSQLException: ERROR: column "account_number" does not exist.
   Old pods fail live HTTP requests while new pods deploy.

3. Flyway Lock Acquisition Timeouts:
   org.flywaydb.core.api.FlywayException: Unable to obtain table lock for flyway_schema_history.

4. Checksum Mismatch Startup Failures:
   Validate failed: Migration checksum mismatch for migration version 12.

5. Database Lock Deadlocks Between Concurrent Transactions and DDL:
   ERROR: deadlock detected - Process 4201 waits for AccessExclusiveLock on accounts;
   blocked by process 4198 which waits for RowExclusiveLock.
```

---

### 3. Possible Root Causes

1. **Destructive Single-Step DDL (Dropping/Renaming Columns):** Modifying database schemas in a way that breaks backward compatibility with older application pods still running during rolling releases.
2. **Heavy DDL Operations Without Lock Timeouts:** Running table alters or non-concurrent index creations without `SET lock_timeout = '2s'`, allowing DDL to wait indefinitely and queue behind long-running queries.
3. **Running Migrations Inside Application Pods:** Running Flyway inside standard multi-replica application pods rather than as a dedicated, single-execution Kubernetes Job / InitContainer.
4. **Altering Tables with Non-Constant Defaults on Legacy Databases:** Triggering full table rewrites and exclusive locks on massive tables.
5. **Modifying Applied Migration Files:** Changing already-applied SQL migration scripts in Git, causing Flyway checksum validation errors.

---

### 4. Architecture Context: The 4-Phase Expand and Contract Zero-Downtime Pattern

```text
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                     THE 4-PHASE EXPAND AND CONTRACT (PARALLEL RUN) PATTERN                      │
│                                                                                                 │
│  PHASE 1: EXPAND (Release N)                                                                    │
│  - SQL: ALTER TABLE accounts ADD COLUMN account_uuid VARCHAR(64); (Nullable / Non-blocking)     │
│  - App Code: Dual-Write (Writes to BOTH account_number and account_uuid).                       │
│  - Result: V1 Pods write account_number; V2 Pods write BOTH. Zero downtime!                     │
│                                                                                                 │
│  PHASE 2: BACKFILL (Background Data Migration)                                                  │
│  - Background Job: Batch updates historical records where account_uuid IS NULL.                 │
│  - Processed in small batches (e.g. 1,000 rows/batch) with pause to avoid lock contention.      │
│                                                                                                 │
│  PHASE 3: SWITCH READS (Release N+1)                                                            │
│  - App Code: All reads switched to account_uuid. (Dual-write continues).                        │
│  - All running pods now read and write the new column.                                          │
│                                                                                                 │
│  PHASE 4: CONTRACT (Release N+2)                                                                │
│  - App Code: Dual-write removed. Only writes to account_uuid.                                   │
│  - SQL: ALTER TABLE accounts DROP COLUMN account_number; (Safe after all old pods dead!)        │
│                                                                                                 │
│  ┌───────────────────────────────────────────────────────────────────────────────────────────┐  │
│  │ KUBERNETES ZERO-DOWNTIME MIGRATION RUNNER ARCHITECTURE:                                   │  │
│  │                                                                                           │  │
│  │   CI/CD Pipeline ──► 1. Deploy Single-Replica K8s Job: [Flyway Migration Job]             │  │
│  │                                  │ (Applies DDL with lock_timeout = 2s)                   │  │
│  │                                  ▼                                                        │  │
│  │                      2. Migration Job Completes Successfully (Exit 0)                     │  │
│  │                                  │                                                        │  │
│  │                                  ▼                                                        │  │
│  │                      3. Trigger Rolling Deployment of Application Pods (10 Replicas)      │  │
│  │                         (Application pods run with `spring.flyway.enabled: false`)        │  │
│  └───────────────────────────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 5. How to Reproduce the Issues

#### ❌ Anti-Pattern 1: Direct Column Rename in Single Migration (Breaks Rolling Deploy)
```sql
-- ❌ FATAL ANTI-PATTERN: Version 1 pods will crash immediately when this runs!
ALTER TABLE accounts RENAME COLUMN account_number TO account_uuid;
```

#### ❌ Anti-Pattern 2: DDL Without Lock Timeout on Massive Table
```sql
-- ❌ FATAL ANTI-PATTERN: Queues behind long queries, blocking all incoming SELECT/UPDATEs!
ALTER TABLE transactions ADD COLUMN merchant_tier VARCHAR(50) NOT NULL DEFAULT 'STANDARD';
```

#### ❌ Anti-Pattern 3: Non-Concurrent Index Creation in PostgreSQL
```sql
-- ❌ ANTI-PATTERN: Acquires ShareLock on table, blocking all concurrent writes!
CREATE INDEX idx_accounts_balance ON accounts (balance);
```

---

### 6. Evidence Collection & Diagnostic Probing

#### Method 1: Inspect Blocked Queries and Lock Types in PostgreSQL
```sql
SELECT blocked_locks.pid     AS blocked_pid,
       blocked_activity.query AS blocked_statement,
       blocking_locks.pid    AS blocking_pid,
       blocking_activity.query AS blocking_statement,
       blocked_locks.mode    AS blocked_lock_mode,
       blocking_locks.mode   AS blocking_lock_mode
FROM  pg_catalog.pg_locks         blocked_locks
JOIN pg_catalog.pg_stat_activity blocked_activity ON blocked_activity.pid = blocked_locks.pid
JOIN pg_catalog.pg_locks         blocking_locks 
    ON blocking_locks.locktype = blocked_locks.locktype
    AND blocking_locks.relation = blocked_locks.relation
JOIN pg_catalog.pg_stat_activity blocking_activity ON blocking_activity.pid = blocking_locks.pid
WHERE NOT blocked_locks.granted;
```

#### Method 2: Check Flyway Schema History Status
```sql
SELECT installed_rank, version, description, type, script, checksum, installed_by, execution_time, success
FROM flyway_schema_history
ORDER BY installed_rank DESC;
```

---

### 7. Step-by-Step Debugging Procedure

```text
Step 1: Inspect pg_locks for Blocking AccessExclusiveLock.
        If DDL is blocking live production traffic, terminate the DDL process PID (`SELECT pg_terminate_backend(PID)`).

Step 2: Enforce Expand & Contract Pattern for All Schema Changes.
        Never rename or drop columns in a single release. Follow Expand -> Backfill -> Switch -> Contract.

Step 3: Add Lock Timeouts to All Migration Scripts.
        Prepend `SET lock_timeout = '2s';` to every Flyway SQL migration.

Step 4: Create Indexes Concurrently.
        Use `CREATE INDEX CONCURRENTLY` outside of transaction blocks.

Step 5: Move Flyway Execution to Kubernetes InitContainers or Pre-Sync Jobs.
        Disable Flyway execution inside standard application pods (`spring.flyway.enabled: false`).
```

---

### 8. Technical Root Cause Deep-Dive

#### 1. PostgreSQL Lock Hierarchy & The DDL Lock Queue Trap
- Standard queries (`SELECT`) acquire `AccessShareLock`.
- Write queries (`INSERT`, `UPDATE`, `DELETE`) acquire `RowExclusiveLock`.
- `AccessShareLock` and `RowExclusiveLock` are mutually compatible; thousands of concurrent readers and writers run simultaneously without blocking.
- However, `ALTER TABLE` requires **`AccessExclusiveLock`**, which conflicts with ALL lock modes.
- When `ALTER TABLE` is issued:
  1. It waits for all active `SELECT`/`UPDATE` transactions to complete.
  2. PostgreSQL places `ALTER TABLE` at the front of the lock queue.
  3. **All subsequent `SELECT` and `UPDATE` queries are queued BEHIND the `ALTER TABLE`!**
  4. Even if the alter takes 5ms to execute, waiting 30 seconds for a slow query to finish blocks all application traffic for 30 seconds!

#### 2. The Safe Lock Timeout Solution
By configuring:
```sql
SET lock_timeout = '2s';
ALTER TABLE accounts ADD COLUMN risk_tier VARCHAR(32);
```
If the lock cannot be acquired within 2 seconds, the DDL fails fast and releases its position in the queue, preventing production query starvation.

#### 3. Flyway Checksum Mechanics & `flyway:repair`
- Flyway calculates a CRC32 checksum for every SQL script and records it in `flyway_schema_history`.
- If an engineer modifies whitespace, comments, or SQL in an already-applied file `V5__init.sql`, Flyway fails startup with `ValidateException: Checksum mismatch`.
- In production, applied migrations must be treated as **immutable**. Fixes must be added as new versioned migrations (`V6__fix.sql`). If a cosmetic change was made, `flyway repair` recalculates checksums.

---

### 9. Production-Grade Fixes

#### ✅ Fix 1: Expand & Contract Service Implementation (`ExpandContractMigrationService.java`)
```java
@Service
public class ExpandContractMigrationService {

    @Transactional
    public AccountEntity createAccountWithDualWrite(String accountNumber, BigDecimal balance) {
        String generatedUuid = "UUID-" + UUID.randomUUID().toString().substring(0, 8);
        AccountEntity account = new AccountEntity(accountNumber, generatedUuid, balance, "STANDARD");
        return accountRepository.save(account); // Dual-writes both columns!
    }

    @Transactional
    public int backfillBatch(int batchSize) {
        List<AccountEntity> pending = accountRepository.findAccountsNeedingBackfill(PageRequest.of(0, batchSize));
        for (AccountEntity entity : pending) {
            entity.setAccountUuid("UUID-BACKFILLED-" + entity.getId());
            accountRepository.save(entity);
        }
        return pending.size();
    }
}
```

#### ✅ Fix 2: Safe PostgreSQL Migration Template
```sql
-- Safe PostgreSQL DDL Template with Lock Timeout
SET lock_timeout = '2s';
SET statement_timeout = '30s';

-- 1. Add column as nullable (zero table lock wait)
ALTER TABLE accounts ADD COLUMN account_uuid VARCHAR(64);

-- 2. Add default value for new rows (PostgreSQL 11+ metadata-only update)
ALTER TABLE accounts ALTER COLUMN risk_tier SET DEFAULT 'STANDARD';
```

#### ✅ Fix 3: Kubernetes Migration Job Spec
```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: finflow-flyway-migration
spec:
  template:
    spec:
      containers:
        - name: flyway
          image: flyway/flyway:10-alpine
          args: ["-url=jdbc:postgresql://postgres:5432/finflow", "-user=finflow", "-password=$(DB_PASS)", "migrate"]
      restartPolicy: OnFailure
```

---

### 10. Verification

1. **Dual-Write Compatibility Test:** Run `ExpandContractMigrationTest.java` to verify that both legacy and expanded columns are populated during Phase 1.
2. **Backfill Batch Test:** Verify legacy rows with `NULL` new columns are backfilled in small batches without blocking.
3. **Flyway Safety Test:** Run `FlywaySafetyConfigurationTest.java` to verify `cleanDisabled: true` and that all migrations execute cleanly.
4. **Integration Test:** Run `Module25IntegrationTest.java` to verify Spring Boot context and Actuator health.

---

### 11. Prevention & Production Readiness

1. **Rule: Never Run Destructive DDL in a Single Release:**
   Enforce Expand & Contract over multiple software releases for all column renames, type changes, and column removals.
2. **Rule: Always Set `clean-disabled: true` in Production:**
   Protect against catastrophic accidental schema drops.
3. **Prometheus Alerting Rule for Failed Flyway Migrations:**
```yaml
- alert: FlywayMigrationFailed
  expr: flyway_schema_history_success == 0
  for: 1m
  labels:
    severity: critical
  annotations:
    summary: "Database migration failed on {{ $labels.instance }}"
```

---

### 12. Interview & Production Questions

#### Interview Questions
1. **Q: What is the Expand and Contract pattern, and why is it required for zero-downtime microservice deployments?**
   *Answer:* Expand and Contract divides breaking database schema changes across multiple releases. Phase 1 (Expand) adds new columns as nullable and dual-writes. Phase 2 backfills data. Phase 3 switches reads. Phase 4 (Contract) drops legacy columns once all older application pods are decommissioned, ensuring running pods never encounter missing columns.
2. **Q: Why does running `ALTER TABLE` on a busy PostgreSQL table cause connection pool exhaustion?**
   *Answer:* `ALTER TABLE` requests an `AccessExclusiveLock`. PostgreSQL places the request in the lock queue behind active long-running queries and blocks all *subsequent* `SELECT` and `UPDATE` queries behind it. HikariCP connection pools exhaust in seconds while waiting for the lock.
3. **Q: Why should Flyway migrations be executed via a dedicated Kubernetes Job rather than inside application pod startup?**
   *Answer:* Running Flyway on multiple pods scaling concurrently creates lock contention on `flyway_schema_history`. If a pod is killed during startup, the lock is orphaned, preventing all other replicas from starting. A single-replica Kubernetes Job guarantees exactly-once migration execution before application pods boot.
4. **Q: What is the danger of `spring.jpa.hibernate.ddl-auto: update` in production?**
   *Answer:* Hibernate automatic schema update can execute uncontrolled, unversioned DDL on application startup, acquire exclusive locks without timeouts, drop constraints unexpectedly, and make schema rollbacks impossible. Production must always use `ddl-auto: validate` with versioned Flyway scripts.
5. **Q: Why is `CREATE INDEX CONCURRENTLY` necessary in production PostgreSQL?**
   *Answer:* Standard `CREATE INDEX` acquires a `ShareLock` that blocks all concurrent `INSERT`, `UPDATE`, and `DELETE` writes on the table until index creation finishes. `CONCURRENTLY` builds the index without blocking writes.

#### Production Incident Questions
1. **Incident:** During a rolling deployment, 50% of HTTP requests fail with `column "tax_id" does not exist`. What happened?
   *Diagnosis:* Migration dropped or renamed `tax_id` in a single step while older Version 1 pods were still serving traffic. Fix: Follow the Expand & Contract pattern.
2. **Incident:** An `ALTER TABLE` statement crashed production because it waited for 45 minutes and blocked all traffic. How do you prevent this?
   *Diagnosis:* Missing lock timeout. Fix: Add `SET lock_timeout = '2s';` to all migration scripts so DDL fails fast rather than queueing.
3. **Incident:** All 8 application pods fail to start with `Unable to obtain table lock for flyway_schema_history`. How do you recover?
   *Diagnosis:* An orphaned Flyway lock from a killed pod. Fix: Run `flyway repair` or clear the lock manually, then move Flyway execution to a Kubernetes Pre-Sync Job.
4. **Incident:** Flyway fails on startup with `Checksum mismatch for migration V4__create_ledger.sql`. Why?
   *Diagnosis:* An applied migration file was edited in Git after being applied to the database. Fix: Revert the file in Git and create a new versioned migration script (`V5__fix.sql`), or run `flyway repair` if change was cosmetic.
5. **Incident:** A developer accidentally ran `mvn flyway:clean` against the staging database, dropping all tables. How do you prevent this?
   *Diagnosis:* `clean-disabled` was set to `false`. Fix: Set `spring.flyway.clean-disabled: true` in all environment configurations.

#### Trick Questions
1. **Trick:** Does adding a `NULL` column with `ALTER TABLE ... ADD COLUMN col VARCHAR(50);` lock a 100M row PostgreSQL table for hours?
   *Answer:* No! In PostgreSQL, adding a nullable column with no default is a metadata-only operation that takes $<1\text{ms}$ (though it still briefly acquires an `AccessExclusiveLock`).
2. **Trick:** In PostgreSQL 11+, does `ALTER TABLE ... ADD COLUMN col INT NOT NULL DEFAULT 0;` require a full table rewrite?
   *Answer:* No. Starting with PostgreSQL 11, adding a column with a constant default value is a metadata-only operation that does not rewrite the table.
3. **Trick:** Can `CREATE INDEX CONCURRENTLY` be run inside a standard Flyway transaction block?
   *Answer:* No! PostgreSQL forbids `CREATE INDEX CONCURRENTLY` inside transaction blocks (`BEGIN ... COMMIT`). You must set `mixed=true` or configure Flyway to execute the script outside a transaction.

---

*(Answers and detailed explanations are provided in the Master Answer Guide)*
