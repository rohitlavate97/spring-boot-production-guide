# Module 08: JPA & Hibernate Production Bottlenecks

## Overview
This module explores critical JPA and Hibernate ORM production bottlenecks, including the N+1 select query problem, `LazyInitializationException`, Open-Session-in-View (OSIV) anti-patterns, First-Level Cache (L1) bloat during batch jobs, and batch insert optimizations.

## Key Scenarios Covered
1. **N+1 Select Query Elimination:**
   - Standard lazy traversal causes N additional queries for N parents.
   - Solution A: Explicit JPQL `JOIN FETCH`.
   - Solution B: Spring Data JPA `@EntityGraph(attributePaths = {"orders"})`.
   - Solution C: `hibernate.default_batch_fetch_size: 30`.
2. **`LazyInitializationException` & OSIV Disabling:**
   - Why disabling `spring.jpa.open-in-view=false` throws `LazyInitializationException` outside transactions, protecting DB connection pools from view-layer starvation.
3. **First-Level Cache (L1) Eviction:**
   - Prevents `OutOfMemoryError` during large data imports by invoking `entityManager.flush()` and `entityManager.clear()`.
4. **JDBC Batch Inserts:**
   - Configures `hibernate.jdbc.batch_size` and explains why `GenerationType.IDENTITY` disables JDBC batching.

## Project Structure
- `src/main/java/.../entity/`: `CustomerEntity.java`, `OrderEntity.java`.
- `src/main/java/.../repository/`: `CustomerRepository.java` (`JOIN FETCH` & `@EntityGraph`).
- `src/main/java/.../service/`: `CustomerBatchService.java`.
- `src/main/java/.../controller/`: `CustomerDiagnosticsController.java`.
- `src/test/java/.../`:
  - `NPlusOneDetectionTest.java`
  - `LazyInitializationExceptionTest.java`
  - `BatchProcessingL1ClearTest.java`
  - `Module08IntegrationTest.java`

## How to Run Tests
```bash
mvn clean test
```

## Complete Documentation
For the full 12-section technical incident guide, see [Module 08 Documentation](../../docs/module-08-jpa-hibernate-bottlenecks.md).
