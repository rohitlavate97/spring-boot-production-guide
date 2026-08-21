# Module 12: External API Timeouts, Retries & Cascading Cascades

## Overview
This module explores microservice inter-service communication patterns, the "infinite timeout" default hazard, client timeout configuration (Connect Timeout vs Read Timeout), and fault tolerance using Resilience4j (Circuit Breaker, Retry with exponential backoff, Bulkhead isolation, and Fallbacks).

## Key Scenarios Covered
1. **The Infinite Timeout Default Trap:**
   - Why unconfigured HTTP clients (`RestClient`, `RestTemplate`, `WebClient`) hang worker threads indefinitely when downstream services freeze.
2. **Resilience4j Circuit Breaker State Transitions:**
   - Observing `CLOSED -> OPEN -> HALF_OPEN` transitions under downstream failure rates and automatic fallback execution.
3. **Bulkhead Concurrency Isolation:**
   - Limiting concurrent requests to unreliable downstream APIs to protect shared worker thread pools.
4. **Retry Storms & Exponential Backoff with Jitter:**
   - Preventing cascading failures caused by aggressive immediate retries against failing downstream services.

## Project Structure
- `src/main/java/.../dto/`: `CreditAssessmentResult.java`.
- `src/main/java/.../client/`: `ExternalCreditAgencyClient.java` (configured with 500ms connect and 1000ms read timeouts).
- `src/main/java/.../service/`: `CreditAssessmentService.java` (`@CircuitBreaker`, `@Retry`, `@Bulkhead`).
- `src/main/java/.../controller/`: `PaymentProcessingController.java`.
- `src/test/java/.../`:
  - `ClientTimeoutConfigurationTest.java`
  - `CircuitBreakerStateTransitionTest.java`
  - `BulkheadIsolationTest.java`
  - `Module12IntegrationTest.java`

## How to Run Tests
```bash
mvn clean test
```

## Complete Documentation
For the full 12-section technical incident guide, see [Module 12 Documentation](../../docs/module-12-external-api-timeouts-circuit-breakers.md).
