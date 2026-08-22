# Module 21: Concurrency, Race Conditions & Distributed Locks

## Issue 21.1: Double-Spending Lost Updates, Distributed Lock Auto-Release Disasters, and AB-BA Deadlocks

---

### 1. Scenario

During a major crypto/stock trading rush on the **FinFlow Digital Wallet & Settlement Platform**:
1. A retail customer with an account balance of **$500.00** submitted 10 simultaneous debit requests ($100.00 each) within 15 milliseconds across multiple browser tabs and mobile devices.
2. Because the application executed standard non-atomic **Check-Then-Act logic** (`if (acc.getBalance() >= amount) { acc.setBalance(acc.getBalance() - amount); repo.save(acc); }`), all 10 concurrent threads read the initial **$500.00** snapshot before any write committed.
3. Every thread concluded the balance was sufficient and saved a decremented balance of $400.00. The customer successfully **withdrew $1,000.00 from a $500.00 balance**, resulting in an unrecoverable negative balance and direct financial loss (**The Lost Update / Double-Spending Race Condition**).
4. To mitigate, an engineer introduced Redis distributed locks: `SET lock:account:101 <UUID> NX PX 5000` (5-second lease time). However, during an external KYC compliance call taking **7.5 seconds**, the lock automatically expired in Redis.
5. Process B immediately acquired the lock on the same account. When Process A finally finished its slow call, it executed a blind `redis.del("lock:account:101")`, **deleting Process B's lock**! Process C acquired the freed lock, resulting in **two concurrent threads executing inside the critical section simultaneously**, corrupting the ledger (**Mutual Exclusion Failure**).
6. Concurrently, high-frequency currency conversions between Account A and Account B executed simultaneously (`A -> B` on Node 1, `B -> A` on Node 2), acquiring database row locks in opposite order and triggering catastrophic **PostgreSQL Row-Level Deadlocks** (`40P01: deadlock detected`).

---

### 2. Symptoms

```text
1. Account Balance Inconsistencies & Negative Balances:
   User accounts balance drops below zero without overdraft permissions.
   Audit logs show multiple successful withdrawals exceeding initial total funds.

2. Distributed Lock Mutual Exclusion Failures:
   Two different nodes execute the exact same payment clearance at the exact same timestamp.

3. Database Row-Level Deadlocks:
   org.postgresql.util.PSQLException: ERROR: deadlock detected
   Detail: Process 14210 waits for ExclusiveLock on tuple (12, 4) of relation accounts;
   blocked by process 14218 which waits for ExclusiveLock on tuple (14, 8).

4. High Optimistic Locking Rollback Rates:
   Flash-sale inventory decrement fails with OptimisticLockingFailureException for 95% of users.

5. Local Lock Ineffectiveness in Clustered Environments:
   Java synchronized blocks or ReentrantLock providing zero protection across multi-pod deployments.
```

---

### 3. Possible Root Causes

1. **Non-Atomic Check-Then-Act Pattern:** Reading state into application memory, evaluating conditions, and writing back without atomic database constraints or CAS operations.
2. **Blind Distributed Lock Deletion:** Calling `DEL lock:key` without verifying that the caller still owns the lock via a Lua script.
3. **Inconsistent Lock Ordering (Circular Wait):** Acquiring locks on multiple resources in random or caller-defined order instead of enforcing deterministic lexicographical sorting.
4. **Local JVM Synchronization in Microservice Clusters:** Using `synchronized` or `ReentrantLock` which only synchronizes threads within a single JVM process.
5. **Lock Lease Starvation:** Setting static lock timeouts without background renewal (Watchdog) for long-running operations.

---

### 4. Architecture Context: Concurrency Hazards & Safe Locking Mechanics

```text
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                    THE CHECK-THEN-ACT RACE CONDITION & SAFE ATOMIC CAS                          │
│                                                                                                 │
│  ❌ CHECK-THEN-ACT FLAW (Lost Update):                                                          │
│  Thread 1: Read Balance ($500) ──────────────► Validate ($500 >= $100) ──► Write Balance ($400) │
│  Thread 2:      Read Balance ($500) ──► Validate ($500 >= $100) ────────► Write Balance ($400)  │
│  Result: Both threads withdraw $100 ($200 total), but stored balance is $400 ($100 Lost Update!)│
│                                                                                                 │
│  ✅ ATOMIC IN-DATABASE CAS UPDATE:                                                              │
│  SQL: UPDATE accounts SET balance = balance - 100 WHERE id = 101 AND balance >= 100             │
│  - Thread 1 executes: Rows Affected = 1 (Balance becomes $400, Success!)                        │
│  - Thread 2 executes: Rows Affected = 1 (Balance becomes $300, Success!)                        │
│  - ... (Thread 6 executes): Rows Affected = 0 (Condition Fails, Insufficient Funds!)            │
│  Result: EXACTLY 5 withdrawals succeed; Balance is $0.00; ZERO LOST UPDATES!                    │
│                                                                                                 │
│  ✅ SAFE DISTRIBUTED LOCK RELEASE (LUA SCRIPT):                                                 │
│  if redis.call('get', KEYS[1]) == ARGV[1] then                                                  │
│      return redis.call('del', KEYS[1])                                                          │
│  else                                                                                           │
│      return 0 -- Prevent deleting another process's lock if lease expired!                      │
│  end                                                                                            │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 5. How to Reproduce the Issues

#### ❌ Anti-Pattern 1: Check-Then-Act in Spring Service
```java
// ❌ FATAL ANTI-PATTERN: Concurrent threads read stale balance before save commits!
@Transactional
public void unsafeWithdraw(String accountId, double amount) {
    Account acc = accountRepository.findById(accountId).orElseThrow();
    if (acc.getBalance() >= amount) {
        acc.setBalance(acc.getBalance() - amount);
        accountRepository.save(acc); // Overwrites concurrent debits!
    }
}
```

#### ❌ Anti-Pattern 2: Blind Redis Lock Release
```java
// ❌ ANTI-PATTERN: If operation takes longer than 5s, this DEL releases Process B's lock!
public void processWithLock(String accountId) {
    String lockKey = "lock:" + accountId;
    redisTemplate.opsForValue().set(lockKey, "LOCKED", Duration.ofSeconds(5));
    try {
        slowDatabaseAndKycOperation(); // Takes 7.5 seconds
    } finally {
        redisTemplate.delete(lockKey); // ❌ Deletes new lock acquired by another pod!
    }
}
```

#### ❌ Anti-Pattern 3: Inconsistent Lock Acquisition (Deadlock Trap)
```java
// ❌ ANTI-PATTERN: Thread 1 locks A then B; Thread 2 locks B then A -> DEADLOCK!
public void transfer(String fromId, String toId, double amount) {
    lockService.lock(fromId);
    lockService.lock(toId); // Circular wait deadlock!
    // transfer logic
}
```

---

### 6. Evidence Collection & Diagnostic Probing

#### Method 1: Detect PostgreSQL Deadlocks in Real Time
```sql
SELECT blocked_locks.pid     AS blocked_pid,
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

#### Method 2: Audit Redis Lock Operations via Monitor (⚠️ Do not run blindly in production: MONITOR degrades Redis throughput by up to 50%)
```bash
redis-cli monitor | grep -E "SET lock|DEL lock"
```

---

### 7. Step-by-Step Debugging Procedure

```text
Step 1: Check for Lost Updates & Balance Drift.
        Compare sum of transaction ledger records against current account balances.

Step 2: Replace Check-Then-Act with Atomic In-Database CAS Statements.
        Convert read-modify-write loops to single `UPDATE ... WHERE balance >= :amount`.

Step 3: Enforce Safe Lua Script Distributed Lock Release.
        Ensure all Redis lock releases verify owner UUID before deletion.

Step 4: Implement Deterministic Resource Lock Ordering.
        Sort multiple resource IDs lexicographically before acquiring locks to eliminate deadlocks.

Step 5: Add Lock Expiration Watchdogs or Redisson RLock.
        Use background renewal timers for operations with dynamic execution durations.
```

---

### 8. Technical Root Cause Deep-Dive

#### 1. Why Check-Then-Act Fails Under Concurrency
- Under the default `READ COMMITTED` transaction isolation level, each SQL query sees only committed changes at the moment the query began.
- When 10 concurrent transactions execute `SELECT balance FROM accounts WHERE id = 101`, all 10 transactions read the committed balance of `$500.00`.
- Each transaction calculates $500 - 100 = 400$ in application memory.
- Each transaction writes `UPDATE accounts SET balance = 400 WHERE id = 101`.
- The final committed balance is `$400.00`, completely ignoring 9 out of 10 debits!

#### 2. The Atomic Compare-And-Swap (CAS) SQL Pattern
By moving the condition evaluation into the database engine:
```sql
UPDATE accounts 
SET balance = balance - :amount, 
    version = version + 1 
WHERE id = :id AND balance >= :amount;
```
The database's internal row-level lock serializes the updates. Each update evaluates `balance >= :amount` against the *current row state*, returning `1` for valid debits and `0` for insufficient funds without any application-level lock overhead.

#### 3. Dijkstra's Resource Hierarchy for Deadlock Prevention
Deadlocks require 4 conditions: Mutual Exclusion, Hold and Wait, No Preemption, and **Circular Wait**.
By assigning a strict total order to resources (e.g. lexicographical sorting of account IDs: $\text{accA} < \text{accB}$):
- Thread 1 (Transfer A -> B): Locks A, then locks B.
- Thread 2 (Transfer B -> A): Also locks A, then locks B!
- Thread 2 blocks on Lock A until Thread 1 finishes. **Circular wait is mathematically impossible.**

---

### 9. Production-Grade Fixes

#### ✅ Fix 1: Atomic CAS Debit Service (`WalletBalanceService.java`)
```java
@Service
public class WalletBalanceService {

    public boolean atomicCasDebit(String accountId, double amount) {
        AtomicReference<Double> balanceRef = accounts.get(accountId);
        while (true) {
            Double current = balanceRef.get();
            if (current < amount) return false; // Insufficient funds
            Double updated = current - amount;
            if (balanceRef.compareAndSet(current, updated)) {
                return true; // CAS succeeded atomically!
            }
        }
    }
}
```

#### ✅ Fix 2: Safe Distributed Locking with Lua Release (`DistributedLockService.java`)
```java
@Service
public class DistributedLockService {

    // Equivalent to Redis Lua Script:
    // if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end
    public synchronized boolean releaseSafely(String resourceKey, String ownerId) {
        LockRecord current = lockTable.get(resourceKey);
        if (current != null && current.ownerId().equals(ownerId)) {
            lockTable.remove(resourceKey);
            return true;
        }
        return false; // Prevented deleting another process's lock!
    }
}
```

#### ✅ Fix 3: Deadlock-Free Deterministic Lock Ordering
```java
public boolean transferWithLockOrdering(String fromAcc, String toAcc, double amount, long leaseMs) {
    String firstLock = fromAcc.compareTo(toAcc) < 0 ? "lock:wallet:" + fromAcc : "lock:wallet:" + toAcc;
    String secondLock = fromAcc.compareTo(toAcc) < 0 ? "lock:wallet:" + toAcc : "lock:wallet:" + fromAcc;

    String ownerId = UUID.randomUUID().toString();
    if (!lockService.tryAcquire(firstLock, ownerId, leaseMs)) return false;

    try {
        if (!lockService.tryAcquire(secondLock, ownerId, leaseMs)) return false;
        try {
            return executeTransfer(fromAcc, toAcc, amount);
        } finally {
            lockService.releaseSafely(secondLock, ownerId);
        }
    } finally {
        lockService.releaseSafely(firstLock, ownerId);
    }
}
```

---

### 10. Verification

1. **Atomic CAS Concurrency Test:** Run `RaceConditionLostUpdateTest.java` to verify that 10 concurrent $100 debits against a $500 balance result in exactly 5 debits and $0.00 balance.
2. **Distributed Lock Safety Test:** Run `DistributedLockSafetyTest.java` to verify that expired locks reacquired by another process cannot be released by the original owner.
3. **Deadlock-Free Transfer Test:** Run `DeadlockFreeTransferTest.java` to verify that concurrent bidirectional transfers complete without deadlocks.
4. **Integration Test:** Run `Module21IntegrationTest.java` to verify Spring Boot context and Actuator health.

---

### 11. Prevention & Production Readiness

1. **Rule: Always Use Atomic SQL Updates for Counters & Balances:**
   Never read a counter to Java memory, increment it, and write it back. Use `UPDATE ... SET count = count + 1`.
2. **Rule: Never Delete Distributed Locks Without Checking Owner UUID:**
   Always execute a Lua script verifying ownership before calling `DEL`.
3. **Prometheus Alerting Rule for Database Deadlocks:**
```yaml
- alert: PostgresDeadlocksDetected
  expr: rate(pg_stat_database_deadlocks[5m]) > 0
  for: 1m
  labels:
    severity: critical
  annotations:
    summary: "PostgreSQL database is experiencing active deadlocks (Rate: {{ $value }} deadlocks/sec)"
```

---

### 12. Interview & Production Questions

#### Interview Questions
1. **Q: What is a Check-Then-Act race condition, and why does `@Transactional` fail to prevent it?**
   *Answer:* Check-Then-Act occurs when application code reads data, checks a condition, and updates the database based on that stale snapshot. `@Transactional` with standard `READ COMMITTED` isolation allows concurrent transactions to read the same initial state simultaneously, causing Lost Updates unless explicit locking or atomic CAS updates are used.
2. **Q: Why must distributed lock release be executed via a Redis Lua script rather than a simple `redis.del()`?**
   *Answer:* If a process takes longer than the lock lease time, the lock expires and another process acquires it. A blind `DEL` will delete the new process's lock, breaking mutual exclusion. The Lua script atomically verifies that the lock's value matches the caller's UUID before deleting.
3. **Q: How does deterministic lock ordering eliminate database deadlocks?**
   *Answer:* Deadlocks require circular wait (Process 1 holds A, waits for B; Process 2 holds B, waits for A). By sorting resource IDs before acquiring locks, all processes request locks in the same global sequence, mathematically preventing circular waits.
4. **Q: What is the difference between Optimistic Locking and Pessimistic Locking in high-contention flash sales?**
   *Answer:* Optimistic locking (`@Version`) does not acquire DB locks during reads but rolls back transactions on commit conflicts, leading to high failure rates under heavy contention. Pessimistic locking (`SELECT ... FOR UPDATE`) serializes access at the DB row level, preventing rollbacks at the cost of connection wait latency.
5. **Q: What is a Lock Watchdog, and how does Redisson implement it?**
   *Answer:* A Lock Watchdog is a background timer that automatically extends a distributed lock's lease time as long as the holding thread remains alive, preventing premature lock expiration during long-running operations.

#### Production Incident Questions
1. **Incident:** An account balance became negative (-$200) despite code having `if (balance >= amount)`. What went wrong?
   *Diagnosis:* Concurrent requests read the same balance simultaneously before any write committed. Fix: Use atomic CAS SQL updates (`UPDATE accounts SET balance = balance - :amount WHERE id = :id AND balance >= :amount`).
2. **Incident:** A batch payment job took 8 seconds with a 5-second Redis lock, and duplicate payments were processed. Why?
   *Diagnosis:* Lock lease expired at 5s, allowing a second worker to acquire the lock, followed by the first worker deleting the second worker's lock via blind `DEL`. Fix: Use Redisson Watchdog renewal and safe Lua release.
3. **Incident:** High concurrency money transfers between accounts cause frequent `40P01: deadlock detected` in PostgreSQL. How do you resolve it?
   *Diagnosis:* Inconsistent lock acquisition order (`A -> B` vs `B -> A`). Fix: Enforce sorted lexicographical lock ordering on account IDs.
4. **Incident:** You deployed 8 replicas of a microservice with `synchronized (this) { ... }`, but race conditions still occur in production. Why?
   *Diagnosis:* Local JVM synchronization only protects threads within a single JVM instance. Across 8 replicas, each JVM has its own monitor lock. Fix: Use distributed Redis locks or atomic database updates.
5. **Incident:** A flash-sale inventory endpoint has a 90% error rate due to `OptimisticLockingFailureException`. How do you optimize it?
   *Diagnosis:* High contention makes optimistic version checking fail repeatedly. Fix: Switch to atomic SQL decrement (`UPDATE inventory SET stock = stock - 1 WHERE item_id = :id AND stock > 0`) or Redis Lua atomic decrement (`DECRBY`).

#### Trick Questions
1. **Trick:** Does `AtomicInteger.incrementAndGet()` work across multiple Spring Boot microservice instances?
   *Answer:* No! `AtomicInteger` uses JVM CPU-level hardware CAS instructions (`Unsafe.compareAndSwapInt`), which only apply to the local JVM heap memory on that physical host.
2. **Trick:** If a transaction uses `SERIALIZABLE` isolation, are deadlocks impossible?
   *Answer:* No! In fact, `SERIALIZABLE` isolation increases the rate of serialization failure anomalies and deadlocks because the database engine must abort and roll back transactions whenever concurrent dependency graphs cannot be serialized.
3. **Trick:** Can Redis single-threaded execution eliminate the need for distributed locks in multi-step transactions?
   *Answer:* Redis single-threading only guarantees that *individual Redis commands* (or Lua scripts) are atomic. If your business logic spans multiple commands, DB writes, and external network calls, an explicit distributed lock is still required.

---

*(Answers and detailed explanations are provided in the Master Answer Guide)*
