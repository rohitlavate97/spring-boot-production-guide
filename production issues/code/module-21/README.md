# Module 21: Concurrency, Race Conditions & Distributed Locks

## Overview
This module explores concurrent programming pitfalls, Check-Then-Act race conditions, Lost Updates, atomic Compare-And-Swap (CAS) SQL updates, and Distributed Locking failure modes (lock lease expiration, blind `DEL` releasing foreign locks, and circular deadlocks).

## Key Scenarios Covered
1. **Check-Then-Act Race Conditions (Double Spending):**
   - Why concurrent reads of mutable state before write commits allow users to over-debit balances (e.g. withdrawing $1,000 from a $500 balance).
   - Atomic in-database CAS updates (`UPDATE accounts SET balance = balance - :amount WHERE id = :id AND balance >= :amount`).
2. **Distributed Lock Lease Expiry & Blind Release Trap:**
   - Why slow database transactions cause distributed locks to auto-expire, allowing Process B to acquire the lock while Process A accidentally deletes Process B's lock via blind `DEL`.
   - Implementing safe Lua script release (`if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end`).
3. **Deadlock-Free Bidirectional Transfers:**
   - Preventing AB-BA circular wait deadlocks across concurrent transfers by enforcing deterministic lexicographical lock ordering (`min(accA, accB) -> max(accA, accB)`).
4. **Local JVM Lock Fallacy in Multi-Instance Deployments:**
   - Why `synchronized` and `ReentrantLock` fail across multi-node microservices.

## Project Structure
- `src/main/java/.../service/`:
  - `DistributedLockService.java` (Implements atomic lock acquisition, safe Lua release, and lease expiration tracking).
  - `WalletBalanceService.java` (Implements unsafe debit, atomic CAS debit, and deadlock-free transfers).
- `src/main/java/.../controller/`:
  - `ConcurrencyDiagnosticsController.java` (REST endpoints for race condition simulation, balance queries, and lock release traps).
- `src/test/java/.../`:
  - `RaceConditionLostUpdateTest.java`
  - `DistributedLockSafetyTest.java`
  - `DeadlockFreeTransferTest.java`
  - `ConcurrencyDiagnosticsControllerTest.java`
  - `Module21IntegrationTest.java`

## How to Run Tests
```bash
mvn clean test
```

## Complete Documentation
For the full deep-dive technical incident guide, see [Module 21 Documentation](../../docs/module-21-concurrency-race-conditions-distributed-locks.md).
