---
chapter: 120
topic: Spring Data JPA Fundamentals — Repository Abstraction, Query Derivation, @Query, Projections, Specifications
prerequisite_chapters: [10, 30, 40, 50, 60, 70, 80, 90, 100, 110]
reference_system_node: Payment Service ↔ PostgreSQL payment_db (PaymentIntent repository, DTO projections, Specifications, HikariCP connection lifecycle)
---

# Chapter 120: Spring Data JPA Fundamentals — Repository Abstraction, Query Derivation, @Query, Projections, Specifications

## 1. Concept

Spring Data JPA provides a highly abstracted repository layer over the Java Persistence API (JPA), sitting between your application's domain logic and the underlying JPA provider (typically Hibernate). Instead of writing boilerplate Data Access Objects (DAOs) using the JPA `EntityManager`, developers declare interfaces extending standard Spring Data interfaces like `Repository`, `CrudRepository`, `ListCrudRepository`, or `JpaRepository`.

The core conceptual pillars include:
*   **Repository Abstraction:** Standard interfaces that expose CRUD operations without tying the domain to JPA semantics.
*   **Query Derivation:** Automatic generation of queries based on the method names (e.g., `findByCustomerIdAndStatus`).
*   **@Query and JPQL/Native SQL:** Explicitly defined queries for scenarios where derivation is too complex or lacks performance optimizations.
*   **Projections:** Mechanisms to fetch partial data (DTOs or Interfaces) instead of the full managed entity graph, vital for high-throughput reads.
*   **Specifications:** A programmatic API leveraging the JPA Criteria Builder to dynamically construct predicates for complex, multi-parameter searches.

## 2. Internal Working

When a Spring Boot application starts, Spring Data JPA scans for interfaces extending `Repository`. It does not generate Java source code for these interfaces; instead, it creates dynamic JDK proxies using `JpaRepositoryFactory`. 

### Proxy Creation and Method Interception
Through `RepositoryComposition`, the factory assembles a proxy that intercepts method calls via `QueryExecutorMethodInterceptor`. If a method is a standard CRUD operation, it delegates to `SimpleJpaRepository`. If it's a custom query method, it routes the call to a specific `AbstractJpaQuery` implementation (like `PartTreeJpaQuery` or `SimpleJpaQuery`).

### Query Derivation AST
For derived queries, the `PartTree` component parses the method name during initialization. It breaks the name into an Abstract Syntax Tree (AST) (e.g., `findBy` -> `CustomerId` -> `And` -> `Status`) and translates this into a JPQL statement. This happens once at startup, so runtime overhead is minimal.

### Projection Mechanisms
*   **Dynamic Proxies (Interfaces):** If a method returns an interface (e.g., `PaymentIntentView`), Spring generates a proxy that implements this interface. Under the hood, if the projection is "closed" (only references exact entity properties), Spring modifies the query to select only those columns. If it is "open" (uses SpEL like `@Value("#{target.customer.name}")`), Spring cannot optimize the query and falls back to fetching the full entity graph, applying the projection in-memory.
*   **Record DTOs (Direct Instantiation):** Java Records can be used in JPQL via constructor expressions (`SELECT new com.finflow.chapter120.domain.PaymentIntentSummary(...)`). Hibernate instantiates the Record directly, completely bypassing the first-level cache and dirty checking mechanism.

### Specifications Execution
When you pass a `Specification<T>` to a `JpaSpecificationExecutor`, Spring translates it into a JPA `CriteriaQuery`. It applies the defined predicates to construct the SQL `WHERE` clause dynamically, safely handling parameters without string concatenation.

## 3. Enterprise Scenario

In the FinFlow Payment Platform, the **Payment Service** handles up to 4,000 req/sec at peak. The merchant dashboard provides a critical view of these payments via `GET /v1/payments`. This endpoint allows merchants to view a paginated list of their payments, apply up to 8 optional filters (amount range, date range, status, etc.), and perform bulk status updates.

The database `payment_db` contains millions of `PaymentIntent` rows. The dashboard only requires lightweight reporting details (ID, status, amount, created date) and does not need the complete `PaymentIntent` entity along with its related `Charge` or `Refund` lists. Efficiency is critical to prevent saturating the HikariCP connection pool (10 connections/pod across 20 pods) and keeping latency within the ~120ms p50 SLA.

## 4. Incorrect Implementation

The following naive implementation introduces critical flaws: over-fetching full entities for read-only listings, utilizing open projections with SpEL, vulnerable native queries, and unsynchronized `@Modifying` queries.

```java
package com.finflow.chapter120.incorrect;

import com.finflow.chapter120.domain.PaymentIntentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PaymentIntentRepository extends JpaRepository<PaymentIntentEntity, UUID> {

    // PROBLEM 1: Over-fetching full entities for a dashboard list view
    List<PaymentIntentEntity> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    // PROBLEM 2: Open projection with SpEL. Forces full entity fetch and potential N+1
    @Query("SELECT p FROM PaymentIntent p WHERE p.status = :status")
    List<PaymentIntentSpelView> findByStatusWithOpenProjection(@Param("status") String status);

    // PROBLEM 3: SQL Injection vulnerability - Unparameterized string concatenation
    @Query(value = "SELECT * FROM payment_intent WHERE customer_id = '" + 
                   "\\:customerId" + "'", nativeQuery = true)
    List<PaymentIntentEntity> unsafeFindByCustomerIdNative(String customerId);

    // PROBLEM 4: Missing clearAutomatically/flushAutomatically. 
    // Causes stale cache reads for subsequent queries in the same transaction.
    @Modifying
    @Query("UPDATE PaymentIntent p SET p.status = :newStatus WHERE p.customerId = :customerId")
    int bulkUpdateStatus(@Param("customerId") UUID customerId, @Param("newStatus") String newStatus);
}
```

```java
package com.finflow.chapter120.incorrect;

import org.springframework.beans.factory.annotation.Value;
import java.util.UUID;

public interface PaymentIntentSpelView {
    UUID getId();
    
    // SpEL forces the projection to be "open"
    @Value("#{target.amountCents / 100.0}")
    Double getAmountDollars();
    
    String getStatus();
}
```

## 5. Production Incident

During the Black Friday flash sale, merchant dashboard traffic surged as sellers eagerly refreshed their payment feeds. The dashboard queries mapped to `findByStatusWithOpenProjection` and `findByCustomerIdOrderByCreatedAtDesc`.

Because these methods fetched full `PaymentIntent` entities, Hibernate had to construct thousands of managed entity snapshots in the Persistence Context for dirty checking. The open SpEL projection worsened the situation by forcing Hibernate to fetch the entire entity graph just to compute the derived `getAmountDollars()` property.

Latency spiked from a p50 of 15ms to over 3.8s. The 10 HikariCP connections per pod were held open for seconds instead of milliseconds. Across all 20 Payment Service pods, the connection pools exhausted in under 12 seconds. The Ingress Gateway began dropping 80% of payment traffic due to downstream timeouts. 

Simultaneously, a rogue tenant discovered that the search endpoint backed by `unsafeFindByCustomerIdNative` was susceptible to SQL injection. By passing `' OR 1=1 --` as the customer ID, they managed to retrieve payment intents belonging to other merchants, triggering a critical security alert.

## 6. Logs

```text
2026-11-27T10:04:12.331 [http-nio-8080-exec-15] WARN  com.zaxxer.hikari.pool.PoolBase - traceId=abc1234 - HikariPool-1 - Connection is not available, request timed out after 30000ms.
2026-11-27T10:04:15.112 [http-nio-8080-exec-22] WARN  org.hibernate.SQL_SLOW - traceId=def5678 - SlowQuery: 3850 milliseconds. SQL: 'SELECT p1_0.id, p1_0.amount_cents, p1_0.customer_id, p1_0.status FROM payment_intent p1_0 WHERE p1_0.status = ?'
2026-11-27T10:04:20.998 [http-nio-8080-exec-45] ERROR com.finflow.security.AuditLog - traceId=ghi9012 - Potential SQL Injection Detected. Payload: ' OR 1=1 --
```

Heap dump analysis post-incident showed `org.hibernate.engine.spi.EntityEntry` and `Object[]` snapshots consuming over 800MB of the 2GB heap per pod, triggering aggressive G1GC pauses.

## 7. Root Cause Analysis

*   **Persistence Context Memory Footprint:** When `JpaRepository` returns entities, Hibernate attaches them to the first-level cache. It stores both the object reference and a deep copy of the original state (the snapshot) to facilitate dirty checking at flush time. Loading 1,000 entities means 2,000 objects in memory.
*   **Open Projections with SpEL:** Spring Data JPA cannot parse SpEL (`@Value`) at query creation time to determine which specific database columns to select. It defaults to selecting `*` (all columns), fetching the entire entity, and running the SpEL expression in-memory against the initialized entity proxy.
*   **First-Level Cache Staleness:** The `@Modifying` annotation triggers an `executeUpdate()` directly against JDBC. However, it does not inform the `EntityManager`. Any `PaymentIntent` entities already loaded in the first-level cache remain unmodified. If the application reads those entities later in the same `@Transactional` block, it will see stale data.
*   **SQL Injection:** Standard JPQL parameter binding (`:paramName`) is compiled into `PreparedStatement` placeholders (`?`). String concatenation in `nativeQuery = true` circumvents JDBC parameter binding, making the database vulnerable to payload injection.

## 8. Debugging Process

1.  **Connection Metrics:** We observed `hikaricp_connections_active` pegged at 10 and `hikaricp_connections_pending` soaring into the hundreds.
2.  **Query Plan Inspection:** Enabled Hibernate query logging (`org.hibernate.SQL: DEBUG`). We noticed that the SpEL projection query was emitting `SELECT id, customer_id, amount_cents, currency, status, ... FROM payment_intent`.
3.  **JFR Profiling:** Java Flight Recorder captures indicated heavy allocation pressure in `org.hibernate.sql.results.internal.StandardRowReader.readRow()`, validating the theory of entity over-fetching.
4.  **Testing Modifying Queries:** Added a test asserting that querying an entity immediately after `bulkUpdateStatus` reflected the new status. The test failed, highlighting the cache synchronization issue.

## 9. Correct Implementation

We redesign the repository using Java Record DTO projections, safe JPA specifications for dynamic filtering, and properly configured `@Modifying` annotations.

```java
package com.finflow.chapter120.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_intent")
public class PaymentIntentEntity {
    
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Getters, Setters, etc.
}
```

```java
package com.finflow.chapter120.correct;

import java.util.UUID;
import java.time.Instant;

// Record DTO for high-performance projections
public record PaymentIntentSummary(
    UUID id,
    String status,
    Long amountCents,
    Instant createdAt
) {}
```

```java
package com.finflow.chapter120.correct;

import com.finflow.chapter120.domain.PaymentIntentEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PaymentIntentRepository extends 
    JpaRepository<PaymentIntentEntity, UUID>, 
    JpaSpecificationExecutor<PaymentIntentEntity> {

    // 1. DTO Projection using constructor expression. Bypasses 1st-level cache entirely.
    @Query("""
        SELECT new com.finflow.chapter120.correct.PaymentIntentSummary(p.id, p.status, p.amountCents, p.createdAt)
        FROM PaymentIntentEntity p
        WHERE p.customerId = :customerId
        ORDER BY p.createdAt DESC
    """)
    List<PaymentIntentSummary> findSummariesByCustomerId(@Param("customerId") UUID customerId);

    // 2. Closed Interface Projection. Spring optimizes the SQL to only select 'id' and 'status'.
    List<PaymentIntentStatusView> findByCustomerId(UUID customerId);

    // 3. Safe Native Query with standard parameter binding.
    @Query(value = "SELECT * FROM payment_intent WHERE customer_id = :customerId", nativeQuery = true)
    List<PaymentIntentEntity> safeFindByCustomerIdNative(@Param("customerId") UUID customerId);

    // 4. Synchronized Modifying Query.
    // flushAutomatically = true: Flushes pending changes to DB before executing the update.
    // clearAutomatically = true: Evicts entities from the 1st-level cache so subsequent reads fetch fresh data.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PaymentIntentEntity p SET p.status = :newStatus WHERE p.customerId = :customerId")
    int bulkUpdateStatus(@Param("customerId") UUID customerId, @Param("newStatus") String newStatus);
}
```

```java
package com.finflow.chapter120.correct;

import java.util.UUID;

// Closed interface projection
public interface PaymentIntentStatusView {
    UUID getId();
    String getStatus();
}
```

### Dynamic Specifications

For the multi-filter dashboard search, we use `Specification`.

```java
package com.finflow.chapter120.correct;

import com.finflow.chapter120.domain.PaymentIntentEntity;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

public class PaymentIntentSpecifications {

    public static Specification<PaymentIntentEntity> hasCustomerId(UUID customerId) {
        return (root, query, cb) -> 
            customerId == null ? null : cb.equal(root.get("customerId"), customerId);
    }

    public static Specification<PaymentIntentEntity> hasStatus(String status) {
        return (root, query, cb) -> 
            status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<PaymentIntentEntity> amountBetween(Long min, Long max) {
        return (root, query, cb) -> {
            if (min == null && max == null) return null;
            if (min == null) return cb.lessThanOrEqualTo(root.get("amountCents"), max);
            if (max == null) return cb.greaterThanOrEqualTo(root.get("amountCents"), min);
            return cb.between(root.get("amountCents"), min, max);
        };
    }
}
```

### Service Implementation

```java
package com.finflow.chapter120.correct;

import com.finflow.chapter120.domain.PaymentIntentEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PaymentDashboardService {

    private final PaymentIntentRepository repository;

    public PaymentDashboardService(PaymentIntentRepository repository) {
        this.repository = repository;
    }

    // Read-only transaction prevents flush checks and optimizes JDBC connection
    @Transactional(readOnly = true)
    public List<PaymentIntentEntity> searchPayments(UUID customerId, String status, Long minAmount, Long maxAmount) {
        Specification<PaymentIntentEntity> spec = Specification.where(PaymentIntentSpecifications.hasCustomerId(customerId))
                .and(PaymentIntentSpecifications.hasStatus(status))
                .and(PaymentIntentSpecifications.amountBetween(minAmount, maxAmount));
        
        return repository.findAll(spec);
    }
    
    @Transactional
    public int completeMerchantPayments(UUID customerId) {
        // Will correctly clear cache and flush changes safely
        return repository.bulkUpdateStatus(customerId, "COMPLETED");
    }
}
```

## 10. Performance Comparison

| Metric (100 Rows Fetched) | Managed Entity Query | Record DTO Projection (`new`) | Closed Interface Projection |
| :--- | :--- | :--- | :--- |
| **P50 Latency (illustrative)** | 18.5ms | 2.1ms (9x faster) | 3.5ms |
| **Heap Allocation** | ~420KB | ~35KB (12x less memory) | ~50KB |
| **DB Columns Fetched** | All (e.g., 20+ columns) | Exact 4 columns | Exact 2 columns |
| **Connection Hold Time** | High (GC pauses delay release) | Very Low | Low |
| **1st-Level Cache Overhead** | Yes (Snapshot copies created) | **None** | **None** |

By using DTO Projections for the dashboard, HikariCP connections are checked out and returned in under 3ms, preventing pool exhaustion under 4,000 req/sec loads.

## 11. Best Practices

*   **Default to Projections for Read-Only APIs:** If you do not intend to modify the returned data in the current transaction, use Record DTOs (`SELECT new...`) or Closed Interface Projections.
*   **Favor Record DTOs over Interfaces for Large Result Sets:** Instantiating a Java Record is fundamentally faster than Spring generating JDK Dynamic Proxies for interfaces for every row in a result set.
*   **Use `@Transactional(readOnly = true)`:** Always annotate read-only repository calls at the service layer. This disables dirty checking, hints the JDBC driver to optimize routing, and skips flush checks.
*   **Always clear/flush `@Modifying` queries:** Use `@Modifying(clearAutomatically = true, flushAutomatically = true)` unless you can mathematically prove the current persistence context does not hold stale representations of the updated data.
*   **Use Specifications for Dynamic Queries:** Do not write derived queries like `findByCustomerIdAndStatusAndAmountCentsBetweenOrCreatedDateBefore...`. Use the `CriteriaBuilder` API via `Specification` for maintainable, composable predicates.

## 12. Common Mistakes

*   **SpEL in Projections (Open Projections):** Using `@Value("#{target...}")` silently ruins projection optimization. If derived properties are needed, map closed fields to a Java Record and compute the derivation in the Record's constructor.
*   **Trusting Native Queries Blindly:** Using string concatenation (`+`) instead of binding parameters (`:param`) in `nativeQuery = true`.
*   **Abusing Derived Queries for Joins:** Spring Data JPA's method parser handles simple properties well, but attempting complex joins (e.g., `findByPaymentIntent_Charge_Refund_Status`) creates unreadable methods and often produces suboptimal SQL cross-joins.

## 13. Interview Questions

*   **Junior:** What is the difference between `@Query` and query derivation in Spring Data JPA?
*   **Mid:** Explain what an Open Projection is and why it performs worse than a Closed Projection.
*   **Senior:** Why must you use `@Modifying(clearAutomatically = true)` on bulk update queries? What happens if you forget it?
*   **Staff:** Walk through how Spring Data creates a repository instance at runtime. Where does the proxying happen, and how does it delegate to the `EntityManager`?
*   **Principal:** Compare the memory footprint and CPU overhead of fetching 100,000 entities via `findAll()` versus using a constructor expression (`SELECT new DTO(...)`) and a Scrollable result set.

## 14. Hands-on Exercise

**Scenario:** The FinFlow ops team needs a dynamic search endpoint for Refunds.
**Task:** Build a `RefundSearchSpecification` class that implements `Specification<RefundEntity>`. 
1. Support filtering by `chargeId`.
2. Support an optional date range (`createdAfter`, `createdBefore`).
3. Add a complex criteria builder predicate: Search for refunds where the amount is strictly greater than 50% of the original `Charge` amount (requires a subquery or join via `CriteriaBuilder`).

## 15. Advanced Challenge

**Offset vs. Keyset Pagination:** 
The standard Spring Data `PageRequest.of(page, size)` translates to `LIMIT size OFFSET page*size`. On a 100-million row `payment_intent` table, `OFFSET 5000000` requires the database to read and discard 5,000,000 rows, destroying latency.
**Challenge:** Refactor the dashboard repository to use Spring Data JPA 3.x's `Window<T>` and `ScrollPosition` for Keyset-based (Seek) pagination. Benchmark the p99 latency of fetching page 1,000 using `OFFSET` versus `Window.positionAt(...)`.

## 16. Production Checklist

* [ ] Projections (Records/Interfaces) are used for read-only listings instead of returning full `@Entity` graphs.
* [ ] No SpEL (`@Value`) is used inside Projection interfaces unless strictly necessary (and documented as an open projection).
* [ ] All `@Modifying` queries explicitly define `clearAutomatically = true` and `flushAutomatically = true`.
* [ ] Service layer search methods are annotated with `@Transactional(readOnly = true)`.
* [ ] Dynamic queries with >3 optional parameters use `JpaSpecificationExecutor` instead of massive `@Query` strings with `WHERE (:param IS NULL OR column = :param)`.
* [ ] No string concatenation is present in any `nativeQuery = true` statements.
