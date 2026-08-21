# Module 07: Spring Transactions & Isolation Hazards

## Overview
This module investigates Spring `@Transactional` mechanics, propagation levels (`REQUIRED`, `REQUIRES_NEW`), rollback policies on checked vs unchecked exceptions, swallowed exception traps, and HikariCP connection pool starvation deadlocks.

## Key Scenarios Covered
1. **Swallowed Exception Dirty Commits:** Catching `RuntimeException` inside a `@Transactional` method without rethrowing or setting `setRollbackOnly()` commits corrupt partial state.
2. **Checked Exception Rollback Trap:** Why Spring defaults to committing on checked exceptions unless `rollbackFor = Exception.class` is declared.
3. **`REQUIRES_NEW` Audit Log Isolation:** Verifies that inner independent transactions commit audit logs even when the outer transaction rolls back.
4. **HikariCP Connection Pool Sizing:** Explains how nested `REQUIRES_NEW` transactions hold multiple connections per thread, risking pool starvation deadlocks under concurrency.

## Project Structure
- `src/main/java/.../entity/`: `AccountEntity.java` (`@Version`), `AuditLogEntity.java`.
- `src/main/java/.../repository/`: `AccountRepository.java`, `AuditLogRepository.java`.
- `src/main/java/.../service/`: `BankingTransactionService.java`, `AuditLogService.java`.
- `src/main/java/.../controller/`: `BankingTransactionController.java`.
- `src/test/java/.../`:
  - `TransactionRollbackRulesTest.java`
  - `SwallowedExceptionRollbackTest.java`
  - `RequiresNewAuditIsolationTest.java`
  - `Module07IntegrationTest.java`

## How to Run Tests
```bash
mvn clean test
```

## Complete Documentation
For the full 12-section technical incident guide, see [Module 07 Documentation](../../docs/module-07-spring-transactions.md).
