# Module 07: Spring Transactions and Isolation Hazards

## Issue 7.1: Swallowed Exceptions, Checked Exception Rollback Traps & `REQUIRES_NEW` Hikari Pool Deadlocks

---

### 1. Scenario

During peak holiday shopping on the **FinFlow Core Banking Ledger**, customers report critical balance anomalies:
- A customer transfers $500. The source account is debited, but the destination bank times out. The sender's balance remains debited by $500 even though the transfer failed!
- Under high concurrency (200 req/sec), all application threads freeze in `WAITING` state, CPU drops to 0%, and HikariCP logs `ConnectionTimeoutException: Connection is not available, request timed out after 30000ms`.

Engineering investigation reveals:
1. The developer caught the downstream exception in a `try-catch` block to return a friendly DTO, inadvertently swallowing the exception and allowing the outer transaction to commit dirty debits!
2. An inner audit service used `Propagation.REQUIRES_NEW`, causing each request thread to acquire two simultaneous database connections, starving the Hikari pool and causing a thread deadlock!

---

### 2. Symptoms

```text
1. Money disappears: Source account debited, destination account never credited, yet the database commit succeeded.
2. Checked exceptions thrown from @Transactional methods do not rollback changes.
3. HikariCP pool exhaustion: HikariPool-1 - Connection is not available, request timed out after 30000ms.
4. Thread dumps show dozens of worker threads stuck waiting on HikariPool.getConnection() inside a REQUIRES_NEW method.
5. UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only.
```

---

### 3. Possible Root Causes

1. **Swallowed Exceptions in `try-catch` (Most Common):** If a method annotated with `@Transactional` catches a `RuntimeException` and does **not** rethrow it or invoke `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()`, Spring's `TransactionInterceptor` assumes the method executed successfully and commits the transaction!
2. **Checked Exception Default Rollback Policy:** By default (EJB legacy behavior), Spring only rolls back transactions on unchecked exceptions (`RuntimeException` and `Error`). It does **NOT** roll back on checked `Exception` unless explicitly configured with `@Transactional(rollbackFor = Exception.class)`.
3. **`REQUIRES_NEW` Connection Starvation Deadlock:** When a thread running in an outer transaction calls a `REQUIRES_NEW` method, Spring suspends the outer transaction (holding Connection 1 open) and requests Connection 2 from HikariCP. If the Hikari pool is exhausted because concurrent threads are also holding Connection 1, a **pool-level deadlock** occurs!

---

### 4. Architecture Context: Transaction Interceptor & Connection Lifecycle

```text
Client Call
    │
    ▼
TransactionInterceptor (Around Advice)
    │
    ├─► 1. Check Propagation (e.g. REQUIRED vs REQUIRES_NEW)
    ├─► 2. Obtain Connection from DataSource / HikariCP Pool (Connection 1)
    ├─► 3. Disable Auto-Commit: connection.setAutoCommit(false)
    ├─► 4. Bind Connection to Current Thread (TransactionSynchronizationManager)
    │
    ▼
Execute Business Method (e.g. BankingTransactionService)
    │
    ├─► If REQUIRES_NEW invoked:
    │     ├── Suspend Outer Transaction (Connection 1 remains open/held!)
    │     └── Acquire Connection 2 from HikariCP (DEADLOCK RISK IF POOL EMPTY!)
    │
    ├─► On Success:
    │     └── commit() ──► restore auto-commit ──► return connection to pool
    │
    └─► On Exception:
          ├── If RuntimeException or matches rollbackFor:
          │     └── rollback() ──► return connection to pool
          └── If Checked Exception (default):
                └── commit() [UNEXPECTED DIRTY COMMIT!]
```

---

### 5. How to Reproduce the Issues

#### Step 1: The Swallowed Exception Trap
```java
@Transactional
public void transferWithSwallowedBug(String fromId, String toId, BigDecimal amount) {
    debit(fromId, amount);
    try {
        throw new RuntimeException("Downstream network timeout");
    } catch (RuntimeException ex) {
        log.error("Failed downstream: {}", ex.getMessage());
        // BUG: Swallowing the exception causes Spring to COMMIT the debit!
    }
}
```

#### Step 2: The Checked Exception Trap
```java
// BUG: Checked Exception does NOT roll back by default!
@Transactional
public void transferWithCheckedBug(String fromId, String toId, BigDecimal amount) throws Exception {
    debit(fromId, amount);
    throw new Exception("Custom checked business error"); // Commits!
}
```

#### Step 3: Run Tests
Execute `TransactionRollbackRulesTest.java` and `SwallowedExceptionRollbackTest.java` to verify that dirty commits occur in both cases.

---

### 6. Evidence Collection & Diagnostic Probing

#### Method 1: Enable Spring Transaction TRACE Logging in `application.yml`
```yaml
logging:
  level:
    org.springframework.transaction: TRACE
    org.springframework.orm.jpa: DEBUG
    org.hibernate.SQL: DEBUG
```

**Stdout Log Trace (Catching Rollback Decision):**
```text
TRACE o.s.t.i.TransactionInterceptor - Getting transaction for [BankingTransactionService.transferWithCheckedBug]
DEBUG o.s.o.j.JpaTransactionManager - Creating new transaction with name [BankingTransactionService.transferWithCheckedBug]
DEBUG o.s.o.j.JpaTransactionManager - Exposing JPA transaction as JDBC [org.springframework.orm.jpa.vendor.HibernateJpaDialect]
TRACE o.s.t.i.TransactionInterceptor - Completing transaction for [BankingTransactionService.transferWithCheckedBug] after exception: java.lang.Exception
TRACE o.s.t.i.RuleBasedTransactionAttribute - Applying rules to determine whether transaction should rollback: [java.lang.Exception]
TRACE o.s.t.i.RuleBasedTransactionAttribute - Winning rollback rule is: null
DEBUG o.s.o.j.JpaTransactionManager - Initiating transaction commit
```
*Notice: `Winning rollback rule is: null` $\implies$ Spring COMMITS despite the exception!*

---

### 7. Step-by-Step Debugging Procedure

```text
Step 1: Inspect Exception Types and Rollback Rules.
        Check if the thrown exception is a checked Exception (inheriting Exception directly).
        Ensure @Transactional(rollbackFor = Exception.class) is specified on all transactional methods.

Step 2: Check for Swallowed Exceptions in try-catch Blocks.
        Search for try-catch blocks inside @Transactional methods.
        If an exception is caught to return an error response, either:
        - Rethrow a RuntimeException, OR
        - Call TransactionAspectSupport.currentTransactionStatus().setRollbackOnly().

Step 3: Audit Nested REQUIRES_NEW Connection Requirements.
        Calculate required HikariCP pool size formula:
        Pool Size >= (Max Active Worker Threads * (Max Nested REQUIRES_NEW Depth + 1)) + 1
```

---

### 8. Technical Root Cause Deep-Dive

#### Why `REQUIRES_NEW` Causes Pool Deadlocks

Consider a HikariCP pool configured with `maximum-pool-size: 10` and 10 Tomcat worker threads processing concurrent requests:
1. 10 HTTP requests arrive simultaneously.
2. All 10 threads begin an outer `@Transactional` method, acquiring **10 database connections** (pool now has 0 available connections).
3. Inside the method, all 10 threads call an audit logging method marked `@Transactional(propagation = Propagation.REQUIRES_NEW)`.
4. Spring suspends the outer transactions (retaining the 10 active connections) and requests 10 new connections from HikariCP for the inner transactions.
5. HikariCP has 0 connections remaining. All 10 threads block waiting for a free connection.
6. The outer transactions cannot complete and release their connections until the inner transactions finish. The inner transactions cannot start until the outer transactions release their connections.
7. **Result:** Total application deadlock until HikariCP throws `ConnectionTimeoutException` after 30 seconds!

---

### 9. Production-Grade Fixes

#### ✅ Fix 1: Always Configure `rollbackFor = Exception.class`
```java
@Transactional(rollbackFor = Exception.class)
public void transferFunds(String fromId, String toId, BigDecimal amount) {
    // Both checked Exception and unchecked RuntimeException will trigger rollback
}
```

#### ✅ Fix 2: Programmatic Rollback Flag When Catching Exceptions
```java
@Transactional(rollbackFor = Exception.class)
public TransferResult transferWithHandledException(String fromId, String toId, BigDecimal amount) {
    try {
        debit(fromId, amount);
        credit(toId, amount);
        return TransferResult.success();
    } catch (Exception ex) {
        log.error("Transfer failed: {}", ex.getMessage());
        // Explicitly mark transaction for rollback without crashing the caller
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        return TransferResult.failure(ex.getMessage());
    }
}
```

#### ✅ Fix 3: Independent `REQUIRES_NEW` Audit Logging & Pool Sizing
```java
@Service
public class AuditLogService {
    // Isolated independent transaction
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAuditLog(String operation, String details) {
        auditLogRepository.save(new AuditLogEntity(operation, details));
    }
}
```
*HikariCP Configuration:*
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 30 # Sized to support concurrent outer + inner connections
      connection-timeout: 10000
      leak-detection-threshold: 5000
```

---

### 10. Verification

1. **Checked Exception Rollback Test:** Run `TransactionRollbackRulesTest.java` to verify that checked exceptions without `rollbackFor` commit, while properly configured methods rollback.
2. **Swallowed Exception Test:** Run `SwallowedExceptionRollbackTest.java` to confirm dirty commit behavior when exceptions are swallowed.
3. **REQUIRES_NEW Audit Isolation Test:** Run `RequiresNewAuditIsolationTest.java` to verify that outer transaction rollbacks do not prevent inner audit logs from persisting.

---

### 11. Prevention & Production Readiness

1. **SonarQube / Checkstyle Rules:** Configure static analysis rules to flag any `catch` block inside a `@Transactional` method that does not rethrow or set rollback-only.
2. **Always Use `rollbackFor = Exception.class`:** Standardize on `@Transactional(rollbackFor = Exception.class)` across the engineering organization.
3. **Avoid Unnecessary `REQUIRES_NEW`:** For asynchronous audit logs, prefer out-of-band messaging (e.g. Kafka or `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION)`).

---

### 12. Interview & Production Questions

#### Interview Questions
1. **Q: What is the exact behavioral difference between `Propagation.REQUIRED` and `Propagation.REQUIRES_NEW`?**
2. **Q: Why does Spring's `@Transactional` not roll back on checked exceptions by default?**
3. **Q: How does `@Transactional(readOnly = true)` optimize database interactions under Hibernate?**
4. **Q: What causes `UnexpectedRollbackException` when using nested `Propagation.REQUIRED` methods?**
5. **Q: How does `TransactionSynchronizationManager` bind transactions to threads, and what happens when switching to virtual threads or `@Async`?**

#### Production Incident Questions
1. **Incident:** During a high-concurrency event, all database connections are exhausted and threads deadlock. You discover that a service method marked `@Transactional` calls a `REQUIRES_NEW` audit service. How do you re-architect the audit logging to eliminate connection pool deadlocks?
2. **Incident:** A developer caught `DataAccessException` in a repository layer and returned an empty list. The outer `@Transactional` service committed half-completed business state. How do you enforce rollback without breaking the caller API?
3. **Incident:** An external email is sent inside a `@Transactional` method. If the transaction fails and rolls back, the email was already sent. How do you use `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` to fix this?
4. **Incident:** Two concurrent requests attempt to update the same user account balance. How do you implement optimistic locking with `@Version` and handle `OptimisticLockingFailureException` gracefully?
5. **Incident:** A long-running batch job uses `@Transactional` on a method that processes 100,000 records. The database runs out of undo log / temp tablespace and locks all tables. How do you chunk the transaction into micro-batches?

#### Trick Questions
1. **Trick:** If a method with `@Transactional` calls another method in the same class marked `@Transactional(propagation = Propagation.REQUIRES_NEW)`, does a new transaction open?
2. **Trick:** If a transaction has `readOnly = true` and you execute `entityManager.persist(newEntity)`, will the insert SQL execute in PostgreSQL?
3. **Trick:** Does `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()` throw an exception when called?

---

*(Answers and detailed explanations are provided in the Master Answer Guide)*
