---
chapter: 150
topic: Lazy Loading & Entity Graphs — N+1 Problem, JOIN FETCH, @EntityGraph, Batch Fetching
prerequisite_chapters: [10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140]
reference_system_node: Payment Service ↔ PostgreSQL payment_db (PaymentOrder, PaymentItem, PaymentAuditLog, MerchantAccount)
---

# Chapter 150: Lazy Loading & Entity Graphs — N+1 Problem, JOIN FETCH, @EntityGraph, Batch Fetching

## 1. Concept

In high-throughput enterprise systems, data modeling inevitably involves object graphs: a parent entity references child collections and related entities (for example, a `PaymentOrder` relates to `PaymentItem`s, `PaymentAuditLog`s, and a `MerchantAccount`). When querying the database via an Object-Relational Mapping (ORM) framework like Hibernate/JPA, how and when those associations are retrieved directly determines whether your service sustains 4,000 req/sec or crashes under database connection exhaustion.

### Lazy Loading vs. Eager Loading
- **Lazy Loading (`FetchType.LAZY`)**: The associated entity or collection is not fetched when the parent is loaded. Instead, Hibernate supplies a runtime bytecode proxy or uninitialized collection wrapper. The actual SQL query to load the data is deferred until a getter or method on the association is invoked.
- **Eager Loading (`FetchType.EAGER`)**: Hibernate immediately fetches the association whenever the parent is loaded. 

JPA defaults `@ManyToOne` and `@OneToOne` to `FetchType.EAGER`, while `@OneToMany` and `@ManyToMany` default to `FetchType.LAZY`. In high-performance backend engineering, **`FetchType.EAGER` is universally recognized as an anti-pattern for entity associations** because it deprives the repository layer of query control, triggering unneeded joins and uncoordinated SQL queries regardless of the caller's intent.

### The N+1 Query Problem
The N+1 problem occurs when an application executes 1 initial SQL query to retrieve $N$ parent records, and subsequently triggers $N$ additional SQL queries (one per parent) to fetch a lazily loaded child association or eager relation. If an entity has two lazy associations traversed in a loop (e.g., items and merchant profile), a single HTTP endpoint fetching 50 orders fires $1 + 50 + 50 = 101$ queries against the database.

Under concurrent production load (e.g., 200 requests/sec), this results in tens of thousands of database round-trips per second, saturating database CPU, exhausting HikariCP connection pools, and driving request latencies from 15ms to connection timeout failures (>30,000ms).

### Modern Solutions to N+1
Spring Data JPA and Hibernate provide four primary mechanisms to fetch entity graphs efficiently:
1. **JPQL `JOIN FETCH`**: Inlines an explicit SQL `JOIN` (inner or outer) in JPQL, materializing the parent and association in a single SQL statement.
2. **JPA 2.1 `@EntityGraph`**: Declaratively defines an ad-hoc fetch plan via annotations or dynamic programmatic APIs, overriding static mapping fetch types at query runtime.
3. **Batch Fetching (`@BatchSize` / `hibernate.default_batch_fetch_size`)**: Configures Hibernate to initialize lazy proxies and collections in batches using SQL `WHERE parent_id IN (?, ?, ?, ...)` clauses, reducing $N$ secondary queries down to $\lceil N / \text{batch\_size} \rceil$.
4. **DTO Projections**: Bypasses entity management entirely by selecting only required scalar fields directly into immutable Java Records via JPQL `SELECT new ...` expressions.

---

## 2. Internal Working

Understanding how Hibernate implements lazy loading under the hood is critical to diagnosing production anomalies such as `LazyInitializationException`, `MultipleBagFetchException`, and memory bloat.

### Byte Buddy Proxying & LazyInitializer
When Hibernate loads an entity with a lazy `@ManyToOne` association (e.g., `PaymentOrder.merchantAccount`), it does not instantiate a concrete `MerchantAccount` instance. Instead, it generates a synthetic subclass using **Byte Buddy** (or Javassist in legacy versions):

```
+-------------------------------------------------------------------+
|                  MerchantAccount$HibernateProxy$xyz               |
|                                                                   |
|  +-------------------------------------------------------------+  |
|  | ByteBuddyInterceptor / EntityLazyInitializer                |  |
|  |                                                             |  |
|  | - target: MerchantAccount (null until initialized)          |  |
|  | - identifier: UUID ("a3b4c5...")                            |  |
|  | - isInitialized: false                                      |  |
|  | - session: SessionImpl (Active Persistence Context)         |  |
|  +-------------------------------------------------------------+  |
+-------------------------------------------------------------------+
```

1. **Proxy Creation**: Hibernate populates only the entity's `@Id` field on the proxy without querying the database table.
2. **Interception**: When code invokes any non-identifier method (e.g., `merchantAccount.getBusinessName()`), Byte Buddy intercepts the call.
3. **State Check & Fetch**: The `LazyInitializer` checks `isInitialized`. If `false`, it checks whether `session.isOpen() && session.isConnected()`.
   - If the Persistence Context is open, it issues `SELECT ... FROM merchant_accounts WHERE id = ?`, assigns the materialized entity to `target`, marks `isInitialized = true`, and delegates the method call.
   - If the Persistence Context is closed (transaction committed or outside session), it throws:
     ```
     org.hibernate.LazyInitializationException: could not initialize proxy [com.finflow.chapter150.domain.MerchantAccount#...] - no Session
     ```

### Persistent Collections (`PersistentBag` vs. `PersistentSet`)
When an entity declares a `@OneToMany List<PaymentItem> items`, Hibernate replaces the `java.util.ArrayList` instance with its own internal wrapper: `org.hibernate.collection.spi.PersistentBag`. For `Set<T>`, it uses `PersistentSet`.

- `PersistentBag` permits duplicate elements and has no defined ordering without `@OrderBy` or `@OrderColumn`.
- When `items.iterator()` or `items.get(i)` is called, `PersistentBag` checks its internal `session` reference and triggers collection initialization SQL:
  ```sql
  SELECT item_id, order_id, sku, price, qty FROM payment_items WHERE payment_order_id = ?
  ```

### The `MultipleBagFetchException` Hazard
If a JPQL query attempts to `JOIN FETCH` two or more `java.util.List` collections simultaneously:
```java
// ILLEGAL IN HIBERNATE:
@Query("SELECT p FROM PaymentOrder p JOIN FETCH p.items JOIN FETCH p.auditLogs")
```
Hibernate throws:
```
org.hibernate.loader.MultipleBagFetchException: cannot simultaneously fetch multiple bags: [com.finflow.chapter150.domain.PaymentOrder.items, com.finflow.chapter150.domain.PaymentOrder.auditLogs]
```

**Why does this happen?**
A SQL query joining two one-to-many tables generates a Cartesian product: if an order has 10 items and 10 audit logs, the database returns $10 \times 10 = 100$ result rows. Because `List` (`PersistentBag`) semantics preserve duplicates and position, Hibernate cannot determine whether duplicate rows in the JDBC ResultSet represent actual duplicate list entries or Cartesian product artifacts.

**Solutions for Multiple Collections**:
1. Change one or both collections to `java.util.Set` (`PersistentSet`). Hibernate can deduplicate `Set` elements using `equals()`/`hashCode()`. *(Note: Be mindful of Cartesian product memory footprint!)*
2. Fetch the primary entity with one collection via `JOIN FETCH`, and fetch the second collection using `@BatchSize` or a separate follow-up query.

### Open Session in View (OSIV) Anti-Pattern
Spring Boot defaults `spring.jpa.open-in-view=true` in standard web starters (though it logs a warning).
- **How OSIV works**: An `OpenEntityManagerInViewFilter` or `OpenEntityManagerInViewInterceptor` binds the JPA `EntityManager` to the HTTP request thread for the entire duration of the web request, from Controller through Jackson JSON serialization in the View layer.
- **Why OSIV is dangerous in production**:
  1. **Connection Holding**: The physical database connection from HikariCP remains checked out while the application renders JSON, executes business logic, or waits on external network I/O.
  2. **Hidden N+1 Queries**: Serializing entities to JSON in the web layer silently triggers lazy loading queries during Jackson serialization, completely hidden from the service layer.
  3. **Resource Exhaustion**: Slow network clients cause database connections to stay locked for seconds, causing rapid pool starvation for other critical transactions.

---

## 3. Enterprise Scenario: FinFlow Payment Platform

In the **FinFlow Payment Service** (`payment_db` on PostgreSQL, 20 Kubernetes pods, HikariCP pool of 10 connections per pod = 200 total database connections):

```
Client ──► API Gateway ──► Payment Service (20 Pods) ──► PostgreSQL (payment_db)
                              │
                              ├── /v1/merchants/{merchantId}/reconciliations/daily
                              └── /v1/orders/search
```

- **Traffic Scale**:
  - Daily Settlement & Reconciliation Window: 4,000 req/sec aggregate across merchant dashboards and automated treasury batch jobs.
  - Average merchant settlement report queries 50 `PaymentOrder`s per batch.
  - Each `PaymentOrder` contains an average of 4 `PaymentItem` line items and references 1 `MerchantAccount`.
- **Database Limits**:
  - PostgreSQL RDS instance comfortably rated for ~150 active concurrent queries.
  - HikariCP `connection-timeout = 30000ms`, `maximum-pool-size = 10`.

---

## 4. Incorrect Implementation

Below is the naive implementation typical of services transitioning from development prototypes to production.

```java
package com.finflow.chapter150.incorrect;

import com.finflow.chapter150.domain.PaymentItem;
import com.finflow.chapter150.domain.PaymentOrder;
import com.finflow.chapter150.dto.PaymentItemDto;
import com.finflow.chapter150.dto.PaymentOrderSummaryDto;
import com.finflow.chapter150.repository.PaymentOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * INCORRECT IMPLEMENTATION:
 * 1. Executes 1 + 2N queries for every reconciliation call.
 * 2. If called outside transaction with OSIV disabled, crashes with LazyInitializationException.
 * 3. Under load, 50 orders per page * 4,000 req/sec = 404,000 queries/sec against PostgreSQL!
 */
@Service
public class NPlusOneReconciliationServiceIncorrect {

    private final PaymentOrderRepository paymentOrderRepository;

    public NPlusOneReconciliationServiceIncorrect(PaymentOrderRepository paymentOrderRepository) {
        this.paymentOrderRepository = paymentOrderRepository;
    }

    @Transactional(readOnly = true)
    public List<PaymentOrderSummaryDto> getDailyReconciliationReport(String merchantId) {
        // Query 1: SELECT * FROM payment_orders WHERE merchant_id = ? (Returns 50 rows)
        List<PaymentOrder> orders = paymentOrderRepository.findAllByMerchantId(merchantId);

        List<PaymentOrderSummaryDto> report = new ArrayList<>();

        for (PaymentOrder order : orders) {
            // Queries 2..51: Lazy loading of MerchantAccount ByteBuddy proxy (N queries)
            String merchantCode = order.getMerchantAccount().getMerchantCode();
            String businessName = order.getMerchantAccount().getBusinessName();

            // Queries 52..101: Lazy loading of PaymentItem PersistentBag (N queries)
            List<PaymentItemDto> itemDtos = new ArrayList<>();
            for (PaymentItem item : order.getItems()) {
                itemDtos.add(new PaymentItemDto(
                        item.getId(),
                        item.getSku(),
                        item.getItemDescription(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getFeeAmount()
                ));
            }

            report.add(new PaymentOrderSummaryDto(
                    order.getId(),
                    order.getOrderNumber(),
                    merchantCode,
                    businessName,
                    order.getTotalAmount(),
                    order.getCurrency(),
                    order.getStatus().name(),
                    order.getCreatedAt(),
                    itemDtos
            ));
        }

        return report;
    }

    /**
     * Non-transactional boundary with spring.jpa.open-in-view=false.
     * Throws LazyInitializationException as soon as proxy getter is called.
     */
    public List<String> getOrderSummaryWithoutTransaction(String merchantId) {
        List<PaymentOrder> orders = paymentOrderRepository.findAllByMerchantId(merchantId);
        List<String> summaries = new ArrayList<>();
        for (PaymentOrder order : orders) {
            // CRASH: Session is already closed!
            summaries.add("Order: " + order.getOrderNumber() + ", Merchant: " + order.getMerchantAccount().getBusinessName());
        }
        return summaries;
    }
}
```

---

## 5. Production Incident

### Incident Timeline

| Time (UTC) | Event / Telemetry |
|---|---|
| **08:00:00** | Daily automated reconciliation jobs trigger across enterprise merchant clients. Ingress traffic spikes to 2,800 req/sec. |
| **08:01:15** | PagerDuty fires P1 Alert: `PaymentService_p99_Latency_High (>15,000ms)`. |
| **08:01:45** | PostgreSQL CPU utilization surges from 18% to **99.8%**. Total active database connections hit backend limit (150). |
| **08:02:10** | HikariCP connection pool on all 20 pods saturates (`Active: 10, Idle: 0, Waiting: 450`). |
| **08:02:40** | Client-facing APIs begin failing with HTTP 500 (`ConnectionTimeoutException: Connection is not available, request timed out after 30000ms`). |
| **08:03:00** | Incident Commander pages backend on-call team. Emergency traffic shed initiated at API Gateway. |
| **08:14:00** | On-call identifies N+1 query storm on `/reconciliations/daily` endpoint via `pg_stat_activity` and Hibernate query stats. Hotfix deployed using `JOIN FETCH` and `@BatchSize`. |
| **08:22:00** | Database CPU normalizes to 14%, p99 latency drops to 22ms. Incident resolved. |

### Business & Technical Impact
- **Financial Impact**: 42,000 checkout payment authorizations failed or timed out during the 20-minute incident window. Estimated $1.8M (illustrative) in delayed transaction processing.
- **SRE Alert Summary**:
  ```
  ALERT [CRITICAL] FinFlow-PaymentService-HikariCP-Pool-Exhausted
  Source: Kubernetes Pod payment-service-7d8b9f-x4k21
  Active Connections: 10/10 (100% saturation for > 60s)
  Threads Awaiting Connection: 487
  Database CPU: 99.8% on postgresql-primary-az1
  ```

---

## 6. Logs & Diagnostics

### 1. Application Server Log (HikariCP Connection Pool Exhaustion)
```text
2026-08-20T08:02:15.104Z ERROR [payment-service,trace_id=9f8c12a4b,span_id=3c8e11] 1 --- [http-nio-8080-exec-42] o.a.c.c.C.[.[.[/].[dispatcherServlet] : Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception [Request processing failed: org.springframework.dao.CannotCreateTransactionException: Could not open JPA EntityManager for transaction] with root cause

java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available, request timed out after 30002ms (total=10, active=10, idle=0, waiting=412)
	at com.zaxxer.hikari.pool.HikariPool.createTimeoutException(HikariPool.java:696)
	at com.zaxxer.hikari.pool.HikariPool.getConnection(HikariPool.java:197)
	at com.zaxxer.hikari.HikariDataSource.getConnection(HikariDataSource.java:128)
	at org.hibernate.engine.jdbc.connections.internal.DatasourceConnectionProviderImpl.getConnection(DatasourceConnectionProviderImpl.java:122)
	at org.hibernate.internal.NonContextualJdbcConnectionAccess.obtainConnection(NonContextualJdbcConnectionAccess.java:48)
```

### 2. Hibernate SQL Query Storm Log
```text
2026-08-20T08:01:05.120Z DEBUG [payment-service,,] 1 --- [http-nio-8080-exec-12] org.hibernate.SQL : select po1_0.id,po1_0.created_at,po1_0.currency,po1_0.merchant_account_id,po1_0.merchant_id,po1_0.order_number,po1_0.status,po1_0.total_amount from payment_orders po1_0 where po1_0.merchant_id=?
2026-08-20T08:01:05.125Z DEBUG [payment-service,,] 1 --- [http-nio-8080-exec-12] org.hibernate.SQL : select ma1_0.id,ma1_0.business_name,ma1_0.merchant_code,ma1_0.settlement_tier from merchant_accounts ma1_0 where ma1_0.id=?
2026-08-20T08:01:05.129Z DEBUG [payment-service,,] 1 --- [http-nio-8080-exec-12] org.hibernate.SQL : select i1_0.payment_order_id,i1_0.id,i1_0.fee_amount,i1_0.item_description,i1_0.quantity,i1_0.sku,i1_0.unit_price from payment_items i1_0 where i1_0.payment_order_id=?
2026-08-20T08:01:05.133Z DEBUG [payment-service,,] 1 --- [http-nio-8080-exec-12] org.hibernate.SQL : select ma1_0.id,ma1_0.business_name,ma1_0.merchant_code,ma1_0.settlement_tier from merchant_accounts ma1_0 where ma1_0.id=?
2026-08-20T08:01:05.138Z DEBUG [payment-service,,] 1 --- [http-nio-8080-exec-12] org.hibernate.SQL : select i1_0.payment_order_id,i1_0.id,i1_0.fee_amount,i1_0.item_description,i1_0.quantity,i1_0.sku,i1_0.unit_price from payment_items i1_0 where i1_0.payment_order_id=?
... [101 total queries logged for a SINGLE HTTP request] ...
```

### 3. `LazyInitializationException` Log (When accessed outside session)
```text
2026-08-20T08:04:12.871Z ERROR [payment-service,trace_id=4d3f11a,span_id=7b2e99] 1 --- [http-nio-8080-exec-18] c.f.c.i.NPlusOneReconciliationService : Failed to generate order summary

org.hibernate.LazyInitializationException: could not initialize proxy [com.finflow.chapter150.domain.MerchantAccount#8e4b31f4-9d10-482a-a532-6a7516d00412] - no Session
	at org.hibernate.proxy.AbstractLazyInitializer.initialize(AbstractLazyInitializer.java:169)
	at org.hibernate.proxy.AbstractLazyInitializer.getImplementation(AbstractLazyInitializer.java:315)
	at org.hibernate.proxy.pojo.bytebuddy.ByteBuddyInterceptor.intercept(ByteBuddyInterceptor.java:45)
	at com.finflow.chapter150.domain.MerchantAccount$HibernateProxy$9bT2.getBusinessName(Unknown Source)
	at com.finflow.chapter150.incorrect.NPlusOneReconciliationServiceIncorrect.getOrderSummaryWithoutTransaction(NPlusOneReconciliationServiceIncorrect.java:70)
```

---

## 7. Root Cause Analysis

The incident was caused by a combination of three architectural and ORM interaction factors:

```
+-----------------------------------------------------------------------------------------------+
|                                    Root Cause Mechanism Chain                                 |
|                                                                                               |
|  1. Spring Data JPA findAllByMerchantId() issues 1 SELECT for 50 PaymentOrders                |
|     └── Returns 50 PaymentOrder entities with uninitialized ByteBuddy proxies and             |
|         PersistentBag collection wrappers.                                                    |
|                                                                                               |
|  2. Reconciliation Loop iterates through each order                                            |
|     ├── Accessing order.getMerchantAccount().getBusinessName() triggers ByteBuddy proxy        |
|     │   initialization -> 50 individual SQL SELECT queries.                                   |
|     └── Accessing order.getItems() triggers PersistentBag initialization                     |
|         -> 50 individual SQL SELECT queries.                                                  |
|                                                                                               |
|  3. Multiplied by Concurrency (2,800 req/sec * 101 queries = 282,800 queries/sec)             |
|     ├── PostgreSQL query planner & network stack overwhelmed with connection context switches |
|     ├── DB CPU reaches 100% -> Query execution time spikes from 0.4ms to 350ms                |
|     └── HikariCP pool (10 connections/pod) holds connections 100x longer than normal,        |
|         blocking incoming web threads until 30s timeout threshold is reached.                 |
+-----------------------------------------------------------------------------------------------+
```

---

## 8. Debugging Process

When an on-call engineer investigates an incident of this nature, they follow this exact operational sequence:

```
[1. Dashboards] Check Grafana APM / HikariCP Pool Saturation & PostgreSQL CPU
       │
[2. DB Engine] Query pg_stat_activity to inspect running SQL statements and wait events
       │
[3. App Telemetry] Enable Hibernate Statistics (hibernate.generate_statistics=true)
       │
[4. Profiling] Identify N+1 loop in flame graph / APM traces
       │
[5. Remediation] Implement JOIN FETCH / @EntityGraph / @BatchSize
```

### Step 1: Inspect PostgreSQL Active Queries
Run on the PostgreSQL primary:
```sql
SELECT pid, now() - query_start AS duration, state, query 
FROM pg_stat_activity 
WHERE state != 'idle' 
ORDER BY duration DESC 
LIMIT 10;
```
*Output reveals hundreds of repetitive queries: `select ... from payment_items where payment_order_id = $1` executing concurrently.*

### Step 2: Check HikariCP Pool Metrics via Actuator / Prometheus
```bash
curl -s http://localhost:8081/actuator/metrics/hikaricp.connections.pending
# Value: {"name":"hikaricp.connections.pending","measurements":[{"statistic":"VALUE","value":412.0}]}
```

### Step 3: Enable Hibernate Query Statistics in Application Configuration
Set in `application.yml` or runtime environment:
```yaml
spring:
  jpa:
    properties:
      hibernate:
        generate_statistics: true
```
Hibernate outputs session metrics on transaction close:
```text
Session Metrics {
    35400 nanoseconds spent acquiring 1 JDBC connections;
    0 nanoseconds spent releasing 0 JDBC connections;
    4280000 nanoseconds spent preparing 101 JDBC statements;
    12840000 nanoseconds spent executing 101 JDBC statements;
    0 nanoseconds spent executing 0 JDBC batches;
}
```
*A single transaction prepared and executed 101 statements instead of 1.*

---

## 9. Correct Implementation

Here is the production-grade implementation employing **JPQL `JOIN FETCH`**, **`@EntityGraph`**, and **Batch Fetching (`@BatchSize`)**.

### Repository Layer: `PaymentOrderRepository.java`

```java
package com.finflow.chapter150.repository;

import com.finflow.chapter150.domain.PaymentOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, UUID> {

    // 1. Standard query (used with @BatchSize / default_batch_fetch_size)
    List<PaymentOrder> findAllByMerchantId(String merchantId);

    // 2. JOIN FETCH approach: Inlines SQL joins in a single query
    @Query("SELECT DISTINCT p FROM PaymentOrder p " +
           "JOIN FETCH p.merchantAccount " +
           "LEFT JOIN FETCH p.items " +
           "WHERE p.merchantId = :merchantId")
    List<PaymentOrder> findAllWithItemsAndMerchantByMerchantId(@Param("merchantId") String merchantId);

    // 3. @EntityGraph approach: Dynamic ad-hoc fetch plan
    @EntityGraph(attributePaths = {"merchantAccount", "items"}, type = EntityGraph.EntityGraphType.FETCH)
    @Query("SELECT p FROM PaymentOrder p WHERE p.merchantId = :merchantId")
    List<PaymentOrder> findAllWithEntityGraphByMerchantId(@Param("merchantId") String merchantId);

    // 4. Single entity fetch with details
    @Query("SELECT p FROM PaymentOrder p " +
           "JOIN FETCH p.merchantAccount " +
           "LEFT JOIN FETCH p.items " +
           "WHERE p.id = :id")
    Optional<PaymentOrder> findByIdWithDetails(@Param("id") UUID id);
}
```

### Domain Configuration with `@BatchSize`: `PaymentOrder.java`

```java
package com.finflow.chapter150.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "payment_orders")
public class PaymentOrder {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_number", nullable = false, unique = true, length = 64)
    private String orderNumber;

    @Column(name = "merchant_id", nullable = false, length = 64)
    private String merchantId;

    // Default to LAZY to prevent uncoordinated eager joins
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_account_id", nullable = false)
    private MerchantAccount merchantAccount;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PaymentStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Batch size of 50 collapses N queries into ceil(N/50) batched SQL IN queries
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "paymentOrder", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentItem> items = new ArrayList<>();

    @BatchSize(size = 50)
    @OneToMany(mappedBy = "paymentOrder", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PaymentAuditLog> auditLogs = new HashSet<>();

    protected PaymentOrder() {}

    public PaymentOrder(UUID id, String orderNumber, String merchantId, MerchantAccount merchantAccount,
                        BigDecimal totalAmount, String currency, PaymentStatus status, Instant createdAt) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.merchantId = merchantId;
        this.merchantAccount = merchantAccount;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.status = status;
        this.createdAt = createdAt;
    }

    public void addItem(PaymentItem item) {
        items.add(item);
        item.setPaymentOrder(this);
    }

    public UUID getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public String getMerchantId() { return merchantId; }
    public MerchantAccount getMerchantAccount() { return merchantAccount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public List<PaymentItem> getItems() { return items; }
    public Set<PaymentAuditLog> getAuditLogs() { return auditLogs; }
}
```

### Service Layer: `OptimizedReconciliationService.java`

```java
package com.finflow.chapter150.correct;

import com.finflow.chapter150.domain.PaymentItem;
import com.finflow.chapter150.domain.PaymentOrder;
import com.finflow.chapter150.dto.PaymentItemDto;
import com.finflow.chapter150.dto.PaymentOrderSummaryDto;
import com.finflow.chapter150.repository.PaymentOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class OptimizedReconciliationService {

    private final PaymentOrderRepository paymentOrderRepository;

    public OptimizedReconciliationService(PaymentOrderRepository paymentOrderRepository) {
        this.paymentOrderRepository = paymentOrderRepository;
    }

    /**
     * Strategy 1: JOIN FETCH.
     * Executes exactly 1 SQL query containing LEFT JOINs for items and INNER JOIN for merchant.
     */
    @Transactional(readOnly = true)
    public List<PaymentOrderSummaryDto> getReconciliationReportViaJoinFetch(String merchantId) {
        List<PaymentOrder> orders = paymentOrderRepository.findAllWithItemsAndMerchantByMerchantId(merchantId);
        return mapToDto(orders);
    }

    /**
     * Strategy 2: @EntityGraph.
     * Dynamic runtime fetch plan via JPA 2.1 EntityGraph.
     */
    @Transactional(readOnly = true)
    public List<PaymentOrderSummaryDto> getReconciliationReportViaEntityGraph(String merchantId) {
        List<PaymentOrder> orders = paymentOrderRepository.findAllWithEntityGraphByMerchantId(merchantId);
        return mapToDto(orders);
    }

    /**
     * Strategy 3: Batch Fetching (@BatchSize).
     * Executes 1 parent query + 1 batched IN query for MerchantAccounts + 1 batched IN query for Items.
     * Total: 3 SQL queries instead of 101, immune to Cartesian product explosion!
     */
    @Transactional(readOnly = true)
    public List<PaymentOrderSummaryDto> getReconciliationReportViaBatchFetching(String merchantId) {
        List<PaymentOrder> orders = paymentOrderRepository.findAllByMerchantId(merchantId);
        return mapToDto(orders);
    }

    private List<PaymentOrderSummaryDto> mapToDto(List<PaymentOrder> orders) {
        List<PaymentOrderSummaryDto> dtos = new ArrayList<>();
        for (PaymentOrder order : orders) {
            List<PaymentItemDto> itemDtos = new ArrayList<>();
            for (PaymentItem item : order.getItems()) {
                itemDtos.add(new PaymentItemDto(
                        item.getId(),
                        item.getSku(),
                        item.getItemDescription(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getFeeAmount()
                ));
            }

            dtos.add(new PaymentOrderSummaryDto(
                    order.getId(),
                    order.getOrderNumber(),
                    order.getMerchantAccount().getMerchantCode(),
                    order.getMerchantAccount().getBusinessName(),
                    order.getTotalAmount(),
                    order.getCurrency(),
                    order.getStatus().name(),
                    order.getCreatedAt(),
                    itemDtos
            ));
        }
        return dtos;
    }
}
```

---

## 10. Performance Comparison

All figures below represent illustrative production estimates based on 50 `PaymentOrder` records per report under 4,000 req/sec aggregate load on FinFlow infrastructure.

| Metric | Incorrect (N+1 Lazy Loading) | Correct (JOIN FETCH) | Correct (Batch Size 50) |
|---|---|---|---|
| **SQL Queries per Request** | 101 queries | **1 query** | **3 queries** |
| **Response Latency (p99)** | > 30,000ms *(timed out)* (illustrative) | **24ms** (illustrative) | **28ms** (illustrative) |
| **Response Latency (p50)** | 1,450ms (illustrative) | **8ms** (illustrative) | **11ms** (illustrative) |
| **PostgreSQL CPU Load** | 99.8% *(saturated)* | **14.2%** | **16.5%** |
| **HikariCP Active Conn Duration** | 1,450ms | **7ms** | **10ms** |
| **Cartesian Row Multiplier** | None ($1 \times 1$) | $1 \times \text{items}$ | None (Linear) |
| **Risk of `MultipleBagFetchException`** | None | High (if multiple `List`s) | **None (Zero risk)** |
| **Memory Allocation per Request** | 12.4 MB (illustrative) | 3.2 MB (illustrative) | 3.4 MB (illustrative) |

---

## 11. Best Practices

### The Do's
- **DO default every association to `FetchType.LAZY`**: Override `@ManyToOne` and `@OneToOne` defaults from `EAGER` to `LAZY` explicitly on every entity field.
- **DO disable Open Session in View (`spring.jpa.open-in-view=false`)**: Force all data fetching to be explicit within transactional service boundaries.
- **DO configure `hibernate.default_batch_fetch_size: 50` globally**: Provides a robust safety net against accidental N+1 queries across your entire application.
- **DO use `@EntityGraph` for dynamic query customization**: Allows the same base repository method to be reused with different fetch plans for different use cases.
- **DO use DTO Projections for read-only reporting endpoints**: Avoids loading entities into the Persistence Context altogether, saving CPU and heap memory.

### The Don'ts
- **DON'T use `FetchType.EAGER` to fix `LazyInitializationException`**: EAGER forces joins on every query, even when the relationship is completely irrelevant to the caller.
- **DON'T fetch multiple `List` collections with `JOIN FETCH`**: Triggers `MultipleBagFetchException` or massive in-memory Cartesian product deduplication.
- **DON'T perform pagination in memory with `JOIN FETCH` on collections**: Hibernate will log warning `HHH000104: firstResult/maxResults specified with collection fetch; applying in memory!` and pull the entire database table into JVM RAM before paginating.
- **DON'T call `.size()` on a collection just to check if it has elements**: Use a JPQL `COUNT()` query or check `collection.isEmpty()` (which still initializes the collection unless optimized).

---

## 12. Common Mistakes

### Mistake 1: Pagination with `JOIN FETCH` Collection
```java
// SEVERE MEMORY DANGER:
@Query("SELECT p FROM PaymentOrder p JOIN FETCH p.items WHERE p.merchantId = :merchantId")
Page<PaymentOrder> findOrders(@Param("merchantId") String merchantId, Pageable pageable);
```
**Why it fails**: SQL pagination (`LIMIT` / `OFFSET`) operates on result rows. Because joining `items` duplicates parent rows, applying `LIMIT 10` at SQL level would truncate child items mid-order. Hibernate detects this, fetches **all millions of rows** into memory, and paginates in Java heap!

**Production Fix**: Fetch order IDs first using pagination, then fetch full entities via `IN` clause:
```java
@Query("SELECT p.id FROM PaymentOrder p WHERE p.merchantId = :merchantId")
Page<UUID> findOrderIds(@Param("merchantId") String merchantId, Pageable pageable);

@Query("SELECT DISTINCT p FROM PaymentOrder p JOIN FETCH p.items WHERE p.id IN :ids")
List<PaymentOrder> findOrdersWithItemsByIds(@Param("ids") List<UUID> ids);
```

### Mistake 2: The Two-List `MultipleBagFetchException`
Declaring both `List<PaymentItem>` and `List<PaymentAuditLog>` and attempting `JOIN FETCH` on both:
```java
@OneToMany(mappedBy = "paymentOrder")
private List<PaymentItem> items;

@OneToMany(mappedBy = "paymentOrder")
private List<PaymentAuditLog> auditLogs;
```
**Production Fix**: Change child collection types from `List<T>` to `Set<T>` or use `@BatchSize(size = 50)` to fetch secondary collections cleanly.

---

## 13. Interview Questions

### Junior Tier
**Q: What is the N+1 select problem in Hibernate, and why does it occur?**
> **Answer**: The N+1 select problem occurs when an application executes 1 initial SQL query to load $N$ parent records, and then issues $N$ subsequent SQL queries to load a lazy or eager association for each parent record when iterated. It occurs because the ORM loads the parent entities without joining the relationship upfront, deferring (or separately dispatching) the child queries per entity instance.

### Mid Tier
**Q: Why does Hibernate throw `LazyInitializationException`, and what is the proper architectural solution?**
> **Answer**: `LazyInitializationException` occurs when application code tries to access an uninitialized lazy proxy or collection after the underlying Hibernate `Session` (Persistence Context) has already closed (e.g., outside a `@Transactional` method when `spring.jpa.open-in-view=false`). The proper architectural solution is NOT to set `FetchType.EAGER` or enable OSIV; rather, use `JOIN FETCH`, `@EntityGraph`, batch fetching, or map directly to a DTO projection within the transactional service layer.

### Senior Tier
**Q: What is `MultipleBagFetchException`, why does it happen, and how do you resolve it without creating a Cartesian product in memory?**
> **Answer**: `MultipleBagFetchException` is thrown when a JPQL query attempts to `JOIN FETCH` more than one `java.util.List` (`PersistentBag`) association in a single query. A `PersistentBag` allows duplicates and lacks positional indexing, making it mathematically impossible for Hibernate to differentiate between Cartesian product join duplicates and true duplicate collection items. To resolve it without Cartesian memory explosion, fetch the primary collection using `JOIN FETCH` (or `Set`), and fetch secondary collections via `@BatchSize` or a distinct second query filtering by parent IDs.

### Staff Tier
**Q: Explain how Hibernate Byte Buddy proxies work internally when intercepting method calls on lazy `@ManyToOne` associations.**
> **Answer**: When Hibernate loads an entity with a lazy `@ManyToOne`, it generates a Byte Buddy dynamic subclass proxy containing an `EntityLazyInitializer`. The proxy holds the target's primary key (`id`), a null `target` reference, an `isInitialized = false` flag, and a reference to the active `SessionImpl`. When a method (e.g. `getName()`) is invoked on the proxy, the interceptor intercepts the call, verifies `isInitialized`, checks session liveness, executes a SQL `SELECT` to load the real entity state into `target`, marks `isInitialized = true`, and invokes the method on the target instance via reflection or method handles.

### Principal Tier
**Q: How do you design a high-throughput GraphQL or dynamic REST API that avoids both N+1 query storms and Cartesian product blowups when clients request arbitrary nested relations?**
> **Answer**: A Principal-level solution combines:
> 1. **Dynamic JPA EntityGraphs / Subgraphs**: Parsing the incoming GraphQL field selection set or REST `?expand=` query parameters and constructing a dynamic `EntityGraph` programmatically via `EntityManager.createEntityGraph(PaymentOrder.class)`.
> 2. **Max Depth & Join Guardrails**: Restricting `JOIN FETCH` to a single 1-to-many collection path to prevent Cartesian explosion ($O(N \times M \times K)$ rows).
> 3. **DataLoader / Batch-Fetch Strategy**: For sibling or deeper collections, delegating to GraphQL `DataLoader` or Hibernate `@BatchSize`, fetching child collections in batched `WHERE id IN (...)` queries asynchronously.
> 4. **Projections over Managed Entities**: For read-only queries, using dynamic Blaze-Persistence or JPQL constructor expressions to stream rows directly into records, bypassing Persistence Context tracking entirely.

---

## 14. Hands-on Exercise

### Objective
You have an unoptimized endpoint in FinFlow that fetches 20 `PaymentOrder`s and serializes their `MerchantAccount`, `PaymentItem`s, and `PaymentAuditLog`s. Currently, it fires over 60 SQL queries per request and fails when OSIV is disabled. Optimize this repository and service so that all 3 relations are retrieved in **at most 2 SQL statements** with zero Cartesian product issues and no `MultipleBagFetchException`.

### Solution

#### Step 1: Entity Configuration (`Set` for auditLogs + `@BatchSize`)
```java
@BatchSize(size = 50)
@OneToMany(mappedBy = "paymentOrder", fetch = FetchType.LAZY)
private List<PaymentItem> items = new ArrayList<>();

@BatchSize(size = 50)
@OneToMany(mappedBy = "paymentOrder", fetch = FetchType.LAZY)
private Set<PaymentAuditLog> auditLogs = new HashSet<>();
```

#### Step 2: Repository Method with `JOIN FETCH` on Primary Collection & Merchant
```java
@Query("SELECT DISTINCT p FROM PaymentOrder p " +
       "JOIN FETCH p.merchantAccount " +
       "LEFT JOIN FETCH p.items " +
       "WHERE p.merchantId = :merchantId")
List<PaymentOrder> findOrdersWithMerchantAndItems(@Param("merchantId") String merchantId);
```

#### Step 3: Service Layer Traversal (Triggering Batched IN for Audit Logs)
```java
@Transactional(readOnly = true)
public List<PaymentOrderDetailDto> getDetailedOrderHistory(String merchantId) {
    // Statement 1: Fetches PaymentOrders, MerchantAccounts, and PaymentItems in 1 SQL JOIN
    List<PaymentOrder> orders = repository.findOrdersWithMerchantAndItems(merchantId);

    // Iterating auditLogs triggers Statement 2: Batched IN query for all 20 orders simultaneously
    return orders.stream()
            .map(order -> new PaymentOrderDetailDto(
                    order.getId(),
                    order.getOrderNumber(),
                    order.getMerchantAccount().getBusinessName(),
                    order.getItems().size(),
                    order.getAuditLogs().size()
            ))
            .toList();
}
```
*Total statements executed: Exactly 2.*

---

## 15. Advanced Challenge: Dynamic Runtime Entity Graphs

### Enterprise Problem Statement
In FinFlow's Backoffice API, client queries can specify arbitrary expansion fields:
`GET /v1/orders?expand=merchant,items` or `GET /v1/orders?expand=items,auditLogs`.

Hardcoding repository methods for every permutation ($2^K$) is unmaintainable. Build a dynamic service using the JPA `EntityGraph` API to construct the fetch plan at runtime.

### Enterprise Solution

```java
package com.finflow.chapter150.correct;

import com.finflow.chapter150.domain.PaymentOrder;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class DynamicEntityGraphService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<PaymentOrder> findOrdersWithDynamicGraph(String merchantId, Set<String> expandFields) {
        // 1. Create dynamic root entity graph
        EntityGraph<PaymentOrder> entityGraph = entityManager.createEntityGraph(PaymentOrder.class);

        // 2. Add requested attribute nodes dynamically
        if (expandFields.contains("merchant")) {
            entityGraph.addAttributeNodes("merchantAccount");
        }
        if (expandFields.contains("items")) {
            entityGraph.addAttributeNodes("items");
        }
        if (expandFields.contains("auditLogs")) {
            entityGraph.addAttributeNodes("auditLogs");
        }

        // 3. Apply graph as a query hint (jakarta.persistence.fetchgraph)
        TypedQuery<PaymentOrder> query = entityManager.createQuery(
                "SELECT p FROM PaymentOrder p WHERE p.merchantId = :merchantId",
                PaymentOrder.class
        );
        query.setParameter("merchantId", merchantId);
        query.setHint("jakarta.persistence.fetchgraph", entityGraph);

        return query.getResultList();
    }
}
```

---

## 16. Production Checklist

Before approving any pull request involving Spring Data JPA entity relationships:

- [ ] **Open Session in View Disabled**: Verify `spring.jpa.open-in-view=false` is set in all production configuration files.
- [ ] **No `FetchType.EAGER`**: Ensure zero occurrences of `FetchType.EAGER` on `@ManyToOne`, `@OneToOne`, or collection mappings.
- [ ] **No Unbounded `JOIN FETCH` on Collections with Pagination**: Verify that queries with `Pageable` do not `JOIN FETCH` collection associations.
- [ ] **Batch Fetching Enabled**: Confirm `hibernate.default_batch_fetch_size` (e.g. 50 or 100) is configured globally or `@BatchSize` is present on collections.
- [ ] **MultipleBagFetch Prevention**: Verify no single JPQL query joins multiple `List` collections.
- [ ] **DTO Projections for High-Volume Reads**: Ensure high-throughput endpoints select scalar projections/records rather than full entity graphs.
- [ ] **Query Count Assertion Tests**: Validate that integration tests assert maximum prepared statement counts using Hibernate `Statistics` or datasource proxies (e.g., QuickPerf / DataSource-Proxy).
