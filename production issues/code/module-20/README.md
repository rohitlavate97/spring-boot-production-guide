# Module 20: Apache Kafka: Consumer Lag, Poison Pills & Rebalances

## Overview
This module explores enterprise Apache Kafka event-driven architectures, deep-diving into message consumption failures, Poison Pill recovery (`ErrorHandlingDeserializer` + Dead Letter Topics), avoiding Rebalance Storms via `max.poll.interval.ms` budgeting, `CooperativeStickyAssignor` non-blocking rebalances, and manual offset commit semantics.

## Key Scenarios Covered
1. **The Poison Pill & Infinite Deserialization Loop:**
   - Why schema mismatches and malformed JSON crash standard Kafka consumers into endless re-poll loops.
   - Mitigating with Spring Kafka `ErrorHandlingDeserializer` and `DeadLetterPublishingRecoverer` routing unparseable records to `.DLT` immediately.
2. **The Rebalance Storm (`max.poll.interval.ms` Exhaustion):**
   - How batch sizes (`max.poll.records`) combined with slow database/external API writes exceed poll interval timeouts, triggering cascading group rebalances and duplicate message processing.
3. **Consumer Lag & Partition Assignment Strategy:**
   - Replacing legacy Stop-The-World `RangeAssignor` with incremental `CooperativeStickyAssignor`.
4. **Offset Commit Semantics:**
   - Eliminating `enable.auto.commit=true` in favor of `AckMode.RECORD` or `AckMode.MANUAL_IMMEDIATE` to guarantee zero message loss.

## Project Structure
- `src/main/java/.../model/`:
  - `PaymentEvent.java` (Immutable payment event record).
- `src/main/java/.../config/`:
  - `KafkaResilienceConfig.java` (Configures `CommonErrorHandler`, `DeadLetterPublishingRecoverer`, and non-retryable exception rules).
- `src/main/java/.../service/`:
  - `KafkaDiagnosticsService.java` (Poll budget calculator, consumer lag tracker, and DLT routing).
- `src/main/java/.../controller/`:
  - `KafkaDiagnosticsController.java` (REST endpoints for event publishing, poison pill simulation, and lag monitoring).
- `src/test/java/.../`:
  - `PoisonPillRecoveryTest.java`
  - `PollIntervalBudgetCalculatorTest.java`
  - `KafkaDiagnosticsControllerTest.java`
  - `Module20IntegrationTest.java`

## How to Run Tests
```bash
mvn clean test
```

## Complete Documentation
For the full deep-dive technical incident guide, see [Module 20 Documentation](../../docs/module-20-apache-kafka-consumer-lag-poison-pills-rebalances.md).
