# Module 06: Spring AOP & Proxy Traps

## Overview
This module explores Spring AOP proxy mechanics (CGLIB / JDK dynamic proxies), the infamous self-invocation trap (`this.method()`), method visibility constraints, and aspect ordering.

## Key Scenarios Covered
1. **Self-Invocation Aspect Bypass:** Calling an annotated method via `this.method()` bypasses the CGLIB proxy wrapper, causing `@Transactional`, `@Async`, `@Cacheable`, and custom aspects to silently not execute.
2. **Collaborator Bean Extraction:** Architectural refactoring pattern separating business tasks into distinct Spring beans.
3. **Self-Injected Proxy Pattern:** Resolving self-invocation using `@Lazy MyService selfProxy`.
4. **Aspect Precedence & `@Order`:** Controlling advice execution sequences across multiple cross-cutting concerns.

## Project Structure
- `src/main/java/.../annotation/`: `@AuditedTransaction`.
- `src/main/java/.../aspect/`: `TransactionAuditAspect.java`.
- `src/main/java/.../service/`: `AccountBalanceService.java`, `AccountDebitExecutor.java`.
- `src/main/java/.../controller/`: `AopDiagnosticsController.java`.
- `src/test/java/.../`:
  - `SelfInvocationAopTrapTest.java`
  - `ProxyInvocationFixedTest.java`
  - `Module06IntegrationTest.java`

## How to Run Tests
```bash
mvn clean test
```

## Complete Documentation
For the full 12-section technical incident guide, see [Module 06 Documentation](../../docs/module-06-aop-proxy-problems.md).
