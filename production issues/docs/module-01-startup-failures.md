# Module 01: Spring Boot Startup and Application Context Failures

## Issue 1.1: Circular Dependency Deadlock & BeanCurrentlyInCreationException

---

### 1. Scenario

During a production deployment of the **FinFlow Core Payment Service**, a new feature is merged: `OrderProcessingService` needs to call `PaymentSettlementService` to execute debit authorizations, while `PaymentSettlementService` is modified to call `OrderProcessingService` to update order fulfillment states synchronously.

When the application deployment starts up in staging/production, the JVM launches, but Spring Boot fails to start, throwing an unrecoverable exception and causing Kubernetes pods to enter `CrashLoopBackOff`.

---

### 2. Symptoms

```text
1. Application terminates immediately upon launch with Exit Code 1.
2. Log output shows Spring Boot Banner, followed by:
   "APPLICATION FAILED TO START"
3. Exact Error:
   "org.springframework.beans.factory.UnsatisfiedDependencyException: Error creating bean with name 'orderProcessingService': 
    Unsatisfied dependency expressed through constructor parameter 0: Error creating bean with name 'paymentSettlementService': 
    Requested bean is currently in creation: Is there an unresolvable circular reference?"
4. Kubernetes Pod Status: CrashLoopBackOff (Restart Count: 5).
5. Actuator Liveness & Readiness Probes never respond (connection refused).
```

---

### 3. Possible Root Causes

1. **Circular Constructor Injection (Most Likely):** Two or more singleton beans require each other in their `@Autowired` / explicit constructors (`A -> B -> A` or `A -> B -> C -> A`).
2. **Spring Boot 2.6+ / 3.x Circular References Prohibition:** Starting in Spring Boot 2.6.0+, `spring.main.allow-circular-references` defaults to `false`.
3. **Tight Architectural Coupling of Domain Invariants:** Mixing command orchestration with post-processing notifications in the same service layer instead of using domain events.
4. **Proxy Initialization Cycle during AOP Wrapping:** If Bean A has `@Transactional` or `@Async` and references Bean B which references Bean A, early proxy creation cannot resolve the circular reference.

---

### 4. Architecture Context

```text
                  ┌────────────────────────────────────────┐
                  │          ApplicationContext            │
                  │                                        │
                  │   ┌────────────────────────────────┐   │
                  │   │      OrderProcessingService    │   │
                  │   └───────────────┬────────────────┘   │
                  │                   │ requires           │
                  │                   ▼                    │
                  │   ┌────────────────────────────────┐   │
                  │   │    PaymentSettlementService    │   │
                  │   └───────────────┬────────────────┘   │
                  │                   │ requires           │
                  │                   ▼                    │
                  │      [DEADLOCK: Back to Order]         │
                  └────────────────────────────────────────┘
```

---

### 5. How to Reproduce the Issue

#### Step 1: Define Circular Constructor Injection
```java
@Service
public class OrderProcessingService {
    private final PaymentSettlementService paymentService;

    public OrderProcessingService(PaymentSettlementService paymentService) {
        this.paymentService = paymentService;
    }
}

@Service
public class PaymentSettlementService {
    private final OrderProcessingService orderService;

    public PaymentSettlementService(OrderProcessingService orderService) {
        this.orderService = orderService;
    }
}
```

#### Step 2: Ensure Default Circular Reference Protection in `application.yml`
```yaml
spring:
  main:
    allow-circular-references: false
```

#### Step 3: Run Startup Test
Execute `mvn clean test` on `CircularDependencyReproductionTest.java`. The test asserts `BeanCurrentlyInCreationException` on startup.

---

### 6. Evidence Collection

When investigating startup crashes:

1. **Stdout & Container Logs (`kubectl logs <pod-name>` or console):**
   - Look for the `Description:` section rendered by Spring Boot's `FailureAnalyzers`.
   - Identify the circular chain:
     ```text
     ***************************
     APPLICATION FAILED TO START
     ***************************
     Description:
     The dependencies of some of the beans in the application context form a cycle:
     ┌─────┐
     |  orderProcessingService defined in file [...]
     ↑     ↓
     |  paymentSettlementService defined in file [...]
     └─────┘
     ```

2. **JVM Exit Code:**
   - Exit code `1` $\implies$ ApplicationContext refresh aborted gracefully.
   - Exit code `137` $\implies$ OOMKilled (Not a startup bean definition bug).

---

### 7. Debugging Procedure

```text
Step 1: Inspect the Top of the Stack Trace.
        Identify the root bean being instantiated when the failure occurred.

Step 2: Trace the Dependency Chain.
        Review constructors of Bean A and Bean B to identify the mutual dependency.

Step 3: Check for Hidden Cycles via Aspects/Proxies.
        Verify if @Transactional, @Async, or @Cacheable are forcing early proxy creation.

Step 4: Distinguish "Quick Fix / Anti-Pattern" vs "Architectural Fix".
        DO NOT simply set allow-circular-references=true or inject @Lazy without understanding the coupling smell.

Step 5: Apply Event-Driven Decoupling or Extract Shared Mediator Service.
```

---

### 8. Technical Root Cause Deep-Dive

#### Spring `DefaultSingletonBeanRegistry` Three-Level Cache Mechanics

Spring resolves bean dependencies using a three-level cache in `DefaultSingletonBeanRegistry`:
1. `singletonObjects` (1st Level): Fully initialized singleton instances.
2. `earlySingletonObjects` (2nd Level): Pre-mature singleton instances (instantiated, before property injection/initialization).
3. `singletonFactories` (3rd Level): `ObjectFactory` instances that can produce early references or CGLIB/JDK dynamic proxies.

When using **Constructor Injection**, Spring **cannot** instantiate Bean A without first having the arguments to call `new BeanA(BeanB b)`. Therefore, Bean A cannot even be placed into the 3rd-level `singletonFactories` cache!
When Spring then attempts to instantiate Bean B via `new BeanB(BeanA a)`, it looks for Bean A, discovers Bean A is marked as *currently in creation* (`singletonsCurrentlyInCreation`), and fails immediately with `BeanCurrentlyInCreationException`.

---

### 9. Production-Grade Fixes

#### ❌ The Wrong Fix: Enabling Circular References in Configuration
```yaml
# DANGEROUS ANTI-PATTERN: Masks architectural design rot and causes initialization deadlocks
spring:
  main:
    allow-circular-references: true
```

#### ⚠️ The Band-Aid: `@Lazy` Injection (Use only during emergency triage)
```java
@Service
public class OrderProcessingService {
    private final PaymentSettlementService paymentService;

    // @Lazy forces Spring to inject a synthetic CGLIB proxy rather than constructing the real instance immediately
    public OrderProcessingService(@Lazy PaymentSettlementService paymentService) {
        this.paymentService = paymentService;
    }
}
```

#### ✅ The Production Architecture Fix: Event-Driven Decoupling via `ApplicationEventPublisher`

```java
// 1. Immutable Domain Event
public record PaymentCompletedEvent(String orderId, String customerId, BigDecimal amount, Instant timestamp) {}

// 2. Publisher Service (No dependency on Notification/Order services)
@Service
public class PaymentSettlementService {
    private final ApplicationEventPublisher eventPublisher;

    public PaymentSettlementService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public String settlePayment(String orderId, String customerId, BigDecimal amount) {
        String paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8);
        eventPublisher.publishEvent(new PaymentCompletedEvent(orderId, customerId, amount, Instant.now()));
        return paymentId;
    }
}

// 3. Decoupled Event Listener
@Service
public class NotificationAuditService {
    @EventListener
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        // Send email/SMS notification asynchronously
    }
}
```

---

### 10. Verification

1. **Automated Integration Test:** Run `DecoupledOrderFlowIntegrationTest.java` to verify that `ApplicationContext` initializes without errors and end-to-end checkout executes cleanly.
2. **Actuator Health Check:** Verify `/actuator/health` returns `{"status": "UP"}`.

---

### 11. Prevention

1. **Strict CI/CD Linting:** Ensure `spring.main.allow-circular-references=false` is enforced in all pipeline tests.
2. **ArchUnit Architecture Rule:**
   ```java
   @ArchTest
   public static final ArchRule no_cycles_in_slices = SlicesRuleDefinition.slices()
           .matching("com.finflow.(*)..")
           .should().beFreeOfCycles();
   ```
3. **Single Responsibility Principle:** Separate read operations, write commands, and notification side-effects into distinct services.

---

### 12. Interview & Production Questions

#### Interview Questions
1. **Q: Why does constructor injection fail on circular dependencies while field injection with setter methods historically succeeded in older Spring versions?**
2. **Q: What is the purpose of the 3-level cache (`singletonObjects`, `earlySingletonObjects`, `singletonFactories`) in Spring's `DefaultSingletonBeanRegistry`?**
3. **Q: Why did Spring Boot 2.6+ disable circular references by default?**
4. **Q: How does `@Lazy` work under the hood when placed on a constructor parameter?**
5. **Q: How does using Spring `ApplicationEventPublisher` or `@TransactionalEventListener` eliminate circular dependencies?**

#### Production Incident Questions
1. **Incident:** After upgrading a legacy Spring Boot 2.5 service to Spring Boot 3.3, all production pods fail to start with `BeanCurrentlyInCreationException`. What is the immediate 2-minute containment vs the permanent 1-day fix?
2. **Incident:** Adding `@Async` or `@Transactional` to a method in `ServiceA` causes a startup failure due to proxy wrapping, even though the code started fine without `@Async`. Why?
3. **Incident:** In a multi-module Maven project, a developer creates a circular Maven dependency between `order-module` and `payment-module`. How does Maven react vs how does Spring runtime react?
4. **Incident:** You have a circular dependency between `SecurityConfig` and `CustomUserDetailsService` when password encoder beans are declared in `SecurityConfig`. How do you cleanly resolve this?
5. **Incident:** In a high-concurrency microservice, two `@PostConstruct` methods on circular beans attempt to invoke each other. What happens at runtime?

#### Trick Questions
1. **Trick:** Can two prototype-scoped beans (`@Scope("prototype")`) have a circular dependency if setter injection is used?
2. **Trick:** Does `@Lazy` on a field solve circular dependencies if the bean also implements `InitializingBean` and calls the bean inside `afterPropertiesSet()`?
3. **Trick:** If Bean A has `@Order(1)` and Bean B has `@Order(2)`, does `@Order` determine the initialization order of singleton beans during `ApplicationContext` refresh?

---

*(Answers and detailed explanations are provided in the Master Answer Guide)*
