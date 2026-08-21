---
chapter: 360
topic: Spring Cloud Config & Service Discovery — Config Server, Eureka, Client-Side Load Balancing
prerequisite_chapters: [10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200, 210, 220, 230, 240, 250, 260, 270, 280, 290, 300, 310, 320, 330, 340, 350]
reference_system_node: Spring Cloud Config Server & Eureka Service Discovery Registry ↔ [Payment Service | Order Service | Ledger Service] with Spring Cloud LoadBalancer
---

# Chapter 360: Spring Cloud Config & Service Discovery — Config Server, Eureka, Client-Side Load Balancing

## 1. Concept

In monolithic applications, configuration properties are baked directly into `application.properties` or environment variables, and inter-module communication occurs via in-memory method invocations. 

In a distributed microservice architecture with hundreds of containerized pods across dynamic clusters, two fundamental problems arise:
1. **Configuration Drift & Downtime:** When a database password rotates, a third-party fee changes, or a feature flag flips, restarting 100 pods causes service disruption and JVM JIT warm-up penalties. Furthermore, packaging credentials inside container images violates **The Twelve-Factor App** configuration principles.
2. **Ephemeral Network Topology:** In Kubernetes and cloud environments, pods scale up, crash, and relocate dynamically. Hardcoding static IP addresses or port numbers in client applications is impossible.

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                        Enterprise Configuration & Discovery Ecosystem                           │
│                                                                                                 │
│  [Centralized Config Server] ──► Reads Git / Vault / Database (Encrypted Secrets with RSA)      │
│            │                                                                                    │
│            ▼ (HTTP / Spring Cloud Bus over Kafka)                                               │
│  [Payment Service Pods]      ──► Dynamic Runtime Property Reload via @RefreshScope (0 Downtime)  │
│            │                                                                                    │
│            ▼ (Heartbeats & Registration)                                                        │
│  [Eureka Service Registry]   ──► AP-Model Registry (Self-Preservation Mode & 30s Heartbeats)     │
│            │                                                                                    │
│            ▼ (Instance List Cached Locally)                                                     │
│  [Spring Cloud LoadBalancer] ──► Client-Side Round-Robin & Health-Aware Routing (No Proxy Hop!) │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘
```

### Core Architecture Components

1. **Spring Cloud Config Server:** Centralized HTTP service delivering environment-specific configuration (`application-{profile}.yml`) backed by Git, HashiCorp Vault, or JDBC. Supports end-to-end secret encryption (`{cipher}...`) and dynamic property broadcast via **Spring Cloud Bus**.
2. **`@RefreshScope`:** A specialized Spring BeanFactory scope that enables beans to reload their configuration properties at runtime without restarting the JVM or destroying active client connections.
3. **Netflix Eureka Service Registry:** An **AP (Available / Partition-Tolerant)** service discovery registry. Microservices register their ephemeral host/port and emit periodic heartbeats (every 30 seconds).
4. **Spring Cloud LoadBalancer:** A reactive, non-blocking client-side load balancer replacing deprecated Netflix Ribbon. Executes round-robin, random, or weighted instance selection directly on the caller's JVM, eliminating the latency and single-point-of-failure overhead of intermediate hardware load balancers.

---

## 2. Internal Working

### 2.1 Spring Cloud Config Server Property Resolution Hierarchy

When a microservice boots up (`spring.application.name=payment-service`, `spring.profiles.active=prod`), Spring Cloud Config Client fetches configuration from the Config Server via REST:

```
GET /payment-service/prod/master
```

The Config Server queries its `EnvironmentRepository` SPI and constructs a unified `Environment` object by merging property sources in strict hierarchical precedence:

```
Property Precedence (Highest to Lowest):
1. payment-service-prod.yml  (Application + Profile specific)
2. payment-service.yml       (Application default)
3. application-prod.yml      (Global shared profile specific)
4. application.yml           (Global shared default)
```

#### Secret Encryption & Decryption (`{cipher}`):
- Sensitive secrets (e.g. database credentials, Stripe private keys) are stored encrypted in Git:
  ```yaml
  spring:
    datasource:
      password: '{cipher}AQA78a9c...=='
  ```
- Config Server decrypts the cipher on-the-fly using an asymmetric RSA private key stored in a secure JKS keystore or HashiCorp Vault before transmitting over TLS to the authenticated client.

---

### 2.2 How `@RefreshScope` Works Internally

`@RefreshScope` is not magic; it is a custom Spring bean scope implemented via `org.springframework.cloud.context.scope.refresh.RefreshScope` (extending `GenericScope`).

```
                              ┌───────────────────────────────────────┐
                              │ ApplicationContext (Singleton Registry│
                              └──────────────────┬────────────────────┘
                                                 │
                                                 ▼
                              ┌───────────────────────────────────────┐
                              │  CGLIB Scope Proxy Bean               │
                              │  (PaymentGatewayProperties$$SpringCGLIB)
                              └──────────────────┬────────────────────┘
                                                 │
                                                 ▼ Intercepts Method Invocation
                              ┌───────────────────────────────────────┐
                              │ GenericScope.BeanLifecycleDecoratorCache│
                              │ (Holds the actual underlying instance)│
                              └──────────────────┬────────────────────┘
                                                 │
                   POST /actuator/refresh        │ Destroys Target Instance
                   ────────────────────────────► │ Cache Evicted!
                                                 │
                                                 ▼ Next Call Re-creates Target Bean
                              ┌───────────────────────────────────────┐
                              │ Fresh Target Bean Instantiated with   │
                              │ new @ConfigurationProperties values!  │
                              └───────────────────────────────────────┘
```

#### The Lifecycle Step-by-Step:
1. **Proxy Creation:** At startup, Spring creates a **CGLIB TargetSource Proxy** for any bean annotated with `@RefreshScope`. The proxy is registered as a singleton in the `ApplicationContext`.
2. **Lazy Target Resolution:** When business code calls a method on the proxy, the proxy looks up the real target instance in the `RefreshScope`'s internal bean cache.
3. **Triggering Refresh:** When SRE triggers `POST /actuator/refresh` (or Spring Cloud Bus broadcast):
   - `ContextRefresher.refresh()` queries the Config Server for new properties.
   - It computes the property delta (`Set<String> changedKeys`).
   - It publishes a `RefreshScopeRefreshedEvent`.
   - `RefreshScope.refreshAll()` clears the `BeanLifecycleDecoratorCache`.
4. **Transparent Re-instantiation:** The next time any thread calls a method on the CGLIB proxy, the proxy sees an empty cache slot, invokes `BeanFactory.createBean()`, re-binds the newly updated `@ConfigurationProperties`, and caches the new target instance. **Zero JVM restart, zero dropped sockets.**

---

### 2.3 Netflix Eureka Internals & Self-Preservation Mode

Netflix Eureka is explicitly designed around the **AP** model of the CAP theorem (favoring Availability and Partition Tolerance over Immediate Consistency).

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                               Eureka Client-Server Heartbeat Topology                           │
│                                                                                                 │
│  [Payment Service Pod 1] ────► 30s Heartbeat (POST /eureka/apps/PAYMENT-SERVICE/pod-1) ──────┐   │
│  [Payment Service Pod 2] ────► 30s Heartbeat ───────────────────────────────────────────────┤   │
│  [Payment Service Pod 3] ────► 30s Heartbeat ───────────────────────────────────────────────┤   │
│                                                                                             ▼   │
│                                                                              ┌────────────────┐ │
│                                                                              │ Eureka Server  │ │
│  Client Cache (Local Memory) ◄── 30s Delta Pull (GET /eureka/apps/delta) ───┤ Registry Map   │ │
│  (Payment Service resolves                                                   │ (In-Memory)    │ │
│   Order Service locally!)                                                    └────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘
```

#### 1. Heartbeats & Expiration:
- **Registration:** Upon startup, `EurekaClient` sends a `POST /eureka/apps/{appName}` registration with metadata (host, IP, port, health check URL).
- **Heartbeat:** Every 30 seconds (`leaseRenewalIntervalInSeconds: 30`), the client sends a `PUT` heartbeat renewal.
- **Eviction:** If Eureka Server does not receive a heartbeat within 90 seconds (`leaseExpirationDurationInSeconds: 90`), it evicts the instance from its registry.

#### 2. Self-Preservation Mode (`enable-self-preservation: true`):
In production, a network partition can cause 50% of client pods to lose connectivity to the Eureka Server, even though all microservices can communicate perfectly with each other within their local subnet.

- If Eureka Server detects that the overall percentage of received renewals drops below **85%** of expected renewals within a 15-minute window (`renewalPercentThreshold: 0.85`), it assumes a network glitch is occurring rather than mass-pod termination.
- **Eureka enters Self-Preservation Mode:** It halts all instance evictions to protect the healthy microservices from being falsely deregistered.

---

### 2.4 Client-Side Load Balancing (Spring Cloud LoadBalancer)

Unlike server-side load balancers (F5, AWS ALB, NGINX) where every hop introduces extra network transit and TLS handshakes, **Spring Cloud LoadBalancer** executes inside the caller's JVM memory:

```
[Payment Service Pod]
  ├── In-Memory Local Eureka Cache: [Order-Svc-1 (10.0.1.5), Order-Svc-2 (10.0.1.6)]
  ├── Spring Cloud LoadBalancer (Round-Robin Atomic Counter)
  └── Direct TCP Connection ──► http://10.0.1.6:8082/api/v1/orders (Zero ALB Hops!)
```

- Annotating `WebClient.Builder` or `RestClient.Builder` with `@LoadBalanced` intercepts URLs with virtual hostnames (`http://order-service/api/v1/orders`).
- `ReactorLoadBalancerExchangeFilterFunction` queries the local `ServiceInstanceListSupplier`, selects an instance using lock-free CAS atomics, and rewrites the URI to the physical pod IP.

---

## 3. Enterprise Scenario: FinFlow Configuration & Discovery

In the FinFlow Payment Platform:
- **Payment Service (20 pods)** needs dynamic adjustment of merchant fee rates (`finflow.payment.transaction-fee-percent: 2.5`) and partner gateway endpoints.
- **Order Service (20 pods)** and **Ledger Service (10 pods)** register dynamically with Eureka.
- During Black Friday promotions, SREs adjust fee rates globally across all 20 Payment pods in under 1 second without restarting containers.

---

## 4. Incorrect Implementation

Below is a catastrophic anti-pattern seen in unshielded Spring Cloud applications:

```java
package com.finflow.chapter360.incorrect;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class DangerousRefreshScopeConfiguration {

    // ANTI-PATTERN 1: @Value fields in a Singleton without thread-safe access
    @Value("${finflow.payment.transaction-fee-percent:2.5}")
    private double feePercent;

    /**
     * CATASTROPHIC PRODUCTION MISTAKE:
     * Placing @RefreshScope directly on a stateful DataSource / Connection Pool bean!
     * 
     * What happens during POST /actuator/refresh:
     * 1. RefreshScope destroys the existing HikariDataSource bean.
     * 2. HikariCP closes all 200 active physical TCP connections immediately.
     * 3. In-flight database transactions are violently aborted with "Connection Closed"!
     * 4. 20 pods crash with massive 500 Internal Server Errors.
     */
    @Bean
    @RefreshScope
    public DataSource dataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String user,
            @Value("${spring.datasource.password}") String pass) {
        
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(user);
        ds.setPassword(pass); // Unencrypted raw password committed to Git!
        ds.setMaximumPoolSize(20);
        return ds;
    }
}
```

---

## 5. Production Incident

```
INCIDENT REPORT: INC-91033
Severity: SEV-1 (Database Connection Pool Teardown)
Impact: 100% of active database transactions aborted across 20 pods; 14,200 checkout payments failed; $820,000 (illustrative) in transactional errors.
Duration: 12 minutes
```

### Incident Timeline

| Time (UTC) | Event |
|---|---|
| **14:00:00** | SRE attempts to update a payment fee rate by modifying Git and sending `POST /actuator/busrefresh`. |
| **14:00:02** | All 20 Payment pods receive the refresh event. `RefreshScope.refreshAll()` executes. |
| **14:00:03** | Because `DataSource` was annotated with `@RefreshScope`, the HikariCP connection pool is destroyed while executing 4,000 active SQL queries. |
| **14:00:05** | In-flight checkout transactions throw `java.sql.SQLException: Connection is closed`. |
| **14:00:10** | Pods attempt to reconnect simultaneously, creating a thundering herd against PostgreSQL (`FATAL: remaining connection slots are reserved for non-replication superuser connections`). |
| **14:06:00** | SRE team isolates the issue, removes `@RefreshScope` from `DataSource`, places `@RefreshScope` only on pure `@ConfigurationProperties` domain beans, and restores database connectivity. |
| **14:12:00** | System fully recovers. |

---

## 6. Logs & Diagnostics

### HikariCP Connection Pool Destruction Logs during Bad Refresh
```text
2026-08-21T14:00:03.112+00:00 INFO  [payment-service,,] 18910 --- [payment-service] [      Thread-14] o.s.c.e.event.RefreshEventListener      : Refresh keys changed: [finflow.payment.transaction-fee-percent]
2026-08-21T14:00:03.115+00:00 INFO  [payment-service,,] 18910 --- [payment-service] [      Thread-14] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown initiated...
2026-08-21T14:00:03.158+00:00 WARN  [payment-service,,] 18910 --- [payment-service] [http-nio-8080-42] o.h.e.jdbc.spi.SqlExceptionHelper      : SQL Error: 0, SQLState: 08003
2026-08-21T14:00:03.159+00:00 ERROR [payment-service,,] 18910 --- [payment-service] [http-nio-8080-42] o.h.e.jdbc.spi.SqlExceptionHelper      : Connection is closed
2026-08-21T14:00:03.160+00:00 ERROR [payment-service,4bf92f3577b34da6,00f067aa0ba902b7] 18910 --- [payment-service] [http-nio-8080-42] c.f.c.s.PaymentProcessingService      : Transaction failed for tx: TX-99421
org.springframework.transaction.CannotCreateTransactionException: Could not open JPA EntityManager for transaction
    at org.springframework.orm.jpa.JpaTransactionManager.doBegin(JpaTransactionManager.java:452)
    at org.springframework.transaction.support.AbstractPlatformTransactionManager.getTransaction(AbstractPlatformTransactionManager.java:373)
```

### Eureka Self-Preservation Mode Alert in Console
```text
2026-08-21T14:00:15.841+00:00 WARN  [eureka-server,,] 11024 --- [eureka-server] [Eureka-EvictionTimer] c.n.e.registry.AbstractInstanceRegistry : 
EMERGENCY! EUREKA MAY BE INCORRECTLY CLAIMING INSTANCES ARE UP WHEN THEY'RE NOT. RENEWALS ARE LESSER THAN THRESHOLD AND HENCE THE INSTANCES ARE NOT BEING EXPIRED JUST TO BE SAFE.
```

---

## 7. Root Cause Analysis

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                    Root Cause Mechanism                                         │
│                                                                                                 │
│  1. Stateful vs Stateless Bean Scope Confusion: @RefreshScope is intended ONLY for stateless    │
│     data classes (@ConfigurationProperties). Applying it to stateful connection managers       │
│     (HikariDataSource, KafkaProducer, JedisConnectionFactory) invokes their DisposableBean      │
│     destroy() hooks, severing live sockets.                                                     │
│                                                                                                 │
│  2. Connection Teardown Collision: Destroying the DataSource during peak load (4,000 req/sec)   │
│     causes thousands of threads to fail mid-query and slam PostgreSQL simultaneously.           │
│                                                                                                 │
│  3. Missing Secret Encryption: Plaintext passwords committed to Git repositories pose high     │
│     security breach risks. Secrets must be encrypted with {cipher} RSA asymmetric cryptography. │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Debugging Process

### Step 1: Inspect Current Dynamic Environment Properties
```bash
curl -s http://localhost:8080/actuator/env/finflow.payment.transaction-fee-percent | jq .
```

### Step 2: Trigger Live Context Refresh Manually
```bash
curl -X POST http://localhost:8080/actuator/refresh -H "Content-Type: application/json"
```
*Returns JSON array of modified configuration keys.*

### Step 3: Inspect Eureka Registered Applications
```bash
curl -s -H "Accept: application/json" http://localhost:8761/eureka/apps | jq .
```
*Verifies instance health status, hostnames, and lease renewal timestamps.*

---

## 9. Correct Implementation

### 9.1 Dedicated Dynamic Properties Bean (`PaymentGatewayProperties.java`)

```java
package com.finflow.chapter360.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Clean @RefreshScope separation:
 * 1. Contains ONLY configuration data (stateless POJO).
 * 2. Never wraps DataSources or network thread pools.
 * 3. Re-instantiated seamlessly by CGLIB proxy upon /actuator/refresh.
 */
@Component
@RefreshScope
@ConfigurationProperties(prefix = "finflow.payment")
public class PaymentGatewayProperties {

    private BigDecimal transactionFeePercent = BigDecimal.valueOf(2.5);
    private int fixedFeeCents = 30;
    private boolean instantSettlementEnabled = true;
    private BigDecimal maxDailyVolume = BigDecimal.valueOf(1000000.00);
    private String partnerGatewayUrl = "https://api.stripe-mock.finflow.internal/v1";
    private String environmentTier = "PRODUCTION";

    public BigDecimal getTransactionFeePercent() { return transactionFeePercent; }
    public void setTransactionFeePercent(BigDecimal transactionFeePercent) { this.transactionFeePercent = transactionFeePercent; }

    public int getFixedFeeCents() { return fixedFeeCents; }
    public void setFixedFeeCents(int fixedFeeCents) { this.fixedFeeCents = fixedFeeCents; }

    public boolean isInstantSettlementEnabled() { return instantSettlementEnabled; }
    public void setInstantSettlementEnabled(boolean instantSettlementEnabled) { this.instantSettlementEnabled = instantSettlementEnabled; }

    public BigDecimal getMaxDailyVolume() { return maxDailyVolume; }
    public void setMaxDailyVolume(BigDecimal maxDailyVolume) { this.maxDailyVolume = maxDailyVolume; }

    public String getPartnerGatewayUrl() { return partnerGatewayUrl; }
    public void setPartnerGatewayUrl(String partnerGatewayUrl) { this.partnerGatewayUrl = partnerGatewayUrl; }

    public String getEnvironmentTier() { return environmentTier; }
    public void setEnvironmentTier(String environmentTier) { this.environmentTier = environmentTier; }
}
```

---

### 9.2 Dynamic Fee Calculation Service (`DynamicPaymentRateService.java`)

```java
package com.finflow.chapter360.service;

import com.finflow.chapter360.config.PaymentGatewayProperties;
import com.finflow.chapter360.model.FeeCalculationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class DynamicPaymentRateService {

    private static final Logger log = LoggerFactory.getLogger(DynamicPaymentRateService.class);
    private final PaymentGatewayProperties properties;

    public DynamicPaymentRateService(PaymentGatewayProperties properties) {
        this.properties = properties;
    }

    public FeeCalculationResult calculateTransactionFee(String transactionId, BigDecimal grossAmount) {
        BigDecimal feePercent = properties.getTransactionFeePercent();
        int fixedCents = properties.getFixedFeeCents();
        String environmentTier = properties.getEnvironmentTier();

        log.info("[DynamicFeeEngine] Calculating fee for tx: {} | Gross: ${} | Rate: {}% + {}¢ | Tier: {}",
                transactionId, grossAmount, feePercent, fixedCents, environmentTier);

        BigDecimal percentageFee = grossAmount.multiply(feePercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal fixedFee = BigDecimal.valueOf(fixedCents)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal totalFee = percentageFee.add(fixedFee);
        BigDecimal netPayout = grossAmount.subtract(totalFee);

        return new FeeCalculationResult(
                transactionId, grossAmount, percentageFee, fixedFee,
                totalFee, netPayout, feePercent, environmentTier
        );
    }

    public PaymentGatewayProperties getProperties() {
        return properties;
    }
}
```

---

### 9.3 Client-Side Load Balancer & Discovery (`ServiceDiscoveryManager.java`)

```java
package com.finflow.chapter360.service;

import com.finflow.chapter360.model.DiscoveredInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ServiceDiscoveryManager {

    private static final Logger log = LoggerFactory.getLogger(ServiceDiscoveryManager.class);
    private final Map<String, List<DiscoveredInstance>> registry = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> roundRobinIndices = new ConcurrentHashMap<>();

    public ServiceDiscoveryManager() {
        initDefaultTopology();
    }

    private void initDefaultTopology() {
        registerInstance("order-service", new DiscoveredInstance(
                "order-service", "order-svc-pod-1", "order-service-1.finflow.internal", 8082, false, "UP",
                Map.of("zone", "us-east-1a", "version", "2.4.0")
        ));
        registerInstance("order-service", new DiscoveredInstance(
                "order-service", "order-svc-pod-2", "order-service-2.finflow.internal", 8082, false, "UP",
                Map.of("zone", "us-east-1b", "version", "2.4.0")
        ));
    }

    public synchronized void registerInstance(String serviceId, DiscoveredInstance instance) {
        registry.computeIfAbsent(serviceId, k -> new ArrayList<>()).add(instance);
        roundRobinIndices.putIfAbsent(serviceId, new AtomicInteger(0));
    }

    public Optional<DiscoveredInstance> chooseInstance(String serviceId) {
        List<DiscoveredInstance> instances = registry.get(serviceId);
        if (instances == null || instances.isEmpty()) return Optional.empty();

        List<DiscoveredInstance> healthy = instances.stream()
                .filter(i -> "UP".equalsIgnoreCase(i.getStatus()))
                .toList();

        if (healthy.isEmpty()) return Optional.empty();

        AtomicInteger indexCounter = roundRobinIndices.get(serviceId);
        int index = Math.abs(indexCounter.getAndIncrement() % healthy.size());
        return Optional.of(healthy.get(index));
    }

    public Map<String, List<DiscoveredInstance>> getAllServices() {
        return Collections.unmodifiableMap(registry);
    }
}
```

---

## 10. Performance Comparison

The table below contrasts Dynamic Property Refreshing via `@RefreshScope` against traditional Pod Rolling Restarts across 20 pods:

| Metric | Traditional Pod Rolling Restart | Dynamic `@RefreshScope` Refresh | Production Benefit |
|---|---|---|---|
| **Property Propagation Time** | 300–600 seconds (Pod restarts) | **0.4 seconds** (illustrative) | 1,000x faster deployment |
| **Active DB Transactions Aborted** | 100% of in-flight queries during kill | **0 Aborted Transactions** | Zero dropped business requests |
| **JVM Warm-Up JIT Penalty** | Severe CPU spike (C2 compiler) | **0ms JIT warm-up** | Steady-state CPU preserved |
| **Memory Footprint Delta** | Full heap re-allocation | **~2 KB** (Bean cache eviction) | Zero memory thrashing |
| **Secret Security** | Plaintext in container image env | **Encrypted `{cipher}` RSA in Git** | SOC2 / PCI-DSS compliance |

---

## 11. Best Practices

- [x] **Never Put `@RefreshScope` on Stateful Resources:** Only annotate `@ConfigurationProperties` POJOs with `@RefreshScope`. Never annotate `DataSource`, `EntityManagerFactory`, `KafkaProducer`, or `JedisConnectionFactory`.
- [x] **Encrypt All Production Secrets:** Use asymmetric RSA cryptography (`{cipher}AQ...`) with Spring Cloud Config Server so secrets in Git repositories are unreadable without the private key.
- [x] **Keep Eureka Self-Preservation Enabled in Production:** Never set `eureka.server.enable-self-preservation=false` in production environments; it prevents mass instance deregistrations during transient network partitions.
- [x] **Use Spring Cloud LoadBalancer with Caffeine Cache:** Enable Caffeine caching for `ServiceInstanceListSupplier` to avoid querying Eureka Server on every single HTTP RPC request.
- [x] **Use `@LoadBalanced` on Client Builders:** Annotate `WebClient.Builder` or `RestClient.Builder` with `@LoadBalanced` to resolve virtual service hostnames seamlessly.

---

## 12. Common Mistakes

### 1. Returning CGLIB Proxies Directly to Jackson Serializer
```java
// INCORRECT: Returning @RefreshScope bean directly causes Jackson InvalidDefinitionException!
@GetMapping("/properties")
public PaymentGatewayProperties getProps() {
    return this.properties; // CRASH: Jackson tries to serialize Spring CGLIB TargetSource internals!
}

// CORRECT: Map to DTO or Map before serialization
@GetMapping("/properties")
public Map<String, Object> getProps() {
    return Map.of("feePercent", properties.getTransactionFeePercent());
}
```

### 2. Using Unsynchronized Mutable `@Value` Fields
Mutating `@Value` fields across concurrent worker threads without thread safety causes race conditions. Always encapsulate dynamic configuration in immutable or thread-safe `@ConfigurationProperties` beans.

### 3. Forgetting `@ConfigurationPropertiesScan`
Without `@ConfigurationPropertiesScan` or `@EnableConfigurationProperties`, Spring Boot will not register the property binder beans in the ApplicationContext.

---

## 13. Interview Questions

### Junior Tier
**Q: What is the purpose of Spring Cloud Config Server?**  
*Answer:* Spring Cloud Config Server provides externalized, centralized configuration management across all environments (dev, test, prod). Instead of storing configuration files inside container images, microservices fetch their properties over HTTP upon startup, enabling centralized secret management, version control in Git, and dynamic property updates without rebuilding images.

---

### Mid Tier
**Q: How does `@RefreshScope` reload bean properties without restarting the JVM?**  
*Answer:* `@RefreshScope` creates a CGLIB proxy around the target bean. When `POST /actuator/refresh` is called, `ContextRefresher` fetches new properties and clears the `RefreshScope` internal bean cache (`BeanLifecycleDecoratorCache`). The next time a method is called on the proxy, the proxy detects the missing instance, instantiates a fresh target bean using the newly bound `@ConfigurationProperties`, and caches it.

---

### Senior Tier
**Q: Under the CAP theorem, why is Netflix Eureka classified as an AP system while HashiCorp Consul and ZooKeeper are CP systems?**  
*Answer:* 
- **Eureka (AP):** Favors Availability and Partition Tolerance. Each Eureka node accepts registrations and heartbeats independently without requiring distributed consensus (Raft/Paxos). In a network partition, Eureka nodes continue serving slightly stale instance data rather than rejecting requests.
- **Consul / ZooKeeper (CP):** Enforces Consistency and Partition Tolerance using Raft/ZAB consensus. If a network partition prevents a quorum of master nodes, the cluster refuses read/write operations to guarantee strong consistency, trading off availability.

---

### Staff Tier
**Q: Why is placing `@RefreshScope` on a `HikariDataSource` bean catastrophic in a high-throughput production service?**  
*Answer:* `HikariDataSource` is a stateful bean managing open OS TCP socket pools. When `@RefreshScope` destroys the target instance, Spring executes `HikariDataSource.close()`, which closes all active physical database connections immediately. In-flight SQL transactions are aborted with `Connection Closed`, and subsequent requests trigger connection thundering herds against the database server.

---

### Principal Tier
**Q: How would you design a zero-downtime, distributed configuration update pipeline across 1,000 microservice pods deployed across 3 global cloud regions?**  
*Answer:*
1. **Source of Truth:** Secrets stored in HashiCorp Vault; non-sensitive properties stored in Git with branch-per-environment controls.
2. **Event Broadcast (Spring Cloud Bus):** Connect Config Server to a distributed Kafka cluster. When a commit lands on `main`, a CI/CD webhook calls `POST /actuator/busrefresh` on the Config Server.
3. **Targeted Destination Filtering:** Use Cloud Bus destination targeting (`/busrefresh?destination=payment-service:**`) to update only the relevant service fleet across regions.
4. **Graceful Scope Refresh:** Microservices reload stateless `@ConfigurationProperties` beans via `@RefreshScope` with zero container restarts, while Grafana monitors error rates to auto-rollback if bad configuration is pushed.

---

## 14. Hands-on Exercise

### Task: Dynamic Fee Adjustment via `@RefreshScope` and Discovery
1. Create a `PaymentGatewayProperties` bean annotated with `@RefreshScope` and `@ConfigurationProperties(prefix = "finflow.payment")`.
2. Build a `DynamicPaymentRateService` that calculates transaction fees based on `transactionFeePercent` and `fixedFeeCents`.
3. Implement `ServiceDiscoveryManager` providing round-robin load balancing over microservice instances.
4. Write unit and integration tests verifying:
   - Initial fee calculation at 2.5% + 30¢.
   - Dynamic property update to 3.0% + 50¢ applying immediately without container restart.
   - Round-robin cycling across discovered service pods.

### Solution
See complete runnable code in [DynamicPaymentRateServiceUnitTest.java](file:///D:/Projects/Spring%20Boot/spring-boot-production-guide/code/chapter-360/src/test/java/com/finflow/chapter360/unit/DynamicPaymentRateServiceUnitTest.java) and [PaymentConfigControllerIntegrationTest.java](file:///D:/Projects/Spring%20Boot/spring-boot-production-guide/code/chapter-360/src/test/java/com/finflow/chapter360/integration/PaymentConfigControllerIntegrationTest.java).

---

## 15. Advanced Challenge: Distributed Multi-Region Secret Key Rotation

### The Challenge
In a banking system processing credit card authorizations:
1. Data encryption keys (AES-256 GCM) must rotate every 90 days across 50 payment pods.
2. During the rotation window, pods must be able to decrypt payloads encrypted with Key Version $N-1$ while encrypting new payloads with Key Version $N$.
3. Key rotation must execute dynamically via Spring Cloud Config and Vault without restarting pods or dropping transactions.

### Enterprise Architecture
```
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                    Enterprise Key Rotation Architecture (Dual-Key Window)                       │
│                                                                                                 │
│  [HashiCorp Vault] ──► Generates Key V2; updates Config Server with:                           │
│                        - active-key-version: 2                                                  │
│                        - keyring.v1: {cipher}... (Retained for decrypting old records)          │
│                        - keyring.v2: {cipher}... (Active for new encryptions)                   │
│                                                                                                 │
│  [Kafka Spring Cloud Bus] ──► Emits RefreshRemoteApplicationEvent to all 50 Payment pods       │
│                                                                                                 │
│  [Payment Service Pods]   ──► @RefreshScope reloads KeyRing:                                    │
│                               1. Encrypt: uses Key V2                                           │
│                               2. Decrypt: inspects payload header version byte (V1 or V2)       │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 16. Production Checklist

Before approving PRs for Spring Cloud Config or Service Discovery:

- [ ] **No Stateful Beans in `@RefreshScope`:** Verified `@RefreshScope` is NEVER placed on `DataSource`, `EntityManagerFactory`, or connection pools.
- [ ] **Secrets Encrypted with `{cipher}`:** No plaintext passwords or private keys committed to Git repositories.
- [ ] **Eureka Self-Preservation Enabled:** `eureka.server.enable-self-preservation: true` in production configurations.
- [ ] **Local Load Balancer Caching:** Spring Cloud LoadBalancer configured with Caffeine caching to minimize Eureka Server query traffic.
- [ ] **DTO Serialization for Config:** REST endpoints returning configuration properties map them to DTOs/Maps to prevent CGLIB proxy serialization exceptions.
- [ ] **Actuator Refresh Endpoint Secured:** `/actuator/refresh` and `/actuator/busrefresh` restricted to authorized CI/CD service accounts and internal SRE CIDRs.
- [ ] **Twelve-Factor Compliance:** Environment-specific overrides configured via Config Server profile hierarchy rather than hardcoded profiles.
