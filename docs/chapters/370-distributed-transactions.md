---
chapter: 370
topic: Distributed Transactions — Saga Pattern, Transactional Outbox, Choreography vs Orchestration
prerequisite_chapters: [10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200, 210, 220, 230, 240, 250, 260, 270, 280, 290, 300, 310, 320, 330, 340, 350, 360]
reference_system_node: Payment Service ↔ Order Service ↔ Ledger Service ↔ Kafka Topic with Transactional Outbox & Saga Orchestrator
---

# Chapter 370: Distributed Transactions — Saga Pattern, Transactional Outbox, Choreography vs Orchestration

## 1. Concept

In a monolithic database architecture, transactional integrity is guaranteed by relational database **ACID** properties (Atomicity, Consistency, Isolation, Durability) managed by a single database transaction coordinator.

In a distributed microservice architecture, where each microservice owns its private database (e.g. `order-db`, `payment-db`, `ledger-db`), cross-database transactions cannot rely on standard database transactions.

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                           The Distributed Transaction Landscape                                 │
│                                                                                                 │
│  ❌ Legacy 2PC (Two-Phase Commit / XA):                                                         │
│     - Global locks across all databases during PREPARE and COMMIT phases.                       │
│     - Latency cascades; coordinator failure locks rows indefinitely; anti-cloud native.         │
│                                                                                                 │
│  ✅ Saga Pattern with Eventual Consistency (BASE):                                               │
│     - Sequence of local ACID transactions across independent services.                          │
│     - Failure in any step triggers a series of COMPENSATING TRANSACTIONS undoing prior work.    │
│                                                                                                 │
│  ✅ Transactional Outbox Pattern:                                                               │
│     - Solves the "Dual-Write Problem" (DB commit vs Message Broker publish).                    │
│     - Saves domain entity + event table in 1 local ACID transaction, then dispatches reliably.  │
│                                                                                                 │
│  ✅ Idempotent Consumer:                                                                        │
│     - Converts message broker "at-least-once" delivery into "exactly-once" business semantics.  │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘
```

### Core Patterns Explained

1. **Why 2PC (Two-Phase Commit / XA) is Dead in Modern Microservices:**
   - In 2PC, a centralized coordinator sends `PREPARE` to all databases. Every database holds pessimistic row and table locks until the coordinator sends `COMMIT`.
   - If a network partition occurs between Phase 1 and Phase 2, locks remain open indefinitely, starving thread pools, skyrocketing P99 latency, and violating high availability (CAP theorem CP vs AP).

2. **The Saga Pattern:**
   - A Saga is an architectural pattern that breaks a distributed transaction into a sequence of **local transactions**.
   - Each local transaction updates its private database and publishes a message/event.
   - If a step fails (e.g. insufficient merchant balance), the Saga executes **Compensating Transactions** backward to undo changes made by earlier steps.

3. **Choreography vs. Orchestration:**
   - **Choreography (Event-Driven / Decentralized):** Each microservice listens to domain events from sibling services and executes its local transaction independently.
     - *Pros:* High decoupling, no centralized single point of failure.
     - *Cons:* Distributed workflow is difficult to visualize; high risk of cyclic dependencies.
   - **Orchestration (Command-Driven / Centralized):** A dedicated **Saga Orchestrator** manages the state machine, sends explicit commands to participant services, evaluates responses, and triggers compensations on failure.
     - *Pros:* Centralized visibility, easy to audit state, clear rollback path.
     - *Cons:* Additional orchestrator service to maintain.

4. **The Dual-Write Problem & Transactional Outbox:**
   - Executing `databaseRepository.save(order)` followed immediately by `kafkaTemplate.send(topic, event)` inside `@Transactional` is inherently broken:
     - If the database commits but the Kafka broker is down $\rightarrow$ Ghost state (DB updated, downstream never notified).
     - If Kafka succeeds but the database transaction rolls back $\rightarrow$ Phantom event (downstream processes an action that never existed in the source DB).
   - **Solution:** Write both the domain entity and the outbox event to the **same local database** inside one atomic transaction. A background poller or Change Data Capture (CDC / Debezium) stream then publishes the outbox events to Kafka.

---

## 2. Internal Working

### 2.1 The Transactional Outbox Mechanics

```
  HTTP Request ──► [Payment Service]
                         │
                         ▼ @Transactional BEGIN
  ┌───────────────────────────────────────────────────────────┐
  │  INSERT INTO payment_orders (id, order_id, amount, ...)  │ (Local ACID Commit)
  │  INSERT INTO outbox_events (id, aggregate_id, payload, ..)│
  └───────────────────────────────────────────────────────────┘
                         │
                         ▼ COMMIT (Both or Neither!)
                         │
        ┌────────────────┴────────────────┐
        │                                 │
  [Async Outbox Poller]            [Debezium CDC Engine]
  (SELECT ... FOR UPDATE           (Tail Postgres WAL
   SKIP LOCKED)                     Logical Replication)
        │                                 │
        └────────────────┬────────────────┘
                         ▼
             [Kafka Message Broker]
                         │
                         ▼ At-Least-Once Delivery
             [Ledger Consumer Service]
                         │
                         ▼ (Idempotency Check)
  ┌───────────────────────────────────────────────────────────┐
  │ INSERT INTO processed_messages (msg_id, group) UNIQUE    │
  │ ON CONFLICT DO NOTHING;                                  │
  └───────────────────────────────────────────────────────────┘
```

#### SQL Atomicity Guarantee:
```sql
BEGIN TRANSACTION;

-- 1. Mutate Domain Entity
INSERT INTO payment_orders (order_id, merchant_id, amount, currency, status, created_at)
VALUES ('ORD-99120', 'MERCHANT-01', 250.00, 'USD', 'AUTHORIZED', NOW());

-- 2. Insert Outbox Event in the SAME ACID Transaction
INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, payload, status, retry_count, created_at)
VALUES ('evt-1a2b3c', 'PAYMENT_ORDER', 'ORD-99120', 'PAYMENT_AUTHORIZED', '{"amount":250.00,"orderId":"ORD-99120"}', 'PENDING', 0, NOW());

COMMIT;
```

---

### 2.2 Saga State Machine Transitions

A production Saga Orchestrator manages explicit state transitions to prevent lost updates and dirty reads:

```
[STARTED]
   │
   ├──► (Step 1: Authorize Payment)
   │         │
   │         ├──► Success ──► [PAYMENT_AUTHORIZED]
   │         │                       │
   │         │                       ├──► (Step 2: Commit Ledger Journal)
   │         │                       │         │
   │         │                       │         ├──► Success ──► [LEDGER_COMMITTED] ──► [COMPLETED]
   │         │                       │         │
   │         │                       │         └──► Failure (Account Frozen / Validation Error)
   │         │                       │                  │
   │         │                       │                  ▼
   │         │                       │         [COMPENSATING_PAYMENT]
   │         │                       │                  │
   │         │                       │                  ▼ (Refund / Void Payment via Outbox)
   │         │                       │         [COMPENSATED_FAILED]
   │         │                       │
   │         └──► Failure ──► [PAYMENT_FAILED]
```

### 2.3 Idempotent Consumer Mechanics

Because message brokers guarantee **at-least-once** delivery, network timeouts during ACKs will cause duplicate message delivery. 

The subscriber prevents double-processing by maintaining an atomic **deduplication log**:

```sql
-- Deduplication check within subscriber's transaction
INSERT INTO processed_messages (message_id, consumer_group, processed_at)
VALUES ('evt-1a2b3c', 'ledger-consumer-group', NOW());
-- If primary key / unique constraint fails -> abort duplicate processing immediately!
```

---

## 3. Enterprise Scenario: FinFlow Distributed Checkout

In the FinFlow Payment Platform:
- **Order Service:** Creates orders in `ORDER_PENDING` state.
- **Payment Service:** Authorizes credit card transactions and inserts `PAYMENT_AUTHORIZED` outbox records.
- **Ledger Service:** Updates double-entry accounts (`Merchant Clearing Account` vs `Platform Liability Account`).

### The Disaster Workflow:
When a merchant account has an active compliance freeze in the Ledger Service:
1. `Payment Service` successfully authorizes $500 on the customer's credit card.
2. `Ledger Service` rejects the transaction due to a compliance freeze.
3. The `Saga Orchestrator` catches the ledger rejection and triggers a **Compensating Transaction**:
   - Voids/refunds the customer's credit card authorization.
   - Updates the payment status to `REVERSED`.
   - Emits a `PAYMENT_REVERSED` Outbox event to notify the customer.

---

## 4. Incorrect Implementation

The snippet below demonstrates the disastrous **Naive Dual-Write Anti-Pattern**:

```java
package com.finflow.chapter370.incorrect;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BrokenDualWritePaymentService {

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public BrokenDualWritePaymentService(PaymentRepository paymentRepository,
                                         KafkaTemplate<String, String> kafkaTemplate) {
        this.paymentRepository = paymentRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * CATASTROPHIC DUAL-WRITE ANTI-PATTERN:
     * Mixing database write and Kafka publish in the same method!
     */
    @Transactional
    public void processPayment(PaymentRequest request) {
        // 1. Write to local database
        Payment payment = new Payment(request.getOrderId(), request.getAmount());
        paymentRepository.save(payment);

        // 2. Publish to Kafka inside @Transactional
        // If Kafka times out or broker is unreachable:
        // - Exception is thrown
        // - DB rolls back, BUT if Kafka had already received the message -> PHANTOM EVENT!
        // - Or if DB commit fails after Kafka sends -> DUPLICATE GHOST TRANSACTION!
        kafkaTemplate.send("payment-events", request.getOrderId(), "{\"status\":\"AUTHORIZED\"}");
    }
}
```

---

## 5. Production Incident

```
INCIDENT REPORT: INC-92410
Severity: SEV-1 (Dual-Write Financial Desynchronization)
Impact: 8,400 customers double-charged; $340,000 (illustrative) financial ledger imbalance; 6 hours of manual reconciliation.
Duration: 4 hours 35 minutes
```

### Incident Timeline

| Time (UTC) | Event |
|---|---|
| **09:15:00** | Black Friday traffic surge hits 12,000 checkout req/sec. Kafka broker partition 4 experiences transient leader rebalancing. |
| **09:15:04** | `PaymentService.processPayment()` saves `Payment` record to PostgreSQL, but `kafkaTemplate.send()` times out after 5,000ms. |
| **09:15:09** | Spring `@Transactional` catches `TimeoutException` and rolls back the PostgreSQL transaction. |
| **09:15:10** | However, Kafka broker had actually acknowledged the packet before the client socket timed out. Downstream `Order Service` creates the order. |
| **09:15:15** | User receives "Checkout Failed, Please Retry" in UI, clicks "Pay Now" again. |
| **09:15:18** | Customer is charged a second time. `Payment DB` has 1 record, `Order DB` has 2 orders, `Ledger DB` has mismatched debits and credits. |
| **11:00:00** | Finance team sounds SEV-1 alarm after automated reconciliation discovers a $340,000 (illustrative) imbalance. |
| **13:50:00** | Emergency fix deployed: Transactional Outbox pattern implemented with idempotent consumers. |

---

## 6. Logs & Diagnostics

### Dual-Write Failure Log Trace
```text
2026-08-21T09:15:09.114+00:00 ERROR [payment-service,7e88c01fa9112bc4,a1098bfe19234567] 24108 --- [http-nio-8080-88] c.f.c.s.BrokenDualWritePaymentService      : Failed to publish payment event to Kafka
org.apache.kafka.common.errors.TimeoutException: Topic payment-events not present in metadata after 5000 ms.
    at org.apache.kafka.clients.producer.KafkaProducer.waitOnMetadata(KafkaProducer.java:1082)
    at org.apache.kafka.clients.producer.KafkaProducer.doSend(KafkaProducer.java:936)
    at org.springframework.kafka.core.DefaultKafkaProducerFactory$CloseSafeProducer.send(DefaultKafkaProducerFactory.java:994)
2026-08-21T09:15:09.120+00:00 INFO  [payment-service,,] 24108 --- [http-nio-8080-88] o.s.o.j.JpaTransactionManager          : Initiating transaction rollback
```

### Financial Reconciliation Mismatch Alert
```text
2026-08-21T11:00:02.891+00:00 ERROR [reconciliation-cron,,] 33104 --- [scheduling-1] c.f.c.s.FinancialReconciliationEngine    : 
[RECONCILIATION_FATAL] Ledger imbalance detected for date 2026-08-21:
  - Total Authorized Payments in Payment DB: $1,420,500.00
  - Total Settled Journal Entries in Ledger DB: $1,760,500.00
  - Unreconciled Variance: +$340,000.00 (8,400 orphaned transactions!)
```

---

## 7. Root Cause Analysis

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                    Root Cause Mechanism                                         │
│                                                                                                 │
│  1. Non-Atomic Dual-Write Boundary: Database commit and Kafka publish are two distinct,        │
│     uncoordinated I/O systems. A network timeout on Kafka after DB insert or vice versa         │
│     inevitably causes one system to succeed while the other fails.                              │
│                                                                                                 │
│  2. Missing Compensating Actions: When downstream ledger posting failed, the system lacked a    │
│     Saga coordinator to roll back the credit card authorization in the upstream Payment Service. │
│                                                                                                 │
│  3. Absence of Consumer Deduplication: Network retries caused downstream services to process    │
│     duplicate payment events, charging cards multiple times without unique idempotency checks.  │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Debugging Process

### Step 1: Query Pending Outbox Events
```sql
SELECT id, aggregate_id, event_type, retry_count, status, created_at
FROM outbox_events
WHERE status = 'PENDING'
ORDER BY created_at ASC;
```

### Step 2: Audit Saga Execution Instances
```sql
SELECT saga_id, order_id, current_step, status, created_at, updated_at
FROM saga_instances
WHERE status IN ('STARTED', 'COMPENSATING', 'COMPENSATED_FAILED');
```

### Step 3: Verify Consumer Idempotency Deduplication Table
```sql
SELECT message_id, consumer_group, processed_at
FROM processed_messages
WHERE message_id = 'ORD-99120';
```

---

## 9. Correct Implementation

### 9.1 Transactional Outbox Dual-Write (`PaymentProcessingService.java`)

```java
package com.finflow.chapter370.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.chapter370.entity.OutboxEvent;
import com.finflow.chapter370.entity.PaymentOrder;
import com.finflow.chapter370.repository.OutboxEventRepository;
import com.finflow.chapter370.repository.PaymentOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class PaymentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessingService.class);
    private final PaymentOrderRepository paymentOrderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public PaymentProcessingService(PaymentOrderRepository paymentOrderRepository,
                                    OutboxEventRepository outboxEventRepository,
                                    ObjectMapper objectMapper) {
        this.paymentOrderRepository = paymentOrderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PaymentOrder authorizePayment(String orderId, String merchantId, BigDecimal amount, String currency) {
        log.info("[PaymentService] Authorizing payment for order '{}' | Amount: {} {}", orderId, amount, currency);

        // 1. Mutate Domain Entity
        PaymentOrder order = new PaymentOrder(orderId, merchantId, amount, currency);
        order.setStatus("AUTHORIZED");
        paymentOrderRepository.save(order);

        // 2. Construct Outbox Event Payload
        Map<String, Object> eventPayload = Map.of(
                "orderId", orderId,
                "merchantId", merchantId,
                "amount", amount,
                "currency", currency,
                "status", "AUTHORIZED",
                "timestamp", System.currentTimeMillis()
        );

        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(eventPayload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox event payload", e);
        }

        // 3. Insert Outbox Event in the EXACT SAME ACID Transaction
        OutboxEvent outboxEvent = new OutboxEvent("PAYMENT_ORDER", orderId, "PAYMENT_AUTHORIZED", jsonPayload);
        outboxEventRepository.save(outboxEvent);

        log.info("[OutboxPattern] PaymentOrder and OutboxEvent '{}' created atomically.", outboxEvent.getId());
        return order;
    }

    @Transactional
    public PaymentOrder compensatePayment(String orderId) {
        log.warn("[SagaCompensation] Compensating reversal for order '{}'", orderId);

        PaymentOrder order = paymentOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("Order not found: " + orderId));

        order.setStatus("REVERSED");
        paymentOrderRepository.save(order);

        Map<String, Object> payload = Map.of(
                "orderId", orderId,
                "status", "REVERSED",
                "reason", "Downstream ledger validation failed",
                "timestamp", System.currentTimeMillis()
        );

        try {
            OutboxEvent event = new OutboxEvent("PAYMENT_ORDER", orderId, "PAYMENT_REVERSED",
                    objectMapper.writeValueAsString(payload));
            outboxEventRepository.save(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return order;
    }
}
```

---

### 9.2 Reliable Outbox Poller Worker (`OutboxPublisherWorker.java`)

```java
package com.finflow.chapter370.service;

import com.finflow.chapter370.entity.OutboxEvent;
import com.finflow.chapter370.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OutboxPublisherWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherWorker.class);
    private final OutboxEventRepository outboxEventRepository;

    public OutboxPublisherWorker(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    public int publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc("PENDING");
        if (pendingEvents.isEmpty()) return 0;

        int publishedCount = 0;
        for (OutboxEvent event : pendingEvents) {
            try {
                // Simulate publishing to Kafka
                simulateKafkaPublish(event);

                event.markPublished();
                outboxEventRepository.save(event);
                publishedCount++;
            } catch (Exception e) {
                log.error("[OutboxPoller] Failed to publish outbox event '{}'", event.getId(), e);
                event.markFailed();
                outboxEventRepository.save(event);
            }
        }
        return publishedCount;
    }

    private void simulateKafkaPublish(OutboxEvent event) {
        log.info("[KafkaProducer] Published event to topic 'finflow.{}.events' | ID: {} | Type: {}",
                event.getAggregateType().toLowerCase(), event.getId(), event.getEventType());
    }
}
```

---

### 9.3 Saga Orchestrator with Rollback (`SagaOrchestrator.java`)

```java
package com.finflow.chapter370.service;

import com.finflow.chapter370.entity.PaymentOrder;
import com.finflow.chapter370.entity.SagaInstance;
import com.finflow.chapter370.repository.SagaInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class SagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SagaOrchestrator.class);
    private final SagaInstanceRepository sagaInstanceRepository;
    private final PaymentProcessingService paymentProcessingService;

    public SagaOrchestrator(SagaInstanceRepository sagaInstanceRepository,
                            PaymentProcessingService paymentProcessingService) {
        this.sagaInstanceRepository = sagaInstanceRepository;
        this.paymentProcessingService = paymentProcessingService;
    }

    public SagaInstance executeCheckoutSaga(String orderId, String merchantId, BigDecimal amount,
                                            String currency, boolean simulateLedgerFailure) {
        SagaInstance saga = new SagaInstance(orderId);
        sagaInstanceRepository.save(saga);

        try {
            // STEP 1: Authorize Payment
            saga.setCurrentStep("AUTHORIZE_PAYMENT");
            paymentProcessingService.authorizePayment(orderId, merchantId, amount, currency);
            saga.setStatus("PAYMENT_AUTHORIZED");
            sagaInstanceRepository.save(saga);

            // STEP 2: Post to Ledger Service
            saga.setCurrentStep("POST_LEDGER");
            if (simulateLedgerFailure) {
                throw new RuntimeException("Ledger Service Error: Account currency mismatch / compliance freeze!");
            }
            saga.setStatus("LEDGER_COMMITTED");

            // STEP 3: Complete Saga
            saga.setCurrentStep("COMPLETE_ORDER");
            saga.setStatus("COMPLETED");
            sagaInstanceRepository.save(saga);
            return saga;

        } catch (Exception e) {
            log.error("[SagaOrchestrator] Step failed in Saga '{}': {}. Initiating COMPENSATION...",
                    saga.getSagaId(), e.getMessage());

            // COMPENSATION: Reverse Payment
            saga.setCurrentStep("COMPENSATING_PAYMENT");
            saga.setStatus("COMPENSATING");
            sagaInstanceRepository.save(saga);

            paymentProcessingService.compensatePayment(orderId);
            saga.setStatus("COMPENSATED_FAILED");
            saga.setCurrentStep("COMPENSATION_COMPLETE");
            sagaInstanceRepository.save(saga);

            return saga;
        }
    }
}
```

---

### 9.4 Idempotent Consumer Deduplication (`IdempotentConsumerService.java`)

```java
package com.finflow.chapter370.service;

import com.finflow.chapter370.entity.ProcessedMessage;
import com.finflow.chapter370.repository.ProcessedMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotentConsumerService {

    private static final Logger log = LoggerFactory.getLogger(IdempotentConsumerService.class);
    private final ProcessedMessageRepository processedMessageRepository;

    public IdempotentConsumerService(ProcessedMessageRepository processedMessageRepository) {
        this.processedMessageRepository = processedMessageRepository;
    }

    @Transactional
    public boolean processMessage(String messageId, String consumerGroup, Runnable businessLogic) {
        if (processedMessageRepository.existsByMessageIdAndConsumerGroup(messageId, consumerGroup)) {
            log.warn("[Idempotency] Duplicate message '{}' detected for group '{}'. Skipping.", messageId, consumerGroup);
            return false;
        }

        // Execute business logic
        businessLogic.run();

        // Record processed message atomically
        processedMessageRepository.save(new ProcessedMessage(messageId, consumerGroup));
        return true;
    }
}
```

---

## 10. Performance Comparison

| Metric | Two-Phase Commit (2PC / XA) | Naive Dual-Write | Transactional Outbox + Saga |
|---|---|---|---|
| **Max Throughput** | ~250 req/sec (Lock bound) | ~12,000 req/sec (Unsafe) | **9,500 req/sec** (illustrative) |
| **P99 Latency** | 450ms (Global locks) | 35ms (Inconsistent) | **42ms** (Non-blocking async) |
| **Consistency Guarantee** | Immediate ACID | None (Data corruption) | **Eventual BASE (Zero Drift)** |
| **Kafka Broker Outage Impact** | Entire system halts | Data loss / phantom events | **0 Data Loss (Queued in Outbox)** |
| **Network Partition Resilience** | Fatal (Dangling locks) | Partial failure / Desync | **High (Automatic Compensation)** |

---

## 11. Best Practices

- [x] **Never Publish to Message Brokers Inside `@Transactional`:** Always write to the Outbox table within the business transaction and publish asynchronously.
- [x] **Use `SELECT ... FOR UPDATE SKIP LOCKED` for Outbox Polling:** Prevents multiple worker threads or pods from competing over the same outbox rows.
- [x] **Enforce Unique Constraints on Idempotency Keys:** Ensure the `processed_messages` table has a composite unique constraint on `(message_id, consumer_group)`.
- [x] **Keep Compensating Transactions Idempotent & Retriable:** Compensating transactions must be designed to succeed even if executed multiple times.
- [x] **Differentiate Pivot vs. Retriable vs. Compensable Transactions:**
  - *Compensable:* Steps that can be rolled back (e.g. reserving funds).
  - *Pivot:* The go/no-go step (e.g. executing the non-reversible settlement).
  - *Retriable:* Steps after the pivot that are guaranteed to eventually succeed (e.g. sending confirmation email).

---

## 12. Common Mistakes

### 1. Polling Outbox Without `SKIP LOCKED`
Polling outbox tables across 20 pods with standard `SELECT ... FOR UPDATE` causes massive database lock contention and deadlocks. Always use `SKIP LOCKED` or Change Data Capture (Debezium).

### 2. Failing to Persist Saga State
Storing Saga state only in JVM memory means a pod restart or crash mid-transaction leaves orphaned states in participant microservices with no compensation executed. Always persist `SagaInstance` to a database.

### 3. Assuming Kafka Delivery is Exactly-Once
Kafka producer idempotence only prevents duplicates between the producer and the broker partition; it does **NOT** prevent downstream consumer duplicate deliveries caused by consumer crashes or rebalances. Consumers **must** implement deduplication tables.

---

## 13. Interview Questions

### Junior Tier
**Q: What is the Dual-Write problem in microservices?**  
*Answer:* The Dual-Write problem occurs when an application needs to update a database and publish a message to a message broker (like Kafka) for the same event. Because these are two separate systems without a distributed transaction manager, one can succeed while the other fails (e.g. DB commits but Kafka network times out), leading to permanent data desynchronization between services.

---

### Mid Tier
**Q: How does the Transactional Outbox pattern solve the Dual-Write problem?**  
*Answer:* Instead of directly calling the message broker, the service writes both the domain entity and an event record into an `outbox_events` table within the **same local ACID database transaction**. Because both inserts occur in one atomic transaction, either both succeed or both roll back. A separate background worker or CDC engine (Debezium) then reads the outbox table and publishes events to Kafka with at-least-once delivery guarantees.

---

### Senior Tier
**Q: What is the difference between Saga Choreography and Saga Orchestration? When should you use which?**  
*Answer:*
- **Choreography:** Decentralized event-driven workflow where services emit events and react to sibling events. Best for simple workflows (2–3 steps) with few participants to keep architecture lightweight.
- **Orchestration:** Centralized coordinator sends explicit commands to participant services, tracks state machines, and manages compensations. Best for complex enterprise workflows (4+ steps, financial transactions, compliance auditing) where centralized visibility and strict rollback control are critical.

---

### Staff Tier
**Q: What are Pivot Transactions in a Saga and how do they determine compensation boundaries?**  
*Answer:* 
In a Saga workflow:
1. **Compensable Transactions:** Steps occurring *before* the pivot. If any subsequent step fails, these must be rolled back via compensating transactions (e.g. release reserved inventory, void payment authorization).
2. **Pivot Transaction:** The irreversible point of no return (e.g. charging non-refundable credit card). Once the pivot transaction commits, the Saga guarantees it will not roll back.
3. **Retriable Transactions:** Steps occurring *after* the pivot. These steps cannot be compensated and must be retried until they succeed (e.g. generating tax invoice, sending receipt email).

---

### Principal Tier
**Q: How would you architect a zero-data-loss CDC (Change Data Capture) Transactional Outbox engine handling 50,000 events/second in PostgreSQL?**  
*Answer:*
1. **Engine:** Use Debezium embedded or Kafka Connect to tail PostgreSQL's Write-Ahead Log (WAL) via logical replication (`pgoutput` plugin).
2. **Zero Polling Overhead:** Avoid `SELECT` queries on the outbox table completely; Debezium reads raw WAL binary logs directly off disk, eliminating database CPU load.
3. **Table Partitioning & Truncation:** Route outbox inserts to daily or hourly partitioned PostgreSQL tables (`outbox_events_2026_08_21`). Drop old partitions via DDL `DROP TABLE` instead of expensive `DELETE` queries.
4. **Order Preservation:** Partition Kafka topics using the domain entity `aggregate_id` (e.g. `order_id`) to ensure sequential event ordering per account while scaling horizontally across 64 Kafka partitions.

---

## 14. Hands-on Exercise

### Task: Implement Outbox Atomicity & Saga Compensation
1. Create `PaymentOrder` and `OutboxEvent` entities mapped with Spring Data JPA.
2. Implement `PaymentProcessingService.authorizePayment()` ensuring both records are committed in a single `@Transactional` method.
3. Implement `SagaOrchestrator` to coordinate payment authorization and ledger posting.
4. Write automated tests proving:
   - Happy path: Saga completes and creates `PAYMENT_AUTHORIZED` outbox record.
   - Failure path: Simulating a ledger failure reverses the payment and creates `PAYMENT_REVERSED` compensation event.
   - Idempotent consumer suppresses duplicate deliveries.

### Solution
See complete runnable code in [TransactionalOutboxUnitTest.java](file:///D:/Projects/Spring%20Boot/spring-boot-production-guide/code/chapter-370/src/test/java/com/finflow/chapter370/unit/TransactionalOutboxUnitTest.java) and [SagaOrchestratorIntegrationTest.java](file:///D:/Projects/Spring%20Boot/spring-boot-production-guide/code/chapter-370/src/test/java/com/finflow/chapter370/integration/SagaOrchestratorIntegrationTest.java).

---

## 15. Advanced Challenge: High-Throughput Debezium CDC Outbox Pipeline

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                 High-Throughput Debezium CDC Outbox Architecture (50k events/s)                 │
│                                                                                                 │
│  [Spring Boot Payment Service]                                                                  │
│    ├── INSERT INTO payment_orders (...)                                                         │
│    └── INSERT INTO outbox_events (...) ──► PostgreSQL Write-Ahead Log (WAL)                     │
│                                                   │                                             │
│                                                   ▼ (Logical Replication Stream)                │
│                                            [Debezium Engine]                                    │
│                                                   │ (Extracts payload & traceparent)            │
│                                                   ▼                                             │
│                                            [Kafka Cluster]                                      │
│                                            Topic: payment-events (Partitioned by order_id)       │
│                                                   │                                             │
│                                                   ▼                                             │
│                                       [Ledger Service Consumers]                                │
│                                       (Idempotency Filter + OpenTelemetry Trace Context)        │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 16. Production Checklist

Before approving PRs involving distributed transactions or messaging:

- [ ] **No Direct Broker Publishes Inside `@Transactional`:** Verified `KafkaTemplate.send()` or `RabbitTemplate.convertAndSend()` are NEVER called inside database transaction methods.
- [ ] **Transactional Outbox Implemented:** Domain entity and outbox event are persisted in the same transaction.
- [ ] **Idempotent Consumers Configured:** Subscribers verify message IDs against a `processed_messages` unique table before executing domain logic.
- [ ] **Compensating Actions are Idempotent:** Rollback handlers can be safely retried without double-refunding or corrupting account states.
- [ ] **Outbox Polling uses `SKIP LOCKED`:** Poller queries use `SELECT ... FOR UPDATE SKIP LOCKED` or Debezium CDC to prevent row lock contention.
- [ ] **Saga State is Persisted:** All Saga steps and states are recorded in persistent storage to survive pod crashes.
- [ ] **Trace Context Propagated:** W3C `traceparent` headers are stored in the outbox payload metadata and restored on consumer consumption for end-to-end distributed tracing.
