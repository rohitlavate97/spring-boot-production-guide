# Module 10: Database Performance, Query Plans & Deadlocks

## Overview
This module explores database deadlock reproduction, lock acquisition ordering, missing indexes (`EXPLAIN ANALYZE`), pessimistic vs. optimistic concurrency control, and automatic deadlock retry strategies using Spring Retry.

## Key Scenarios Covered
1. **Circular Deadlocks in Bidirectional Transfers:**
   - Why transferring money between Account A and Account B concurrently with Account B and Account A causes `DeadlockLoserDataAccessException`.
2. **Deterministic Lock Ordering:**
   - Sorting entity IDs before acquiring `SELECT ... FOR UPDATE` locks (`minId` locked before `maxId`), mathematically eliminating circular wait conditions.
3. **Pessimistic vs. Optimistic Locking:**
   - Using `@Lock(LockModeType.PESSIMISTIC_WRITE)` with lock timeouts vs `@Version` with Spring Retry (`@Retryable`).
4. **Execution Plan Analysis (`EXPLAIN ANALYZE`):**
   - Sequential Scans vs. Index Scans, function-wrapped index invalidations, and composite index prefix rules.

## Project Structure
- `src/main/java/.../entity/`: `LedgerAccountEntity.java` (`@Version`, indexed `accountNumber`).
- `src/main/java/.../repository/`: `LedgerAccountRepository.java` (`findByIdForUpdate` with pessimistic lock and query hints).
- `src/main/java/.../service/`: `TransferService.java` (unordered transfer, deterministic transfer, and optimistic transfer with retry).
- `src/main/java/.../controller/`: `TransferController.java`.
- `src/test/java/.../`:
  - `DeterministicLockOrderingTest.java`
  - `OptimisticLockRetryTest.java`
  - `Module10IntegrationTest.java`

## How to Run Tests
```bash
mvn clean test
```

## Complete Documentation
For the full 12-section technical incident guide, see [Module 10 Documentation](../../docs/module-10-database-performance-query-plans-deadlocks.md).
