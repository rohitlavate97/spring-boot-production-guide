# Module 09: Database & HikariCP Connection Pool Exhaustion

## Overview
This module investigates HikariCP connection pool mechanics, pool starvation diagnosis, connection leak detection, mathematical pool sizing, and Prometheus telemetry metrics.

## Key Scenarios Covered
1. **Connection Pool Starvation (`ConnectionTimeoutException`):**
   - High concurrency with insufficient `maximum-pool-size` causing threads to block waiting for database connections.
2. **Holding DB Connections During External I/O (Anti-Pattern):**
   - Why executing HTTP REST calls inside `@Transactional` methods starves connection pools.
3. **HikariCP Leak Detection (`leak-detection-threshold`):**
   - Tracing unclosed raw JDBC connections and long-running transaction boundaries.
4. **Pool Telemetry via `HikariPoolMXBean`:**
   - Programmatically monitoring active, idle, pending, and total connection metrics.

## Project Structure
- `src/main/java/.../entity/`: `SettlementEntity.java`.
- `src/main/java/.../repository/`: `PaymentSettlementRepository.java`.
- `src/main/java/.../service/`: `HikariPoolMetricsService.java`, `LeakSimulationService.java`.
- `src/main/java/.../controller/`: `ConnectionPoolDiagnosticsController.java`.
- `src/test/java/.../`:
  - `HikariPoolMetricsTest.java`
  - `ConnectionPoolStarvationTest.java`
  - `Module09IntegrationTest.java`

## How to Run Tests
```bash
mvn clean test
```

## Complete Documentation
For the full 12-section technical incident guide, see [Module 09 Documentation](../../docs/module-09-hikaricp-connection-pool-exhaustion.md).
