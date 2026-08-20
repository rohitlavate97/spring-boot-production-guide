---
chapter: 180
topic: Optimistic & Pessimistic Locking — @Version, SELECT FOR UPDATE, Deadlock Prevention
prerequisite_chapters: [10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170]
reference_system_node: Payment Service & Merchant Wallet Engine ↔ PostgreSQL payment_db (MerchantWallet, TransferTask, PessimisticWalletService, OptimisticWalletService)
---

# Chapter 180: Optimistic & Pessimistic Locking — @Version, SELECT FOR UPDATE, Deadlock Prevention

## 1. Concept

In high-concurrency financial platforms like FinFlow, multiple threads frequently attempt to read and mutate the same shared state simultaneously (for example, two concurrent card payments debiting a merchant's reserve wallet, or simultaneous inventory reservations). Without explicit concurrency control, applications suffer from the **Lost Update Problem**, silently corrupting account balances and inventory counts.

### The Lost Update Problem
Consider two concurrent transactions ($T_1$ and $T_2$) operating on a wallet with an initial balance of **$1,000**:
1. $T_1$ reads balance = **$1,000**.
2. $T_2$ reads balance = **$1,000**.
3. $T_1$ debits $200$ and calculates new balance = **$800**.
4. $T_2$ debits $300$ and calculates new balance = **$700**.
5. $T_1$ commits balance = **$800**.
6. $T_2$ commits balance = **$700** (overwriting $T_1$'s debit!).

**Final Balance**: **$700** instead of the correct **$500**. $200 has vanished from the system.

### Concurrency Control Strategies: Optimistic vs. Pessimistic

```
+------------------------------------+----------------------------------------------------+
| Optimistic Locking (@Version)      | Pessimistic Locking (SELECT FOR UPDATE)            |
+------------------------------------+----------------------------------------------------+
| - Assumes conflicts are RARE.      | - Assumes conflicts are FREQUENT / EXPENSIVE.      |
| - Holds NO database row locks      | - Acquires an exclusive row-level lock in the      |
|   during business execution.       |   database engine (FOR UPDATE) immediately.        |
| - Verifies version at commit time. | - Other transactions BLOCK until lock is released. |
| - High throughput for read-heavy   | - Prevents conflicts upfront for high-contention   |
|   or low-contention domains.       |   write-heavy mutations.                           |
+------------------------------------+----------------------------------------------------+
```

---

## 2. Internal Working

### Optimistic Locking Internals (`@Version`)
When JPA/Hibernate maps an entity field with `@Version` (e.g., `Long version`):
1. **Initial Read**: Hibernate loads the entity state along with its current version (e.g., `version = 3`).
2. **In-Memory Mutation**: Application code modifies entity fields in memory without acquiring database locks.
3. **Flush / Commit Verification**: At flush time, Hibernate generates a conditional `UPDATE` statement:
   ```sql
   UPDATE merchant_wallets 
   SET available_balance = ?, version = 4 
   WHERE id = ? AND version = 3;
   ```
4. **Row Count Check**: Hibernate inspects the JDBC update count returned by the database driver:
   - If `updateCount == 1`: Success. The update is applied, and the version is incremented.
   - If `updateCount == 0`: Another transaction modified the row in the interim. Hibernate throws `org.hibernate.StaleObjectStateException`, which Spring translates into `org.springframework.orm.ObjectOptimisticLockingFailureException`.

```
Transaction 1 (Tx1)                      Database (version=3)                    Transaction 2 (Tx2)
       │                                          │                                       │
       ├─── SELECT id, balance, version=3 ───────►│                                       │
       │                                          │◄────── SELECT id, balance, version=3 ─┤
       │                                          │                                       │
       │ (Mutates balance to $800 in RAM)         │                                       │ (Mutates balance to $700 in RAM)
       │                                          │                                       │
       ├─── UPDATE ... WHERE version=3 ──────────►│                                       │
       │    (Rows updated: 1 -> version becomes 4)│                                       │
       │◄── SUCCESS (Tx1 Commits) ────────────────┤                                       │
       │                                          │                                       │
       │                                          │◄────── UPDATE ... WHERE version=3 ────┤
       │                                          │        (Rows updated: 0 -> Fails!)    │
       │                                          ├──────► StaleObjectStateException ────►│ (Tx2 Aborts)
```

---

### Pessimistic Locking Internals (`LockModeType.PESSIMISTIC_WRITE`)
When a repository method executes with `@Lock(LockModeType.PESSIMISTIC_WRITE)`:
1. Spring Data JPA generates a `SELECT ... FOR UPDATE` SQL query.
2. In PostgreSQL:
   - The storage engine places an **`ExclusiveLock`** on the target row tuple in shared memory (`pg_locks`).
   - The transaction's ID is written to the row's `xmax` header field.
   - Any concurrent transaction attempting `SELECT FOR UPDATE`, `UPDATE`, or `DELETE` on the same tuple blocks and sleeps until the lock-holding transaction issues `COMMIT` or `ROLLBACK`.

#### Lock Modes & Clauses in Modern PostgreSQL
- **`SELECT FOR UPDATE`**: Exclusive row lock. Blocks all other readers with `FOR UPDATE`/`FOR SHARE` and all writers.
- **`SELECT FOR UPDATE NOWAIT`**: Throws an immediate error (SQLState `55P03: lock_not_available`) if the row is already locked by another transaction, instead of blocking.
- **`SELECT FOR UPDATE SKIP LOCKED`**: Skips all currently locked rows and returns only unlocked rows. **Essential for high-throughput distributed worker queues without lock contention.**

---

### Database Deadlocks & Wait-For Graphs

A **Deadlock** occurs when two or more transactions hold locks on resources the other transactions need to proceed, forming a circular dependency:

```
[Transaction 1 (PID 101)]                              [Transaction 2 (PID 102)]
       │                                                        │
       ├── Holds Lock on Wallet A                               ├── Holds Lock on Wallet B
       │                                                        │
       └── Attempts to lock Wallet B (BLOCKS)                   └── Attempts to lock Wallet A (BLOCKS)
                     │                                                        │
                     └────────────── Circular Dependency Cycle ───────────────┘
```

#### PostgreSQL Deadlock Detection Engine
1. When a transaction blocks waiting for a lock, it starts a timer (`deadlock_timeout`, default `1000ms`).
2. If the lock is not granted within 1 second, PostgreSQL runs a cycle-detection algorithm on the internal **Wait-For Graph**.
3. Upon discovering a directed cycle ($T_1 \to T_2 \to T_1$), PostgreSQL picks one transaction as the "victim," aborts it, and throws:
   ```text
   ERROR: deadlock detected (SQLState: 40P01)
   DETAIL: Process 101 waits for ExclusiveLock on tuple (12, 4); blocked by process 102.
           Process 102 waits for ExclusiveLock on tuple (5, 18); blocked by process 101.
   ```

---

### Mathematical Deadlock Prevention: Canonical Resource Ordering

To guarantee zero database deadlocks across multi-resource transactions:

> [!IMPORTANT]
> **The Canonical Resource Ordering Rule**:
> All transactions in the system must acquire locks on multiple resources in the **exact same deterministic order** (e.g., sorted lexicographically by Resource UUID / ID), regardless of business direction (source vs. destination, debit vs. credit).

```java
// CANONICAL ORDERING ALGORITHM:
String firstId  = fromId.compareTo(toId) < 0 ? fromId : toId;
String secondId = fromId.compareTo(toId) < 0 ? toId : fromId;

// Always lock firstId BEFORE secondId!
walletRepository.findByMerchantIdWithPessimisticWriteLock(firstId);
walletRepository.findByMerchantIdWithPessimisticWriteLock(secondId);
```
*Because every concurrent thread locks the smaller ID first, a directed cycle in the wait-for graph is mathematically impossible ($A \to B$ is allowed, but $B \to A$ can never be initiated).*

---

## 3. Enterprise Scenario: FinFlow Merchant Settlement & Wallet Engine

In the **FinFlow Merchant Balance & Settlement Subsystem**:

```
Payment Gateway / Settlement Jobs
       │
       ▼ (4,000 req/sec Peak)
Payment Service (20 pods) ──► PostgreSQL (payment_db)
       │
       ├── Endpoint 1: POST /v1/wallets/{merchantId}/debit (Single-wallet mutation)
       ├── Endpoint 2: POST /v1/wallets/transfer (Multi-wallet rebalancing)
       └── Worker 3: TaskQueueWorker (Leaderless batch settlement queue)
```

- **Scale & Contention Profile**:
  - Top 10 enterprise marketplace accounts experience **350 concurrent debits/credits per second** against the same wallet row.
  - Nightly clearing transfers funds between marketplace partner wallets bidirectionally ($A \to B$ and $B \to A$).
  - PostgreSQL RDS instance with `deadlock_timeout = 1000ms`, `max_connections = 200`.

---

## 4. Incorrect Implementation

Below is the naive implementation typical of financial systems suffering lost updates and deadlock storms:

```java
package com.finflow.chapter180.incorrect;

import com.finflow.chapter180.domain.MerchantWallet;
import com.finflow.chapter180.domain.UnversionedWallet;
import com.finflow.chapter180.repository.MerchantWalletRepository;
import com.finflow.chapter180.repository.UnversionedWalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class WalletTransferServiceIncorrect {

    private final UnversionedWalletRepository unversionedWalletRepository;
    private final MerchantWalletRepository merchantWalletRepository;

    public WalletTransferServiceIncorrect(UnversionedWalletRepository unversionedWalletRepository,
                                         MerchantWalletRepository merchantWalletRepository) {
        this.unversionedWalletRepository = unversionedWalletRepository;
        this.merchantWalletRepository = merchantWalletRepository;
    }

    /**
     * Anti-Pattern 1: Lost Update on unversioned entity.
     * Overwrites concurrent balance updates without detection.
     */
    @Transactional
    public void debitUnversioned(String merchantId, BigDecimal amount) {
        UnversionedWallet wallet = unversionedWalletRepository.findByMerchantId(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + merchantId));

        wallet.debit(amount);
        unversionedWalletRepository.save(wallet);
    }

    /**
     * Anti-Pattern 2: Non-deterministic Lock Acquisition Order -> Severe Deadlocks!
     * Thread 1 (A -> B) locks A, then B.
     * Thread 2 (B -> A) locks B, then A.
     * Under load, triggers constant 40P01 deadlock exceptions.
     */
    @Transactional
    public void transferWithDeadlockRisk(String fromMerchantId, String toMerchantId, BigDecimal amount) {
        // Lock 1: Lock source wallet
        MerchantWallet fromWallet = merchantWalletRepository.findByMerchantIdWithPessimisticWriteLock(fromMerchantId)
                .orElseThrow(() -> new IllegalArgumentException("Source wallet not found: " + fromMerchantId));

        try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // Lock 2: Lock target wallet
        MerchantWallet toWallet = merchantWalletRepository.findByMerchantIdWithPessimisticWriteLock(toMerchantId)
                .orElseThrow(() -> new IllegalArgumentException("Target wallet not found: " + toMerchantId));

        fromWallet.debit(amount);
        toWallet.credit(amount);
    }
}
```

---

## 5. Production Incident

### Incident Timeline

| Time (UTC) | Event / Telemetry |
|---|---|
| **00:00:00** | Midnight automated marketplace settlement clearing starts. 120 concurrent batch threads execute cross-merchant transfers. |
| **00:01:15** | PagerDuty fires SEV-1 Alert: `PostgreSQL_Deadlock_Spike (> 2,400 deadlocks/min)`. |
| **00:02:00** | Transaction rollback rate spikes to **68%**. HikariCP connection pool on all pods hits 100% saturation as transactions block for 1,000ms awaiting deadlock resolution. |
| **00:03:30** | Incoming customer payment authorizations queue behind blocked settlement threads, failing with HTTP 500 (`CannotAcquireLockException`). |
| **00:05:00** | $2.4M in merchant payouts stalled. Emergency traffic shed initiated for batch clearing workers. |
| **00:18:00** | On-call engineers identify bidirectional lock acquisition cycles in `pg_stat_activity` and deploy hotfix introducing **Canonical Resource Ordering**. |
| **00:25:00** | Deadlock count drops to **0**. 100% of transfers complete cleanly in 1 minute 40 seconds. |

---

## 6. Logs & Diagnostics

### 1. PostgreSQL Engine Deadlock Log (Cycle Detection)
```text
2026-08-20 00:01:24.812 UTC [14210] ERROR:  deadlock detected
2026-08-20 00:01:24.812 UTC [14210] DETAIL:  Process 14210 waits for ExclusiveLock on tuple (14, 2) of relation 16422 of database 16384; blocked by process 14218.
	Process 14218 waits for ExclusiveLock on tuple (8, 5) of relation 16422 of database 16384; blocked by process 14210.
	Process 14210: SELECT * FROM merchant_wallets WHERE merchant_id = 'MERCHANT_B' FOR UPDATE
	Process 14218: SELECT * FROM merchant_wallets WHERE merchant_id = 'MERCHANT_A' FOR UPDATE
2026-08-20 00:01:24.812 UTC [14210] HINT:  See server log for query details.
2026-08-20 00:01:24.812 UTC [14210] STATEMENT:  SELECT w1_0.id,w1_0.available_balance,w1_0.currency,w1_0.merchant_id,w1_0.reserved_balance,w1_0.version FROM merchant_wallets w1_0 WHERE w1_0.merchant_id=$1 FOR UPDATE
```

### 2. Spring Application Stack Trace (`CannotAcquireLockException`)
```text
2026-08-20T00:01:25.104Z ERROR [payment-service,trace_id=5f1a2b,span_id=9c8d7e] 1 --- [http-nio-8080-exec-31] c.f.c.i.WalletTransferServiceIncorrect : Transfer failed due to database deadlock

org.springframework.dao.CannotAcquireLockException: could not execute query using SELECT FOR UPDATE; SQL [select ... from merchant_wallets where merchant_id=? for update]
	at org.springframework.orm.jpa.vendor.HibernateJpaDialect.convertHibernateAccessException(HibernateJpaDialect.java:293)
	at org.springframework.orm.jpa.vendor.HibernateJpaDialect.translateExceptionIfPossible(HibernateJpaDialect.java:241)
	at org.springframework.dao.support.ChainedPersistenceExceptionTranslator.translateExceptionIfPossible(ChainedPersistenceExceptionTranslator.java:61)
Caused by: org.hibernate.exception.LockAcquisitionException: could not execute query using SELECT FOR UPDATE
	at org.hibernate.exception.internal.SQLStateConversionDelegate.convert(SQLStateConversionDelegate.java:125)
Caused by: org.postgresql.util.PSQLException: ERROR: deadlock detected
  Detail: Process 14210 waits for ExclusiveLock on tuple (14, 2); blocked by process 14218.
  Where: while locking tuple (14, 2) in relation "merchant_wallets"
```

### 3. Spring Optimistic Locking Exception (`ObjectOptimisticLockingFailureException`)
```text
org.springframework.orm.ObjectOptimisticLockingFailureException: Batch update returned unexpected row count from update [0]; actual row count: 0; expected: 1; statement executed: update merchant_wallets set available_balance=?, version=? where id=? and version=?
	at org.springframework.orm.jpa.vendor.HibernateJpaDialect.convertHibernateAccessException(HibernateJpaDialect.java:319)
	at org.hibernate.internal.ExceptionConverterImpl.convert(ExceptionConverterImpl.java:139)
```

---

## 7. Root Cause Analysis

```
+-------------------------------------------------------------------------------------------------+
|                                     Deadlock Root Cause Chain                                   |
|                                                                                                 |
|  1. Inconsistent Locking Sequence across Concurrent Transfers                                   |
|     ├── Worker Thread 1 transfers $50 from Merchant A ➔ Merchant B                             |
|     │   └── Acquires ExclusiveLock on Merchant A's tuple header.                                |
|     └── Worker Thread 2 transfers $100 from Merchant B ➔ Merchant A                            |
|         └── Acquires ExclusiveLock on Merchant B's tuple header.                                |
|                                                                                                 |
|  2. Circular Wait Condition                                                                     |
|     ├── Thread 1 attempts to lock Merchant B -> Enters lock wait queue behind Thread 2.         |
|     └── Thread 2 attempts to lock Merchant A -> Enters lock wait queue behind Thread 1.         |
|                                                                                                 |
|  3. Deadlock Detector Intervenes                                                                |
|     └── deadlock_timeout (1,000ms) expires -> PostgreSQL aborts Thread 1 with SQLState 40P01.  |
|                                                                                                 |
|  4. Connection Pool Cascading Exhaustion                                                        |
|     └── With hundreds of threads blocking for 1,000ms before failing, HikariCP pools starve,   |
|         bringing down user-facing payment authorization APIs.                                  |
+-------------------------------------------------------------------------------------------------+
```

---

## 8. Debugging Process

```
[1. Metric Triage] Inspect pg_stat_database.deadlocks & HikariCP pending threads
       │
[2. Live Lock Inspection] Run pg_locks query to view blocking and waiting PIDs
       │
[3. Query Plan Verification] Confirm indexes exist on locked columns (prevent table-scan locks)
       │
[4. Architectural Fix] Apply Canonical Resource Ordering & Exponential Backoff Retries
```

### Step 1: Query PostgreSQL Blocking vs. Waiting Locks
```sql
SELECT 
    blocked_locks.pid     AS blocked_pid,
    blocked_activity.usename  AS blocked_user,
    blocking_locks.pid    AS blocking_pid,
    blocking_activity.usename AS blocking_user,
    blocked_activity.query    AS blocked_statement,
    blocking_activity.query   AS blocking_statement
FROM  pg_catalog.pg_locks         blocked_locks
JOIN pg_catalog.pg_stat_activity blocked_activity ON blocked_activity.pid = blocked_locks.pid
JOIN pg_catalog.pg_locks         blocking_locks 
    ON blocking_locks.locktype = blocked_locks.locktype
    AND blocking_locks.database IS NOT DISTINCT FROM blocked_locks.database
    AND blocking_locks.relation IS NOT DISTINCT FROM blocked_locks.relation
    AND blocking_locks.page IS NOT DISTINCT FROM blocked_locks.page
    AND blocking_locks.tuple IS NOT DISTINCT FROM blocked_locks.tuple
    AND blocking_locks.virtualxid IS NOT DISTINCT FROM blocked_locks.virtualxid
    AND blocking_locks.transactionid IS NOT DISTINCT FROM blocked_locks.transactionid
    AND blocking_locks.classid IS NOT DISTINCT FROM blocked_locks.classid
    AND blocking_locks.objid IS NOT DISTINCT FROM blocked_locks.objid
    AND blocking_locks.objsubid IS NOT DISTINCT FROM blocked_locks.objsubid
    AND blocking_locks.pid != blocked_locks.pid
JOIN pg_catalog.pg_stat_activity blocking_activity ON blocking_activity.pid = blocking_locks.pid
WHERE NOT blocked_locks.granted;
```

---

## 9. Correct Implementation

### 1. Optimistic Locking with Backoff Retry: `OptimisticWalletService.java`

```java
package com.finflow.chapter180.correct;

import com.finflow.chapter180.domain.MerchantWallet;
import com.finflow.chapter180.repository.MerchantWalletRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class OptimisticWalletService {

    private final MerchantWalletRepository walletRepository;

    public OptimisticWalletService(MerchantWalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @Transactional
    public MerchantWallet debitOptimistic(String merchantId, BigDecimal amount) {
        MerchantWallet wallet = walletRepository.findByMerchantId(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + merchantId));

        wallet.debit(amount);
        return walletRepository.save(wallet);
    }

    /**
     * Application-level retry mechanism with exponential backoff and jitter.
     */
    public MerchantWallet debitWithRetry(String merchantId, BigDecimal amount, int maxAttempts) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                return debitOptimistic(merchantId, amount);
            } catch (ObjectOptimisticLockingFailureException ex) {
                if (attempt >= maxAttempts) {
                    throw new IllegalStateException("Exhausted " + maxAttempts + " optimistic lock retries for: " + merchantId, ex);
                }
                try {
                    // Exponential backoff with random jitter to prevent stampedes
                    long backoff = (long) (Math.pow(2, attempt) * 20 + Math.random() * 20);
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
            }
        }
    }
}
```

### 2. Deadlock-Free Pessimistic Locking: `PessimisticWalletService.java`

```java
package com.finflow.chapter180.correct;

import com.finflow.chapter180.domain.MerchantWallet;
import com.finflow.chapter180.repository.MerchantWalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class PessimisticWalletService {

    private final MerchantWalletRepository walletRepository;

    public PessimisticWalletService(MerchantWalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @Transactional
    public MerchantWallet debitPessimistic(String merchantId, BigDecimal amount) {
        MerchantWallet wallet = walletRepository.findByMerchantIdWithPessimisticWriteLock(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + merchantId));

        wallet.debit(amount);
        return walletRepository.save(wallet);
    }

    /**
     * CANONICAL RESOURCE ORDERING:
     * Guarantees zero deadlocks by sorting resource IDs prior to lock acquisition.
     */
    @Transactional
    public void transferDeadlockFree(String fromMerchantId, String toMerchantId, BigDecimal amount) {
        if (fromMerchantId.equals(toMerchantId)) {
            throw new IllegalArgumentException("Cannot transfer to the same wallet: " + fromMerchantId);
        }

        // Canonical ordering: determine which ID comes first lexicographically
        boolean fromIsFirst = fromMerchantId.compareTo(toMerchantId) < 0;
        String firstId = fromIsFirst ? fromMerchantId : toMerchantId;
        String secondId = fromIsFirst ? toMerchantId : fromMerchantId;

        // Step 1: Acquire lock on the 1st wallet in global order
        MerchantWallet firstWallet = walletRepository.findByMerchantIdWithPessimisticWriteLock(firstId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + firstId));

        // Step 2: Acquire lock on the 2nd wallet in global order
        MerchantWallet secondWallet = walletRepository.findByMerchantIdWithPessimisticWriteLock(secondId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + secondId));

        MerchantWallet fromWallet = fromIsFirst ? firstWallet : secondWallet;
        MerchantWallet toWallet = fromIsFirst ? secondWallet : firstWallet;

        fromWallet.debit(amount);
        toWallet.credit(amount);

        walletRepository.save(fromWallet);
        walletRepository.save(toWallet);
    }
}
```

### 3. Queue Worker with `SKIP LOCKED`: `TaskQueueWorkerService.java`

```java
package com.finflow.chapter180.correct;

import com.finflow.chapter180.domain.TransferTask;
import com.finflow.chapter180.repository.TransferTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaskQueueWorkerService {

    private final TransferTaskRepository taskRepository;

    public TaskQueueWorkerService(TransferTaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    /**
     * Polls pending settlement tasks using SELECT FOR UPDATE SKIP LOCKED.
     * Allows N concurrent pods to ingest disjoint task batches without blocking.
     */
    @Transactional
    public List<TransferTask> pollAndLockPendingTasks(int limit) {
        List<TransferTask> tasks = taskRepository.fetchPendingTasksSkipLocked(limit);
        for (TransferTask task : tasks) {
            task.setStatus("PROCESSING");
            taskRepository.save(task);
        }
        return tasks;
    }
}
```

---

## 10. Performance Comparison

Under 4,000 req/sec concurrent wallet operations on FinFlow infrastructure.

| Metric | Unversioned Naive | Pessimistic (Inconsistent Order) | Pessimistic (Canonical Order) | Optimistic with Backoff |
|---|---|---|---|---|
| **Lost Updates Detected** | **14.2% lost** | 0.0% | **0.0%** | **0.0%** |
| **Deadlock Rate (40P01)** | 0 / min | **2,410 / min** | **0 / min (Zero)** | **0 / min (Zero)** |
| **Transaction Failure Rate** | 0.0% *(Corrupted data)* | 68.4% *(Deadlocks)* | **0.0%** | < 0.1% *(After 3 retries)* |
| **Response Latency (p99)** | 12ms (illustrative) | 1,450ms (illustrative) | **28ms** (illustrative) | **34ms** (illustrative) |
| **Throughput (tx/sec)** | High (Incorrect) | 480 tx/s *(Degraded)* | **3,250 tx/s** | **3,890 tx/s** |
| **DB Lock Hold Time** | 0ms | 1,000ms *(deadlock timer)*| **6ms** | **0ms (No DB lock held)**|

---

## 11. Best Practices

### The Do's
- **DO add `@Version` to all mutable business entities**: Provides baseline protection against lost updates with zero database lock overhead.
- **DO enforce Canonical Resource Ordering for multi-entity updates**: Always sort entity IDs before acquiring pessimistic locks.
- **DO add lock timeouts (`jakarta.persistence.lock.timeout`)**: Never allow pessimistic lock queries to block indefinitely.
- **DO use `SELECT FOR UPDATE SKIP LOCKED` for task queues**: Eliminates lock contention across distributed worker pods.
- **DO add randomized jitter to optimistic lock retry loops**: Prevents retry stampedes on hotly contested rows.

### The Don'ts
- **DON'T hold pessimistic locks across external network I/O**: Keeps database row locks open for seconds, starving all concurrent transactions.
- **DON'T use Optimistic Locking on extreme write hotspots**: If 500 threads update 1 row/sec, optimistic locking results in 99% abort rates; use pessimistic locking or an append-only ledger with background rollup.
- **DON'T lock unindexed foreign key columns**: Can cause PostgreSQL to escalate to table-level locks, halting unrelated transactions.
- **DON'T use `PESSIMISTIC_READ` when you intend to update the row**: Upgrading a shared read lock to an exclusive write lock in concurrent transactions guarantees a deadlock!

---

## 12. Common Mistakes

### Mistake 1: Lock Upgrade Deadlock
```java
// SEVERE DEADLOCK TRAP:
@Lock(LockModeType.PESSIMISTIC_READ)
Optional<MerchantWallet> findById(UUID id);

// Inside service:
MerchantWallet w = repository.findById(id).get(); // Both Tx1 and Tx2 acquire shared read locks
w.debit(amount);
repository.save(w); // Both Tx1 and Tx2 attempt to upgrade to write lock -> DEADLOCK!
```
**Production Fix**: If you plan to mutate the entity, acquire `LockModeType.PESSIMISTIC_WRITE` immediately.

### Mistake 2: Missing Index on Locked Query
Executing `SELECT ... WHERE merchant_code = ? FOR UPDATE` when `merchant_code` has no index forces a table scan, acquiring row locks on **every single row in the table**!

---

## 13. Interview Questions

### Junior Tier
**Q: What is the difference between Optimistic and Pessimistic locking in Spring Data JPA?**
> **Answer**: Optimistic locking does not acquire physical database locks. It detects concurrent modifications at commit time using a `@Version` column (`UPDATE ... WHERE id = ? AND version = ?`), throwing `ObjectOptimisticLockingFailureException` if the version has changed. Pessimistic locking acquires an exclusive row-level lock in the database engine (`SELECT ... FOR UPDATE`), blocking other transactions until the lock holder commits or rolls back.

### Mid Tier
**Q: How does `SELECT FOR UPDATE SKIP LOCKED` work, and why is it preferred over standard `FOR UPDATE` for task queues?**
> **Answer**: Standard `SELECT FOR UPDATE` blocks when encountering a locked row until that row is unlocked. In a distributed worker queue, multiple workers would block on the top row of the queue. `SKIP LOCKED` instructs the database to skip past any rows currently locked by other transactions and return only unlocked rows. This allows $N$ worker pods to poll the database table concurrently in parallel with zero lock contention and zero blocking.

### Senior Tier
**Q: What is Canonical Resource Ordering, and how does it prevent database deadlocks in multi-account financial transfers?**
> **Answer**: A database deadlock occurs when transactions form a cycle in the wait-for graph ($T_1$ holds Lock A and wants Lock B; $T_2$ holds Lock B and wants Lock A). Canonical Resource Ordering eliminates cycles by enforcing a global deterministic order for lock acquisition (e.g., sorting account IDs lexicographically: $\min(\text{ID}_1, \text{ID}_2) \to \max(\text{ID}_1, \text{ID}_2)$). Because all transactions acquire locks in the exact same sequence regardless of transfer direction, a circular wait is mathematically impossible.

### Staff Tier
**Q: Why does acquiring a `PESSIMISTIC_READ` lock followed by an entity mutation frequently cause deadlocks under concurrency?**
> **Answer**: Multiple concurrent transactions can acquire `PESSIMISTIC_READ` (shared `FOR SHARE`) locks on the same row simultaneously. When both transactions subsequently attempt to update the entity, each requires an exclusive lock (`FOR UPDATE`). However, neither transaction can be granted the exclusive lock because the other transaction holds an active shared read lock. Both transactions enter a permanent mutual wait state, forcing the database deadlock detector to abort one of the transactions.

### Principal Tier
**Q: How do you design a balance mutation architecture for an extreme write-hotspot account (e.g., Apple processing 10,000 transactions/sec) where neither optimistic nor pessimistic row locking can sustain the throughput?**
> **Answer**: For extreme write hotspots, row-level locking on a single balance record is physically constrained by database transaction log commit latencies ($O(1 \text{ms})$ per serialized commit $\approx 1,000 \text{ tx/sec}$ max). A Principal-level solution uses **Append-Only Ledger Partitioning & Sharded Accumulators**:
> 1. **Append-Only Ingestion**: Instead of updating a single balance row, each payment inserts an immutable `LedgerEntry` row using batch JDBC inserts ($> 50,000 \text{ writes/sec}$).
> 2. **Sharded Balance Buckets**: If balance checks are required in real-time, the account balance is partitioned across $K$ bucket rows (`wallet_shard_0` ... `wallet_shard_9`). Transactions debit/credit a randomly chosen bucket ($1/K$ contention).
> 3. **Asynchronous Rollup Worker**: A background worker periodically sums entries and writes consolidated account snapshots.

---

## 14. Hands-on Exercise

### Objective
Implement a multi-wallet transfer coordinator in FinFlow that transfers funds between two merchant wallets under high concurrency. Ensure the service:
1. Prevents lost updates using `PESSIMISTIC_WRITE`.
2. Implements Canonical Resource Ordering to eliminate all deadlocks.
3. Sets a lock timeout of 3,000ms.

### Solution

#### Step 1: Repository with Lock Timeout
```java
@Repository
public interface MerchantWalletRepository extends JpaRepository<MerchantWallet, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT w FROM MerchantWallet w WHERE w.merchantId = :merchantId")
    Optional<MerchantWallet> findByMerchantIdWithLockTimeout(@Param("merchantId") String merchantId);
}
```

#### Step 2: Transfer Service with Canonical Ordering
```java
@Service
public class DeadlockFreeTransferService {

    private final MerchantWalletRepository repository;

    public DeadlockFreeTransferService(MerchantWalletRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void executeTransfer(String fromId, String toId, BigDecimal amount) {
        // Enforce global lexicographical ordering
        boolean fromFirst = fromId.compareTo(toId) < 0;
        String id1 = fromFirst ? fromId : toId;
        String id2 = fromFirst ? toId : fromId;

        // Step 1: Lock smaller ID
        MerchantWallet w1 = repository.findByMerchantIdWithLockTimeout(id1)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + id1));

        // Step 2: Lock larger ID
        MerchantWallet w2 = repository.findByMerchantIdWithLockTimeout(id2)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + id2));

        MerchantWallet fromWallet = fromFirst ? w1 : w2;
        MerchantWallet toWallet = fromFirst ? w2 : w1;

        fromWallet.debit(amount);
        toWallet.credit(amount);
    }
}
```

---

## 15. Advanced Challenge: Distributed Leaderless Task Queue with `SKIP LOCKED`

### Enterprise Problem Statement
FinFlow runs 20 Kubernetes worker pods processing settlement reconciliation tasks. If all pods query `SELECT * FROM tasks WHERE status = 'PENDING' LIMIT 10`, they will either collide on the same tasks or block each other.

Build a production-grade worker using Spring Data JPA and native `FOR UPDATE SKIP LOCKED` that guarantees lock-free parallel execution across all 20 pods.

### Enterprise Solution

```java
package com.finflow.chapter180.correct;

import com.finflow.chapter180.domain.TransferTask;
import com.finflow.chapter180.repository.TransferTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DistributedQueueService {

    private final TransferTaskRepository taskRepository;

    public DistributedQueueService(TransferTaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional
    public List<TransferTask> acquireBatchForProcessing(int batchSize) {
        // SELECT * FROM transfer_tasks WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT :batchSize FOR UPDATE SKIP LOCKED
        List<TransferTask> tasks = taskRepository.fetchPendingTasksSkipLocked(batchSize);

        for (TransferTask task : tasks) {
            task.setStatus("PROCESSING");
            taskRepository.save(task);
        }

        return tasks;
    }
}
```

---

## 16. Production Checklist

Before approving any pull request involving concurrent data access and locking:

- [ ] **`@Version` Present on Mutable Entities**: Verify all entities subject to concurrent updates declare a `@Version` field.
- [ ] **Canonical Resource Ordering Enforced**: Ensure all multi-row lock acquisitions sort resource identifiers before calling `SELECT FOR UPDATE`.
- [ ] **Lock Timeout Specified**: Verify pessimistic lock queries specify `jakarta.persistence.lock.timeout` to prevent infinite blocking.
- [ ] **No Network Calls inside Pessimistic Lock**: Verify no external HTTP/RPC calls execute while holding a row-level database lock.
- [ ] **Indexes on Locked Columns**: Ensure all columns in `WHERE` clauses of `FOR UPDATE` queries have covering indexes.
- [ ] **`SKIP LOCKED` for Queue Workers**: Verify that table-based queue polling uses `FOR UPDATE SKIP LOCKED`.
- [ ] **Optimistic Retry Backoff with Jitter**: Confirm that optimistic locking retries include exponential backoff and randomized jitter to prevent stampedes.
