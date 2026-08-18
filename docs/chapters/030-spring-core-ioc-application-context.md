---
chapter: 30
topic: Spring Core — IoC Container, BeanFactory vs ApplicationContext
prerequisite_chapters: [10, 20]
reference_system_node: Payment Service (application startup, bean wiring, context lifecycle)
---

# Chapter 030: Spring Core — IoC Container, BeanFactory vs ApplicationContext

## 1. Concept

Inversion of Control (IoC) is the defining architectural principle behind the Spring Framework. In a traditional Java application, custom code controls the instantiation, configuration, and lifecycle of objects, pulling in dependencies manually using the `new` keyword or factories. Under the IoC principle, this flow is inverted: the framework container controls object creation and lifecycle, pushing dependencies into the objects at runtime. This is often summarized by the Hollywood Principle: "Don't call us, we'll call you."

The **Spring IoC Container** is the central artifact of the framework. It acts as an advanced factory capable of maintaining a registry of configured components (beans), wiring them together via Dependency Injection (DI), and managing their complete lifecycle from initialization to destruction. 

Within the Spring Framework, this container is defined by two primary interfaces:
*   **`BeanFactory`**: The foundational, root interface of the IoC container. It provides the core dependency injection mechanism. A `BeanFactory` operates under a lazy-initialization paradigm by default, meaning it only instantiates a bean when it is explicitly requested or when another bean requests it as a dependency.
*   **`ApplicationContext`**: A complete superset of `BeanFactory`. In enterprise applications like our FinFlow platform, `ApplicationContext` is universally used. It extends the core DI capabilities with enterprise features: unified resource loading (files, classpaths), event publication (Observer pattern), internationalization (`MessageSource`), AOP integration, and transparent processing of `@Configuration` classes and environment profiles.

### Why the Distinction Matters in Production

While modern Spring Boot applications abstract away the direct usage of these interfaces, the underlying mechanics drastically impact production behavior. The `ApplicationContext` proactively instantiates and wires all singleton beans during the startup phase. For a microservice like the FinFlow Payment Service, operating at ~4,000 requests/sec (illustrative) peak load, this eager initialization is a critical fail-fast mechanism. If a database credential is malformed or a dependency cycle exists, the service fails to start up, which prevents Kubernetes from routing live traffic to a broken pod. 

Conversely, relying on lazy initialization limits the upfront memory footprint (useful in serverless environments) but risks runtime `BeanCreationException`s when the first live request hits a misconfigured bean. Understanding these tradeoffs directly impacts application startup time, memory consumption, and deployment reliability.

### BeanDefinition: The Blueprint

Before the container creates an actual Java object, it constructs a `BeanDefinition`. This metadata object serves as the blueprint for the bean, describing its class name, scope (singleton, prototype, request), constructor arguments, autowired properties, and lifecycle hooks (`init-method`, `destroy-method`). When you declare `@Service` or `@Bean`, Spring parses this into a `BeanDefinition` and registers it in a `BeanDefinitionRegistry`.

### What Problem IoC Solves

IoC decouples the declaration of a component from its realization. In the FinFlow architecture, the `PaymentService` requires a connection to a payment processor. By wiring a `PaymentGateway` interface via IoC, the service code never hardcodes a dependency on `StripeGatewayImpl` or `MockGatewayImpl`. The IoC container injects the correct implementation based on the active Spring profile, enabling hermetic unit testing, swap-ability, and clean microservice boundaries.

---

## 2. Internal Working

To master Spring in a production environment, you must understand the container bootstrap sequence—what actually happens when your `main` method calls `SpringApplication.run()`.

### Container Bootstrap Sequence

1.  **Environment Preparation**: Spring Boot determines the application type (Servlet, Reactive, or None) and instantiates the appropriate `ApplicationContext` (e.g., `AnnotationConfigServletWebServerApplicationContext` for standard web MVC).
2.  **BeanDefinition Scanning**: The `BeanDefinitionReader` scans the classpath. Utilizing Java ASM (as detailed in Chapter 010), it parses `.class` files without fully loading them into the JVM Metaspace, searching for stereotype annotations (`@Component`, `@Service`, `@Controller`). It registers a `BeanDefinition` for each discovered component.
3.  **BeanFactoryPostProcessor Execution**: The container invokes all registered `BeanFactoryPostProcessor` implementations. These processors modify the `BeanDefinition` metadata *before* any beans are instantiated.
    *   `PropertySourcesPlaceholderConfigurer` resolves `${payment.timeout}` placeholders.
    *   `ConfigurationClassPostProcessor` parses `@Configuration` classes, processes `@Import`, and executes `@ComponentScan`.
4.  **Registration Finalization**: The `BeanDefinition` registry is locked. No new bean definitions should be added after this phase.
5.  **Eager Instantiation**: The `ApplicationContext` iterates through all non-lazy singleton bean definitions, performs a topological sort based on dependencies, and begins instantiation.
6.  **BeanPostProcessor Pipeline**: During instantiation, beans pass through a critical transformation pipeline governed by `BeanPostProcessor`s.
    *   `AutowiredAnnotationBeanPostProcessor` injects dependencies for `@Autowired` and `@Value`.
    *   `CommonAnnotationBeanPostProcessor` invokes `@PostConstruct` methods.
    *   `AbstractAutoProxyCreator` wraps the raw bean in a JDK Dynamic Proxy or CGLIB proxy (providing `@Transactional`, `@Cacheable`, and AOP capabilities).
7.  **Context Refresh**: The container finishes initialization and publishes a `ContextRefreshedEvent` via its `ApplicationEventMulticaster`.
8.  **Server Startup**: The embedded web server (Tomcat/Netty) is started and bound to its configured port, officially making the application ready to accept traffic.

```text
+---------------------------------------------------------------------------------------+
|  SpringApplication.run()                                                              |
|   |                                                                                   |
|   v                                                                                   |
| [ ApplicationContext Created ]                                                        |
|   |                                                                                   |
|   v                                                                                   |
| [ Classpath Scanning & BeanDefinition Registration ]                                  |
|   |  -> ASM bytecode parsing, identifying @Component, @Service                        |
|   |                                                                                   |
|   v                                                                                   |
| [ BeanFactoryPostProcessors Execute ]                                                 |
|   |  -> Resolve properties, process @Configuration                                    |
|   |                                                                                   |
|   v                                                                                   |
| [ Bean Instantiation (Singletons) ] <-- DefaultListableBeanFactory                    |
|   |  -> Constructor resolution, Dependency Injection                                  |
|   |                                                                                   |
|   v                                                                                   |
| [ BeanPostProcessors Execute ]                                                        |
|   |  -> @Autowired injection, @PostConstruct invocation                               |
|   |  -> AOP Proxy generation (@Transactional wrappers)                                |
|   |                                                                                   |
|   v                                                                                   |
| [ Context Refreshed & Embedded Server Started ]                                       |
+---------------------------------------------------------------------------------------+
```

### BeanFactory Internals

The actual workhorse implementation is `DefaultListableBeanFactory`. It maintains several critical internal data structures:
*   `beanDefinitionMap`: A `ConcurrentHashMap` mapping bean names to their `BeanDefinition`.
*   `singletonObjects`: The L1 cache (often called the singleton cache) holding fully initialized, ready-to-use beans.
*   `earlySingletonObjects`: The L2 cache holding early references to beans that are instantiated but not yet fully populated (used to resolve circular dependencies).
*   `singletonFactories`: The L3 cache holding `ObjectFactory` instances that can generate early references (allowing AOP proxies to be created early if needed).

### The BeanPostProcessor Pipeline

The `BeanPostProcessor` (BPP) pipeline is where Spring works its magic. BPPs operate on object instances, not definitions. The order of execution is crucial and determined by the `Ordered` interface. If a custom proxy is created before the `AutowiredAnnotationBeanPostProcessor` executes, the resulting proxy will completely bypass dependency injection, resulting in `NullPointerException`s at runtime.

---

## 3. Enterprise Scenario

The FinFlow `PaymentService` operates 20 active instances in a Kubernetes cluster to handle the ~4,000 req/sec (illustrative) peak load. Over the past 18 months, the codebase has accumulated over 450 Spring beans. 

Historically, the service started in 8 seconds (illustrative). Following a recent release upgrading to Spring Boot 3.3.x and including several new compliance features, the application startup time severely degraded to 28 seconds (illustrative). 

During a rolling deployment, Kubernetes begins terminating old pods and provisioning new ones. Because the new pods are taking nearly 30 seconds to report readiness, traffic is disproportionately routed to the remaining older pods, causing CPU throttling and thread pool exhaustion (HikariCP 10 conn/instance bottleneck).

An investigation reveals three distinct IoC-related anti-patterns introduced in the latest sprint:
1.  A junior developer introduced an `AuditableBeanRegistrar` (a `BeanFactoryPostProcessor`) to dynamically audit secure beans. However, they improperly queried bean *instances* rather than *definitions*.
2.  A circular dependency was introduced between the `PaymentProcessingService` and the new `FraudDetectionService`. Spring Boot 3.x enforces strict circular dependency prevention, leading to startup crashes.
3.  A heavy `ReportGenerationService` was added. This singleton bean aggressively pre-loads 500MB of static compliance data from PostgreSQL during its `@PostConstruct` phase, blocking the main thread and halting the container refresh.

---

## 4. Incorrect Implementation

The following code illustrates the exact flaws deployed to production.

### Problem 1: BeanFactoryPostProcessor Triggering Premature Instantiation

The developer attempted to inspect beans for an `@Auditable` annotation. By calling `beanFactory.getBean()`, they forced the IoC container to eagerly instantiate the bean *before* the `BeanPostProcessor` pipeline was fully registered.

```java
package com.finflow.chapter030.faulty.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Component;
import com.finflow.chapter030.common.Auditable;

@Component
public class AuditableBeanRegistrar implements BeanFactoryPostProcessor {
    
    private static final Logger log = LoggerFactory.getLogger(AuditableBeanRegistrar.class);

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        String[] beanNames = beanFactory.getBeanDefinitionNames();
        
        for (String beanName : beanNames) {
            try {
                // ANTI-PATTERN: Calling getBean() inside a BeanFactoryPostProcessor
                // This triggers instantiation BEFORE BeanPostProcessors (like @Autowired) are registered.
                Object bean = beanFactory.getBean(beanName);
                
                if (bean.getClass().isAnnotationPresent(Auditable.class)) {
                    log.info("Registered auditable bean: {}", beanName);
                }
            } catch (Exception e) {
                // Silently ignoring errors, masking the underlying container corruption
                log.warn("Could not check bean {}", beanName);
            }
        }
    }
}
```

### Problem 2: Constructor-Based Circular Dependency

The developer correctly used constructor injection, but created an unsolvable cycle. Constructor injection requires the dependency to be fully constructed before it can be passed.

```java
package com.finflow.chapter030.faulty.service;

import org.springframework.stereotype.Service;

@Service
public class PaymentProcessingService {
    
    private final FraudDetectionService fraudDetectionService;
    
    // Circular dependency injected here
    public PaymentProcessingService(FraudDetectionService fraudDetectionService) {
        this.fraudDetectionService = fraudDetectionService;
    }
    
    public void processPayment(String paymentId) {
        fraudDetectionService.analyzeRisk(paymentId);
        // Process logic...
    }
}
```

```java
package com.finflow.chapter030.faulty.service;

import org.springframework.stereotype.Service;

@Service
public class FraudDetectionService {
    
    private final PaymentProcessingService paymentProcessingService;
    
    // Circular dependency injected here
    public FraudDetectionService(PaymentProcessingService paymentProcessingService) {
        this.paymentProcessingService = paymentProcessingService;
    }
    
    public void analyzeRisk(String paymentId) {
        // Risk logic...
    }
}
```

### Problem 3: Unnecessary Eager Initialization Blocking Startup

A service that generates end-of-day reports pre-loads data aggressively. Because it is a non-lazy singleton, it executes synchronously during the `ApplicationContext` refresh.

```java
package com.finflow.chapter030.faulty.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

@Service
public class ReportGenerationService {
    
    private static final Logger log = LoggerFactory.getLogger(ReportGenerationService.class);
    
    @PostConstruct
    public void initializeReferenceData() {
        log.info("Pre-loading 500MB compliance reference data into memory...");
        try {
            // Simulating a heavy, synchronous database pull that blocks the main thread
            Thread.sleep(12000); 
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("Reference data loaded.");
    }
    
    public void generateReport() {
        // Report generation logic
    }
}
```

---

## 5. Production Incident

**T+0:** The SRE team initiates a rolling deployment for the `PaymentService` (20 pods). The Kubernetes strategy is configured with `maxUnavailable: 25%` (5 pods) and `maxSurge: 25%` (5 pods).
**T+30s:** The first batch of 5 new pods attempts to start. Three of them immediately fail with a `BeanCreationException`. The `BeanFactoryPostProcessor` prematurely instantiated the `TransactionRouter` bean, causing its `@Autowired` database connection to be null. A subsequent `@PostConstruct` method threw a `NullPointerException`.
**T+45s:** Kubernetes detects `CrashLoopBackOff` on the 3 failed pods. The cluster is now running 15 old pods and 2 heavily burdened new pods. Traffic density per pod increases by ~15% (illustrative).
**T+1m:** The 2 new pods that bypassed the NPE take 28 seconds (illustrative) to start due to the `ReportGenerationService` blocking the main Spring Boot thread. During this window, Kubernetes marks them as unready.
**T+2m:** Kubernetes terminates another batch of old pods, adhering to the rolling update strategy. Capacity drops further. HikariCP connection pools on the remaining pods saturate.
**T+3m:** PagerDuty P1 Alert triggered: `[URGENT] Payment Service — 5xx error rate > 5% for 3 consecutive minutes.`
**T+5m:** An on-call engineer forces a rollback to the previous deployment. The system stabilizes. A post-mortem is scheduled.

---

## 6. Logs

The application logs revealed precisely where the IoC container failed.

**Log 1: Premature Instantiation Error**
```text
Caused by: java.lang.NullPointerException: Cannot invoke "com.finflow.chapter030.common.DatabaseRouter.route()" because "this.dbRouter" is null
    at com.finflow.chapter030.faulty.service.TransactionManager.initConnections(TransactionManager.java:42)
    at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
    at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.invokeCustomInitMethod(AbstractAutowireCapableBeanFactory.java:1942)
    ... 
    at com.finflow.chapter030.faulty.config.AuditableBeanRegistrar.postProcessBeanFactory(AuditableBeanRegistrar.java:24)
```

**Log 2: Circular Dependency Error**
```text
***************************
APPLICATION FAILED TO START
***************************

Description:

The dependencies of some of the beans in the application context form a cycle:

┌─────┐
|  paymentProcessingService defined in file [PaymentProcessingService.class]
↑     ↓
|  fraudDetectionService defined in file [FraudDetectionService.class]
└─────┘

Action:

Relying upon circular references is discouraged and they are prohibited by default. Update your application to remove the dependency cycle between beans. As a last resort, it may be possible to break the cycle automatically by setting spring.main.allow-circular-references=true.
```

**Log 3: Startup Actuator Telemetry**
```text
2024-11-20T10:45:12.123Z  INFO 1 --- [main] org.springframework.boot.StartupInfoLogger : 
Started PaymentServiceApplication in 28.452 seconds (process running for 29.112)
...
[spring.context.beans.instantiate] beanName: reportGenerationService, duration: 12.015s
```

---

## 7. Root Cause Analysis

### Mechanism 1: BeanFactoryPostProcessor vs BeanPostProcessor
The `BeanFactoryPostProcessor` runs strictly to manipulate `BeanDefinition` objects. At this stage, Spring has not registered the `BeanPostProcessor`s (like `AutowiredAnnotationBeanPostProcessor`). 
When the junior developer called `beanFactory.getBean(beanName)`, they forced Spring to instantiate the bean. Because the autowiring processors weren't registered yet, Spring created the object using reflection (Chapter 010), bypassed all `@Autowired` fields, and cached the resulting partially-constructed object in the `singletonObjects` L1 cache. Any subsequent attempt to use that bean resulted in an NPE.

### Mechanism 2: Constructor Injection & Circular Constraints
Spring resolves circular dependencies for setter or field injection by utilizing its three-level cache. If Bean A needs Bean B, and Bean B needs Bean A, Spring instantiates Bean A, places an early reference of it in the `earlySingletonObjects` cache, and then begins constructing Bean B. Bean B can accept the early reference to Bean A, complete construction, and then Bean A finishes.
However, with *constructor injection*, Java requires the arguments to be fully evaluated before the constructor can be invoked. Therefore, an early reference cannot be created. Spring Boot 3.x explicitly enforces `spring.main.allow-circular-references=false` to prevent bad architectural designs from hiding behind field injection.

### Mechanism 3: Synchronous Refresh Blocking
The `ApplicationContext` executes `refresh()`, which calls `finishBeanFactoryInitialization()`. This method iterates over all registered beans and invokes `getBean()` for all non-lazy singletons. Because the `ReportGenerationService` executed a `Thread.sleep(12000)` equivalent inside its `@PostConstruct`, the entire container initialization blocked sequentially. The JVM (Chapter 020) dedicated the main thread entirely to this sleep state, preventing the embedded web server from starting.

---

## 8. Debugging Process

1.  **Identify CrashLoopBackOff:** Observing the deployment state in K8s, `kubectl logs -f pod/payment-service-xyz` revealed the application failing to start.
2.  **Trace Premature Instantiation:** The logs showed an NPE originating from `TransactionManager.initConnections`. Examining the stack trace, the call hierarchy originated from `AuditableBeanRegistrar.postProcessBeanFactory`. The fix is to inspect definitions, not instances.
3.  **Trace Circular Dependency:** The Spring Boot failure analyzer beautifully prints the ASCII cycle. Both `PaymentProcessingService` and `FraudDetectionService` inject each other via constructors. The architectural fix is to extract the shared logic into a neutral third service.
4.  **Profile Startup Bottleneck:** By adding `spring.main.log-startup-info=true` and `-Dspring.context.startup-tracker=enabled` (or enabling the Spring Boot Startup Actuator), the telemetry pinpointed `reportGenerationService` taking 12 seconds. Since this report isn't needed instantly to process payments, it should be initialized lazily.

---

## 9. Correct Implementation

The following code rectifies all three issues cleanly and efficiently.

### Fix 1: Inspecting BeanDefinitions

Instead of instantiating the bean, we inspect the metadata (`BeanDefinition`). To check annotations without loading the class into the JVM Metaspace, we utilize Spring's `AnnotatedBeanDefinition`.

```java
package com.finflow.chapter030.fixed.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.type.MethodMetadata;
import org.springframework.stereotype.Component;

@Component
public class SafeAuditableBeanRegistrar implements BeanFactoryPostProcessor {
    
    private static final Logger log = LoggerFactory.getLogger(SafeAuditableBeanRegistrar.class);

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            BeanDefinition bd = beanFactory.getBeanDefinition(beanName);
            
            // Check metadata without eagerly instantiating the bean
            if (bd instanceof AnnotatedBeanDefinition annotatedBd) {
                if (annotatedBd.getMetadata().hasAnnotation("com.finflow.chapter030.common.Auditable")) {
                    log.info("Found auditable component via definition: {}", beanName);
                    // Safe to manipulate BeanDefinition properties here
                }
            }
        }
    }
}
```

### Fix 2: Breaking the Circular Dependency

We break the cycle by extracting the core validation logic into a shared, neutral service (`PaymentValidationService`). Both services now depend on the validator, creating a directed acyclic graph (DAG) of dependencies.

```java
package com.finflow.chapter030.fixed.service;

import org.springframework.stereotype.Service;

@Service
public class PaymentValidationService {
    public boolean validatePaymentIntegrity(String paymentId) {
        // Shared logic executed by both domains
        return true;
    }
}

@Service
public class PaymentProcessingService {
    
    private final PaymentValidationService validationService;
    private final FraudDetectionService fraudService; // One-way dependency
    
    public PaymentProcessingService(PaymentValidationService validationService, 
                                    FraudDetectionService fraudService) {
        this.validationService = validationService;
        this.fraudService = fraudService;
    }
    
    public void processPayment(String paymentId) {
        validationService.validatePaymentIntegrity(paymentId);
        fraudService.analyzeRisk(paymentId);
    }
}

@Service
public class FraudDetectionService {
    
    private final PaymentValidationService validationService;
    // PaymentProcessingService dependency removed entirely
    
    public FraudDetectionService(PaymentValidationService validationService) {
        this.validationService = validationService;
    }
    
    public void analyzeRisk(String paymentId) {
        validationService.validatePaymentIntegrity(paymentId);
        // Risk logic...
    }
}
```

### Fix 3: Lazy Initialization for Heavy Beans

By applying `@Lazy`, the `ApplicationContext` will register the `BeanDefinition` but bypass instantiation during the `refresh()` phase. The 500MB payload is only loaded when a user actually requests the report, freeing up the startup thread and drastically reducing initial JVM memory pressure.

```java
package com.finflow.chapter030.fixed.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

@Service
@Lazy // The crucial addition. Bean is created only upon first usage.
public class ReportGenerationService {
    
    private static final Logger log = LoggerFactory.getLogger(ReportGenerationService.class);
    
    @PostConstruct
    public void initializeReferenceData() {
        log.info("Lazy loading 500MB compliance reference data...");
        try {
            Thread.sleep(12000); 
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    public void generateReport() {
        // Report logic
    }
}
```

---

## 10. Performance Comparison

Implementing these fixes yields a dramatic improvement in the deployment pipeline for the FinFlow application.

| Metric | Before | After |
|--------|--------|-------|
| Startup time | 28.5s (illustrative) | 9.2s (illustrative) |
| Failed deployments | 3 out of 5 pods crashed | 0 crashes |
| Readiness probe pass time | 28s | 10s |
| Memory at startup peak | 1.8GB (illustrative) | 1.1GB (illustrative) |
| Deployment completion time | Rolled back | 90s |
| Bean instantiation failures | 47 NPEs across cluster | 0 |

---

## 11. Best Practices

*   **Never call `getBean()` inside `BeanFactoryPostProcessor`**: Modifying definitions is safe; triggering instantiation is dangerous. Only inspect `BeanDefinition` metadata.
*   **Prefer Constructor Injection**: It ensures immutability, fails fast if dependencies are missing, and strictly prohibits circular dependencies.
*   **Use `@Lazy` for Heavy Beans**: Report generators, batch processors, or caching services that aren't immediately required to serve core HTTP requests should be lazily loaded to speed up container startup.
*   **Do Not Alter the Default Circular Dependency Rules**: Maintain `spring.main.allow-circular-references=false`. Extract shared logic or use `ObjectProvider<T>` to break the cycle properly rather than reverting to setter injection hacks.
*   **Measure Startup Times**: Use `spring.main.log-startup-info=true` and the Spring Boot startup actuator to monitor regressions in bean initialization times.
*   **Avoid `ApplicationContext.getBean()` in Business Logic**: Relying on the context to fetch beans dynamically constitutes the Service Locator anti-pattern. Rely on standard DI frameworks wherever possible.

---

## 12. Common Mistakes

*   **Confusing `BeanFactoryPostProcessor` and `BeanPostProcessor`**: The former alters bean blueprints (definitions) before anything is created. The latter alters actual object instances (injecting fields, wrapping in proxies).
*   **Overloading `@PostConstruct`**: Performing heavy, synchronous I/O or network calls in `@PostConstruct` paralyzes the container startup.
*   **Using `new` for Managed Beans**: Manually instantiating a class that is meant to be managed by Spring bypasses AOP. Annotations like `@Transactional` or `@Cacheable` will simply be ignored.
*   **Misunderstanding Bean Ordering**: Assuming beans are instantiated in the order they are declared is false. Dependency resolution defines the order. Use `@DependsOn` only when an implicit dependency exists (e.g., forcing a database schema migration to execute before JPA repositories initialize).

---

## 13. Interview Questions

**Junior:**
*   What is IoC? How does Spring implement it?
*   What is the difference between `BeanFactory` and `ApplicationContext`?

**Mid:**
*   Explain the Spring bean creation lifecycle. What happens between `getBean()` and the bean being ready to use?
*   What is a `BeanPostProcessor`? Give two examples from Spring's own codebase (e.g., `AutowiredAnnotationBeanPostProcessor`).

**Senior:**
*   Walk through how Spring resolves a circular dependency between two singleton beans using setter injection. What are the three levels of cache involved?
*   A Spring Boot 3.x application that worked in 2.x now fails with `BeanCurrentlyInCreationException`. What changed, and what are your options to fix it?

**Staff:**
*   Design the startup optimization strategy for a Spring Boot service with 500+ beans and a 30-second startup time. How would you identify bottlenecks, what changes would you make, and how would you measure improvement without breaking existing functionality?
*   Explain the difference between `BeanFactoryPostProcessor` and `BeanDefinitionRegistryPostProcessor`. When would you use each? What are the risks of calling `getBean()` inside either?

**Principal:**
*   You're designing a multi-tenant SaaS platform where each tenant gets an isolated Spring `ApplicationContext` with tenant-specific beans, sharing some common parent context beans. How would you architect this? What are the memory implications? How do you handle tenant lifecycle (onboarding, teardown) without memory leaks?

---

## 14. Hands-on Exercise

**Task:** Build a custom `BeanPostProcessor` for the Payment Service that detects all beans annotated with `@Auditable`, wraps them in a proxy that records method entry/exit timing, and does NOT break existing `@Transactional` proxies.

**Expected Solution:**

```java
package com.finflow.chapter030.exercise;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import java.lang.reflect.Proxy;

@Component
public class AuditableBeanPostProcessor implements BeanPostProcessor, Ordered {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean.getClass().isAnnotationPresent(com.finflow.chapter030.common.Auditable.class)) {
            // Create a JDK dynamic proxy to intercept calls
            return Proxy.newProxyInstance(
                bean.getClass().getClassLoader(),
                bean.getClass().getInterfaces(),
                (proxy, method, args) -> {
                    long start = System.currentTimeMillis();
                    try {
                        return method.invoke(bean, args);
                    } finally {
                        long duration = System.currentTimeMillis() - start;
                        System.out.println("Audit [" + method.getName() + "] took " + duration + "ms");
                    }
                }
            );
        }
        return bean;
    }

    @Override
    public int getOrder() {
        // Return LOWEST_PRECEDENCE so this runs AFTER Spring's AOP auto-proxies (like @Transactional)
        // This ensures we wrap the proxy, not the other way around.
        return Ordered.LOWEST_PRECEDENCE; 
    }
}
```

---

## 15. Advanced Challenge

**Design and implement a "bean startup profiler".**

Your task is to hook into Spring's bean creation pipeline via a highly prioritized `BeanPostProcessor`. You must measure instantiation time (time before properties are set) vs initialization time (time spent in `@PostConstruct`). 

*Requirements:*
1.  Store timing data in an internal `ConcurrentHashMap`.
2.  Identify the critical path (the longest continuous dependency chain blocking startup).
3.  Implement a `ContextRefreshedEvent` listener to output a sorted startup report.
4.  Generate a Mermaid diagram string output showing the dependency graph with timing annotations.

*Hint:* You will need to implement both `InstantiationAwareBeanPostProcessor` and `BeanPostProcessor` to capture the granular lifecycle timestamps.

---

## 16. Production Checklist

- [ ] No `getBean()` calls inside `BeanFactoryPostProcessor` implementations.
- [ ] `spring.main.allow-circular-references=false` (default, not overridden).
- [ ] All circular dependencies resolved by design (extract shared logic into new components).
- [ ] Heavy `@PostConstruct` methods moved to lazy initialization or async startup routines.
- [ ] Startup time measured and baselined in CI/CD pipeline; alert if > N seconds.
- [ ] `@Lazy` applied to beans not needed at startup (e.g., report generators, batch processors).
- [ ] Spring Boot startup actuator enabled for continuous startup profiling.
- [ ] ApplicationContext shutdown hooks registered for graceful cleanup of resources.
- [ ] No `ApplicationContext.getBean()` usage scattered in application business logic (avoiding the Service Locator anti-pattern).
- [ ] Parent-child context hierarchy strictly understood and documented if Spring Cloud is used in the workspace.
