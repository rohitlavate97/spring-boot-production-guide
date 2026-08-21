# Module 06: Spring AOP and Proxy Problems

## Issue 6.1: The Self-Invocation Trap & Silent `@Transactional` / `@Async` / `@Cacheable` Aspect Bypasses

---

### 1. Scenario

During a high-stakes audit of the **FinFlow Core Ledger Service**, compliance regulators discover that 40% of financial debit operations have **zero audit records** in the security ledger, and failed account transfers did **not** roll back debit changes, resulting in account imbalances.

The developer reviews the code and shows that the debit method is annotated with `@Transactional` and `@AuditedTransaction`:
```java
@Service
public class AccountBalanceService {

    public void processDebit(String accountId, BigDecimal amount) {
        // Validation logic
        this.executeDebit(accountId, amount); // <--- INTERNAL CALL
    }

    @Transactional
    @AuditedTransaction(action = "DEBIT")
    public void executeDebit(String accountId, BigDecimal amount) {
        // Database debit updates
    }
}
```

The code looks 100% correct in source code, yet in production, the transaction boundary and audit aspect **never execute**!

---

### 2. Symptoms

```text
1. Methods annotated with @Transactional do not open transactions and fail to rollback on RuntimeException.
2. Methods annotated with @Async execute synchronously on the caller HTTP thread instead of worker pools.
3. Methods annotated with @Cacheable bypass Redis/in-memory caches on every call, hammering the database.
4. Custom @Around aspects fail to intercept calls when invoked from another method in the same class.
5. No error or warning is logged by Spring Framework at runtime.
```

---

### 3. Possible Root Causes

1. **Internal Self-Invocation (`this.method()`) (Most Likely):** Calling an annotated method from within the same class (`this.executeDebit()`) invokes the target object directly, completely bypassing the Spring CGLIB/JDK proxy wrapper where the aspect interceptor resides!
2. **Non-Public Method Visibility:** In standard Spring AOP (proxy-based), `@Transactional`, `@Async`, and `@Cacheable` are ignored on `private`, `package-private`, or `protected` methods.
3. **`final` Method or `final` Class Modifier:** CGLIB generates subclasses at runtime. If a method is marked `final`, the CGLIB proxy cannot override the method, so invocations bypass the proxy interceptor.
4. **Aspect Ordering Misconfiguration:** Multiple aspects without explicit `@Order` annotations execute in non-deterministic order (e.g. security aspect executing after transaction commits).

---

### 4. Architecture Context: Proxy Interception vs Direct `this` Invocation

```text
CALL FROM EXTERNAL BEAN (e.g. Controller):
┌────────────┐        ┌──────────────────────────────────────────────┐        ┌────────────────────────────┐
│ Controller │ ─────► │        Spring CGLIB Proxy Wrapper            │ ─────► │   AccountBalanceService    │
└────────────┘        │                                              │        │       (Real Target)        │
                      │  1. Check @Order                             │        │                            │
                      │  2. Open Transaction (@Transactional)        │        │  processDebit()            │
                      │  3. Execute Audit Aspect (@AuditedTransaction│        │    │                       │
                      │  4. Delegate to target bean                  │        │    │ this.executeDebit()   │
                      └──────────────────────────────────────────────┘        │    │ (DIRECT CALL IN JVM)  │
                                                                              │    ▼                       │
                                                                              │  executeDebit()            │
                                                                              │  [ASPECTS NEVER CALLED!]   │
                                                                              └────────────────────────────┘
```

---

### 5. How to Reproduce the Issue

#### Step 1: Define Custom Aspect
```java
@Aspect
@Component
public class TransactionAuditAspect {
    private final AtomicInteger count = new AtomicInteger(0);

    @Around("@annotation(auditedTransaction)")
    public Object intercept(ProceedingJoinPoint pjp, AuditedTransaction auditedTransaction) throws Throwable {
        count.incrementAndGet(); // Intercepted
        return pjp.proceed();
    }
}
```

#### Step 2: Implement Service with Self-Invocation
```java
@Service
public class AccountBalanceService {
    public void processDebitBuggy(String acc, BigDecimal amt) {
        this.internalDebitWithAspect(acc, amt); // Bypasses proxy
    }

    @AuditedTransaction(action = "INTERNAL_DEBIT")
    public void internalDebitWithAspect(String acc, BigDecimal amt) {
        // Business logic
    }
}
```

#### Step 3: Run Test
Execute `SelfInvocationAopTrapTest.java`. The test asserts `auditAspect.getInterceptionCount() == 0`, proving aspect execution was skipped!

---

### 6. Evidence Collection & Diagnostic Probing

#### Method 1: Check If Object is a Spring Proxy at Runtime
```java
boolean isProxy = AopUtils.isAopProxy(service);
boolean isCglib = AopUtils.isCglibProxy(service);
System.out.println("Is AOP Proxy: " + isProxy + ", Is CGLIB: " + isCglib);
```

#### Method 2: Inspect Thread IDs for `@Async` Failures
If `@Async` is called via self-invocation:
```java
log.info("Caller Thread: {}", Thread.currentThread().getName());
// If the async method logs the SAME thread name (e.g. 'http-nio-8080-exec-1'),
// the proxy was bypassed and execution is synchronous!
```

---

### 7. Step-by-Step Debugging Procedure

```text
Step 1: Inspect the Calling Path.
        Trace the invocation chain. Was the annotated method called directly via this.method()
        or via another bean?

Step 2: Check Method Visibility and Final Modifiers.
        Ensure the target method is:
        - public
        - non-final
        - declared in a non-final class.

Step 3: Choose the Production Remediation Strategy.
        - Option A (Best): Extract method into a distinct Collaborator Service (Single Responsibility).
        - Option B: Inject self-proxy via @Lazy AccountBalanceService selfProxy.
        - Option C: Use ((AccountBalanceService) AopContext.currentProxy()).executeDebit().
```

---

### 8. Technical Root Cause Deep-Dive

#### DynamicAdvisedInterceptor & `ReflectiveMethodInvocation` Execution Chain

When Spring creates an AOP proxy using CGLIB:
1. It creates an enhanced subclass that overrides all non-final `public` methods.
2. Invocations from outside the bean hit `DynamicAdvisedInterceptor.intercept(...)`.
3. The interceptor builds an interceptor chain (e.g. `TransactionInterceptor` $\rightarrow$ `CustomAuditAspect`).
4. When `ReflectiveMethodInvocation.proceed()` finally delegates to the real target object, execution enters the actual Java instance.
5. Inside the target instance, any call to `this.otherMethod()` executes native Java bytecode `invokevirtual` on the current `this` pointer in heap memory. The JVM has no knowledge of Spring or CGLIB proxies, so the proxy is never entered!

---

### 9. Production-Grade Fixes

#### ✅ Fix 1: Extract Collaborator Service (Architectural Best Practice)
Separate distinct business responsibilities into separate Spring beans:

```java
// 1. Separate Executor Service Bean
@Service
public class AccountDebitExecutor {
    @AuditedTransaction(action = "COLLABORATOR_DEBIT")
    public String executeDebit(String accountId, BigDecimal amount) {
        // Executed through CGLIB proxy -> Aspect intercepted!
        return "TXN-" + UUID.randomUUID().toString().substring(0, 8);
    }
}

// 2. Orchestrating Service Bean
@Service
public class AccountBalanceService {
    private final AccountDebitExecutor debitExecutor;

    public AccountBalanceService(AccountDebitExecutor debitExecutor) {
        this.debitExecutor = debitExecutor;
    }

    public String processDebitWithCollaborator(String accountId, BigDecimal amount) {
        return debitExecutor.executeDebit(accountId, amount);
    }
}
```

#### ✅ Fix 2: Self-Injected Proxy via `@Lazy` (Safe Refactoring Pattern)
If extracting a new class is impractical due to legacy constraints:

```java
@Service
public class AccountBalanceService {

    private final AccountBalanceService selfProxy;

    public AccountBalanceService(@Lazy AccountBalanceService selfProxy) {
        this.selfProxy = selfProxy;
    }

    public String processDebitWithSelfProxy(String accountId, BigDecimal amount) {
        // Invoking through selfProxy routes the call back through the CGLIB proxy!
        return selfProxy.internalDebitWithAspect(accountId, amount);
    }

    @AuditedTransaction(action = "INTERNAL_DEBIT")
    public String internalDebitWithAspect(String accountId, BigDecimal amount) {
        return "TXN-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
```

#### ✅ Fix 3: Aspect Ordering with `@Order`
When combining multiple aspects, always specify explicit `@Order` values:
```java
@Aspect
@Component
@Order(1) // Highest precedence: executes before Transaction
public class SecurityAuditAspect { ... }

@Aspect
@Component
@Order(2) // Executes inside security context
public class TransactionAuditAspect { ... }
```

---

### 10. Verification

1. **Self-Invocation Failure Test:** Run `SelfInvocationAopTrapTest.java` to verify that `processDebitBuggy` bypasses aspect counting (`count == 0`).
2. **Fixed Collaborator & Self-Proxy Test:** Run `ProxyInvocationFixedTest.java` to prove that both collaborator and self-proxy patterns achieve `count == 1`.
3. **Integration Test:** Run `Module06IntegrationTest.java` to verify endpoint telemetry.

---

### 11. Prevention & Production Readiness

1. **ArchUnit Rule to Prevent Self-Invocation:**
   ```java
   @ArchTest
   public static final ArchRule no_self_invocation_of_transactional_methods =
       methods().that().areAnnotatedWith(Transactional.class)
           .should().onlyBeCalled().byMethods().that().areDeclaredInClassesThat()
           .areNot(sameClass());
   ```
2. **Use AspectJ Compile-Time Weaving (CTW):**
   If internal self-invocation cannot be avoided in high-performance computing, switch from Spring AOP dynamic proxies to AspectJ bytecode weaving (`ajc`), which weaves bytecodes directly into the class files.

---

### 12. Interview & Production Questions

#### Interview Questions
1. **Q: What is the fundamental difference between Spring AOP (proxy-based) and AspectJ (weaving-based)?**
2. **Q: Why does standard Spring AOP fail when calling `@Transactional` methods internally via `this`?**
3. **Q: What are the trade-offs between JDK Dynamic Proxies and CGLIB proxies?**
4. **Q: How does `@EnableAspectJAutoProxy(exposeProxy = true)` allow `AopContext.currentProxy()` to work?**
5. **Q: If a class has `@Transactional` and a custom `@Around` aspect, how do you ensure the custom aspect runs inside or outside the transaction?**

#### Production Incident Questions
1. **Incident:** In an e-commerce checkout service, `@Cacheable(value = "products")` is placed on `getProductById(Long id)`. Calls from `getAllFeaturedProducts()` inside the same class result in cache misses and 10,000 queries per second to PostgreSQL. How do you resolve this?
2. **Incident:** A developer marked a `@Transactional` method as `public final void settlePayment()`. In production, payments commit, but exceptions never roll back. Why?
3. **Incident:** A method has `@Async` but runs on the main HTTP Tomcat thread, causing request timeout degradation. How do you verify if the method was invoked through a proxy?
4. **Incident:** Two aspects (`SecurityAspect` and `LoggingAspect`) intercept the same method. `LoggingAspect` logs the user identity, but `SecurityAspect` hasn't authenticated the token yet, causing `NullPointerException`. How do you enforce execution order?
5. **Incident:** An application uses `@Retryable` from Spring Retry. When an external HTTP timeout occurs, the method never retries. What proxy boundary checks should you perform?

#### Trick Questions
1. **Trick:** If a `@Service` bean implements an `interface`, will Spring Boot 3 use JDK Dynamic Proxy or CGLIB by default?
2. **Trick:** Does `@Transactional` work on `package-private` (default visibility) methods in standard Spring AOP with CGLIB?
3. **Trick:** If you call a `@Transactional` method through `this` inside a `@PostConstruct` method, does the transaction commit?

---

*(Answers and detailed explanations are provided in the Master Answer Guide)*
