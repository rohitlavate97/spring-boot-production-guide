---
chapter: 110
topic: Spring AOP & Proxy Mechanism — JDK Dynamic Proxy vs CGLIB, Proxy Pitfalls, Self-Invocation
prerequisite_chapters: [10, 30, 40, 50, 60, 70, 80, 90, 100]
reference_system_node: Payment Service (AOP proxy boundary, @Transactional, @Async, custom @AuditPayment aspect, CGLIB subclassing)
---

# Chapter 110: Spring AOP & Proxy Mechanism — JDK Dynamic Proxy vs CGLIB, Proxy Pitfalls, Self-Invocation

## 1. Concept

In enterprise applications, developers constantly face requirements that span multiple, unrelated modules—security, transaction management, auditing, and observability. Object-Oriented Programming (OOP) excels at modeling domain behavior, but struggles to cleanly modularize these **cross-cutting concerns**. Implementing them manually results in boilerplate code scattered across every business method, violating the Single Responsibility Principle (SRP).

**Aspect-Oriented Programming (AOP)** solves this by extracting cross-cutting concerns into standalone components called **Aspects**. 

To apply these aspects to your business logic, the framework must "weave" the cross-cutting code with your core logic. This weaving can happen in three ways:
1. **Compile-Time Weaving (CTW):** The AspectJ compiler (`ajc`) modifies the actual `.class` files during the build process, inserting the aspect logic directly into your bytecodes.
2. **Load-Time Weaving (LTW):** A JVM agent (`-javaagent`) intercepts class loading at startup and modifies the bytecode in memory before the JVM defines the class.
3. **Runtime Dynamic Proxies (Spring AOP):** Spring dynamically generates a wrapper object (a "proxy") around your target bean at application startup. When other beans invoke a method on your bean, they are actually talking to the proxy. The proxy executes the aspect logic before and after delegating the call to your actual bean.

Spring AOP defaults to **Runtime Dynamic Proxies**. It avoids the complexity of custom compilers and JVM agents, cleanly integrating into the standard Spring IoC container lifecycle. However, because it relies on wrapping objects at runtime, it introduces structural caveats—most notably, **self-invocation blindness**, which is the primary cause of AOP-related production incidents.

## 2. Internal Working

During application startup, Spring's `ApplicationContext` instantiates your beans. Before placing a bean into the IoC container, Spring passes it through a series of `BeanPostProcessor` implementations. For AOP, the critical post-processor is `AnnotationAwareAspectJAutoProxyCreator`.

When this post-processor encounters a bean whose methods match an AOP pointcut (such as having a `@Transactional` or `@Async` annotation, or matching a custom `@Aspect`), it replaces the original bean in the IoC container with a dynamically generated proxy.

Spring uses two different mechanisms to generate these proxies:

1. **JDK Dynamic Proxies:** 
   Used if the target bean implements at least one interface. Spring creates a proxy class that implements the same interfaces, using `java.lang.reflect.Proxy` and an `InvocationHandler`.
2. **CGLIB (Code Generation Library):** 
   Used if the bean does not implement any interfaces. CGLIB dynamically generates a subclass of your target bean by directly emitting bytecode. It overrides the non-final methods and uses a `MethodInterceptor` to weave the advice.

> [!NOTE]
> **Spring Boot 2.x and 3.x Default:** By default, Spring Boot sets `spring.aop.proxy-target-class=true`, meaning it will **always** force the use of CGLIB proxies, even if your bean implements interfaces. This prevents `ClassCastException`s when developers inject beans by their concrete class type rather than their interface type.

### The Proxy Dispatch Loop

When a method is called on a proxied bean, the call flows through an Advisor Chain (managed by `ReflectiveMethodInvocation.proceed()`).

```text
+-------------------------------------------------------------------------------+
|                        CALLER BEAN (e.g., OrderService)                       |
+---------------------------------------+---------------------------------------+
                                        | call processPayment()
                                        v
+-------------------------------------------------------------------------------+
|                            PROXY OBJECT (CGLIB)                               |
|-------------------------------------------------------------------------------|
| 1. Intercept Call (MethodInterceptor)                                         |
| 2. Traverse Advisor Chain (PointcutAdvisor)                                   |
|                                                                               |
|   +-----------------------+      +-----------------------+                    |
|   |   TransactionInterceptor  |      |   AuditPaymentAspect   |                    |
|   | (Starts DB Transaction) | ---> | (Logs Start Time)      | --->  [ Proceed ]|
|   +-----------------------+      +-----------------------+          |         |
|                                                                     |         |
+---------------------------------------------------------------------|---------+
                                                                      |
                                                                      | call real
                                                                      v processPayment()
+-------------------------------------------------------------------------------+
|                            TARGET BEAN (PaymentService)                       |
|-------------------------------------------------------------------------------|
|   public void processPayment() {                                              |
|       // Core domain logic                                                    |
|       this.recordLedgerTransaction(); // <--- SELF INVOCATION BOOBY TRAP!     |
|   }                                                                           |
|                                                                               |
|   @Transactional(propagation = Propagation.REQUIRES_NEW)                      |
|   public void recordLedgerTransaction() { ... }                               |
+-------------------------------------------------------------------------------+
```

If `processPayment()` calls `this.recordLedgerTransaction()`, the call is routed internally within the **Target Bean**. The proxy is entirely bypassed!

## 3. Enterprise Scenario

In the FinFlow Payment Platform, the `PaymentService` is responsible for securely managing monetary transitions. It handles roughly **4,000 req/sec** at peak times.

The architecture demands the following cross-cutting concerns:
1. **Transaction Isolation:** `recordLedgerTransaction()` must run in an isolated transaction (`REQUIRES_NEW`). Even if the main payment process fails and rolls back, the attempt must be recorded in the append-only ledger database.
2. **Asynchronous Processing:** Upon a successful payment, `publishEventAsync()` must send a message to the `payment-events` Kafka topic asynchronously so as not to block the critical HTTP request threads.
3. **Auditing & Metrics:** A custom `@AuditPayment` aspect tracks latency, captures secure context metadata, and publishes Micrometer metrics for every payment invocation.
4. **Fault Tolerance:** Circuit breakers via Resilience4j.

## 4. Incorrect Implementation

The following code demonstrates a critical misunderstanding of Spring's proxy model. It compiles flawlessly, passes naive unit tests, but fails catastrophically in production.

### The Target Bean

```java
package com.finflow.chapter110.incorrect.service;

import com.finflow.chapter110.domain.PaymentIntent;
import com.finflow.chapter110.incorrect.annotation.AuditPayment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    @Transactional
    @AuditPayment
    public void processPayment(PaymentIntent intent) {
        // Core payment logic...
        
        // ❌ PROBLEM 1: Self-invocation. Bypasses the @Transactional interceptor.
        // The ledger will NOT be recorded in a new transaction.
        this.recordLedgerTransaction(intent);

        // ❌ PROBLEM 2: Self-invocation. Bypasses the @Async interceptor.
        // Kafka publishing will run synchronously on the Tomcat thread.
        this.publishEventAsync(intent);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLedgerTransaction(PaymentIntent intent) {
        // Write to ledger_db
    }

    @Async
    public void publishEventAsync(PaymentIntent intent) {
        // Network I/O to Kafka brokers
    }

    // ❌ PROBLEM 3: Final method. CGLIB cannot subclass and override this method.
    // The @Transactional annotation here is entirely ignored.
    @Transactional
    public final void calculateFee(PaymentIntent intent) {
        // Fee calculation logic writing to DB
    }
}
```

### The Custom Aspect

```java
package com.finflow.chapter110.incorrect.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
// ❌ PROBLEM 4: Missing @Order. We don't know if this runs INSIDE or OUTSIDE the DB transaction.
public class PaymentAuditAspect {

    @Around("@annotation(com.finflow.chapter110.incorrect.annotation.AuditPayment)")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return pjp.proceed();
        } finally {
            long duration = System.currentTimeMillis() - start;
            System.out.println("Payment processed in " + duration + " ms");
            // Metric recording...
        }
    }
}
```

## 5. Production Incident

During FinFlow’s annual "Black Friday Flash Sale", traffic surged to **6,500 req/sec**. A batch of rogue payment requests hit a misconfigured third-party gateway, which threw a `502 Bad Gateway` error midway through the `processPayment` method.

1. **The Ledger Mismatch:** The main transaction rightfully initiated a rollback. Because `this.recordLedgerTransaction()` was invoked internally, it completely bypassed the proxy. The `REQUIRES_NEW` directive was ignored. Instead of committing in isolation, the ledger entries were rolled back along with the main transaction. 
   **Result:** The ledger and the gateway logs were out of balance by **$420,000 (illustrative)**. Financial auditing flagged a critical discrepancy.
2. **The Cascading Outage:** The Kafka cluster experienced a temporary latency spike (3 seconds per message). Because `this.publishEventAsync()` also bypassed the `@Async` proxy, the Kafka network call was executed synchronously on the Tomcat worker threads. Within 15 seconds, all 200 `http-nio-8080-exec-*` worker threads across 20 pods were blocked waiting for Kafka acknowledgments.
   **Result:** The API Gateway could no longer route requests to the Payment Service, returning `504 Gateway Timeout` to the frontend, resulting in an hour-long total outage.

## 6. Logs

Extracting logs from Datadog during the incident revealed the structural bypasses:

```text
2026-11-27T10:15:02.100Z [http-nio-8080-exec-4] DEBUG o.s.orm.jpa.JpaTransactionManager - Creating new transaction with name [com.finflow.chapter110.incorrect.service.PaymentService.processPayment]
2026-11-27T10:15:02.105Z [http-nio-8080-exec-4] INFO  c.f.c.i.aspect.PaymentAuditAspect - Payment processing started...
# NO log for "Creating new transaction with name [recordLedgerTransaction]"! The REQUIRES_NEW was silently ignored.
2026-11-27T10:15:02.110Z [http-nio-8080-exec-4] WARN  o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 502, SQLState: 08006
2026-11-27T10:15:02.112Z [http-nio-8080-exec-4] DEBUG o.s.orm.jpa.JpaTransactionManager - Initiating transaction rollback
# ...
# Notice that the Kafka publish ran on 'http-nio-8080-exec-4' rather than an 'AsyncExecutor' thread!
2026-11-27T10:15:05.150Z [http-nio-8080-exec-4] INFO  o.a.k.clients.producer.KafkaProducer - [Producer clientId=producer-1] Sending payment-events ... 
```

## 7. Root Cause Analysis

The root cause lies in the mechanics of Java's `invokevirtual` bytecode instruction and how CGLIB implements proxies.

When Spring creates a CGLIB proxy, it dynamically generates a subclass of `PaymentService`. Inside the IoC container, the reference to `paymentService` points to this subclass.

However, the proxy object contains a reference to the **actual** `PaymentService` instance (the "target"). When the proxy delegates the method call using `MethodInterceptor.invokeSuper()`, the JVM enters the actual `PaymentService.processPayment()` method.

Once inside the target object's method, the `this` reference points exclusively to the target object itself, **not** the proxy. When the code executes `this.recordLedgerTransaction()`, the JVM performs a standard virtual method invocation directly on the target object's memory address. The call never routes back out to the proxy, meaning the AOP interceptor chain (`TransactionInterceptor`, `AsyncExecutionInterceptor`) is completely circumvented.

Furthermore, CGLIB relies on overriding methods. If a method is marked as `final` (or the class is marked `final`), the JVM forbids overriding. The proxy generation silently skips the method. When a caller invokes `calculateFee()`, it hits the un-proxied method directly. The compiler and IDE do not flag this because syntactically, standard Java rules apply.

## 8. Debugging Process

When diagnosing AOP failures, engineers must verify if the target object is wrapped in a proxy, and if the proxy is active on the current thread.

1. **Thread Dump Analysis:** Taking a thread dump using `jcmd <pid> Thread.print` revealed that Tomcat threads (`http-nio-8080-exec-*`) were parked in `KafkaProducer.send()`. Since this method was annotated with `@Async`, it proved the AOP boundary was breached.
2. **Transaction Interceptor Tracing:** Enabling `TRACE` level logging for Spring's transaction interceptors:
   ```yaml
   logging:
     level:
       org.springframework.transaction.interceptor: TRACE
   ```
   This confirmed that `TransactionInterceptor.invoke()` was only triggered once (for `processPayment`), and never for `recordLedgerTransaction`.
3. **Runtime Proxy Verification:** During local debugging, injecting `ApplicationContext` and inspecting the bean using Spring's `AopUtils`:
   ```java
   System.out.println("Is AOP Proxy? " + AopUtils.isAopProxy(paymentService));
   System.out.println("Is CGLIB? " + AopUtils.isCglibProxy(paymentService));
   ```

## 9. Correct Implementation

There are two primary ways to fix self-invocation:
1. **Architectural Refactoring (Recommended):** Separate the concerns into different beans.
2. **Self-Injection / AopContext (Workaround):** Obtain a reference to the proxy from within the bean.

### Approach 1: Separation of Concerns (Architectural Fix)

The cleanest fix is extracting the sub-routines into their own specialized components. This forces the calls to traverse the IoC container's proxy boundary.

```java
package com.finflow.chapter110.correct.service;

import com.finflow.chapter110.domain.PaymentIntent;
import com.finflow.chapter110.correct.annotation.AuditPayment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final LedgerService ledgerService;
    private final PaymentEventPublisher eventPublisher;

    public PaymentService(LedgerService ledgerService, PaymentEventPublisher eventPublisher) {
        this.ledgerService = ledgerService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    @AuditPayment
    public void processPayment(PaymentIntent intent) {
        // 1. Core logic
        
        // ✅ Call routes through LedgerService Proxy
        ledgerService.recordLedgerTransaction(intent);

        // ✅ Call routes through PaymentEventPublisher Proxy
        eventPublisher.publishEventAsync(intent);
    }
}
```

```java
package com.finflow.chapter110.correct.service;

import com.finflow.chapter110.domain.PaymentIntent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLedgerTransaction(PaymentIntent intent) {
        // Appends to ledger_db in an isolated transaction
    }
}
```

### Approach 2: Explicit Proxy Ordering

Aspect order dictates nesting. A lower order number means higher priority (executes earlier, wraps the outside). We must explicitly define whether `@AuditPayment` runs inside or outside the database transaction.

```java
package com.finflow.chapter110.correct.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
// ✅ Ensure Audit runs outside the Transaction boundary (Transaction management defaults to Ordered.LOWEST_PRECEDENCE)
// By giving Audit an order of 1, it wraps the transaction.
@Order(1) 
public class PaymentAuditAspect {

    @Around("@annotation(com.finflow.chapter110.correct.annotation.AuditPayment)")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();
        try {
            return pjp.proceed();
        } finally {
            long durationNs = System.nanoTime() - start;
            // Record structured metrics
        }
    }
}
```

### Anti-Pattern Workarounds (Use with extreme caution)

If you absolutely cannot extract a new class, you can force self-invocation to go through the proxy by self-injecting the bean using an `ObjectProvider`, or using `AopContext`. 

```java
@Service
public class AntiPatternPaymentService {
    
    // Lazy injection to avoid circular dependency exceptions during context startup
    @Autowired
    private ObjectProvider<AntiPatternPaymentService> selfProxy;

    @Transactional
    public void processPayment(PaymentIntent intent) {
        // ✅ Routes through proxy, but tightly couples code to Spring infrastructure
        selfProxy.getObject().recordLedgerTransaction(intent);
        
        // Alternatively, using ThreadLocal AopContext (Requires @EnableAspectJAutoProxy(exposeProxy = true))
        // ((AntiPatternPaymentService) AopContext.currentProxy()).recordLedgerTransaction(intent);
    }
}
```

## 10. Performance Comparison

Spring AOP is highly optimized, but it is not free. 

| Metric | Direct Object Invocation | CGLIB Proxy Invocation | AspectJ (CTW) Invocation |
|--------|--------------------------|------------------------|--------------------------|
| **Execution Overhead** | ~0.5 ns | ~5-15 ns **(illustrative)** | ~0.5 ns |
| **Startup Cost** | None | High (Bytecode generation at startup) | None (Compiled offline) |
| **Memory (Metaspace)** | Baseline | High (Generates subclass for every proxied bean) | Low |
| **Self-Invocation** | Works normally | **Fails (Bypasses proxy)** | Works (Bytecode woven) |

For most enterprise systems, the 10ns execution overhead of a CGLIB proxy is completely negligible compared to network I/O, database queries (typically ~5ms), or Kafka operations. The real cost is in Metaspace consumption and startup time, particularly in massive monoliths with tens of thousands of beans.

## 11. Best Practices

1. **Keep Proxy Boundaries Crisp:** Always assume that AOP annotations (`@Transactional`, `@Async`, `@Cacheable`, `@Retryable`) only function when called from *outside* the bean.
2. **Favor Composition over Workarounds:** Extract cross-cutting targets into separate beans rather than using `AopContext.currentProxy()`.
3. **Explicitly Order Aspects:** Always annotate custom `@Aspect` classes with `@Order`. A missing order leads to non-deterministic behavior during Spring version upgrades.
4. **Avoid Private/Protected Method AOP:** Spring AOP only safely proxies `public` methods. Trying to proxy non-public methods leads to silent failures or exceptions depending on the proxy engine.
5. **Use `@EnableAspectJAutoProxy(exposeProxy = true)` sparingly:** This forces the proxy to be bound to a `ThreadLocal`, introducing minor performance overhead and lifecycle complexities.

## 12. Common Mistakes

- **Self-Invocation:** Calling `@Transactional` or `@Async` methods from within the same class.
- **Final Classes/Methods:** Marking a `@Service` class or a `@Transactional` method as `final`. CGLIB will silently fail to proxy it.
- **Leaking Proxy Logic in Catch Blocks:** Throwing raw `Exception` from a custom `@Around` aspect instead of re-throwing the precise domain exception, causing upstream `@Transactional` boundaries to miss rollback rules.
- **Forgetting `@Component` on Aspects:** An `@Aspect` must also be a Spring Bean to be registered by the auto-proxy creator.

## 13. Interview Questions

*   **Junior:** What happens if you call an `@Async` method from another method in the exact same class?
*   **Mid:** Explain the difference between JDK Dynamic Proxies and CGLIB proxies. Which one does Spring Boot use by default?
*   **Senior:** Why does a `@Transactional(propagation = Propagation.REQUIRES_NEW)` method fail to create a new transaction during a self-invocation? Walk through the JVM memory model and `this` pointer to explain it.
*   **Staff:** If you have `@Audit` and `@Transactional` on the same method, how does Spring decide which one runs first? How can you guarantee the audit log accurately captures the database commit time?
*   **Principal:** Your application has 5,000 proxy beans and is suffering from high Metaspace utilization and slow startup in Kubernetes. Would you migrate to AspectJ Compile-Time Weaving (CTW)? Outline the CI/CD, local development, and observability trade-offs of CTW versus Spring AOP.

## 14. Hands-on Exercise

**Goal:** Implement a custom `@RateLimited` aspect that uses a token-bucket algorithm to throttle requests to a specific method.
1. Create a `@RateLimited(limit = 10)` annotation.
2. Create an `@Aspect` that intercepts the annotation and throws a `TooManyRequestsException` if the limit is breached.
3. Write a unit test that verifies the aspect works when called from a separate class.
4. **The Trap:** Write a test that intentionally performs a self-invocation and prove with an assertion that the rate limit is bypassed. Fix it using an `ObjectProvider`.

## 15. Advanced Challenge

**AspectJ Compile-Time Weaving (CTW)**
1. Modify your Maven `pom.xml` to include the `aspectj-maven-plugin`.
2. Configure Spring Boot to disable proxy generation and rely purely on AspectJ.
3. Refactor a self-invoking `@Transactional` method to be `private`.
4. Run the application and observe that with CTW, the `private` self-invocation successfully triggers the aspect! Analyze the resulting `.class` file using `javap -c` to see how AspectJ injected the advice directly into the bytecode.

## 16. Production Checklist

- [ ] **AOP Code Reviews:** Any PR introducing `@Transactional`, `@Async`, `@Cacheable`, or `@Retryable` must be manually checked for self-invocation.
- [ ] **Final Keyword Audit:** Ensure no AOP-targeted beans or methods are marked `final`.
- [ ] **Aspect Ordering:** Ensure all custom `@Aspect` beans explicitly declare an `@Order`.
- [ ] **Integration Testing:** Write `@SpringBootTest` test cases that verify proxy interceptor side-effects (e.g., verifying a Kafka message is actually sent asynchronously, or a DB transaction actually rolls back) rather than purely mocking the service layer.
