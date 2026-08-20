---
chapter: 170
topic: Transactions — @Transactional Internals, Propagation Behaviors, Isolation Levels
prerequisite_chapters: [10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160]
reference_system_node: Payment Service ↔ PostgreSQL payment_db (PaymentTransaction, AuditRecord, LedgerPosting, JpaTransactionManager)
---

# Chapter 170: Transactions — @Transactional Internals, Propagation Behaviors, Isolation Levels

## 1. Concept

In financial payment architectures like FinFlow, transactional integrity is non-negotiable. When a customer authorizes a $500 payment, the platform must persist a `PaymentTransaction`, insert double-entry `LedgerPosting` rows, write an immutable `AuditRecord`, and dispatch an asynchronous event to Kafka. If any step fails, the system must either atomically roll back all database state or preserve critical audit trails without corrupting ledger balances.

Spring's `@Transactional` annotation provides declarative transaction management, abstracting the complexity of JDBC connection management, commit/rollback semantics, and savepoints behind Aspect-Oriented Programming (AOP).

However, `@Transactional` is not magic. When engineers treat it as a black box, high-concurrency systems suffer catastrophic failures:
- **`UnexpectedRollbackException`**: Catching an exception from an inner method that silently marked the physical transaction as `rollback-only`.
- **Phantom Commits on Checked Exceptions**: Spring's default rollback policy silently commits transactions when checked exceptions are thrown.
- **Self-Invocation Proxy Bypass**: Calling a `@Transactional(propagation = REQUIRES_NEW)` method from within the same class runs in the caller's transaction, bypassing isolation.
- **HikariCP Connection Pool Deadlocks**: Holding database connections open while waiting on slow third-party HTTP APIs (e.g., Stripe) or exhausting connection pools with nested `REQUIRES_NEW` transactions.

---

## 2. Internal Working

### The `@Transactional` AOP Interception Pipeline

When a Spring Bean annotated with `@Transactional` is invoked, execution flows through Spring's transaction interceptor chain:

```
Caller ──► CGLIB / Dynamic Proxy ──► TransactionInterceptor
                                            │
               ┌────────────────────────────┴───────────────────────────┐
               ▼                                                        ▼
   1. TransactionAttributeSource                          2. PlatformTransactionManager
      (Parses Propagation, Isolation,                        (JpaTransactionManager)
       Timeout, Rollback rules)                                         │
                                                                        ▼
                                                   3. TransactionSynchronizationManager
                                                      (Binds Connection to ThreadLocal)
                                                                        │
                                                                        ▼
                                                   4. Target Business Method Execution
                                                                        │
                                       ┌────────────────────────────────┴──────────────────┐
                                       ▼ (Success)                                         ▼ (Exception)
                          5. Commit & afterCommit hooks                       6. Rollback / markRollbackOnly
```

1. **`TransactionInterceptor`**: Invokes `invokeWithinTransaction()`, delegating to `TransactionAspectSupport`.
2. **`PlatformTransactionManager`** (`JpaTransactionManager` / `DataSourceTransactionManager`): Obtains or resumes a `TransactionStatus` instance.
3. **`TransactionSynchronizationManager`**: Uses `ThreadLocal<Map<Object, Object>>` to bind the physical database connection (`ConnectionHolder`) and active transaction synchronizations to the current execution thread.
4. **Target Execution**: Invokes the real service method.
5. **Commit / Rollback Decision**:
   - If the method completes normally, the transaction manager commits the physical connection and triggers `TransactionSynchronization.afterCommit()` callbacks.
   - If an exception is thrown, `TransactionAspectSupport.completeTransactionAfterThrowing()` evaluates the exception against rollback rules.

---

### Propagation Behaviors Explained

Spring supports 7 transaction propagation behaviors. In enterprise backend systems, three dominate:

| Propagation | Behavior | Physical DB Connection Impact |
|---|---|---|
| **`REQUIRED`** *(Default)* | Joins current transaction if one exists; creates a new one if none exists. | Shares the same physical JDBC connection. |
| **`REQUIRES_NEW`** | Suspends current transaction; creates a **new, independent** physical transaction. | **Borrows a 2nd physical JDBC connection** from HikariCP! |
| **`NESTED`** | Executes within a nested transaction using JDBC **Savepoints** if a transaction exists; acts like `REQUIRED` if none exists. | Uses the same JDBC connection via `connection.setSavepoint()`. |
| **`MANDATORY`** | Requires an existing transaction; throws `IllegalTransactionStateException` if none exists. | Shares existing connection. |
| **`SUPPORTS`** | Runs transactionally if a transaction exists; runs non-transactionally if none exists. | Shares or none. |
| **`NOT_SUPPORTED`**| Suspends active transaction; executes non-transactionally. | Suspends connection. |
| **`NEVER`** | Throws exception if an active transaction exists. | None. |

```
Scenario: REQUIRED vs. REQUIRES_NEW

[Thread-1] Service A (@Transactional REQUIRED) ── [Holds Conn-1 from HikariCP]
                 │
                 ├──► Service B (@Transactional REQUIRED) ────── [Reuses Conn-1]
                 │
                 └──► Service C (@Transactional REQUIRES_NEW) ──► [Suspends Conn-1, Borrows Conn-2 from HikariCP!]
```

> [!WARNING]
> **HikariCP Pool Deadlock Risk with `REQUIRES_NEW`**:
> If your HikariCP pool has 10 connections and 10 concurrent HTTP threads simultaneously execute `Service A` (each holding 1 connection) and then call `Service C` (`REQUIRES_NEW`), all 10 threads will block waiting for a 2nd connection from the pool. Because no connections are available and no thread can finish, the application enters an unrecoverable **self-deadlock** until connection timeouts expire!
> **Formula for Safe Pool Sizing with `REQUIRES_NEW`**:
> $$\text{Pool Size} > \text{Max Concurrent Threads} \times (\text{Max Nested REQUIRES\_NEW Depth} + 1)$$

---

### The `UnexpectedRollbackException` Mechanism

Why does catching an exception in an outer method still result in a rollback?

```
[Outer Method (@Transactional REQUIRED)]
   │
   ├── try {
   │      [Inner Method (@Transactional REQUIRED)]
   │         └── Throws RuntimeException!
   │   } catch (Exception e) {
   │      // Outer method attempts to swallow exception and return response...
   │   }
   │
   └── Outer Method completes -> Spring attempts COMMIT -> CRASH!
```

1. When `Inner Method` throws a `RuntimeException`, Spring's `TransactionInterceptor` intercepts the exception.
2. Because `Inner Method` is `REQUIRED`, it participates in the outer physical transaction. It cannot roll back the database transaction immediately (since the outer method is still running), so it calls `TransactionStatus.setRollbackOnly()`.
3. The `Outer Method` catches the exception and assumes the failure is handled.
4. When `Outer Method` finishes, Spring's `TransactionInterceptor` attempts to execute `connection.commit()`.
5. The `PlatformTransactionManager` checks `transactionStatus.isRollbackOnly()`. Discovering it is `true`, it issues `connection.rollback()` and throws:
   ```text
   org.springframework.transaction.UnexpectedRollbackException: 
   Transaction silently rolled back because it has been marked as rollback-only
   ```

---

### Rollback Rules: Checked vs. Unchecked Exceptions

Spring's transaction rollback policy defaults to EJB convention:
- **Automatic Rollback**: Subclasses of `java.lang.RuntimeException` and `java.lang.Error`.
- **Automatic Commit (NO Rollback)**: Checked exceptions (subclasses of `java.lang.Exception` that do not extend `RuntimeException`, e.g. `PaymentProcessingException`, `IOException`, `SQLException`).

```java
// SEVERE PRODUCTION HAZARD:
@Transactional // Will NOT roll back if PaymentProcessingException is thrown!
public void processPayment() throws PaymentProcessingException { ... }

// PRODUCTION MANDATE:
@Transactional(rollbackFor = Exception.class) // Guaranteed rollback on ALL exceptions
public void processPayment() throws PaymentProcessingException { ... }
```

---

### Isolation Levels & PostgreSQL MVCC Implementation

| Isolation Level | Dirty Read | Non-Repeatable Read | Phantom Read | PostgreSQL Implementation |
|---|---|---|---|---|
| **`READ_UNCOMMITTED`** | Prevented in PG | Possible | Possible | Treated as `READ_COMMITTED` in PostgreSQL |
| **`READ_COMMITTED`** *(Default)*| Prevented | Possible | Possible | Statement-level snapshot (`xmin`/`xmax` MVCC) |
| **`REPEATABLE_READ`** | Prevented | Prevented | Prevented in PG | Transaction-level snapshot (First query snapshot) |
| **`SERIALIZABLE`** | Prevented | Prevented | Prevented | Serializable Snapshot Isolation (SSI) / Predicate Locks |

In PostgreSQL:
- **`READ_COMMITTED`**: Each SQL query within a transaction sees a new snapshot of committed data as of the instant that query started.
- **`REPEATABLE_READ`**: All queries within the transaction see the exact same snapshot of committed data as of the instant the *first non-transaction-control query* executed. If two concurrent transactions attempt to update the same row, the second transaction throws:
  ```text
  ERROR: could not serialize access due to concurrent update (SQLState: 40001)
  ```
- **`SERIALIZABLE`**: PostgreSQL tracks read-write dependencies across transactions using SIREAD locks. If a cycle is detected, one transaction is aborted with a serialization failure.

---

## 3. Enterprise Scenario: FinFlow Payment Platform

In the **FinFlow Payment Processing Service**:

```
Client ──► API Gateway ──► Payment Service (20 pods) ──► PostgreSQL (payment_db)
                              │
                              ├── (1) Insert PaymentTransaction (DB)
                              ├── (2) Call 3rd-Party Payment Gateway (Stripe HTTP API)
                              ├── (3) Record AuditRecord (REQUIRES_NEW)
                              └── (4) Publish Kafka Event (order-events)
```

- **Scale & Limits**:
  - Peak Traffic: 4,000 req/sec across 20 pods.
  - HikariCP Pool: 10 connections per pod (200 total database connections).
  - Stripe Gateway p99 Latency: Normally 250ms, but spikes to 2,500ms during partner degradation.

---

## 4. Incorrect Implementation

Below is the vulnerable implementation containing four classic enterprise anti-patterns:

```java
package com.finflow.chapter170.incorrect;

import com.finflow.chapter170.domain.AuditRecord;
import com.finflow.chapter170.domain.PaymentTransaction;
import com.finflow.chapter170.domain.TransactionStatus;
import com.finflow.chapter170.exception.FraudDetectedException;
import com.finflow.chapter170.exception.PaymentProcessingException;
import com.finflow.chapter170.repository.AuditRecordRepository;
import com.finflow.chapter170.repository.PaymentTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * INCORRECT IMPLEMENTATION:
 * 1. Holds DB connection during external HTTP calls.
 * 2. Catches exception from inner REQUIRED method -> UnexpectedRollbackException.
 * 3. Checked exception does not roll back transaction.
 * 4. Self-invocation of REQUIRES_NEW method bypasses Spring AOP proxy.
 */
@Service
public class PaymentProcessingServiceIncorrect {

    private final PaymentTransactionRepository transactionRepository;
    private final AuditRecordRepository auditRecordRepository;
    private final FraudCheckServiceIncorrect fraudCheckService;

    public PaymentProcessingServiceIncorrect(PaymentTransactionRepository transactionRepository,
                                            AuditRecordRepository auditRecordRepository,
                                            FraudCheckServiceIncorrect fraudCheckService) {
        this.transactionRepository = transactionRepository;
        this.auditRecordRepository = auditRecordRepository;
        this.fraudCheckService = fraudCheckService;
    }

    /**
     * Anti-Pattern 1: Rollback-Only Catch Trap.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public PaymentTransaction processPaymentWithRollbackCatchTrap(String paymentRef, String customerId, BigDecimal amount) {
        PaymentTransaction tx = new PaymentTransaction(
                UUID.randomUUID(),
                paymentRef,
                customerId,
                amount,
                "USD",
                TransactionStatus.INITIATED,
                Instant.now()
        );
        transactionRepository.save(tx);

        try {
            // Joins existing transaction and throws RuntimeException -> marks rollback-only
            fraudCheckService.validateFraudRules(customerId, amount);
            tx.setStatus(TransactionStatus.SUCCESS);
        } catch (FraudDetectedException ex) {
            // Swallowing exception does NOT clear rollback-only flag!
            tx.setStatus(TransactionStatus.FAILED);
            // CRASH at commit: UnexpectedRollbackException
        }

        return tx;
    }

    /**
     * Anti-Pattern 2: Checked Exception Rollback Default.
     */
    @Transactional
    public void processPaymentWithCheckedException(String paymentRef, String customerId, BigDecimal amount)
            throws PaymentProcessingException {
        PaymentTransaction tx = new PaymentTransaction(
                UUID.randomUUID(),
                paymentRef,
                customerId,
                amount,
                "USD",
                TransactionStatus.INITIATED,
                Instant.now()
        );
        transactionRepository.save(tx);

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            // Checked exception: Spring COMMITS this transaction instead of rolling back!
            throw new PaymentProcessingException("Invalid amount: " + amount);
        }

        tx.setStatus(TransactionStatus.SUCCESS);
    }

    /**
     * Anti-Pattern 3: Self-Invocation bypassing Proxy.
     */
    @Transactional
    public void processWithSelfInvocation(String paymentRef, String customerId, BigDecimal amount) {
        PaymentTransaction tx = new PaymentTransaction(
                UUID.randomUUID(),
                paymentRef,
                customerId,
                amount,
                "USD",
                TransactionStatus.INITIATED,
                Instant.now()
        );
        transactionRepository.save(tx);

        // Self-invocation: Proxy bypassed! REQUIRES_NEW ignored!
        this.recordAuditRequiresNewInternal(paymentRef, "INITIATED");

        throw new RuntimeException("Simulated payment gateway failure");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAuditRequiresNewInternal(String referenceId, String status) {
        AuditRecord audit = new AuditRecord(
                UUID.randomUUID(),
                "PAYMENT_AUTH",
                referenceId,
                status,
                "Audit recorded internally",
                Instant.now()
        );
        auditRecordRepository.save(audit);
    }
}
```

---

## 5. Production Incident

### Incident Timeline

| Time (UTC) | Event / Telemetry |
|---|---|
| **14:00:00** | Flash sale begins. Payment intent authorization volume reaches 3,200 req/sec. |
| **14:02:10** | Downstream third-party payment gateway experiences latency degradation (HTTP response times increase from 220ms to 2,800ms). |
| **14:02:30** | Payment Service pods hold HikariCP physical connections open while blocking on downstream HTTP sockets. |
| **14:03:00** | All 200 HikariCP connections across all 20 pods become saturated (`Active: 10, Idle: 0, Waiting: 380`). |
| **14:03:20** | Nested `REQUIRES_NEW` audit calls attempt to acquire a 2nd connection on the same thread, causing immediate pool self-deadlocks. |
| **14:04:00** | PagerDuty fires SEV-1 Alert: `PaymentService_HikariCP_ConnectionTimeout_Storm`. 94% of authorization requests fail with HTTP 500. |
| **14:15:00** | Incident Commander orders emergency rollout: Extract third-party HTTP call outside `@Transactional` boundary, convert audit logs to separate service bean with asynchronous fallback. |
| **14:22:00** | Connection hold times drop from 3,100ms to **12ms**. HikariCP active connections drop to 2.1 per pod. Incident resolved. |

---

## 6. Logs & Diagnostics

### 1. `UnexpectedRollbackException` Stack Trace
```text
2026-08-20T14:03:12.441Z ERROR [payment-service,trace_id=8a1b2c3d,span_id=0f9e8d] 1 --- [http-nio-8080-exec-15] o.a.c.c.C.[.[.[/].[dispatcherServlet] : Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception [Request processing failed: org.springframework.transaction.UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only] with root cause

org.springframework.transaction.UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only
	at org.springframework.transaction.support.AbstractPlatformTransactionManager.processRollback(AbstractPlatformTransactionManager.java:902)
	at org.springframework.transaction.support.AbstractPlatformTransactionManager.commit(AbstractPlatformTransactionManager.java:728)
	at org.springframework.transaction.interceptor.TransactionAspectSupport.commitTransactionAfterReturning(TransactionAspectSupport.java:654)
	at org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWithinTransaction(TransactionAspectSupport.java:407)
	at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
	at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
	at org.springframework.aop.framework.CglibAopProxy$CglibMethodInvocation.proceed(CglibAopProxy.java:750)
	at com.finflow.chapter170.incorrect.PaymentProcessingServiceIncorrect.processPaymentWithRollbackCatchTrap(PaymentProcessingServiceIncorrect.java:62)
```

### 2. HikariCP Pool Starvation Log (Long-Running External I/O in Transaction)
```text
2026-08-20T14:02:45.102Z WARN [payment-service,,] 1 --- [HikariPool-1 housekeeper] com.zaxxer.hikari.pool.HikariPool : HikariPool-1 - Connection leak detection triggered for connection org.postgresql.jdbc.PgConnection@4a2b1c, stack trace follows

java.lang.Exception: Apparent connection leak detected
	at com.zaxxer.hikari.HikariDataSource.getConnection(HikariDataSource.java:128)
	at org.hibernate.engine.jdbc.connections.internal.DatasourceConnectionProviderImpl.getConnection(DatasourceConnectionProviderImpl.java:122)
	at org.springframework.orm.jpa.vendor.HibernateJpaDialect.beginTransaction(HibernateJpaDialect.java:160)
	at org.springframework.orm.jpa.JpaTransactionManager.doBegin(JpaTransactionManager.java:405)
	... [Thread was blocked inside org.apache.http.impl.client.CloseableHttpClient.execute() for 2,840ms while holding JDBC connection!]
```

---

## 7. Root Cause Analysis

```
+-------------------------------------------------------------------------------------------------+
|                                Transaction Failure Root Cause Chain                             |
|                                                                                                 |
|  1. @Transactional placed at top-level Controller / Orchestration Service                      |
|     └── Spring checks out a physical JDBC connection from HikariCP immediately at method entry. |
|                                                                                                 |
|  2. External HTTP Call (Stripe API) executed inside Transaction Boundary                        |
|     ├── Downstream network latency spikes from 220ms to 2,800ms.                                 |
|     └── Physical JDBC connection remains idle and locked for 2,800ms per request.               |
|                                                                                                 |
|  3. Nested REQUIRES_NEW Audit Call                                                              |
|     ├── Same thread demands a 2nd connection from HikariCP pool.                                |
|     └── With all 10 pool connections held by parent threads, all threads enter self-deadlock!   |
|                                                                                                 |
|  4. Swallowing Inner RuntimeExceptions                                                          |
|     └── Inner method marks transactionStatus.setRollbackOnly(true). Outer commit throws        |
|         UnexpectedRollbackException, corrupting response contracts to clients.                  |
+-------------------------------------------------------------------------------------------------+
```

---

## 8. Debugging Process

```
[1. Metric Triage] Check HikariCP active connection duration & pending threads
       │
[2. DB Engine] Query PostgreSQL pg_stat_activity for 'idle in transaction' state
       │
[3. Thread Dumps] Identify threads blocked on HTTP sockets while holding ConnectionHolder
       │
[4. Transaction Logging] Enable org.springframework.transaction.interceptor DEBUG logs
       │
[5. Refactoring] Decouple external I/O from DB transactions & apply rollbackFor=Exception.class
```

### Step 1: Query PostgreSQL `idle in transaction` Connections
```sql
SELECT pid, now() - state_change AS idle_duration, query, state 
FROM pg_stat_activity 
WHERE state = 'idle in transaction' 
ORDER BY idle_duration DESC;
```
*If connections remain 'idle in transaction' for seconds, application threads are performing non-database network I/O while holding open database transactions.*

### Step 2: Enable Spring Transaction Debug Logs
```yaml
logging:
  level:
    org.springframework.transaction.interceptor: TRACE
    org.springframework.orm.jpa.JpaTransactionManager: DEBUG
```
Output clearly shows transaction lifecycle, savepoints, and rollback-only markers:
```text
DEBUG o.s.o.j.JpaTransactionManager : Participating in existing transaction
DEBUG o.s.o.j.JpaTransactionManager : Participating transaction failed - marking existing transaction as rollback-only
TRACE o.s.t.i.TransactionInterceptor : Completing transaction for [com.finflow.chapter170.incorrect.PaymentProcessingServiceIncorrect] after exception: com.finflow.chapter170.exception.FraudDetectedException
DEBUG o.s.o.j.JpaTransactionManager : Global transaction is marked as rollback-only but transactional code requested commit
```

---

## 9. Correct Implementation

### 1. Risk Evaluation Service: `FraudCheckServiceCorrect.java`

```java
package com.finflow.chapter170.correct;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class FraudCheckServiceCorrect {

    public record FraudEvaluation(boolean isFraudulent, String reason) {}

    /**
     * Non-transactional pure rule evaluation.
     * Returns a domain result object instead of throwing rollback-triggering exceptions.
     */
    public FraudEvaluation evaluateRisk(String customerId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.valueOf(10000.00)) > 0) {
            return new FraudEvaluation(true, "Transaction exceeds suspicious threshold: " + amount);
        }
        return new FraudEvaluation(false, "Approved");
    }
}
```

### 2. Independent Audit Service: `AuditLogServiceCorrect.java`

```java
package com.finflow.chapter170.correct;

import com.finflow.chapter170.domain.AuditRecord;
import com.finflow.chapter170.repository.AuditRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuditLogServiceCorrect {

    private final AuditRecordRepository auditRecordRepository;

    public AuditLogServiceCorrect(AuditRecordRepository auditRecordRepository) {
        this.auditRecordRepository = auditRecordRepository;
    }

    /**
     * Executes in an independent physical transaction via separate Spring Bean proxy.
     * Guaranteed to commit even if the caller transaction rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAuditLog(String action, String referenceId, String status, String details) {
        AuditRecord audit = new AuditRecord(
                UUID.randomUUID(),
                action,
                referenceId,
                status,
                details,
                Instant.now()
        );
        auditRecordRepository.save(audit);
    }
}
```

### 3. Payment Processing Service: `PaymentProcessingServiceCorrect.java`

```java
package com.finflow.chapter170.correct;

import com.finflow.chapter170.domain.PaymentTransaction;
import com.finflow.chapter170.domain.TransactionStatus;
import com.finflow.chapter170.exception.PaymentProcessingException;
import com.finflow.chapter170.repository.PaymentTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class PaymentProcessingServiceCorrect {

    private final PaymentTransactionRepository transactionRepository;
    private final AuditLogServiceCorrect auditLogService;
    private final FraudCheckServiceCorrect fraudCheckService;

    public PaymentProcessingServiceCorrect(PaymentTransactionRepository transactionRepository,
                                           AuditLogServiceCorrect auditLogService,
                                           FraudCheckServiceCorrect fraudCheckService) {
        this.transactionRepository = transactionRepository;
        this.auditLogService = auditLogService;
        this.fraudCheckService = fraudCheckService;
    }

    /**
     * Correct Flow 1: Safe Domain Evaluation & Independent Audit Logging.
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public PaymentTransaction processPaymentSafely(String paymentRef, String customerId, BigDecimal amount) {
        PaymentTransaction tx = new PaymentTransaction(
                UUID.randomUUID(),
                paymentRef,
                customerId,
                amount,
                "USD",
                TransactionStatus.INITIATED,
                Instant.now()
        );
        transactionRepository.save(tx);

        FraudCheckServiceCorrect.FraudEvaluation eval = fraudCheckService.evaluateRisk(customerId, amount);

        if (eval.isFraudulent()) {
            tx.setStatus(TransactionStatus.FAILED);
            // Safely logged via separate REQUIRES_NEW transaction
            auditLogService.recordAuditLog("PAYMENT_AUTH", paymentRef, "FAILED", eval.reason());
            return tx;
        }

        tx.setStatus(TransactionStatus.SUCCESS);
        auditLogService.recordAuditLog("PAYMENT_AUTH", paymentRef, "SUCCESS", "Payment processed successfully");
        return tx;
    }

    /**
     * Correct Flow 2: Explicit rollbackFor = Exception.class for Checked Exceptions.
     */
    @Transactional(rollbackFor = Exception.class)
    public void processPaymentWithCheckedExceptionSafe(String paymentRef, String customerId, BigDecimal amount)
            throws PaymentProcessingException {
        PaymentTransaction tx = new PaymentTransaction(
                UUID.randomUUID(),
                paymentRef,
                customerId,
                amount,
                "USD",
                TransactionStatus.INITIATED,
                Instant.now()
        );
        transactionRepository.save(tx);

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            // Correctly rolls back because rollbackFor = Exception.class is specified
            throw new PaymentProcessingException("Invalid amount: " + amount);
        }

        tx.setStatus(TransactionStatus.SUCCESS);
    }

    /**
     * Correct Flow 3: Audit persistence on parent transaction failure via separate bean.
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void processPaymentWithAuditOnFailure(String paymentRef, String customerId, BigDecimal amount) {
        // Commits immediately in separate physical transaction
        auditLogService.recordAuditLog("PAYMENT_AUTH", paymentRef, "INITIATED", "Payment initiated");

        PaymentTransaction tx = new PaymentTransaction(
                UUID.randomUUID(),
                paymentRef,
                customerId,
                amount,
                "USD",
                TransactionStatus.INITIATED,
                Instant.now()
        );
        transactionRepository.save(tx);

        throw new RuntimeException("Simulated payment gateway network timeout");
    }

    /**
     * Correct Flow 4: Safe Event Publishing after DB Commit.
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public PaymentTransaction processPaymentWithAfterCommitEvent(String paymentRef, String customerId,
                                                                BigDecimal amount, AtomicBoolean eventPublished) {
        PaymentTransaction tx = new PaymentTransaction(
                UUID.randomUUID(),
                paymentRef,
                customerId,
                amount,
                "USD",
                TransactionStatus.SUCCESS,
                Instant.now()
        );
        transactionRepository.save(tx);

        // Guarantees downstream event is only dispatched IF the database commit succeeds!
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventPublished.set(true);
            }
        });

        return tx;
    }
}
```

---

## 10. Performance Comparison

Comparison under 4,000 req/sec load with 2,500ms downstream Stripe latency spikes on FinFlow infrastructure.

| Metric | Incorrect (External I/O in `@Transactional`) | Correct (Decoupled External I/O & `afterCommit`) |
|---|---|---|
| **HikariCP Connection Hold Duration** | 2,850ms *(entire HTTP duration)* | **11ms** *(DB write duration only)* |
| **Active DB Connections per Pod** | 10 / 10 *(100% pool exhaustion)* | **1.8 / 10** *(Healthy reserve)* |
| **API p99 Latency** | > 30,000ms *(timed out)* (illustrative) | **280ms** (illustrative) |
| **Request Failure Rate** | 94.2% *(HTTP 500 Connection Timeout)* | **0.0%** |
| **HikariCP Pool Self-Deadlocks** | Frequent (with nested `REQUIRES_NEW`)| **Zero** |
| **Rollback Integrity on Checked Exceptions** | Broken (Committed partial data) | **100% Consistent Rollback** |
| **Dual-Write Phantom Messages** | Frequent (Kafka sent before DB rollback)| **Zero (afterCommit hook)** |

---

## 11. Best Practices

### The Do's
- **DO keep transaction boundaries as short as possible**: Keep only pure database operations inside `@Transactional`. Perform validation, serialization, and network I/O *outside*.
- **DO specify `rollbackFor = Exception.class`**: Ensure all checked and custom business exceptions trigger a complete rollback.
- **DO use separate Spring Beans for `REQUIRES_NEW`**: Guarantees CGLIB proxy interception and prevents self-invocation bypass.
- **DO use `TransactionSynchronizationManager.afterCommit()` for event publishing**: Guarantees external messages (Kafka, SQS, webhooks) are only dispatched after database state is durable.
- **DO size HikariCP pools conservatively when using `REQUIRES_NEW`**: Ensure pool capacity exceeds concurrent thread count to prevent self-deadlocks.

### The Don'ts
- **DON'T perform HTTP calls, gRPC requests, or disk I/O inside `@Transactional`**: Holding physical database connections during slow I/O is the #1 cause of connection pool exhaustion.
- **DON'T catch and swallow `RuntimeException`s from nested `REQUIRED` methods**: Marks the physical transaction as rollback-only, triggering `UnexpectedRollbackException` at commit time.
- **DON'T call `@Transactional` methods on `this`**: Self-invocation bypasses the Spring AOP proxy, silently ignoring propagation, isolation, and rollback settings.
- **DON'T annotate `private` or `final` methods with `@Transactional`**: Spring CGLIB proxies cannot override private/final methods; the annotation will be silently ignored.

---

## 12. Common Mistakes

### Mistake 1: The Self-Invocation Anti-Pattern
```java
@Service
public class OrderService {
    public void createOrder() {
        // Calling transactional method internally
        this.saveOrderWithNewTransaction(); // PROXY IS BYPASSED! Runs in NO transaction!
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveOrderWithNewTransaction() { ... }
}
```
**Production Fix**: Self-inject the bean or inject a dedicated child service bean:
```java
@Service
public class OrderService {
    private final OrderPersistenceService persistenceService;
    // Call persistenceService.saveOrderWithNewTransaction() via proxy!
}
```

### Mistake 2: The Swallowed Exception Rollback Trap
```java
@Transactional
public void process() {
    try {
        paymentService.chargeCard(); // Throws RuntimeException, marks rollback-only
    } catch (RuntimeException e) {
        log.warn("Payment failed, moving to fallback");
        // Spring throws UnexpectedRollbackException when this method attempts to commit!
    }
}
```
**Production Fix**: If `chargeCard()` is expected to fail without aborting the parent, declare `chargeCard()` with `Propagation.REQUIRES_NEW` or have it return a status object rather than throwing exceptions across transaction boundaries.

---

## 13. Interview Questions

### Junior Tier
**Q: What is the default rollback behavior of Spring's `@Transactional` annotation?**
> **Answer**: By default, Spring's `@Transactional` automatically rolls back transactions only for unchecked exceptions (subclasses of `java.lang.RuntimeException` and `java.lang.Error`). Checked exceptions (subclasses of `java.lang.Exception` that do not extend `RuntimeException`) do **not** trigger a rollback by default; Spring commits the transaction unless `rollbackFor = Exception.class` is explicitly specified.

### Mid Tier
**Q: What causes `org.springframework.transaction.UnexpectedRollbackException`, and how do you resolve it?**
> **Answer**: It occurs when an inner method with `Propagation.REQUIRED` throws an exception, prompting Spring's transaction interceptor to mark the shared physical transaction as `rollbackOnly = true`. If the calling outer method catches and swallows this exception and attempts to commit at method completion, the transaction manager discovers the `rollbackOnly` flag, rolls back the transaction, and throws `UnexpectedRollbackException`. To resolve it, either propagate the exception out, redesign the inner method to return a domain result instead of throwing, or isolate the inner method in an independent transaction using `Propagation.REQUIRES_NEW`.

### Senior Tier
**Q: How does `Propagation.REQUIRES_NEW` interact with the underlying database connection pool (e.g., HikariCP), and what failure mode can it introduce under high concurrency?**
> **Answer**: `REQUIRES_NEW` suspends the active transaction and borrows a **second physical JDBC connection** from the connection pool for the duration of the inner method. Under high concurrency, if all available pool connections are checked out by parent transactions waiting to enter the `REQUIRES_NEW` block, no threads can acquire a second connection. This creates an unrecoverable connection pool **self-deadlock**, causing all threads to block until `connection-timeout` expires.

### Staff Tier
**Q: Explain how `TransactionSynchronizationManager` binds database connections to threads, and how you ensure safe event publishing in a distributed architecture.**
> **Answer**: `TransactionSynchronizationManager` maintains thread-local storage (`ThreadLocal<Map<Object, Object>> resources`) where the `PlatformTransactionManager` binds the `ConnectionHolder` (keyed by `DataSource` or `EntityManagerFactory`) upon transaction inception. To ensure safe event publishing, application code registers a `TransactionSynchronization` callback via `TransactionSynchronizationManager.registerSynchronization()`. By publishing events inside the `afterCommit()` hook, the system guarantees that external messages (e.g., Kafka events) are dispatched only after the database transaction has physically and durably committed, preventing phantom messages during rollbacks.

### Principal Tier
**Q: Design a dual-write architecture for financial ledger mutations and event notifications that guarantees zero message loss and zero phantom events without relying on distributed 2-Phase Commit (XA) transactions.**
> **Answer**: A Principal-level solution uses the **Transactional Outbox Pattern**:
> 1. **Atomic Local Mutation**: Inside a single `@Transactional` boundary, the service writes the business mutation (`LedgerPosting`) and inserts an outbox event row (`OutboxEvent`) into the same PostgreSQL database using standard ACID guarantees.
> 2. **Transaction Commit**: The transaction commits atomically. Either both the ledger and the outbox event exist, or neither does.
> 3. **Asynchronous Outbox Publisher**: A dedicated CDC worker (e.g., Debezium reading PostgreSQL WAL) or a polled polling publisher streams outbox events to Kafka with at-least-once delivery guarantees.
> 4. **Consumer Idempotency**: Downstream Kafka consumers enforce idempotency via unique event IDs (`idempotent consumer` pattern), eliminating the need for blocking distributed XA transactions while guaranteeing exactly-once business processing semantics.

---

## 14. Hands-on Exercise

### Objective
In FinFlow, implement a payment execution service that:
1. Persists a `PaymentTransaction`.
2. Evaluates fraud risk without triggering `UnexpectedRollbackException`.
3. Records an audit entry in a dedicated `REQUIRES_NEW` transaction that persists even if the main transaction fails.
4. Triggers a Kafka notification flag **strictly after** the physical database commit succeeds.

### Solution

#### Step 1: Injected Audit Service
```java
@Service
public class AuditLogService {
    @PersistenceContext private EntityManager em;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAudit(String ref, String status) {
        em.persist(new AuditRecord(UUID.randomUUID(), "PAYMENT", ref, status, "Recorded", Instant.now()));
    }
}
```

#### Step 2: Payment Coordinator Service
```java
@Service
public class PaymentCoordinatorService {

    private final PaymentTransactionRepository repository;
    private final AuditLogService auditService;

    public PaymentCoordinatorService(PaymentTransactionRepository repository, AuditLogService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void executePayment(String ref, BigDecimal amount, AtomicBoolean kafkaEventPublished) {
        // Record initiated audit in separate physical transaction
        auditService.recordAudit(ref, "INITIATED");

        PaymentTransaction tx = new PaymentTransaction(
                UUID.randomUUID(), ref, "CUST-1", amount, "USD", TransactionStatus.SUCCESS, Instant.now()
        );
        repository.save(tx);

        // Register after-commit hook for Kafka dispatch
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                kafkaEventPublished.set(true);
            }
        });
    }
}
```

---

## 15. Advanced Challenge: Distributed Saga vs. Local Outbox Pattern

### Enterprise Problem Statement
In a distributed payment architecture involving Payment Service, Ledger Service, and Kafka, how do you handle partial failures during third-party gateway timeouts?

Implement a robust Transactional Outbox publisher that stores events locally in `payment_db` within the business transaction and uses Spring's `TransactionSynchronizationManager` to signal immediate in-memory dispatch while falling back to periodic table polling if the application crashes.

### Enterprise Solution

```java
package com.finflow.chapter170.correct;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class TransactionalOutboxService {

    @PersistenceContext
    private EntityManager entityManager;

    private final ExecutorService asyncPublisher = Executors.newFixedThreadPool(4);

    @Transactional(rollbackFor = Exception.class)
    public void executeBusinessActionWithOutbox(String aggregateId, String payload) {
        // 1. Persist Outbox Event within the exact same database transaction
        UUID eventId = UUID.randomUUID();
        // Native insert or entity persist into outbox_table
        
        // 2. Register post-commit async dispatcher
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // Instantly notify Kafka publisher thread after durable commit
                asyncPublisher.submit(() -> {
                    // publishToKafka(eventId, aggregateId, payload);
                });
            }
        });
    }
}
```

---

## 16. Production Checklist

Before approving any pull request containing `@Transactional` code:

- [ ] **No External Network I/O in Transactions**: Ensure HTTP, gRPC, Redis locks, and long-running operations are executed outside `@Transactional` methods.
- [ ] **`rollbackFor = Exception.class` Specified**: Verify `@Transactional` explicitly specifies `rollbackFor = Exception.class` if any checked exceptions can be thrown.
- [ ] **No Catching Inner `REQUIRED` Exceptions**: Ensure no `try-catch` blocks swallow `RuntimeException`s from inner `REQUIRED` transactional methods.
- [ ] **No Self-Invocation on `@Transactional`**: Verify transactional methods called internally are routed through an injected bean proxy.
- [ ] **`REQUIRES_NEW` Pool Safety**: Verify connection pool sizing ($N \times 2$) if `REQUIRES_NEW` is used under high concurrency.
- [ ] **Event Publishing After Commit**: Ensure external messaging (Kafka/RabbitMQ) is registered via `TransactionSynchronizationManager.afterCommit()` or the Transactional Outbox pattern.
- [ ] **No Private/Final Method Annotations**: Verify `@Transactional` is placed only on `public` non-final methods.
