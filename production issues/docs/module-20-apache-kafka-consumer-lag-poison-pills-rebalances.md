# Module 20: Apache Kafka: Consumer Lag, Poison Pills & Rebalances

## Issue 20.1: Partition Starvation (Poison Pills), Cascading Rebalance Storms, and CommitFailedException Disasters

---

### 1. Scenario

During high-volume night-time transaction clearing on the **FinFlow Core Banking & Settlement Pipeline**:
1. An upstream legacy payment provider emitted a single malformed JSON event (`{"txnId": 12345, "amount": "INVALID_CURRENCY_HEX\x00"}`) onto the `payment-events` topic (Partition 3).
2. The Spring Boot consumer threw a `DeserializationException`. Because standard Java/Jackson deserialization was configured without Spring Kafka's `ErrorHandlingDeserializer`, the record was rejected **before the listener was ever invoked**.
3. The consumer thread crashed, restarted the polling loop, and immediately fetched the exact same offset on Partition 3, repeating the crash **100 times per second**. All subsequent 45,000 valid payment events stuck behind the poison pill were **completely starved and halted for 2 hours** (**The Poison Pill Partition Starvation**).
4. Concurrently, on Partition 1, a batch consumer polled 500 records (`max.poll.records: 500`). Because each payment required external sanction checks taking 750ms, processing the batch took **375 seconds**.
5. Because `max.poll.interval.ms` was set to the default `300000` (300 seconds / 5 minutes), the Kafka broker's Group Coordinator assumed the consumer had died and evicted it from the consumer group, triggering a **Group Rebalance**.
6. When the consumer finally finished and attempted to commit its offsets, Kafka rejected the commit with a fatal:
   `org.apache.kafka.clients.consumer.CommitFailedException: Commit cannot be completed since the group has already rebalanced and assigned the partitions to another member`.
7. The new consumer assigned to Partition 1 re-polled the **exact same 500 records**, spent another 375 seconds processing them, timed out, and triggered *another* rebalance, creating a **Continuous Rebalance Death Spiral** that **double-processed $4.2M in merchant disbursements**!

---

### 2. Symptoms

```text
1. Infinite Deserialization Loop & Partition Starvation:
   Logs flooded with DeserializationException / JsonParseException repeating every few milliseconds.
   Consumer lag on one specific partition grows continuously while other partitions process normally.

2. Cascading Rebalance Storms & CommitFailedException:
   CommitFailedException: Commit cannot be completed since the group has already rebalanced.
   Consumers repeatedly transition through (Revoked Partitions -> Rebalancing -> Assigned Partitions).

3. Duplicate Message Processing:
   Downstream payment settlement systems receive and execute the same transaction multiple times.

4. Uncontrolled Consumer Group Lag:
   Prometheus kafka_consumergroup_lag spikes across all partitions.

5. Silent Message Loss on Application Shutdown:
   Messages acknowledged by enable.auto.commit=true but never processed due to SIGTERM / OOMKill.
```

---

### 3. Possible Root Causes

1. **Missing `ErrorHandlingDeserializer`:** Standard Kafka deserializers throw exceptions during `poll()`. Without `ErrorHandlingDeserializer`, the offset is never advanced, creating an infinite retry loop on the broker.
2. **Batch Processing Time Exceeding `max.poll.interval.ms`:**
   $$\text{Total Batch Time} = \text{max.poll.records} \times \text{Per-Message Processing Time}$$
   If $\text{Total Batch Time} > \text{max.poll.interval.ms}$, the coordinator evicts the consumer on every poll.
3. **Eager Stop-The-World Rebalances (`RangeAssignor`):** Revoking all partitions across all consumers during any minor node scale event instead of using incremental `CooperativeStickyAssignor`.
4. **Auto-Commit Enabled (`enable.auto.commit: true`):** Committing offsets asynchronously on background timers regardless of whether business transactions succeeded.
5. **Missing Non-Blocking Dead Letter Queue (DLT) Topology:** Inability to isolate failed records to a dead-letter topic without blocking partition consumption.

---

### 4. Architecture Context: Kafka Polling Loop, Error Handling & Rebalance Mechanics

```text
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                    KAFKA CONSUMER POLLING LOOP & ERROR HANDLING RECOVERY                        │
│                                                                                                 │
│  Kafka Broker [payment-events Topic - Partition 3]                                              │
│           │                                                                                     │
│           ▼ Records: [Offset 100: Valid, Offset 101: POISON PILL, Offset 102: Valid]            │
│  ┌───────────────────────────────────────────────────────────────────────────────────────────┐  │
│  │ Spring Boot Kafka Consumer Container (ConcurrentMessageListenerContainer)                 │  │
│  │                                                                                           │  │
│  │  1. ErrorHandlingDeserializer:                                                            │  │
│  │     - Offset 100: Decodes PaymentEvent -> Passed to @KafkaListener (Processed OK)         │  │
│  │     - Offset 101: Deserialization Fails!                                                  │  │
│  │       ErrorHandlingDeserializer wraps error in DeserializationException header & passes it│  │
│  │       to DefaultErrorHandler.                                                             │  │
│  │                                                                                           │  │
│  │  2. DefaultErrorHandler + DeadLetterPublishingRecoverer:                                  │  │
│  │     - Catches fatal exception (0 retries for poison pills).                               │  │
│  │     - Publishes raw corrupted payload to "payment-events.DLT" with error headers.         │  │
│  │     - Advances Partition 3 offset past 101 to 102!                                        │  │
│  │                                                                                           │  │
│  │  3. Offset 102: Decodes PaymentEvent -> Passed to @KafkaListener (PROCESSED SUCCESSFULLY!)│  │
│  │     (ZERO PARTITION STARVATION!)                                                          │  │
│  └────────────────────────┬──────────────────────────────────────────┬───────────────────────┘  │
│                           │ (Valid Settlements)                      │ (Poison Pills)           │
│                           ▼                                          ▼                          │
│  ┌─────────────────────────────────┐        ┌────────────────────────────────────────────────┐  │
│  │ PostgreSQL Settlement Ledger    │        │ Kafka Topic: payment-events.DLT                │  │
│  └─────────────────────────────────┘        └────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 5. How to Reproduce the Issues

#### ❌ Anti-Pattern 1: Raw Deserializer Without Error Handler (Poison Pill Crash Loop)
```yaml
# ❌ FATAL ANTI-PATTERN: Raw JsonDeserializer will crash the consumer thread forever on bad JSON!
spring:
  kafka:
    consumer:
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
```

#### ❌ Anti-Pattern 2: Oversized Batch Size Triggering Rebalance Storms
```yaml
# ❌ FATAL ANTI-PATTERN: 500 records * 800ms = 400s > max.poll.interval.ms (300s)!
spring:
  kafka:
    consumer:
      max-poll-records: 500
      properties:
        max.poll.interval.ms: 300000 # 5 minutes
```

#### ❌ Anti-Pattern 3: Auto-Commit Enabled (Data Loss on Crash)
```yaml
# ❌ ANTI-PATTERN: Offsets committed in background; pod crash loses uncommitted business records!
spring:
  kafka:
    consumer:
      enable-auto-commit: true
```

---

### 6. Evidence Collection & Diagnostic Probing

#### Method 1: Inspect Consumer Group Lag and Partition Assignments
```bash
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group finflow-clearing-group
```
**Diagnostic Output:**
```text
GROUP                  TOPIC           PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG             CONSUMER-ID     HOST
finflow-clearing-group payment-events  0          184200          184200          0               consumer-1      /10.0.1.10
finflow-clearing-group payment-events  1          92000           142000          50000           consumer-2      /10.0.1.11
finflow-clearing-group payment-events  2          110500          110500          0               consumer-3      /10.0.1.12
finflow-clearing-group payment-events  3          45101           90102           45001 (STALLED) consumer-1      /10.0.1.10
```

#### Method 2: Inspect Messages Trapped in Dead Letter Queue
```bash
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic payment-events.DLT --from-beginning --property print.headers=true
```

#### Method 3: Monitor Kafka Rebalances via Prometheus
```promql
# Rebalance Rate across consumer group
sum(rate(kafka_consumer_coordinator_rebalance_latency_total[5m])) by (client_id)
```

---

### 7. Step-by-Step Debugging Procedure

```text
Step 1: Check If Consumer Lag Is Isolated to One Partition (Poison Pill) or All Partitions (Slow Processing).
        Run `kafka-consumer-groups.sh --describe`. If lag is isolated to 1 partition, suspect poison pill.

Step 2: Inspect Application Logs for DeserializationException.
        If found, immediately deploy `ErrorHandlingDeserializer` and route malformed offsets to `.DLT`.

Step 3: Check for CommitFailedException.
        If found, calculate batch processing time: `max.poll.records * p99_latency_ms`.
        Reduce `max.poll.records` or increase `max.poll.interval.ms`.

Step 4: Switch Partition Assignment to CooperativeStickyAssignor.
        Eliminates stop-the-world rebalances during pod autoscaling.

Step 5: Enforce AckMode.RECORD or MANUAL_IMMEDIATE with Idempotent Consumer Logic.
        Guarantees exact offset progression and protects against duplicate processing.
```

---

### 8. Technical Root Cause Deep-Dive

#### 1. Why Poison Pills Break the Standard Kafka Polling Loop
- In the Kafka Java Consumer, `poll()` invokes the configured `Deserializer.deserialize()`.
- If an uncaught exception occurs inside `deserialize()`, `poll()` throws before returning any `ConsumerRecords`.
- The consumer cannot commit an offset because it never received the record.
- On the next iteration of the while-loop, `poll()` requests the same offset from the broker again, creating an **infinite deserialization loop** that permanently blocks all valid messages behind it.
- **The Solution:** `ErrorHandlingDeserializer` catches the exception during `deserialize()`, wraps the failure in a special `DeserializationException` object, and passes it to Spring Kafka's `CommonErrorHandler` which forwards the record directly to the DLT.

#### 2. The Rebalance Storm Mathematics
Kafka decouples the **Heartbeat Thread** (`heartbeat.interval.ms = 3s`, `session.timeout.ms = 45s`) from the **Record Processing Thread** (`max.poll.interval.ms = 300s`).
- If a batch of 500 records takes $500 \times 800\text{ms} = 400\text{s}$ to execute business logic:
- The Heartbeat Thread continues sending heartbeats, so `session.timeout.ms` does NOT fire.
- However, because the consumer thread failed to call `poll()` within `max.poll.interval.ms` (300s), the broker coordinator concludes the consumer is livelocked and triggers a rebalance.
- **The Safe Poll Budget Formula:**
  $$\text{max.poll.records} \le \frac{\text{max.poll.interval.ms} \times 0.70}{\text{P99 Processing Time per Message (ms)}}$$

#### 3. Eager Rebalance vs Cooperative Sticky Rebalance
- **Eager Rebalance (`RangeAssignor`):** All consumers revoke ALL partitions simultaneously, pausing all consumption across the entire cluster for several seconds.
- **Cooperative Sticky Rebalance (`CooperativeStickyAssignor`):** Consumers only revoke the specific partitions that are migrating. All unaffected partitions continue consuming without interruption.

---

### 9. Production-Grade Fixes

#### ✅ Fix 1: Hardened `application.yml` Consumer Configuration
```yaml
spring:
  kafka:
    consumer:
      group-id: finflow-clearing-group
      auto-offset-reset: earliest
      enable-auto-commit: false
      max-poll-records: 50 # Sized safely for 500ms processing
      key-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.deserializer.key.delegate.class: org.apache.kafka.common.serialization.StringDeserializer
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
        spring.json.trusted.packages: "com.finflow.*"
        partition.assignment.strategy: org.apache.kafka.clients.consumer.CooperativeStickyAssignor
        max.poll.interval.ms: 300000
    listener:
      ack-mode: record
```

#### ✅ Fix 2: Error Handler with DLT Recovery (`KafkaResilienceConfig.java`)
```java
@Configuration
public class KafkaResilienceConfig {

    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));

        // Route fatal deserialization errors directly to DLT without retries
        errorHandler.addNotRetryableExceptions(
                DeserializationException.class,
                SerializationException.class,
                ClassCastException.class,
                IllegalArgumentException.class
        );
        return errorHandler;
    }
}
```

#### ✅ Fix 3: Safe Poll Records Budgeting Service
```java
@Service
public class KafkaDiagnosticsService {

    public PollBudgetResult calculatePollBudget(long maxPollIntervalMs, long p99ProcessingTimeMs, int configuredMaxPollRecords) {
        double safetyFactor = 0.70;
        int recommendedRecords = (int) Math.max(1, (maxPollIntervalMs * safetyFactor) / Math.max(1, p99ProcessingTimeMs));
        long estimatedBatchTime = configuredMaxPollRecords * p99ProcessingTimeMs;

        String status = (estimatedBatchTime >= maxPollIntervalMs) ? "CRITICAL_REBALANCE_STORM_RISK"
                : (estimatedBatchTime >= maxPollIntervalMs * safetyFactor) ? "WARNING_HIGH_REBALANCE_RISK" : "SAFE_POLL_BUDGET";

        return new PollBudgetResult(maxPollIntervalMs, p99ProcessingTimeMs, configuredMaxPollRecords, recommendedRecords, estimatedBatchTime, status);
    }
}
```

---

### 10. Verification

1. **Poison Pill Recovery Test:** Run `PoisonPillRecoveryTest.java` to verify that corrupted records are routed to `payment-events.DLT` and valid messages continue processing.
2. **Poll Budget Calculator Test:** Run `PollIntervalBudgetCalculatorTest.java` to verify safe `max.poll.records` estimation.
3. **Controller API Test:** Run `KafkaDiagnosticsControllerTest.java` to test event production, poison pill simulation, and lag metrics.
4. **Integration Test:** Run `Module20IntegrationTest.java` to verify Spring context and Actuator health.

---

### 11. Prevention & Production Readiness

1. **Rule: Always Use `ErrorHandlingDeserializer` for Kafka Consumers:**
   Never configure raw `JsonDeserializer` or `StringDeserializer` directly as the top-level value deserializer.
2. **Rule: Always Use `CooperativeStickyAssignor` in Multi-Node Clusters:**
   Eliminate stop-the-world rebalance pauses during rolling deployments and autoscaling.
3. **Prometheus Alerting Rule for Consumer Lag:**
```yaml
- alert: KafkaConsumerLagHigh
  expr: sum(kafka_consumergroup_lag{topic="payment-events"}) by (consumergroup) > 5000
  for: 3m
  labels:
    severity: critical
  annotations:
    summary: "Kafka consumer group {{ $labels.consumergroup }} lag exceeded 5,000 messages (Current: {{ $value }})"
```

---

### 12. Interview & Production Questions

#### Interview Questions
1. **Q: What is a Kafka Poison Pill, and why does standard Spring Kafka fail to recover from it without `ErrorHandlingDeserializer`?**
   *Answer:* A poison pill is a corrupted or unparseable record. Without `ErrorHandlingDeserializer`, the exception is thrown inside `poll()` before the record reaches the listener, preventing offset commits and causing the consumer to re-poll and crash on the same record infinitely.
2. **Q: What is the difference between `session.timeout.ms` and `max.poll.interval.ms`?**
   *Answer:* `session.timeout.ms` is the timeout for the background Heartbeat Thread (detects dead JVM or network partition). `max.poll.interval.ms` is the maximum allowed time between successive calls to `poll()` on the main processing thread (detects hung or slow record processing).
3. **Q: Why does `CooperativeStickyAssignor` perform better than `RangeAssignor` during pod scaling?**
   *Answer:* `RangeAssignor` uses eager rebalancing, revoking all partitions across all consumers and halting consumption cluster-wide. `CooperativeStickyAssignor` performs incremental rebalances, migrating only the necessary partitions while remaining partitions continue processing.
4. **Q: What happens if `enable.auto.commit` is set to `true` and a container crashes during processing?**
   *Answer:* If the auto-commit timer fires before processing completes, the offset is committed to Kafka. When the pod crashes, the message is lost forever because the new consumer starts at the committed offset.
5. **Q: How does `DeadLetterPublishingRecoverer` work in Spring Kafka?**
   *Answer:* When `DefaultErrorHandler` exhausts retries or encounters a non-retryable exception, `DeadLetterPublishingRecoverer` publishes the failed record to a dead-letter topic (e.g. `<topic>.DLT`), attaching headers with the original exception class, message, and stack trace, then commits the offset on the primary topic.

#### Production Incident Questions
1. **Incident:** Consumer lag is 0 on partitions 0, 1, and 2, but 80,000 on partition 3. Why?
   *Diagnosis:* A poison pill is stuck on Partition 3, repeatedly crashing the deserializer. Fix: Configure `ErrorHandlingDeserializer` and `DeadLetterPublishingRecoverer`.
2. **Incident:** Every time traffic increases, logs show `CommitFailedException` and consumers repeatedly rebalance. Why?
   *Diagnosis:* Large batches (`max.poll.records`) take longer to process than `max.poll.interval.ms`. Fix: Reduce `max.poll.records` or increase `max.poll.interval.ms`.
3. **Incident:** After rolling deployment of a new consumer version, duplicate payments are executed in the database. Why?
   *Diagnosis:* Eager rebalances caused consumers to lose partition ownership before committing offsets. Fix: Ensure consumers are idempotent (e.g. database unique transaction constraints) and use `CooperativeStickyAssignor`.
4. **Incident:** You need to retry transient payment gateway 503 errors with exponential backoff without blocking the partition. How?
   *Diagnosis:* Use `@RetryableTopic` with backoff topics (e.g. `payment-events-retry-1000`, `payment-events-retry-2000`) and a final `.DLT` topic.
5. **Incident:** Messages are published with `null` keys. Why did consumer lag increase on Partition 0?
   *Diagnosis:* Kafka's default partitioner round-robins null-key records, but if partition 0 worker is slower, lag accumulates. If keyed messages are poorly hashed (e.g. all transactions sharing same `accountId`), one partition receives 90% of traffic. Fix: Use custom partitioners or high-cardinality keys.

#### Trick Questions
1. **Trick:** Does increasing `concurrency` in `@KafkaListener` allow 10 threads to consume from a topic with 3 partitions?
   *Answer:* No! Kafka guarantees that a single partition can only be consumed by at most one consumer thread in a group. 7 threads will remain completely idle.
2. **Trick:** If `enable.idempotence=true` is set on the producer, does that prevent duplicate messages on the consumer?
   *Answer:* No! Producer idempotence only guarantees that the *producer* does not write duplicate messages to the broker during network retries. If the *consumer* crashes before committing offsets, it will still re-read messages.
3. **Trick:** Can `DeadLetterPublishingRecoverer` modify the original message payload when sending to DLT?
   *Answer:* No, the original payload is preserved exactly as received; the error metadata and stack trace are appended as Kafka Record Headers.

---

*(Answers and detailed explanations are provided in the Master Answer Guide)*
