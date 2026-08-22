# Module 27: Distributed Microservice Failure & Sagas

## Issue 27.1: Distributed Partial Failures, Missing Compensating Transactions, and the Dual-Write Outbox Hazard

---

### 1. Scenario

During cross-border currency conversion on the **FinFlow Cross-Border Payment & FX Settlement Engine**:
1. A customer initiated a **$5,000.00 USD to EUR transfer**. The workflow spanned 4 independent microservices: `OrderService`, `PaymentWalletService`, `ForeignExchangeService`, and `NotificationService`.
2. The legacy application executed the workflow using standard synchronous REST calls:
   - `OrderService` created the order record (Status: `PENDING`).
   - `PaymentWalletService` successfully **debited $5,000.00** from the customer's wallet.
   - `ForeignExchangeService` timed out with an `HTTP 503 Service Unavailable / SocketTimeoutException` due to an upstream bank liquidity outage.
3. Because the system lacked a **Saga Coordinator** and had no automated compensating rollback logic, the transaction thread crashed.
4. **The customer's wallet was debited $5,000.00, but the EUR conversion never occurred and the order was never fulfilled.** The customer lost $5,000 with no record of a refund (**Distributed Partial Failure & Money Loss**).
5. Concurrently, an `OrderService` feature attempted to publish order events to Kafka:
   `orderRepository.save(order);` followed by `kafkaTemplate.send("orders", order);`
6. When the database committed the order but the Kafka broker network connection dropped before the message was acknowledged, the event was lost forever. Downstream fraud screening and fulfillment services **never received the order**, causing permanent data divergence (**The Dual-Write Problem**).

---

### 2. Symptoms

```text
1. Financial Ledger Inconsistencies Across Microservices:
   WalletService records a successful debit of $5,000; OrderService records order as PENDING; FxService has no record.
   Money disappears from user balances without corresponding merchant delivery.

2. Permanent Database-to-Kafka Event Discrepancies (Dual-Write Loss):
   PostgreSQL database contains records that never exist on Kafka topics, or vice versa.

3. Cyclic Event Storms in Choreographed Sagas:
   Microservices trigger infinite ping-pong loops: OrderCreated -> PaymentFailed -> CancelOrder -> Restock -> Retry.

4. Duplicate Refund Inconsistencies (Non-Idempotent Compensations):
   Compensating refund events retried over the network cause customers to be refunded twice ($10,000 refunded on $5,000 order).

5. Two-Phase Commit (2PC) Resource Starvation:
   X/Open XA distributed transactions hold database locks across network boundaries for 30+ seconds.
```

---

### 3. Possible Root Causes

1. **Synchronous Distributed REST Chains Without Saga Orchestration:** Attempting to maintain distributed consistency through raw HTTP calls without state tracking or rollback handlers.
2. **Missing Transactional Outbox Pattern:** Executing database writes and message broker publishes in separate non-atomic steps instead of a single ACID database transaction.
3. **Non-Idempotent Compensating Actions:** Failing to use idempotency keys on refund and cancellation endpoints, causing duplicate payouts during network retries.
4. **Out-of-Order Compensation Arrival:** Network latency causing a compensating refund message to arrive at the wallet service *before* the original debit message has finished processing.
5. **Relying on Two-Phase Commit (2PC) Across Cloud Microservices:** Using blocking XA transactions that deadlock under network latency.

---

### 4. Architecture Context: Saga Orchestrator & Transactional Outbox Pattern

```text
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                     ORCHESTRATED SAGA PATTERN & REVERSE COMPENSATION                            │
│                                                                                                 │
│  [Client: $5,000 Transfer Request]                                                              │
│                 │                                                                               │
│                 ▼                                                                               │
│  ┌───────────────────────────────────────────────────────────────────────────────────────────┐  │
│  │ PaymentSagaOrchestrator (State Machine & Saga Execution Log)                              │  │
│  │                                                                                           │  │
│  │   FORWARD EXECUTION:                                                                      │  │
│  │   1. [Step 1] OrderService: Create Order (Status: PENDING) ──► SUCCESS!                   │  │
│  │   2. [Step 2] WalletService: Debit $5,000 USD ───────────────► SUCCESS!                   │  │
│  │   3. [Step 3] FxService: Reserve EUR Liquidity ──────────────► ❌ HTTP 503 FAILURE!       │  │
│  │                                                                                           │  │
│  │   AUTOMATED REVERSE COMPENSATION (LIFO Order):                                            │  │
│  │   4. [Compensate Step 2] WalletService: Refund $5,000 USD ───► REFUNDED! (Balance OK)     │  │
│  │   5. [Compensate Step 1] OrderService: Cancel Order ─────────► CANCELLED! (Status OK)     │  │
│  │                                                                                           │  │
│  │   Final Saga Status: COMPENSATED (Zero Financial Drift!)                                  │  │
│  └───────────────────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                                 │
│  ┌───────────────────────────────────────────────────────────────────────────────────────────┐  │
│  │ THE TRANSACTIONAL OUTBOX PATTERN (Zero Dual-Write Loss):                                  │  │
│  │                                                                                           │  │
│  │   ACID Transaction (PostgreSQL):                                                          │  │
│  │   BEGIN;                                                                                  │  │
│  │     INSERT INTO orders (id, status, amount) VALUES ('ORD-101', 'CONFIRMED', 5000.00);     │  │
│  │     INSERT INTO outbox_events (id, aggregate, payload) VALUES ('EVT-1', 'ORDER', ...);    │  │
│  │   COMMIT;  <-- Atomically guaranteed by single DB engine!                                 │  │
│  │                                                                                           │  │
│  │   Background Relay / CDC (Debezium) ──► Reads outbox_events ──► Dispatches to Kafka Topic │  │
│  └───────────────────────────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 5. How to Reproduce the Issues

#### ❌ Anti-Pattern 1: Uncoordinated REST Chain (Money Disappearance)
```java
// ❌ FATAL ANTI-PATTERN: If fxClient fails, customer is debited $5000 with NO REFUND!
public void processPayment(String accountId, double amount) {
    orderClient.createOrder(accountId, amount);
    walletClient.debit(accountId, amount); // $5000 deducted
    fxClient.reserveLiquidity(amount);     // ❌ THROWS 503! Process terminates here.
    orderClient.confirmOrder();
}
```

#### ❌ Anti-Pattern 2: The Dual-Write Flaw (DB vs Kafka Disconnect)
```java
// ❌ ANTI-PATTERN: If network blips before kafka.send completes, event is lost forever!
@Transactional
public void createOrder(Order order) {
    orderRepository.save(order); // DB Commits
    kafkaTemplate.send("orders", order.getId(), order); // ❌ Throws SocketTimeoutException!
}
```

---

### 6. Evidence Collection & Diagnostic Probing

#### Method 1: Inspect Saga Instance Execution History
```sql
SELECT saga_id, saga_type, status, current_step, failure_reason, created_at, updated_at
FROM saga_instances
ORDER BY created_at DESC;
```
**Diagnostic Output:**
```text
saga_id       | status      | current_step       | failure_reason
SAGA-998811   | COMPENSATED | STEP_3_RESERVE_FX  | FX Liquidity Provider 503 Service Unavailable
```

#### Method 2: Inspect Pending Outbox Events
```sql
SELECT id, aggregate_type, aggregate_id, event_type, status, created_at
FROM outbox_events
WHERE status = 'PENDING';
```

---

### 7. Step-by-Step Debugging Procedure

```text
Step 1: Inspect Distributed Trace (Zipkin/Tempo) for Failed Step in Distributed Flow.
        Locate the exact microservice span that returned HTTP 5xx or timed out.

Step 2: Check Saga Execution Log (`saga_instances` table).
        Verify whether the state machine transitioned to COMPENSATING and executed all rollback steps.

Step 3: Verify All Compensating Actions are Idempotent.
        Ensure refund and cancellation endpoints check idempotency keys to prevent duplicate credits.

Step 4: Implement Transactional Outbox for Message Dispatch.
        Write business data and event records into the same ACID database transaction.

Step 5: Deploy a Reliable Outbox Relay / CDC Worker.
        Use Debezium or a scheduled poller to guarantee At-Least-Once event delivery to Kafka.
```

---

### 8. Technical Root Cause Deep-Dive

#### 1. Why 2-Phase Commit (2PC / XA) Fails in Distributed Systems
- Two-Phase Commit requires a central Transaction Manager coordinating `prepare` and `commit` phases across all participating databases.
- If any node crashes or a network partition occurs between Phase 1 and Phase 2, all participating databases hold row locks indefinitely.
- In cloud environments with ephemeral containers and network latency, 2PC degrades throughput by $10\text{x}$ to $100\text{x}$ and causes cluster-wide lock deadlocks.

#### 2. The Mechanics of Saga Orchestration & Reverse Compensation
- A Saga is a sequence of local ACID transactions: $T_1, T_2, \dots, T_n$.
- Each transaction $T_i$ has a corresponding **Compensating Transaction** $C_i$ that semantically undoes the effect of $T_i$.
- If transaction $T_k$ fails ($1 \le k \le n$):
- The orchestrator halts forward execution.
- The orchestrator executes compensations in reverse order: $C_{k-1}, C_{k-2}, \dots, C_1$.
- **Result:** The system reaches eventual consistency without holding distributed database locks.

#### 3. The Dual-Write Problem & Transactional Outbox
- Dual-write occurs when a single business operation requires updating two distinct storage systems (e.g. PostgreSQL and Apache Kafka) without a distributed transaction.
- Because physical network failures can occur between Step 1 and Step 2, one system will inevitably succeed while the other fails.
- The **Transactional Outbox Pattern** solves this by writing the message into an `outbox_events` table within the *same local database transaction* as the business entity. A separate asynchronous process reads the outbox table and publishes to Kafka with guaranteed At-Least-Once semantics.

---

### 9. Production-Grade Fixes

#### ✅ Fix 1: Saga Orchestrator with Reverse Compensation (`PaymentSagaOrchestrator.java`)
```java
@Service
public class PaymentSagaOrchestrator {

    @Transactional
    public SagaExecutionResult executePaymentSaga(String accountId, double amount, boolean simulateFxFailure) {
        String sagaId = "SAGA-" + UUID.randomUUID().toString().substring(0, 8);
        SagaInstance saga = new SagaInstance(sagaId, "PAYMENT_SAGA", SagaStatus.STARTED, "...");
        sagaRepository.save(saga);

        boolean walletDebited = false;
        boolean orderCreated = false;

        try {
            // Step 1: Create Order
            orderRepository.save(new OrderEntity(orderId, accountId, amount, "PENDING"));
            orderCreated = true;

            // Step 2: Debit Wallet
            walletService.debit(accountId, amount);
            walletDebited = true;

            // Step 3: Reserve FX
            if (simulateFxFailure) throw new IllegalStateException("FX 503 Unavailable");
            fxService.reserve(amount);

            // Step 4: Confirm Order
            orderRepository.save(new OrderEntity(orderId, accountId, amount, "CONFIRMED"));
            saga.setStatus(SagaStatus.COMPLETED);
            return new SagaExecutionResult(SagaStatus.COMPLETED);

        } catch (Exception ex) {
            // Reverse Compensation
            saga.setStatus(SagaStatus.COMPENSATING);
            if (walletDebited) walletService.refund(accountId, amount);
            if (orderCreated) orderRepository.save(new OrderEntity(orderId, accountId, amount, "CANCELLED"));
            saga.setStatus(SagaStatus.COMPENSATED);
            return new SagaExecutionResult(SagaStatus.COMPENSATED);
        }
    }
}
```

#### ✅ Fix 2: Transactional Outbox Service (`TransactionalOutboxService.java`)
```java
@Service
public class TransactionalOutboxService {

    @Transactional
    public OrderEntity createOrderWithOutboxEvent(String accountId, BigDecimal amount) {
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);
        OrderEntity order = orderRepository.save(new OrderEntity(orderId, accountId, amount, "CREATED"));

        // Saved in the exact same ACID database transaction!
        outboxRepository.save(new OutboxEvent("ORDER", orderId, "OrderCreatedEvent", "..."));
        return order;
    }
}
```

---

### 10. Verification

1. **Successful Saga Test:** Run `PaymentSagaOrchestratorTest.java` to verify that all 4 steps complete successfully with order confirmed and wallet debited.
2. **Reverse Compensation Test:** Run `PaymentSagaOrchestratorTest.java` to verify that when Step 3 fails, the customer wallet is fully refunded and the order is cancelled.
3. **Transactional Outbox Test:** Run `TransactionalOutboxTest.java` to verify atomic database write of business entity and outbox event.
4. **Integration Test:** Run `Module27IntegrationTest.java` to verify Spring context and Actuator health.

---

### 11. Prevention & Production Readiness

1. **Rule: Never Chain Multi-Service Business Logic Without Sagas:**
   Any operation modifying state across $>1$ microservice must be managed by a Saga Orchestrator.
2. **Rule: Never Publish Events Directly Inside JPA `@Transactional` Methods:**
   Always use the Transactional Outbox Pattern to eliminate dual-write data loss.
3. **Prometheus Alerting Rule for Failed Saga Compensations:**
```yaml
- alert: SagaCompensationFailed
  expr: increase(saga_instances_total{status="FAILED"}[5m]) > 0
  labels:
    severity: critical
  annotations:
    summary: "Distributed Saga failed during reverse compensation; manual intervention required"
```

---

### 12. Interview & Production Questions

#### Interview Questions
1. **Q: What is the difference between Orchestration-based Sagas and Choreography-based Sagas?**
   *Answer:* In Orchestration, a central coordinator service explicitly directs each microservice what to execute and coordinates compensating actions if a step fails. In Choreography, microservices listen to domain events and publish next-step events independently without a central coordinator. Orchestration is preferred for complex financial workflows because it provides centralized visibility and prevents cyclic event loops.
2. **Q: How does the Transactional Outbox pattern solve the Dual-Write problem?**
   *Answer:* Dual-write fails when a database write succeeds but an immediate Kafka publish fails due to network issues. The Outbox pattern writes the event into an `outbox_events` table inside the *exact same ACID database transaction* as the business entity. A separate background worker reads the outbox table and relays messages to Kafka with guaranteed At-Least-Once delivery.
3. **Q: Why must compensating transactions in a Saga be idempotent?**
   *Answer:* In distributed networks, message brokers or orchestrators may retry compensating actions (e.g. `refundWallet`) due to network timeouts or transient acknowledgment failures. If the refund endpoint is not idempotent, a customer could be refunded twice for a single cancelled order.
4. **Q: What is a Semantic Rollback in a Saga versus an ACID Rollback?**
   *Answer:* An ACID rollback reverts physical database pages to their exact prior state. A Semantic Rollback cannot revert committed transactions; instead, it executes a new forward compensating transaction (e.g. issuing a credit transaction to counteract an earlier debit transaction).
5. **Q: What happens if a compensating action in a Saga fails?**
   *Answer:* The orchestrator must retry the compensating action with exponential backoff. If retries are exhausted, the saga transitions to `FAILED` and routes to a Dead Letter Queue / alert dashboard for human operator reconciliation.

#### Production Incident Questions
1. **Incident:** A customer was debited $1,200 for a flight booking, but the airline API returned 504 and no ticket was issued. The customer never received a refund. What happened?
   *Diagnosis:* Synchronous REST chain without a Saga Coordinator. When the third step failed, the thread crashed without invoking the compensating refund. Fix: Implement Orchestrated Saga with automated reverse compensation.
2. **Incident:** 200 orders exist in PostgreSQL that were never sent to the shipping warehouse topic in Kafka. Why?
   *Diagnosis:* Dual-write anti-pattern (`save()` followed by `kafka.send()`). A Kafka broker restart caused `kafka.send()` to fail after DB commit. Fix: Implement the Transactional Outbox pattern.
3. **Incident:** An e-commerce service entered an infinite loop of creating orders, failing payment, and restocking inventory 1,000 times per minute. Why?
   *Diagnosis:* Cyclic event storm in a Choreography-based saga. Fix: Switch to Orchestration-based Saga with explicit state transitions.
4. **Incident:** A customer received two $500 refunds for a single failed purchase. Why?
   *Diagnosis:* The orchestrator retried the compensating refund call due to a socket timeout, but the wallet service was non-idempotent. Fix: Enforce idempotency keys on all compensating endpoints.
5. **Incident:** A Two-Phase Commit (XA) transaction locked the `accounts` table for 45 seconds during network latency. How do you resolve it?
   *Diagnosis:* 2PC holds row locks across network boundaries. Fix: Replace 2PC with asynchronous Saga orchestration and eventual consistency.

#### Trick Questions
1. **Trick:** Does the Saga pattern guarantee ACID isolation across microservices?
   *Answer:* No! Sagas guarantee Atomicity, Consistency, and Durability, but lack **Isolation** because intermediate states (e.g. debited wallet before order confirmation) are visible to other concurrent transactions. Sagas manage this using semantic locks (e.g. `PENDING` states).
2. **Trick:** If an Outbox relay crashes after publishing to Kafka but before marking the event `PUBLISHED` in the database, what happens?
   *Answer:* The relay restarts and re-publishes the same message to Kafka (At-Least-Once delivery). Downstream consumers must implement **idempotent message handling** (e.g. tracking processed message IDs).
3. **Trick:** Can a compensating transaction simply delete the database row created by the forward step?
   *Answer:* In financial and audited systems, NO! Deleting rows violates regulatory audit trails. A compensation must insert an explicit compensating record (e.g. `CANCELLED` status or `REFUND` ledger entry).

---

*(Answers and detailed explanations are provided in the Master Answer Guide)*
