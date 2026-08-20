---
chapter: 200
topic: PostgreSQL & MySQL for Spring — Engine Differences, Index Strategy, Query Plans, MVCC, Vacuum
prerequisite_chapters: [10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190]
reference_system_node: Payment Service & Settlement Analytics Query Engine ↔ PostgreSQL payment_db (SettlementTransaction, Composite Indexes, Partial Indexes, Covering Indexes, Autovacuum Tuning)
---

# Chapter 200: PostgreSQL & MySQL for Spring — Engine Differences, Index Strategy, Query Plans, MVCC, Vacuum

## 1. Concept

When building enterprise applications on Spring Boot, developers often treat relational databases as interchangeable SQL dialects behind Spring Data JPA. However, the internal storage architectures, concurrency engines, and indexing strategies of **PostgreSQL** and **MySQL (InnoDB)** differ fundamentally. 

Failing to design JPA entities and SQL queries around the underlying database engine results in severe production degradation: queries that execute in 1ms on small datasets degrade to 45-second sequential disk scans when tables scale to tens of millions of rows.

```
+------------------------------------+----------------------------------------------------+
| Architecture Dimension             | PostgreSQL                                         | MySQL (InnoDB)                                     |
+------------------------------------+----------------------------------------------------+
| Table Storage Model                | Heap Storage + Tuple Identifiers (CTID)            | Clustered Index (B+Tree keyed by Primary Key)      |
| Secondary Index Pointers           | Direct pointer to Heap Page Tuple (CTID)           | Pointer to Primary Key value (2-level B+Tree walk) |
| MVCC Concurrency Model             | Append-Only Heap Tuples (xmin/xmax) + VACUUM       | In-place Updates + Undo Log Segments               |
| Concurrency / Connection Model     | Process-per-connection (Dedicated OS Process)     | Thread-per-connection (Lightweight OS Threads)     |
| Partial & Expression Indexes       | Native Support (WHERE clauses, functions)          | Functional indexes (8.0+), No Partial Indexes      |
| Covering Indexes                   | Native INCLUDE clause (Index Only Scan)            | Secondary index covering query columns             |
+------------------------------------+----------------------------------------------------+
```

---

## 2. Internal Working

### Table Storage: PostgreSQL Heap vs. MySQL Clustered Index

```
PostgreSQL: Heap Storage + CTID Pointers
[Index: (merchant_id, status)] ──► Points directly to Heap Page (Page 14, Tuple 2) [CTID]
                                          │
                                          ▼
                      ┌───────────────────────────────────────┐
                      │ Heap Page 14                          │
                      │ [Tuple 1: xmin, xmax, ID, Data...]    │
                      │ [Tuple 2: xmin, xmax, ID, Data...] ◄──┘
                      └───────────────────────────────────────┘

MySQL (InnoDB): Clustered Index (B+Tree)
[Secondary Index: (merchant_id)] ──► Stores Primary Key Value ("UUID-101")
                                          │
                                          ▼ (Secondary B+Tree Search)
                      ┌───────────────────────────────────────┐
                      │ Clustered Index B+Tree (Key: ID)      │
                      │ Leaf Node stores FULL ROW DATA        │
                      └───────────────────────────────────────┘
```

- **PostgreSQL**: Table rows are appended to unordered **Heap Pages**. Every index (including primary key) contains a direct tuple identifier `(page_number, tuple_offset)` known as **CTID**. Lookups via any index jump directly to the heap page.
- **MySQL InnoDB**: The table *is* the Primary Key B+Tree (**Clustered Index**). Secondary indexes store the Primary Key value at the leaf nodes. A secondary index lookup requires a **two-step traversal**: first searching the secondary index to find the Primary Key, then traversing the Clustered Index B+Tree to retrieve the row data (**Bookmark Lookup**).

---

### B-Tree Indexing Strategies in PostgreSQL

#### 1. The Leftmost Prefix Rule on Composite Indexes
When creating a composite index `CREATE INDEX idx_settlement ON settlement_transactions (merchant_id, status, created_at)`:
- The B-Tree is sorted primarily by `merchant_id`, secondarily by `status`, and tertiarily by `created_at`.
- **SARGable Queries (Index Scan)**:
  - `WHERE merchant_id = ?` (Matches leading column)
  - `WHERE merchant_id = ? AND status = ?` (Matches leading 2 columns)
  - `WHERE merchant_id = ? AND status = ? AND created_at >= ?` (Matches all 3 columns)
- **Non-SARGable Queries (Sequential Scan)**:
  - `WHERE status = ?` (Skips leading column `merchant_id` $\to$ **Full Table Scan**)
  - `WHERE status = ? AND created_at >= ?` (Skips leading column $\to$ **Full Table Scan**)

#### 2. Partial Indexes for Skewed Status Distributions
In financial settlement workflows, **99%** of transactions are in `SETTLED` status, while only **1%** are `PENDING`. An index on `(status)` over 50 million rows is virtually useless because the query planner will choose a sequential scan for `SETTLED`.

A **Partial Index** indexes *only* the active minority:
```sql
CREATE INDEX idx_settlement_pending ON settlement_transactions (created_at)
WHERE status = 'PENDING';
```
- **Index Size**: Reduced by **99%** (e.g. from 2.8 GB to 28 MB).
- **Write Performance**: Inserts and updates for `SETTLED` transactions bypass index maintenance entirely!

#### 3. Covering Indexes (`INCLUDE` Clause) for Index Only Scans
When a query requires only a few columns in addition to the search filters:
```sql
CREATE INDEX idx_settlement_summary ON settlement_transactions (merchant_id, status)
INCLUDE (amount, currency);
```
- The database engine executes an **`Index Only Scan`**.
- It reads all required data directly from the B-Tree index pages in RAM without touching the underlying heap tables on disk!

---

### SARGability & Function-Wrapped Columns

A query predicate is **SARGable** (*Search Argument Able*) if the query engine can use an index to evaluate it:

```java
// DISASTROUS ANTI-PATTERN (Non-SARGable):
// Wrapping indexed column in LOWER() or DATE() forces a full table scan on 50M rows!
@Query("SELECT s FROM SettlementTransaction s WHERE LOWER(s.merchantId) = LOWER(:merchantId)")
List<SettlementTransaction> findByMerchantIdLower(@Param("merchantId") String merchantId);

@Query("SELECT s FROM SettlementTransaction s WHERE DATE(s.createdAt) = :date")
List<SettlementTransaction> findByDate(@Param("date") LocalDate date);

// PRODUCTION MANDATE (Fully SARGable):
@Query("SELECT s FROM SettlementTransaction s WHERE s.merchantId = :merchantId")
List<SettlementTransaction> findByMerchantId(@Param("merchantId") String merchantId);

@Query("SELECT s FROM SettlementTransaction s WHERE s.createdAt >= :startOfDay AND s.createdAt < :endOfDay")
List<SettlementTransaction> findByDateRange(@Param("startOfDay") Instant start, @Param("endOfDay") Instant end);
```

---

### PostgreSQL MVCC, Dead Tuples & Autovacuum Tuning

In PostgreSQL, an `UPDATE` does not overwrite the row in place:
1. It writes a **new tuple version** to a heap page and sets `xmin` = current transaction ID.
2. It updates the old tuple's header with `xmax` = current transaction ID, marking it as a **Dead Tuple**.
3. Dead tuples occupy disk space and buffer cache until purged by **VACUUM**.

```
[Heap Page]
┌─────────────────────────────────────────────────────────────┐
│ Tuple 1 (Dead): xmin=100, xmax=105, Status='PENDING' (Old)  │
├─────────────────────────────────────────────────────────────┤
│ Tuple 2 (Live): xmin=105, xmax=0,   Status='SETTLED' (New)  │
└─────────────────────────────────────────────────────────────┘
```

#### The Heap-Only Tuple (HOT) Optimization
If an update does not modify any indexed column and the heap page has sufficient free space, PostgreSQL uses **HOT (Heap-Only Tuple)**:
- It creates the new tuple in the same page and links the old tuple directly to the new tuple via a line pointer chain.
- **Benefit**: No index updates are required across any secondary indexes!

#### Autovacuum Production Tuning on High-Write Tables
Default PostgreSQL autovacuum settings are too conservative for high-velocity tables:
```ini
# PostgreSQL Default: Triggers vacuum only after 20% of table rows change!
# On a 50,000,000-row table, autovacuum will NOT run until 10,000,000 updates occur!

# PRODUCTION TUNING for high-velocity payment tables:
ALTER TABLE settlement_transactions SET (
    autovacuum_vacuum_scale_factor = 0.05,       -- Trigger vacuum after 5% changes
    autovacuum_vacuum_threshold = 5000,          -- Minimum 5,000 dead tuples
    autovacuum_vacuum_cost_limit = 2000,         -- Increase I/O budget for vacuum worker
    autovacuum_vacuum_cost_delay = 2             -- Lower sleep delay between pages
);
```

---

## 3. Enterprise Scenario: FinFlow Settlement Analytics

In the **FinFlow Settlement & Clearing Subsystem**:

```
Payment Processing ──► settlement_transactions (50,000,000 rows in payment_db)
                              │
       ┌──────────────────────┴──────────────────────┐
       ▼                                             ▼
Nightly Settlement Job (Batch)              Merchant Dashboard API
 (Queries WHERE status = 'PENDING')          (Queries WHERE merchant_id = ? AND status = ? AND date)
```

- **Scale**: 50,000,000 total settlement rows.
- **Write Velocity**: 3,500 status updates per second during evening clearing batches.
- **SLA**: Merchant dashboard analytics must respond in $< 50\text{ms}$.

---

## 4. Incorrect Implementation

Below is an inefficient repository and service typical of applications where database indexing fundamentals were overlooked:

```java
package com.finflow.chapter200.incorrect;

import com.finflow.chapter200.domain.SettlementTransaction;
import com.finflow.chapter200.repository.SettlementTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * INCORRECT IMPLEMENTATION:
 * 1. Non-SARGable queries: Wraps indexed columns in functions (LOWER, DATE).
 * 2. Leftmost Prefix violation: Queries on (status, created_at) when index is (merchant_id, status, created_at).
 * 3. Loading full entities into JVM memory to calculate sums/counts.
 */
@Service
@Transactional(readOnly = true)
public class SettlementQueryServiceIncorrect {

    private final SettlementTransactionRepository repository;

    public SettlementQueryServiceIncorrect(SettlementTransactionRepository repository) {
        this.repository = repository;
    }

    /**
     * Anti-Pattern 1: Non-SARGable Function Call on Column.
     * Prevents Index Scan -> Triggers 50M row Sequential Table Scan!
     */
    public List<SettlementTransaction> searchMerchantCaseInsensitive(String merchantId) {
        return repository.findByMerchantIdLowerWrapped(merchantId);
    }

    /**
     * Anti-Pattern 2: Leftmost Prefix Rule Violation.
     * Index is (merchant_id, status, created_at).
     * Querying only status and created_at ignores index completely!
     */
    public List<SettlementTransaction> findByStatusAndRange(String status, Instant from, Instant to) {
        return repository.findByStatusAndCreatedAtBetween(status, from, to);
    }
}
```

---

## 5. Production Incident

### Incident Timeline

| Time (UTC) | Event / Telemetry |
|---|---|
| **08:00:00** | Merchants log in to view monthly settlement reports. Dashboard traffic hits 450 queries/sec. |
| **08:01:30** | Analytics endpoints trigger queries with `LOWER(merchant_id)` and date truncation on `created_at`. |
| **08:02:00** | PostgreSQL query planner initiates **Sequential Scans** across 50 million heap pages for every request. |
| **08:02:45** | RDS storage I/O bandwidth hits **100% saturation (3,000 IOPS ceiling)**. Read latency spikes from 0.8ms to **480ms** per page. |
| **08:03:30** | 42% table bloat (21 million dead tuples from untuned autovacuum) forces PostgreSQL to scan 18 GB of dead pages from disk. |
| **08:04:00** | PagerDuty fires SEV-1 Alert: `MerchantDashboard_p99_Latency > 45,000ms`. Client HTTP requests fail with 504 Gateway Timeout. |
| **08:15:00** | Incident Commander deploys hotfix: Refactors queries to SARGable predicates, creates composite covering index `(merchant_id, status) INCLUDE (amount, currency)`, and schedules an aggressive `VACUUM ANALYZE`. |
| **08:22:00** | Query execution drops from 45,000ms to **1.4ms** via **Index Only Scans**. I/O drops to 2%, CPU normalizes to 8%. Outage resolved. |

---

## 6. Logs & Diagnostics

### 1. PostgreSQL Slow Query Log (`EXPLAIN (ANALYZE, BUFFERS)`)
```text
2026-08-20 08:02:15.112 UTC [24102] LOG:  duration: 44820.142 ms  statement: SELECT s.* FROM settlement_transactions s WHERE LOWER(s.merchant_id) = 'merchant_acme_001'

QUERY PLAN:
Seq Scan on settlement_transactions s  (cost=0.00..1284102.00 rows=250000 width=142) (actual time=142.102..44810.210 rows=42 loops=1)
  Filter: (lower((merchant_id)::text) = 'merchant_acme_001'::text)
  Rows Removed by Filter: 49999958
  Buffers: shared hit=4102 read=982140
Planning Time: 0.184 ms
Execution Time: 44820.240 ms
```
*Notice: `Seq Scan` read **982,140 disk buffer pages** and evaluated the `lower()` filter 50 million times!*

### 2. Fast Query Plan with Composite Covering Index (`Index Only Scan`)
```text
2026-08-20 08:22:05.841 UTC [24102] LOG:  duration: 1.412 ms  statement: SELECT merchant_id, status, SUM(amount), COUNT(*) FROM settlement_transactions WHERE merchant_id = 'merchant_acme_001' AND status = 'SETTLED' GROUP BY merchant_id, status

QUERY PLAN:
GroupAggregate  (cost=0.56..8.58 rows=1 width=48) (actual time=1.380..1.382 rows=1 loops=1)
  Group Key: merchant_id, status
  ->  Index Only Scan using idx_settlement_summary on settlement_transactions  (cost=0.56..8.56 rows=42 width=24) (actual time=0.042..1.120 rows=42 loops=1)
        Index Cond: ((merchant_id = 'merchant_acme_001'::text) AND (status = 'SETTLED'::text))
        Heap Fetches: 0
        Buffers: shared hit=4
Planning Time: 0.112 ms
Execution Time: 1.412 ms
```
*Notice: `Index Only Scan` with `Heap Fetches: 0` touched only **4 buffer pages in RAM**!*

---

## 7. Root Cause Analysis

```
+-------------------------------------------------------------------------------------------------+
|                                Database Degradation Root Cause Chain                            |
|                                                                                                 |
|  1. Non-SARGable SQL Predicates (LOWER(merchant_id), DATE(created_at))                          |
|     └── PostgreSQL B-Tree indexes index raw column values, not arbitrary function outputs.      |
|     └── Query Planner is forced to abort B-Tree traversal and scan 50 million heap rows.       |
|                                                                                                 |
|  2. Severe Table Bloat from Default Autovacuum Scale Factor (0.20)                              |
|     ├── 3,500 status updates/sec generated 21,000,000 dead tuples.                             |
|     └── Sequential scan was forced to read 18 GB of dead, unreachable tuple pages from disk.    |
|                                                                                                 |
|  3. Disk I/O Saturation                                                                         |
|     └── Storage IOPS hit 100% capacity -> All concurrent application queries queue and time out.|
+-------------------------------------------------------------------------------------------------+
```

---

## 8. Debugging Process

```
[1. Metric Triage] Inspect RDS Storage IOPS Utilization & p99 Query Latencies
       │
[2. Execution Plan Inspection] Run EXPLAIN (ANALYZE, BUFFERS) on slow statements
       │
[3. Dead Tuple / Bloat Check] Query pg_stat_user_tables for n_dead_tup counts
       │
[4. Index Alignment] Verify Leftmost Prefix adherence and convert to SARGable ranges
       │
[5. Table Tuning] Configure aggressive autovacuum thresholds and create covering indexes
```

### Step 1: Query PostgreSQL Dead Tuple Ratio
```sql
SELECT 
    schemaname, 
    relname, 
    n_live_tup, 
    n_dead_tup, 
    round(n_dead_tup * 100.0 / nullif(n_live_tup + n_dead_tup, 0), 2) AS dead_tuple_percent,
    last_vacuum, 
    last_autovacuum 
FROM pg_stat_user_tables 
WHERE relname = 'settlement_transactions';
```

---

## 9. Correct Implementation

### 1. Production Service Layer: `SettlementQueryServiceCorrect.java`

```java
package com.finflow.chapter200.correct;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.chapter200.domain.SettlementTransaction;
import com.finflow.chapter200.dto.SettlementSummaryDto;
import com.finflow.chapter200.repository.SettlementTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class SettlementQueryServiceCorrect {

    private final SettlementTransactionRepository repository;
    private final ObjectMapper objectMapper;

    public SettlementQueryServiceCorrect(SettlementTransactionRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Fully SARGable & Leftmost Prefix Aligned: (merchant_id, status, created_at).
     */
    public List<SettlementTransaction> findMerchantSettlements(String merchantId, String status, Instant start, Instant end) {
        return repository.findByMerchantIdAndStatusAndCreatedAtBetweenOrderByCreatedAtDesc(merchantId, status, start, end);
    }

    /**
     * Leftmost Prefix Subset Match (merchant_id, status).
     */
    public List<SettlementTransaction> findByMerchantAndStatus(String merchantId, String status) {
        return repository.findByMerchantIdAndStatus(merchantId, status);
    }

    /**
     * Database-Level Aggregation exploiting Covering Index (Index Only Scan).
     */
    public Optional<SettlementSummaryDto> getSettlementSummary(String merchantId, String status) {
        return repository.summarizeMerchantSettlements(merchantId, status);
    }

    /**
     * Structured JSON Metadata Extraction.
     */
    public Optional<String> extractRoutingCodeFromMetadata(SettlementTransaction tx) {
        if (tx.getMetadataJson() == null || tx.getMetadataJson().isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(tx.getMetadataJson());
            if (root.has("routing_code")) {
                return Optional.of(root.get("routing_code").asText());
            }
        } catch (Exception ignored) {}
        return Optional.empty();
    }
}
```

### 2. Repository with SARGable Queries & Projections: `SettlementTransactionRepository.java`

```java
package com.finflow.chapter200.repository;

import com.finflow.chapter200.domain.SettlementTransaction;
import com.finflow.chapter200.dto.SettlementSummaryDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SettlementTransactionRepository extends JpaRepository<SettlementTransaction, UUID> {

    List<SettlementTransaction> findByMerchantIdAndStatusAndCreatedAtBetweenOrderByCreatedAtDesc(
            String merchantId, String status, Instant start, Instant end);

    List<SettlementTransaction> findByMerchantId(String merchantId);

    List<SettlementTransaction> findByMerchantIdAndStatus(String merchantId, String status);

    Optional<SettlementTransaction> findByGatewayReference(String gatewayReference);

    @Query("SELECT new com.finflow.chapter200.dto.SettlementSummaryDto(s.merchantId, s.status, SUM(s.amount), COUNT(s)) " +
           "FROM SettlementTransaction s " +
           "WHERE s.merchantId = :merchantId AND s.status = :status " +
           "GROUP BY s.merchantId, s.status")
    Optional<SettlementSummaryDto> summarizeMerchantSettlements(
            @Param("merchantId") String merchantId,
            @Param("status") String status);
}
```

---

## 10. Performance Comparison

Benchmarked on a 50,000,000-row table on AWS RDS PostgreSQL `db.r6g.2xlarge` (8 vCPU, 64 GB RAM).

| Metric | Non-SARGable Query (`LOWER(col)`) | SARGable Composite Index (`(merchant, status, date)`) | Covering Index (`INCLUDE (amount)`) |
|---|---|---|---|
| **Query Latency (p99)** | 44,820ms *(Timed out)* (illustrative)| **12.4ms** (illustrative) | **1.4ms** (illustrative) |
| **Query Latency (p50)** | 31,100ms (illustrative) | **4.1ms** (illustrative) | **0.8ms** (illustrative) |
| **Buffer Pages Read** | 982,140 pages (~7.8 GB disk read) | 18 pages | **4 pages (RAM only)** |
| **Execution Scan Type** | `Seq Scan` (Full table scan) | `Index Scan` | `Index Only Scan` (Heap Fetches: 0) |
| **Database Host CPU** | 100% *(Saturated)* | **6.8%** | **2.1%** |
| **Dead Tuple Impact** | Severe (Scanned 21M dead rows) | None (B-Tree ignores dead heap rows) | None |

---

## 11. Best Practices

### The Do's
- **DO adhere to the Leftmost Prefix Rule**: Order composite index columns from most selective / frequently equality-filtered to range-filtered: `(equality_col1, equality_col2, range_col)`.
- **DO use Partial Indexes for status columns**: If `status = 'PENDING'` represents $< 5\%$ of table rows, create a partial index `WHERE status = 'PENDING'`.
- **DO use Covering Indexes (`INCLUDE`) for high-frequency analytical queries**: Eliminates heap table lookups entirely by keeping summary fields in the index leaf pages.
- **DO tune Autovacuum scale factors on high-update tables**: Set `autovacuum_vacuum_scale_factor = 0.05` to prevent dead tuple bloat.
- **DO index all Foreign Key columns**: PostgreSQL does *not* automatically create indexes on foreign key columns; missing FK indexes cause table-level share locks during deletes.

### The Don'ts
- **DON'T wrap indexed columns in SQL functions**: `WHERE LOWER(col) = ?` or `WHERE DATE(col) = ?` completely invalidates B-Tree index traversal.
- **DON'T use leading wildcards in `LIKE` queries**: `WHERE col LIKE '%searchTerm'` cannot use standard B-Tree indexes (use `pg_trgm` GIN indexes instead).
- **DON'T create excessive indexes on high-write tables**: Every index adds write latency and forces disk I/O on `INSERT` / `UPDATE` / `DELETE`.

---

## 12. Common Mistakes

### Mistake 1: Case-Insensitive Search Index Trap
```sql
-- Query written in Spring Data JPA:
SELECT * FROM users WHERE LOWER(username) = LOWER('john_doe');
-- Standard index on 'username' is completely bypassed!
```
**Production Fix**: Create an **Expression Index**:
```sql
CREATE INDEX idx_users_username_lower ON users (LOWER(username));
```

### Mistake 2: Range Filter in Middle of Composite Index
Creating an index on `(created_at, merchant_id, status)` and querying `WHERE created_at >= ? AND merchant_id = ?`.
**Why it fails**: Once a range operator (`<`, `>`, `BETWEEN`) is evaluated on the first column, B-Tree index traversal stops filtering subsequent columns.
**Production Fix**: Place equality columns *before* range columns: `(merchant_id, status, created_at)`.

---

## 13. Interview Questions

### Junior Tier
**Q: What is the Leftmost Prefix Rule in composite database indexes?**
> **Answer**: When a composite index is created on columns `(A, B, C)`, the B-Tree is sorted hierarchically starting with `A`, then `B`, then `C`. A query can utilize the index only if its `WHERE` clause filters by leading prefix columns (e.g. `WHERE A = ?`, `WHERE A = ? AND B = ?`, or `WHERE A = ? AND B = ? AND C = ?`). If a query filters only by `(B, C)` without `A`, the database cannot traverse the B-Tree from the root node and falls back to a sequential table scan.

### Mid Tier
**Q: What is a SARGable query, and why does `WHERE DATE(created_at) = '2026-08-20'` cause performance degradation?**
> **Answer**: A query predicate is SARGable (*Search Argument Able*) if the query engine can directly utilize a B-Tree index to narrow down matching rows without evaluating functions on every row. `DATE(created_at)` wraps the column in a function, requiring the database to compute `DATE()` on every single row in the table (a full `Seq Scan`). A SARGable refactoring uses an indexed date range: `WHERE created_at >= '2026-08-20T00:00:00Z' AND created_at < '2026-08-21T00:00:00Z'`.

### Senior Tier
**Q: Compare PostgreSQL MVCC and MySQL InnoDB MVCC. How do they handle row updates, and what are the operational trade-offs?**
> **Answer**: 
> - **PostgreSQL**: Implements MVCC via **Append-Only Heap Tuples**. An update writes a new tuple with `xmin` and marks the old tuple with `xmax`. Old tuple versions remain in the heap table as "Dead Tuples" until purged by the **Autovacuum** daemon. Trade-off: High-frequency updates cause table and index bloat if autovacuum is untuned, but transaction rollbacks are instantaneous ($O(1)$).
> - **MySQL InnoDB**: Updates rows **in place** in the Clustered Index B+Tree and writes previous row versions to dedicated **Undo Log Segments**. Older transactions reconstruct earlier snapshots by walking the undo log chain. Trade-off: No heap table bloat, but long-running transactions create massive undo log lag, slowing down read queries and risking undo tablespace exhaustion.

### Staff Tier
**Q: Explain how PostgreSQL's Heap-Only Tuple (HOT) optimization works and what conditions are required for it to activate.**
> **Answer**: HOT eliminates index bloat during `UPDATE` operations. When an update occurs, if (1) none of the table's indexed columns are modified, and (2) the heap page containing the old tuple has sufficient free space, PostgreSQL creates the new tuple version within the *exact same page* and links the old tuple's line pointer directly to the new tuple. Because the tuple remains in the same page and no indexed columns changed, **zero secondary indexes need to be updated**, drastically reducing disk I/O and index page splits.

### Principal Tier
**Q: Design a high-scale partitioning and indexing strategy for a financial ledger table growing by 500 million rows per month, supporting both real-time fraud lookups and historical analytics.**
> **Answer**: A Principal-level architecture implements **Declarative Range Partitioning with Tiered Storage & Hybrid Indexing**:
> 1. **Declarative Partitioning**: Partition the ledger table by `created_at` using monthly or weekly range partitions (`PARTITION BY RANGE (created_at)`).
> 2. **Local Partition Indexes**:
>    - Hot Partitions (current month): High-speed B-Tree composite index `(account_id, created_at)` with `FILLFACTOR = 85` to maximize HOT updates.
>    - Cold Partitions (older months): Re-indexed with `FILLFACTOR = 100`, moved to compressed columnar storage (e.g. `pg_analytics` / DuckDB extension) or AWS EBS cold storage tiers.
> 3. **Partition Pruning**: Ensure all queries include temporal bounds (`created_at BETWEEN ...`) so the PostgreSQL query planner executes **Static/Dynamic Partition Pruning**, scanning only the single relevant monthly partition rather than the entire 6-billion-row dataset.

---

## 14. Hands-on Exercise

### Objective
In FinFlow, design and verify an optimal indexing strategy for `settlement_transactions` that:
1. Optimizes lookup by `(merchant_id, status, created_at)` using a composite index.
2. Implements a partial index for the high-priority `PENDING` queue.
3. Implements a covering index for merchant summary aggregations (`SUM`, `COUNT`).

### Solution

#### Step 1: Database DDL
```sql
-- 1. Composite Index for Range Queries
CREATE INDEX idx_settlement_merchant_status_created 
ON settlement_transactions (merchant_id, status, created_at DESC);

-- 2. Partial Index for Active Pending Processing Queue
CREATE INDEX idx_settlement_pending_queue 
ON settlement_transactions (created_at ASC) 
WHERE status = 'PENDING';

-- 3. Covering Index for Merchant Summary Dashboard
CREATE INDEX idx_settlement_summary_covering 
ON settlement_transactions (merchant_id, status) 
INCLUDE (amount, currency);
```

#### Step 2: Spring Data JPA Repository
```java
@Repository
public interface SettlementTransactionRepository extends JpaRepository<SettlementTransaction, UUID> {

    // Uses Composite Index
    List<SettlementTransaction> findByMerchantIdAndStatusAndCreatedAtBetweenOrderByCreatedAtDesc(
            String merchantId, String status, Instant start, Instant end);

    // Uses Covering Index (Index Only Scan)
    @Query("SELECT new com.finflow.chapter200.dto.SettlementSummaryDto(s.merchantId, s.status, SUM(s.amount), COUNT(s)) " +
           "FROM SettlementTransaction s " +
           "WHERE s.merchantId = :merchantId AND s.status = :status " +
           "GROUP BY s.merchantId, s.status")
    Optional<SettlementSummaryDto> summarizeMerchantSettlements(
            @Param("merchantId") String merchantId,
            @Param("status") String status);
}
```

---

## 15. Advanced Challenge: Declarative Table Partitioning by Range

### Enterprise Problem Statement
To prevent single-table bloat on a 100-million-row financial settlements table, implement PostgreSQL Declarative Range Partitioning by `created_at` with automated partition routing in Spring Boot.

### Enterprise Solution

```sql
-- 1. Create Partitioned Root Table
CREATE TABLE settlement_transactions_partitioned (
    id UUID NOT NULL,
    merchant_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    amount NUMERIC(18, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- 2. Create Monthly Partitions
CREATE TABLE settlements_y2026m08 PARTITION OF settlement_transactions_partitioned
    FOR VALUES FROM ('2026-08-01 00:00:00+00') TO ('2026-09-01 00:00:00+00');

CREATE TABLE settlements_y2026m09 PARTITION OF settlement_transactions_partitioned
    FOR VALUES FROM ('2026-09-01 00:00:00+00') TO ('2026-10-01 00:00:00+00');

-- 3. Local Indexes on Partitions
CREATE INDEX idx_settlements_2026m08_merchant_status 
ON settlements_y2026m08 (merchant_id, status, created_at);
```

---

## 16. Production Checklist

Before approving any pull request involving database queries and indexing:

- [ ] **Leftmost Prefix Rule Verified**: Confirm composite index columns match the order of equality and range predicates in queries.
- [ ] **Predicates are SARGable**: Verify no indexed columns are wrapped in SQL/JPQL functions (`LOWER()`, `UPPER()`, `DATE()`, `TRUNC()`).
- [ ] **No Leading Wildcards**: Ensure `LIKE` queries do not start with `%` on B-Tree indexed columns.
- [ ] **Partial Indexes for Skewed Statuses**: Verify status columns where active states $< 10\%$ use partial index `WHERE` clauses.
- [ ] **Covering Indexes for Aggregations**: Ensure high-throughput dashboards use `INCLUDE` covering indexes for `Index Only Scans`.
- [ ] **Foreign Key Columns Indexed**: Confirm all foreign key relationship columns have supporting indexes.
- [ ] **Autovacuum Scale Factor Tuned**: Confirm tables receiving $> 1,000 \text{ writes/sec}$ have `autovacuum_vacuum_scale_factor` tuned down to `0.05`.
