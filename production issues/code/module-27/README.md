# Module 27: Distributed Microservice Failure & Sagas

## Overview
This module explores distributed transaction management in Spring Boot microservice architectures, deep-diving into the Saga Orchestration pattern with automated reverse compensation, the Dual-Write problem, and the Transactional Outbox pattern.

## Key Scenarios Covered
1. **The Distributed Partial Failure Disaster:**
   - Why microservice workflows that perform sequential REST calls without a Saga Coordinator cause money disappearance when a downstream service fails mid-flow.
   - Solving with Orchestrated Sagas and automated reverse compensating actions (`Refund Wallet -> Cancel Order`).
2. **The Dual-Write Problem:**
   - Why saving to a database and publishing to Kafka sequentially without atomic transactions leads to permanent data divergence during network partitions.
   - Solving with the Transactional Outbox Pattern (persisting business entities and outbox events in the same ACID database transaction).
3. **Idempotent Compensating Actions:**
   - Protecting against duplicate compensation executions during network retries.

## Project Structure
- `src/main/java/.../model/`:
  - `SagaInstance.java` (Saga execution state machine tracking).
  - `OutboxEvent.java` (Transactional Outbox event entity).
  - `OrderEntity.java` (Order domain entity).
- `src/main/java/.../saga/`:
  - `PaymentSagaOrchestrator.java` (Implements 4-step forward execution and automated reverse compensation).
- `src/main/java/.../service/`:
  - `TransactionalOutboxService.java` (Atomic outbox writing and background event relay).
- `src/main/java/.../controller/`:
  - `SagaDiagnosticsController.java` (REST endpoints for executing sagas, inspecting instances, and triggering outbox relay).
- `src/test/java/.../`:
  - `PaymentSagaOrchestratorTest.java`
  - `TransactionalOutboxTest.java`
  - `SagaDiagnosticsControllerTest.java`
  - `Module27IntegrationTest.java`

## How to Run Tests
```bash
mvn clean test
```

## Complete Documentation
For the full deep-dive technical incident guide, see [Module 27 Documentation](../../docs/module-27-distributed-microservice-failure-sagas.md).
