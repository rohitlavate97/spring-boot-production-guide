# Module 10: Database Performance, Query Plans & Deadlocks

## Issue 10.1: Circular Deadlocks, Missing Indexes, and Query Execution Plans

---

### 1. Scenario

During peak trading on the **FinFlow Core Ledger & Settlement Platform**:
1. High-frequency bidirectional wallet transfers start crashing with:
   ```text
   org.springframework.dao.CannotAcquireLockException: could not execute statement [Deadlock found when trying to get lock; try restarting transaction]
   ```
2. Database diagnostic logs reveal classic **circular deadlock loops**:
   - **Transaction 1 (Thread-A):** Transfers \$100 from Account 1 to Account 2 $\rightarrow$ Acquires row lock on Account 1, waits for row lock on Account 2.
   - **Transaction 2 (Thread-B):** Transfers \$50 from Account 2 to Account 1 $\rightarrow$ Acquires row lock on Account 2, waits for row lock on Account 1.
3. Simultaneously, a query filtering by `WHERE LOWER(account_number) = 'acc-101'` causes a full 10-million row **Sequential Scan (`Seq Scan`)**, holding shared read locks for 8.4 seconds and compounding lock contention across the database.

---

### 2. Symptoms

```text
1. Database Deadlock Exception:
   org.springframework.dao.DeadlockLoserDataAccessException / CannotAcquireLockException.
2. PostgreSQL Error Code 40P01:
   ERROR: deadlock detected - Process 12345 waits for ExclusiveLock on tuple (0,1); blocked by process 12346.
3. MySQL InnoDB Engine Status:
   LATEST DETECTED DEADLOCK: *** (1) TRANSACTION ... WAITING FOR THIS LOCK TO BE GRANTED.
4. Latency Spikes from Full Table Scans:
   EXPLAIN ANALYZE shows "Seq Scan on finflow_ledger_accounts" taking 8,400ms instead of 2ms Index Scan.
5. High Lock Wait Time in APM:
   Transactions blocking in LockSupport.park() or HikariPool waiting on database row locks.
```

---

### 3. Possible Root Causes

1. **Non-Deterministic Lock Acquisition Ordering:** Concurrent transactions acquiring row-level exclusive locks (`SELECT ... FOR UPDATE`) in arbitrary or opposite order (e.g. Account A $\rightarrow$ Account B vs Account B $\rightarrow$ Account A).
2. **Missing or Invalidated Indexes:** Missing composite indexes or function-wrapped columns (e.g. `WHERE LOWER(account_number) = ?` without a functional index) forcing the database to scan every table page.
3. **Lock Escalation & Long Transaction Boundaries:** Holding pessimistic write locks during non-database processing, escalating row lock contention into transaction timeouts.
4. **Lack of Automated Deadlock Retries:** Deadlocks are statistically inevitable in high-concurrency systems; failing to implement automated exponential backoff retries results in user-facing 500 errors.

---

### 4. Architecture Context: Deadlock Detection & Wait-For Graphs

```text
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        CIRCULAR DEADLOCK WAIT-FOR GRAPH                                │
│                                                                                        │
│     [Transaction 1 (Thread-A)] ──────── Holds Lock on ────────► [Account #1]           │
│                │                                                     ▲                 │
│          Waits to Acquire                                     Locked by Tx 1           │
│                ▼                                                     │                 │
│          [Account #2] ◄──────── Locked by Tx 2 ──────── [Transaction 2 (Thread-B)]     │
│                │                                                     ▲                 │
│                └──────────────── Waits to Acquire ───────────────────┘                 │
│                                                                                        │
│     💥 DATABASE ENGINE (PostgreSQL/MySQL):                                             │
│        Detects cycle in Wait-For Graph (Cycle: Tx1 ──► Tx2 ──► Tx1)                    │
│        Selects Transaction 1 as deadlock victim and aborts it!                         │
│                                                                                        │
│     ✅ SOLUTION (Deterministic Total Ordering):                                         │
│        Always lock accounts in ascending ID order (min(from, to) then max(from, to)).   │
│        Circular wait becomes mathematically IMPOSSIBLE!                                │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 5. How to Reproduce the Issues

#### Step 1: Unordered Pessimistic Locking (Vulnerable to Deadlock)
```java
// ❌ ANTI-PATTERN: Arbitrary lock ordering
@Transactional
public void transferUnordered(Long fromId, Long toId, BigDecimal amount) {
    // Thread A locks Account 1 first
    LedgerAccountEntity from = accountRepository.findByIdForUpdate(fromId).orElseThrow();
    
    // Thread B locks Account 2 first... then both try to lock each other's account!
    LedgerAccountEntity to = accountRepository.findByIdForUpdate(toId).orElseThrow();

    from.debit(amount);
    to.credit(amount);
}
```

#### Step 2: Concurrent Bidirectional Execution
- Thread A calls `transferUnordered(1, 2, 100)`
- Thread B calls `transferUnordered(2, 1, 50)`
- **Result:** Deadlock exception thrown!

---

### 6. Evidence Collection & Diagnostic Probing

#### Method 1: MySQL InnoDB Deadlock Analysis
Run inside MySQL CLI:
```sql
SHOW ENGINE INNODB STATUS;
```
**Diagnostic Output:**
```text
------------------------
LATEST DETECTED DEADLOCK
------------------------
2026-08-22 00:10:00 0x7f...
*** (1) TRANSACTION:
TRANSACTION 1001, ACTIVE 0 sec starting index read
mysql tables in use 1, locked 1
LOCK WAIT 2 lock struct(s), heap size 1136, 1 row lock(s)
MySQL thread id 12, OS thread handle 139..., query id 450 UPDATE finflow_ledger_accounts SET balance = balance - 100 WHERE id = 2
*** (1) WAITING FOR THIS LOCK TO BE GRANTED:
RECORD LOCKS space id 42 page no 3 n bits 72 index PRIMARY of table finflow_ledger_accounts trx id 1001 lock_mode X locks rec but not gap waiting
*** (2) HOLDS THE LOCK(S):
RECORD LOCKS space id 42 page no 3 n bits 72 index PRIMARY of table finflow_ledger_accounts trx id 1002 lock_mode X locks rec but not gap
*** WE ROLL BACK TRANSACTION (1)
```

#### Method 2: PostgreSQL Query Plan (`EXPLAIN ANALYZE`)
```sql
EXPLAIN (ANALYZE, BUFFERS) 
SELECT * FROM finflow_ledger_accounts WHERE LOWER(account_number) = 'acc-001';
```
**Diagnostic Output (Missing Functional Index):**
```text
Seq Scan on finflow_ledger_accounts (cost=0.00..185.00 rows=50 width=64) (actual time=8.210..8.412 rows=1 loops=1)
  Filter: (lower((account_number)::text) = 'acc-001'::text)
  Rows Removed by Filter: 999999
  Buffers: shared hit=842 read=1200
Planning Time: 0.120 ms
Execution Time: 8.435 ms
```

---

### 7. Step-by-Step Debugging Procedure

```text
Step 1: Inspect Deadlock Error Logs.
        Identify the exact SQL statements, transaction IDs, and entity primary keys involved.

Step 2: Map Lock Acquisition Sequence.
        Check if transactions lock rows in inconsistent orders across different code paths.

Step 3: Implement Deterministic Lock Ordering.
        Enforce natural sorting on primary keys before acquiring locks:
        Long firstId = Math.min(idA, idB);
        Long secondId = Math.max(idA, idB);

Step 4: Analyze Query Plans for Table Scans.
        Run EXPLAIN (ANALYZE) on slow queries.
        Check for "Seq Scan" or "ALL" (table scan) where "Index Scan" is expected.

Step 5: Configure Automatic Retries with Spring Retry.
        Add @Retryable on transactions susceptible to transient database lock contention.
```

---

### 8. Technical Root Cause Deep-Dive

#### 1. Mathematical Proof: Total Resource Ordering (Dijkstra's Hierarchy)
According to the Coffman conditions, a deadlock can occur only if all four conditions hold:
1. Mutual Exclusion
2. Hold and Wait
3. No Preemption
4. **Circular Wait**

By enforcing a strict **total ordering** on locked resources (i.e. always lock $ID_{\min}$ before $ID_{\max}$):
$$\forall (A, B) \quad \text{Lock}(A) \prec \text{Lock}(B) \iff ID(A) < ID(B)$$
Because all threads request locks in the exact same monotonically increasing sequence, a cycle in the resource allocation graph cannot form. **Circular wait is mathematically eliminated.**

#### 2. Functional Index Invalidation
When an index is created on `account_number`:
- The B-Tree stores raw values: `['ACC-001', 'ACC-002', ...]`.
- A query with `WHERE LOWER(account_number) = 'acc-001'` cannot use the index because the index does not store the lowercased transformation.
- **Fix:** Create a functional index:
  ```sql
  CREATE INDEX idx_ledger_acc_lower ON finflow_ledger_accounts (LOWER(account_number));
  ```

---

### 9. Production-Grade Fixes

#### ✅ Fix 1: Deterministic Pessimistic Lock Ordering
```java
@Service
public class TransferService {

    private final LedgerAccountRepository accountRepository;

    @Transactional
    public void transferDeterministic(Long fromId, Long toId, BigDecimal amount) {
        // Enforce total ordering: Min ID is ALWAYS locked first
        Long firstLockId = Math.min(fromId, toId);
        Long secondLockId = Math.max(fromId, toId);

        LedgerAccountEntity first = accountRepository.findByIdForUpdate(firstLockId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + firstLockId));
        LedgerAccountEntity second = accountRepository.findByIdForUpdate(secondLockId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + secondLockId));

        LedgerAccountEntity from = (firstLockId.equals(fromId)) ? first : second;
        LedgerAccountEntity to = (firstLockId.equals(toId)) ? first : second;

        from.debit(amount);
        to.credit(amount);
        accountRepository.save(from);
        accountRepository.save(to);
    }
}
```

#### ✅ Fix 2: Optimistic Locking with Spring Retry
```java
@Service
public class OptimisticTransferService {

    private final LedgerAccountRepository accountRepository;

    @Transactional
    @Retryable(
            retryFor = {ObjectOptimisticLockingFailureException.class, CannotAcquireLockException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 50, multiplier = 2.0)
    )
    public void transferOptimistic(Long fromId, Long toId, BigDecimal amount) {
        LedgerAccountEntity from = accountRepository.findById(fromId).orElseThrow();
        LedgerAccountEntity to = accountRepository.findById(toId).orElseThrow();

        from.debit(amount);
        to.credit(amount);
        accountRepository.save(from);
        accountRepository.save(to);
    }
}
```

#### ✅ Fix 3: Repository Pessimistic Write Lock with Timeout Hint
```java
public interface LedgerAccountRepository extends JpaRepository<LedgerAccountEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "1000")})
    @Query("SELECT a FROM LedgerAccountEntity a WHERE a.id = :id")
    Optional<LedgerAccountEntity> findByIdForUpdate(@Param("id") Long id);
}
```

---

### 10. Verification

1. **Deterministic Lock Ordering Test:** Run `DeterministicLockOrderingTest.java` to verify 4 concurrent bidirectional transfer threads execute simultaneously without deadlock, producing accurate final account balances.
2. **Optimistic Lock Retry Test:** Run `OptimisticLockRetryTest.java` to confirm `@Version` increment and `@Retryable` execution.
3. **Integration Test:** Run `Module10IntegrationTest.java` to verify account creation and transfer REST endpoints.

---

### 11. Prevention & Production Readiness

1. **Enforce Lock Ordering in Architecture Reviews:**
   Any service that locks multiple database entities must sort the entities by their natural primary key before invoking repository find/lock methods.
2. **Set Global Lock Timeouts:**
   Prevent indefinite thread blocking by configuring database-level lock wait timeouts:
   ```yaml
   spring:
     jpa:
       properties:
         jakarta.persistence.lock.timeout: 3000
   ```
3. **Automate Slow Query & Missing Index Alerts:**
   Configure PostgreSQL `pg_stat_statements` and alert when `mean_exec_time > 100ms`.

---

### 12. Interview & Production Questions

#### Interview Questions
1. **Q: What are the four Coffman conditions required for a database deadlock to occur?**
2. **Q: How does deterministic lock ordering mathematically guarantee deadlock prevention?**
3. **Q: What is the difference between `PESSIMISTIC_READ`, `PESSIMISTIC_WRITE`, and `OPTIMISTIC` locking in JPA?**
4. **Q: Why does wrapping a column in a function like `LOWER(email)` bypass standard B-Tree indexes?**
5. **Q: How does Spring Retry's `@Retryable` handle transient `CannotAcquireLockException`?**

#### Production Incident Questions
1. **Incident:** You see 100 deadlock exceptions per minute between your payment worker and a nightly reconciliation job. Both update the same table. How do you resolve this without reducing concurrency?
2. **Incident:** A query using `WHERE created_at >= ? AND merchant_id = ?` is performing a Sequential Scan. An index exists on `(created_at, merchant_id)`. Why is PostgreSQL ignoring the index?
3. **Incident:** An update query hangs for 60 seconds and then throws `LockTimeoutException`. How do you identify which transaction is holding the blocking lock in PostgreSQL?
4. **Incident:** An engineer replaced optimistic locking with pessimistic locking on a hot row (e.g. global inventory counter). Application throughput dropped by 80%. Why?
5. **Incident:** What is the difference between an Index Scan, an Index Only Scan, and a Bitmap Heap Scan in `EXPLAIN ANALYZE`?

#### Trick Questions
1. **Trick:** If two transactions execute `SELECT ... FOR UPDATE` on different rows of the same table, can they deadlock in InnoDB? *(Hint: Gap locks / Next-key locks during range scans!)*
2. **Trick:** Does adding `@Transactional(isolation = Isolation.SERIALIZABLE)` eliminate deadlocks?
3. **Trick:** If a method with `@Retryable` fails on all attempts, what exception is thrown to the caller?

---

*(Answers and detailed explanations are provided in the Master Answer Guide)*
