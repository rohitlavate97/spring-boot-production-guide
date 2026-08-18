---
chapter: 40
topic: Bean Lifecycle & Scopes — Initialization, Destruction, Singleton, Prototype, Request, Session
prerequisite_chapters: [30]
reference_system_node: Payment Service (bean lifecycle, scope management under concurrent traffic)
---

# Chapter 040: Bean Lifecycle & Scopes — Initialization, Destruction, Singleton, Prototype, Request, Session

## 1. Concept

In a Spring Boot application, the IoC (Inversion of Control) container manages the creation, configuration, and destruction of application objects, known as beans. While Chapter 030 explored *how* the container creates beans (the `BeanFactory` and `BeanDefinition` machinery), this chapter focuses on the journey a bean takes once the container decides to instantiate it. This journey is the **Bean Lifecycle**, and the container's strategy for managing the bean's visibility, sharing, and destruction is defined by its **Scope**.

Production engineers must understand these concepts deeply because mismanaging them leads to severe operational anomalies: resource leaks, race conditions under high concurrency, threading issues in asynchronous processing, and ungraceful shutdowns during Kubernetes pod eviction.

### The Bean Lifecycle

The lifecycle of a Spring bean is a precisely ordered sequence of events. Understanding this sequence is non-negotiable when implementing initialization logic or acquiring system resources.

1.  **Instantiation:** The container invokes the constructor (via reflection or CGLIB proxying) to create the object instance.
2.  **Population:** Dependency injection occurs. The container injects other beans into `@Autowired` fields or setter methods.
3.  **Aware Callbacks:** If the bean implements `Aware` interfaces, the container injects infrastructure dependencies:
    *   `BeanNameAware.setBeanName()`
    *   `BeanClassLoaderAware.setBeanClassLoader()`
    *   `BeanFactoryAware.setBeanFactory()`
    *   `ApplicationContextAware.setApplicationContext()`
4.  **Pre-Initialization Post-Processing:** `BeanPostProcessor.postProcessBeforeInitialization()` executes. Crucially, the `CommonAnnotationBeanPostProcessor` triggers methods annotated with `@PostConstruct`.
5.  **Initialization:** The bean's formal initialization logic executes:
    *   `InitializingBean.afterPropertiesSet()` (if implemented).
    *   Custom `init-method` declared in XML or `@Bean(initMethod = "...")`.
6.  **Post-Initialization Post-Processing:** `BeanPostProcessor.postProcessAfterInitialization()` executes. This is the critical phase where AOP proxies (for `@Transactional`, `@Async`, `@Cacheable`, etc.) are generated to wrap the target bean instance.
7.  **Ready for Use:** The bean is fully assembled and resides in the container, ready to serve application traffic.
8.  **Destruction:** Upon application shutdown (or when a scoped context ends), destruction callbacks fire:
    *   Methods annotated with `@PreDestroy` execute (via `CommonAnnotationBeanPostProcessor`).
    *   `DisposableBean.destroy()` executes.
    *   Custom `destroy-method` executes.

### Bean Scopes

Scope determines the lifespan and visibility of a bean instance. Spring supports several core scopes, and custom scopes can be registered.

*   **Singleton (default):** One shared instance per `ApplicationContext`. This is stateless or thread-safe stateful infrastructure. Over 95% (illustrative) of beans in a typical Spring Boot application are singletons.
*   **Prototype:** A new instance is created every time the bean is requested via `getBean()` or injected into another bean. **Crucial Production Fact:** Spring *does not* manage the full lifecycle of a prototype bean. The container instantiates, configures, and hands over the prototype to the client, but it does *not* track it. Therefore, `@PreDestroy` is *never* called by the container for prototype beans.
*   **Request (`@RequestScope`):** One instance per HTTP request. To allow injection into longer-lived singleton beans, Spring uses a CGLIB scoped proxy.
*   **Session (`@SessionScope`):** One instance per HTTP session.
*   **Application (`@ApplicationScope`):** One instance per `ServletContext`. While similar to a singleton, it differs subtly when dealing with hierarchical `ApplicationContext` architectures.
*   **WebSocket:** One instance per WebSocket session.
*   **Custom Scopes:** Architectures can define proprietary scopes, such as `TenantScope` for multi-tenant SaaS isolation, or `ThreadScope` for batch processing.

### Why This Matters in Production

Lack of strict scope discipline results in catastrophic failure modes under load:
*   **Resource Leaks:** Using `prototype` scope for beans that acquire network sockets, file handles, or off-heap memory without understanding that `@PreDestroy` won't fire leads to immediate resource exhaustion.
*   **Stale State (The "Singleton-Prototype" Problem):** Injecting a prototype bean into a singleton bean means the injection happens exactly once. The singleton holds a stale reference to a single prototype instance, destroying the intended per-invocation isolation and introducing severe thread-safety bugs.
*   **Scope Violations:** Misusing `@RequestScope` without grasping the proxy mechanism causes runtime exceptions (`IllegalStateException`) when the bean is accessed outside a web thread (e.g., in `@Async` methods, `@Scheduled` tasks, or Kafka consumers).
*   **Incomplete Cleanup:** If `@PreDestroy` logic takes longer than the orchestration platform's termination grace period (e.g., Kubernetes `kill -9` after `SIGTERM` timeout), state corruption or lost data ensues.

## 2. Internal Working

To debug lifecycle issues, engineers must understand the Spring framework's internal choreography.

### Lifecycle Callback Execution Order

The core orchestration happens within `AbstractAutowireCapableBeanFactory.initializeBean()`.

```text
+---------------------------------------------------+
|               Bean Instantiation                  |
| (Constructor / Factory Method execution)          |
+---------------------------------------------------+
                         |
+---------------------------------------------------+
|               Dependency Injection                |
| (Field/Setter @Autowired population)              |
+---------------------------------------------------+
                         |
+---------------------------------------------------+
|              invokeAwareMethods()                 |
| - BeanNameAware, BeanClassLoaderAware,            |
| - BeanFactoryAware                                |
+---------------------------------------------------+
                         |
+---------------------------------------------------+
| applyBeanPostProcessorsBeforeInitialization()     |
| - Execution of all registered BeanPostProcessors  |
| - CommonAnnotationBeanPostProcessor invokes       |
|   @PostConstruct methods                          |
+---------------------------------------------------+
                         |
+---------------------------------------------------+
|               invokeInitMethods()                 |
| - InitializingBean.afterPropertiesSet()           |
| - Custom init-method                              |
+---------------------------------------------------+
                         |
+---------------------------------------------------+
| applyBeanPostProcessorsAfterInitialization()      |
| - AbstractAutoProxyCreator generates AOP proxies  |
|   (CGLIB/JDK Dynamic) if advice is required       |
+---------------------------------------------------+
                         |
                 READY FOR USE
                         |
+---------------------------------------------------+
|               Container Shutdown                  |
| - ContextClosedEvent published                    |
| - @PreDestroy invoked                             |
| - DisposableBean.destroy() invoked                |
+---------------------------------------------------+
```

### Scope Implementation Internals

#### Singleton Tracking
Singletons are managed in `DefaultSingletonBeanRegistry`. The cache is a `ConcurrentHashMap` (`singletonObjects`). Bean creation involves double-checked locking using a dedicated monitor object (`singletonLock`). When a singleton is requested, the container simply returns the cached instance. At shutdown, `destroySingletons()` iterates over tracked beans and invokes destruction callbacks in reverse dependency order.

#### Prototype Mechanism
For prototypes, `AbstractBeanFactory.doGetBean()` identifies the scope and invokes `createBean()` *every single time*. The container applies all pre-initialization and initialization logic, but critically, it never adds the bean to any destruction registry. The reference is yielded to the caller, and it becomes the caller's (and eventually the Garbage Collector's) responsibility.

#### Web Scopes and CGLIB Proxies
Request and session scopes are implemented via the `org.springframework.web.context.request.RequestScope` and `SessionScope` classes. These delegate to thread-local storage managed by `RequestContextHolder`.

When a request-scoped bean is injected into a singleton service, Spring cannot inject the actual bean instance because the actual instance doesn't exist yet (and will change per request). Instead, Spring injects a **CGLIB proxy** configured with `ScopedProxyMode.TARGET_CLASS`.

When the singleton invokes a method on this proxy:
1. The proxy intercepts the call.
2. It queries `RequestContextHolder.currentRequestAttributes()` (which reads a `ThreadLocal`).
3. It fetches the actual request-scoped target bean from the current HTTP request context.
4. It delegates the method execution to the target bean.

If `RequestContextHolder` is empty (e.g., the thread is an `@Async` worker pool thread, not a Tomcat HTTP thread), the proxy throws an `IllegalStateException`.

### Graceful Shutdown in Spring Boot and Kubernetes

When a Spring Boot application starts, `SpringApplication` registers a JVM shutdown hook via `Runtime.getRuntime().addShutdownHook()`.

When Kubernetes scales down a pod or updates a deployment:
1. Kubernetes sends a `SIGTERM` signal to the pod's PID 1.
2. The JVM shutdown hook intercepts the `SIGTERM`.
3. `ApplicationContext.close()` is invoked.
4. The context publishes `ContextClosedEvent`.
5. The bean factory executes `destroySingletons()`, calling `@PreDestroy`.

Kubernetes waits for a configured `terminationGracePeriodSeconds` (default: 30s). If the JVM process is still running after this timer expires, Kubernetes sends a `SIGKILL` (`kill -9`), which abruptly terminates the process. If a bean's `@PreDestroy` method is blocked (e.g., flushing large data volumes to a slow Redis instance), the SIGKILL will terminate it mid-execution, leaving the system in an inconsistent state.

## 3. Enterprise Scenario

The FinFlow Payment Platform manages ~4,000 req/sec peak traffic (illustrative). The platform utilizes a microservice architecture where the API Gateway routes requests to the Payment Service, which subsequently coordinates with internal persistence layers (PostgreSQL, Redis, Kafka) and external banking gateways.

Recent architectural changes introduced severe instability into the Payment Service due to fundamentally flawed assumptions regarding bean lifecycle and scopes:

1.  **Resource Leak via Prototype:** To isolate distinct tenant traffic headers when communicating with an external legacy banking gateway, developers encapsulated the TCP socket logic within a `GatewayConnection` bean. They marked it `@Scope("prototype")` to ensure a fresh socket per invocation. They annotated the socket closure logic with `@PreDestroy`, assuming the container would close the socket when the connection object was garbage collected or the transaction ended.
2.  **Stale Prototype Injection:** To utilize the new `GatewayConnection`, a singleton `PaymentProcessor` service injected the prototype bean via an `@Autowired` field. The developers expected a fresh connection on every request to the processor. Instead, all 4,000 RPS began funneling through a single shared connection instance.
3.  **Request-Scope Violation in Async:** To improve latencies, audit logging was moved off the main HTTP thread using `@Async`. The `AuditService` relied on an injected `@RequestScope` bean named `AuditContext` to extract the current User ID and Trace ID. The async threads immediately began failing.
4.  **Incomplete Shutdown / Data Loss:** During batch processing, an in-memory queue of `PaymentIntent` objects was maintained. A `@PreDestroy` method flushed these to Redis on shutdown. However, iterating the collection synchronously during high-volume periods took up to 45 seconds. Kubernetes `SIGKILL` intervened at 30 seconds, leading to lost financial transactions during every rolling update.

## 4. Incorrect Implementation

The following complete, compilable Java code demonstrates the fatal flaws introduced into the FinFlow Payment Service.

**Bug 1 — Prototype Scope Resource Leak:**

```java
package com.finflow.chapter040.gateway;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.Socket;

@Component
@Scope("prototype")
public class GatewayConnection {
    private Socket socket;
    
    @PostConstruct
    public void init() throws IOException {
        // Establishes a raw TCP connection
        this.socket = new Socket("payment-gateway.finflow.internal", 8443);
    }
    
    public String send(String payload) {
        // Implementation omitted for brevity
        return "SUCCESS";
    }
    
    @PreDestroy  // BUG: This is NEVER called by the Spring Container for prototype beans!
    public void close() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Ignored
        }
    }
}
```

**Bug 2 — Stale Prototype Injected into Singleton:**

```java
package com.finflow.chapter040.processor;

import com.finflow.chapter040.gateway.GatewayConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PaymentProcessor {
    
    // BUG: The singleton processor is created once. 
    // The prototype connection is resolved ONCE at startup.
    // The same connection instance is used for all concurrent requests!
    @Autowired
    private GatewayConnection connection; 
    
    public String processPayment(String requestStr) {
        // Thread-safety catastrophe under 4,000 RPS
        return connection.send(requestStr); 
    }
}
```

**Bug 3 — Request-scoped bean accessed from @Async thread:**

```java
package com.finflow.chapter040.audit;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class AuditContext {
    private String userId;
    private String requestId;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}
```

```java
package com.finflow.chapter040.audit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    
    @Autowired
    private AuditContext auditContext; // Injected as a CGLIB Scoped Proxy
    
    @Async
    public void writeAuditLogAsync(String action) {
        // BUG: Executed by a thread in the TaskExecutor pool.
        // No HttpServletRequest is bound to this thread.
        // Calling getUserId() invokes the proxy, which fails to find the target.
        String userId = auditContext.getUserId(); // Throws IllegalStateException
        System.out.println("Audit: " + action + " by " + userId);
    }
}
```

**Bug 4 — Slow @PreDestroy exceeding K8s grace period:**

```java
package com.finflow.chapter040.batch;

import jakarta.annotation.PreDestroy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class PaymentBatchService {
    
    private final List<String> inFlightPayments = new CopyOnWriteArrayList<>();
    private final StringRedisTemplate redisTemplate;

    public PaymentBatchService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void addPayment(String paymentId) {
        inFlightPayments.add(paymentId);
    }
    
    @PreDestroy
    public void flushInFlight() {
        // BUG: Processing 500 items synchronously takes ~50 seconds.
        // K8s terminationGracePeriodSeconds is 30s. Process gets SIGKILLed.
        for (String paymentId : inFlightPayments) {
            try {
                redisTemplate.opsForValue().set("inflight:" + paymentId, "PENDING");
                Thread.sleep(100); // Simulating slow network/Redis I/O under load
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
```

## 5. Production Incident

The integration of the aforementioned code resulted in a catastrophic P1 incident.

*   **Day 1 (Tuesday):** A new deployment rolls out the legacy banking integration containing the `GatewayConnection` prototype. It operates normally in staging under negligible load.
*   **Day 2, 10:00 UTC:** Production traffic ramps up to 4,000 req/sec (illustrative). Because the `PaymentProcessor` is a singleton holding a stale reference to a single `GatewayConnection`, all traffic is funneled through one socket. The socket becomes a massive bottleneck.
*   **Day 2, 10:30 UTC:** To bypass the bottleneck in certain specific fallback retry flows, developers added a new code path that dynamically fetched connections using `applicationContext.getBean(GatewayConnection.class)`. This code path executed roughly 50 times per second. 
*   **Day 2, 14:00 UTC:** Monitoring alerts fire: OS-level open file descriptors for the pod are approaching the `ulimit` (65,536). TCP connections per pod rise from a baseline of ~50 to over 12,000 (illustrative). The prototype leak is silently exhausting the node.
*   **Day 2, 14:05 UTC:** The first pod throws `java.net.SocketException: Too many open files`. Payment failures surge.
*   **Day 2, 14:10 UTC:** Audit logging drops simultaneously. The async audit queues are draining, but the logs are entirely populated with stack traces complaining about missing thread-bound requests.
*   **Day 2, 14:15 UTC:** Panicking SREs attempt an emergency restart of the deployment to clear the exhausted file descriptors. During the rollout, the `PaymentBatchService.@PreDestroy` begins synchronously flushing thousands of pending intents to Redis. 
*   **Day 2, 14:15:30 UTC:** Kubernetes hits the 30-second `terminationGracePeriodSeconds` limit. It dispatches a `SIGKILL` to the running JVMs. The `flushInFlight` loops are abruptly terminated. Roughly 15 in-flight payment states per pod are orphaned in memory and lost.
*   **Day 2, 15:00 UTC:** P1 incident formally declared. Platform degraded for 3 hours, requiring manual ledger reconciliation for lost transactions.

## 6. Logs

During the incident, the observability platform captured the following critical signatures:

**Signature 1: File Descriptor Exhaustion (The Prototype Leak)**
```log
2026-08-18T14:05:12.112Z ERROR [http-nio-8080-exec-114] c.f.c.g.GatewayConnection : Failed to establish connection
java.net.SocketException: Too many open files
    at java.base/sun.nio.ch.Net.socket0(Native Method)
    at java.base/sun.nio.ch.Net.socket(Net.java:571)
```
*Note: There is absolutely no warning from Spring regarding failed destruction, because Spring does not track prototypes.*

**Signature 2: Async Proxy Failure (The Request Scope Violation)**
```log
2026-08-18T14:10:44.881Z ERROR [task-executor-3] c.f.c.a.AuditService : Failed to write async audit log
java.lang.IllegalStateException: No thread-bound request found: Are you referring to request attributes outside of an actual web request, or processing a request outside of the originally receiving thread? If you are actually operating within a web request and still receive this message, your code is probably running outside of DispatcherServlet: In this case, use RequestContextListener or RequestContextFilter to expose the current request.
    at org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes(RequestContextHolder.java:131)
```

**Signature 3: Ungraceful Shutdown (The Slow @PreDestroy)**
```log
2026-08-18T14:15:10.001Z INFO  [SpringContextShutdownHook] o.s.c.s.AbstractApplicationContext : Closing org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext
2026-08-18T14:15:10.122Z INFO  [SpringContextShutdownHook] c.f.c.b.PaymentBatchService : Flushing in-flight payments to Redis...
# ... log goes completely silent ...
# K8s Event Log:
Warning  Unhealthy  Pod/payment-service-84f9b8c  Container payment-service failed liveness probe, will be restarted
Normal   Killing    Pod/payment-service-84f9b8c  Stopping container payment-service
Warning  FailedKill Pod/payment-service-84f9b8c  Pod was killed with signal SIGKILL after grace period
```

## 7. Root Cause Analysis

Understanding the exact mechanism is critical.

### Prototype Lifecycle and the Resource Leak
Why doesn't `@PreDestroy` run for prototypes? When a bean is defined as a singleton, `AbstractBeanFactory` registers a `DisposableBeanAdapter` inside the `DefaultSingletonBeanRegistry` for that instance. This adapter maintains the list of destruction callbacks. For prototypes, `AbstractBeanFactory.createBean()` instantiates the object, runs `postProcessBeforeInitialization` (which handles `@PostConstruct`), runs `postProcessAfterInitialization`, and then *simply returns the reference*. The container discards all knowledge of the instance. Because no reference is held in a destruction registry, when the JVM shuts down, there is nothing telling Spring to call `close()` on those millions of orphaned `GatewayConnection` objects.

### The Stale Prototype Injection
Why does `@Autowired GatewayConnection` inside a singleton yield a single connection? Dependency injection happens during the *population* phase of the singleton's lifecycle. When Spring creates `PaymentProcessor`, it sees the requirement for a `GatewayConnection`. It requests one from the container. The container sees it's a prototype, creates a brand new instance, and injects the reference into the singleton's field. The singleton lifecycle is now complete. The processor never asks for another connection, so the prototype is effectively "promoted" to singleton lifespan, causing severe bottlenecking.

### The Request Scope Proxy Failure
Why does the proxy fail in `@Async`? `RequestContextHolder` stores the HTTP request state in a `ThreadLocal<RequestAttributes>`. When an HTTP request enters Tomcat, Spring's `DispatcherServlet` (or `RequestContextFilter`) binds the request to the current Tomcat worker thread. When the singleton `AuditService` calls an `@Async` method, the method execution is dispatched to a completely different thread residing in a `ThreadPoolTaskExecutor`. The proxy inside the async thread attempts to read its `ThreadLocal`, finds `null`, and throws `IllegalStateException`.

### Synchronous Shutdown Blocking
Why did pods lose data? When `SpringApplication` catches the `SIGTERM`, it calls `AbstractApplicationContext.close()`. This method delegates to `destroyBeans()`, which iterates over the `DefaultSingletonBeanRegistry` and calls the `DisposableBeanAdapter` for every bean synchronously, on the shutdown hook thread. There is no inherent timeout applied to `@PreDestroy` methods. The thread simply blocks on the Redis I/O loop. Meanwhile, the orchestration platform's clock is ticking. At exactly 30 seconds, the kernel delivers `SIGKILL`, terminating the process instantly. Memory is dumped. Execution halts mid-loop.

## 8. Debugging Process

An engineer on-call would follow this deterministic debugging path:

1.  **Alert Triage:** Page received indicating payment failure rates > 1%.
2.  **Log Analysis:** Kibana shows `SocketException: Too many open files`.
3.  **OS Profiling:** Executing `lsof -p <PID> | wc -l` on the container yields 12,000 open descriptors. Running `lsof -p <PID> | grep payment-gateway` confirms the vast majority are active TCP sockets to the internal gateway.
4.  **Code Correlation (Leak):** Searching the codebase for `"payment-gateway.finflow.internal"` points to `GatewayConnection`. The `@Scope("prototype")` annotation is immediately suspicious. Reviewing Spring documentation confirms `@PreDestroy` is a no-op here.
5.  **Code Correlation (Bottleneck):** To understand why performance degraded before the leak killed the pod, inspect usage of `GatewayConnection`. Finding it injected directly into the singleton `PaymentProcessor` explains the thread-safety issues and lock contention.
6.  **Async Thread Dump Analysis:** Reviewing the `IllegalStateException` stack traces shows execution occurring on `task-executor-X` threads instead of `http-nio-8080-exec-X`. This confirms the `ThreadLocal` context propagation failure.
7.  **Shutdown Event Review:** Checking Kubernetes events via `kubectl describe pod` reveals the `SIGKILL` after the grace period. Reviewing the application logs right before termination reveals the system was stuck in `PaymentBatchService.flushInFlight()`.

## 9. Correct Implementation

The following complete, compilable code resolves all four structural defects.

**Fix 1 & 2 — Connection Pooling over Prototypes (Best Approach):**
Prototypes should rarely hold network resources. Instead of generating thousands of uncontrolled TCP connections, we replace the prototype with a pooled singleton.

```java
package com.finflow.chapter040.gateway;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.stereotype.Component;

@Component
public class GatewayClientPool {

    private ObjectPool<GatewayConnectionAdapter> pool;

    @PostConstruct
    public void init() {
        GenericObjectPoolConfig<GatewayConnectionAdapter> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(50); // Bounded connection pool
        config.setMaxIdle(10);
        this.pool = new GenericObjectPool<>(new GatewayConnectionFactory(), config);
    }

    public String executeSafely(String payload) throws Exception {
        GatewayConnectionAdapter adapter = pool.borrowObject();
        try {
            return adapter.send(payload);
        } finally {
            pool.returnObject(adapter); // Safely returns to pool, no leak
        }
    }

    @PreDestroy
    public void closePool() {
        if (pool != null) {
            pool.close(); // Now safe, since this is a Singleton!
        }
    }
}
```

*Alternative for Fix 2 (If Prototype MUST be used):* Use `ObjectProvider` to fetch a fresh instance upon every invocation.
```java
package com.finflow.chapter040.processor;

import com.finflow.chapter040.gateway.GatewayConnection;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PaymentProcessor {

    @Autowired
    private ObjectProvider<GatewayConnection> connectionProvider; 
    
    public String processPayment(String requestStr) {
        // Fetches a fresh prototype instance. STILL LEAKS if connection isn't closed manually!
        GatewayConnection conn = connectionProvider.getObject(); 
        try {
            return conn.send(requestStr);
        } finally {
            conn.close(); // Must explicitly close if using prototype for resources
        }
    }
}
```

**Fix 3 — Request Context Propagation via Snapshot DTO:**
Do not access request-scoped proxies from async threads. Capture the data on the main thread and pass it as a parameter.

```java
package com.finflow.chapter040.audit;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    
    // Instead of injecting AuditContext directly, define a static snapshot DTO
    public record AuditSnapshot(String userId, String requestId) {}
    
    @Async
    public void writeAuditLogAsync(AuditSnapshot snapshot, String action) {
        // Safe! Operating purely on the passed DTO, no proxy invocations.
        System.out.println("Audit: " + action + " by " + snapshot.userId());
    }
}

// Caller implementation:
// @Service
// public class PaymentProcessor {
//     @Autowired private AuditContext context;
//     @Autowired private AuditService auditService;
//     
//     public void doWork() {
//         // Extract state while on the HTTP thread
//         AuditSnapshot snapshot = new AuditSnapshot(context.getUserId(), context.getRequestId());
//         // Pass explicit state
//         auditService.writeAuditLogAsync(snapshot, "PAYMENT_PROCESSED");
//     }
// }
```

**Fix 4 — Bounded Shutdown with Graceful Degradation:**
To ensure K8s doesn't `SIGKILL` the process mid-flight, limit the time spent in `@PreDestroy` by utilizing Spring's native lifecycle timeout property and explicitly managing deadlines.

In `application.properties`:
```properties
# Give Spring 20 seconds to shutdown components, leaving 10s buffer for the 30s K8s grace period
spring.lifecycle.timeout-per-shutdown-phase=20s
```

```java
package com.finflow.chapter040.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class PaymentBatchService implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(PaymentBatchService.class);
    private final List<String> inFlightPayments = new CopyOnWriteArrayList<>();
    private final StringRedisTemplate redisTemplate;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public PaymentBatchService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void start() {
        running.set(true);
    }

    @Override
    public void stop() {
        // Gracefully handle destruction with a bounded time limit
        log.info("Starting safe shutdown of PaymentBatchService");
        long deadline = System.currentTimeMillis() + 15000; // Hard 15s deadline
        
        for (String paymentId : inFlightPayments) {
            if (System.currentTimeMillis() > deadline) {
                log.error("Shutdown deadline exceeded! Remaining payments written to emergency local log.");
                emergencyLocalFileDump(paymentId);
                continue;
            }
            try {
                redisTemplate.opsForValue().set("inflight:" + paymentId, "PENDING");
            } catch (Exception e) {
                log.error("Redis write failed during shutdown", e);
            }
        }
        running.set(false);
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        // High priority component, shuts down LATE in the process.
        // Smaller phase values shut down FIRST. Integer.MAX_VALUE shuts down very early.
        return 100; 
    }
    
    private void emergencyLocalFileDump(String paymentId) {
        // Implementation to write to a persistent PVC
    }
}
```

## 10. Performance Comparison

Implementing strict scope control and bounded shutdown yielded dramatic stability improvements under load.

| Metric | Before | After |
| :--- | :--- | :--- |
| Open TCP connections per pod | 12,000 (leaking) (illustrative) | ~50 (pooled) (illustrative) |
| Connection creation per request | 1 new connection | Pool reuse (~0.05 new/sec) (illustrative) |
| Memory per pod (connections) | ~480MB (leaked objects) (illustrative) | ~20MB (illustrative) |
| Prototype GC churn | ~40MB/min (illustrative) | 0 (pooled) |
| Shutdown duration | 45s (killed at 30s) | 16s (clean exit) (illustrative) |
| Lost payments per restart | ~15 (illustrative) | 0 |
| Async audit failures/hour | ~800 (illustrative) | 0 |

## 11. Best Practices

*   **Avoid Resource-Heavy Prototypes:** Prototype beans should **not** hold OS resources (sockets, file handles, native memory). If they absolutely must, they must implement `AutoCloseable`, and the caller is strictly responsible for `try-with-resources` cleanup.
*   **Use `ObjectProvider` for Fresh Injection:** Never inject a prototype directly into a singleton field expecting fresh instances. Use `ObjectProvider<T>` or `@Lookup` method injection.
*   **DTO Context Propagation:** Never access `@RequestScope` or `@SessionScope` beans from non-request threads (Kafka listeners, `@Scheduled`, `@Async`). Extract required data into immutable DTO snapshots on the main thread and pass them to asynchronous boundaries.
*   **Bounded PreDestroy Timers:** Configure `spring.lifecycle.timeout-per-shutdown-phase` to a value strictly lower than your orchestration environment's `terminationGracePeriodSeconds` minus a 5-second buffer.
*   **Prefer `SmartLifecycle` for Complex Shutdowns:** Instead of relying on arbitrary `@PreDestroy` execution order, implement `SmartLifecycle` and use `getPhase()` to strictly sequence component termination (e.g., stop accepting API requests, then drain queues, then sever database connections).
*   **Default to Singleton:** Prefer singleton scope for almost all service-layer and repository beans unless statefulness dictates otherwise.

## 12. Common Mistakes

*   Assuming `@PreDestroy` runs for prototype beans (it does not, under any circumstances).
*   Injecting a prototype into a singleton and expecting `getBean()` behavior upon every method invocation.
*   Believing `ApplicationContext.close()` enforces time limits automatically; it performs blocking iteration without timeouts unless specifically using `SmartLifecycle` phase timeouts.
*   Testing graceful shutdown scenarios with arbitrary `Thread.sleep()` in integration tests without verifying actual JVM signaling limits.
*   Using `@Scope("prototype")` for HTTP or DB clients instead of utilizing proper connection pooling.

## 13. Interview Questions

**Junior Tier:**
*   What is the default bean scope in Spring, and what does it signify regarding memory usage?
*   What is the difference between `@PostConstruct` and implementing `InitializingBean`?

**Mid Tier:**
*   Explain the complete Spring bean lifecycle from instantiation to destruction, naming the primary callback interfaces.
*   What exact architectural flaw occurs when you inject a prototype-scoped bean into a singleton service? How do you fix it using modern Spring patterns?

**Senior Tier:**
*   Detail how `@RequestScope` beans function internally utilizing CGLIB proxies and `RequestContextHolder`. Why does attempting to invoke a method on a request-scoped proxy from a `@KafkaListener` method result in an `IllegalStateException`?
*   Design a custom bean scope (`TenantScope`) for a multi-tenant SaaS application where each tenant identity gets its own discrete bean instance.

**Staff Tier:**
*   A Spring Boot service handling 4,000 req/sec utilizes a prototype-scoped bean that inadvertently creates a new JDBC connection per invocation, bypassing HikariCP. How would you detect this failure mode in a production environment using metrics, log patterns, and OS-level tooling? How would you implement a zero-downtime hotfix?
*   Architect the graceful shutdown sequencing for an orchestration layer comprising in-flight HTTP requests, active Kafka consumer loops, a Quartz scheduler, and a distributed Redis lock manager. What specific order must components terminate, and how do you enforce this using Spring infrastructure?

**Principal Tier:**
*   You are architecting a massive multi-tenant banking platform where each tenant requires aggressively isolated Spring beans (separate cryptographic configurations, separate downstream connection pools) alongside shared generic infrastructure. Detail the implementation of a proprietary Tenant Scope utilizing Spring's `Scope` interface, custom `BeanFactoryPostProcessors`, and hardened `ThreadLocal` context propagation. Enumerate the precise memory leak risks, GC pressure implications, and cross-tenant pollution vulnerabilities inherent in this design.

## 14. Hands-on Exercise

**Task:** Implement a custom `ThreadScope` for the Payment Service that guarantees thread-local isolation for batch processing beans, preventing context bleeding between parallel worker threads.

1.  Create a `ThreadScope` class implementing `org.springframework.beans.factory.config.Scope`.
2.  Register it with the `BeanFactory` via a `CustomScopeConfigurer` or `BeanFactoryPostProcessor`.
3.  Ensure robust cleanup when threads are yielded back to the executor pool to prevent memory leaks.

**Solution Skeleton:**
```java
package com.finflow.chapter040.scope;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.core.NamedThreadLocal;

import java.util.HashMap;
import java.util.Map;

public class ThreadScope implements Scope {

    private final ThreadLocal<Map<String, Object>> threadScope =
            new NamedThreadLocal<Map<String, Object>>("ThreadScope") {
                @Override
                protected Map<String, Object> initialValue() {
                    return new HashMap<>();
                }
            };

    @Override
    public Object get(String name, ObjectFactory<?> objectFactory) {
        Map<String, Object> scope = threadScope.get();
        Object scopedObject = scope.get(name);
        if (scopedObject == null) {
            scopedObject = objectFactory.getObject();
            scope.put(name, scopedObject);
        }
        return scopedObject;
    }

    @Override
    public Object remove(String name) {
        Map<String, Object> scope = threadScope.get();
        return scope.remove(name);
    }

    @Override
    public void registerDestructionCallback(String name, Runnable callback) {
        // Implementation must track callbacks for when thread context is cleared
    }

    @Override
    public Object resolveContextualObject(String key) {
        return null;
    }

    @Override
    public String getConversationId() {
        return Thread.currentThread().getName();
    }
    
    // Crucial: Must be called before returning thread to pool
    public void clear() {
        threadScope.remove(); 
    }
}
```

## 15. Advanced Challenge

**Design and implement a production-grade graceful shutdown framework:**
Construct a custom `ShutdownOrchestrator` utilizing Spring's `SmartLifecycle` that explicitly dictates the termination cascade:
*   **Phase 1:** Deregister the instance from the Eureka Service Registry and purposefully fail Kubernetes readiness probes (giving K8s time to update endpoints).
*   **Phase 2:** Wait for all currently executing HTTP requests in Tomcat to flush.
*   **Phase 3:** Synchronize any in-memory application caches with the Redis cluster.
*   **Phase 4:** Terminate the Kafka Consumer loops, allowing them to commit current offsets.
*   **Phase 5:** Sever infrastructure connections (HikariCP, Redis Lettuce client).
*   **Constraint:** Implement a unified `AtomicBoolean` cancelation token and verify that the cumulative execution time strictly obeys a defined 20-second threshold. 

## 16. Production Checklist

- [ ] Prototype beans holding physical resources (sockets, file handles, native allocations) have been refactored to pooled singletons or strictly governed factories.
- [ ] Every prototype injection into a singleton uses `ObjectProvider<T>` or `@Lookup` rather than direct field autowiring.
- [ ] No `@RequestScope` or `@SessionScope` beans are injected into or accessed by `@Async`, `@Scheduled`, or Kafka consumer threads.
- [ ] The `spring.lifecycle.timeout-per-shutdown-phase` property is explicitly defined and is at least 5 seconds shorter than the Kubernetes `terminationGracePeriodSeconds`.
- [ ] All significant `@PreDestroy` methods have bounds limits or timeouts implemented internally.
- [ ] `SmartLifecycle` is utilized for mission-critical stateful components to enforce precise termination ordering via `getPhase()`.
- [ ] Graceful shutdown flows are validated under simulated `SIGTERM` load in integration testing.
- [ ] File descriptor monitoring (`lsof` counts) and TCP connection states are integrated into the primary Grafana observability dashboards.
- [ ] The architectural justification for utilizing any prototype or custom-scoped bean has been formally documented.
- [ ] Custom scope implementations completely fulfill the `registerDestructionCallback()` contract and clear `ThreadLocal` allocations.
