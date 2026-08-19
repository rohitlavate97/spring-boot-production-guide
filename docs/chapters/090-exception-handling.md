---
chapter: 90
topic: Exception Handling — @ControllerAdvice, ProblemDetail (RFC 9457), Error Hierarchy Design
prerequisite_chapters: [30, 40, 50, 60, 70, 80]
reference_system_node: Payment Service (HTTP exception boundaries, RFC 9457 ProblemDetail, @RestControllerAdvice, ResponseEntityExceptionHandler, trace correlation)
---

# Chapter 090: Exception Handling — @ControllerAdvice, ProblemDetail (RFC 9457), and Error Hierarchy Design

## 1. Concept

Exception boundaries in distributed architectures represent the critical fault lines where internal application state meets the external network. When a process fails—whether due to an unhandled system crash, an expected business rejection, or a transient infrastructure fault—the way that failure is communicated dictates the stability of the entire ecosystem.

In a microservices mesh, exceptions are not merely developer diagnostics; they are control signals for API gateways, circuit breakers, service meshes, and client retry loops. A poorly handled exception can result in a cascading failure, whereas a properly translated error can trigger automated platform self-healing.

Spring’s exception handling has evolved significantly to address these distributed concerns:
*   **`HandlerExceptionResolver`**: The foundational interface for resolving exceptions to a `ModelAndView`.
*   **`@ExceptionHandler`**: Annotation-driven, method-level handling within controllers.
*   **`@RestControllerAdvice`**: Global, aspect-like exception interception across all controllers.
*   **`ResponseEntityExceptionHandler`**: A convenient base class that pre-handles standard Spring MVC exceptions.
*   **`ProblemDetail` (Spring 6 / Boot 3)**: First-class support for RFC 9457 (Problem Details for HTTP APIs), providing a standardized, machine-readable JSON structure for HTTP errors.

By mapping Java exceptions to proper HTTP semantics and standardized JSON payloads, we empower the infrastructure to make intelligent routing and resilience decisions.

## 2. Internal Working

To understand how Spring translates a thrown `RuntimeException` into an HTTP response, we must examine the request lifecycle within the `DispatcherServlet`.

When a controller method throws an exception, the `DispatcherServlet` catches it and delegates it to the `processHandlerException()` method. This method iterates through an ordered list of `HandlerExceptionResolver` beans.

The most critical resolver in modern Spring Boot applications is the `ExceptionHandlerExceptionResolver`. When it encounters an exception, it performs the following steps:
1.  **Candidate Selection:** It scans the controller hierarchy and globally registered `@ControllerAdvice` beans for methods annotated with `@ExceptionHandler` that declare compatibility with the thrown exception type.
2.  **Distance Scoring (`ExceptionHandlerMethodResolver`):** If multiple handlers match, Spring uses a depth-first distance scoring algorithm. It calculates the inheritance distance between the thrown exception and the exception types declared in the `@ExceptionHandler` annotations. The handler with the shortest distance wins. For example, if `PaymentDeclinedException` extends `DomainException`, a handler for `PaymentDeclinedException` will be chosen over a handler for `DomainException`.
3.  **Content Negotiation:** Once the method executes and returns a result (e.g., a `ProblemDetail` object), Spring uses `HttpMessageConverter`s to serialize the result into the requested format (typically JSON).

Spring Boot 3 natively supports RFC 9457 via the `ProblemDetail` class. When a controller advice returns a `ProblemDetail` (or when Spring automatically creates one for standard MVC exceptions if `spring.mvc.problemdetails.enabled=true` is set), the resulting JSON matches the RFC specification: `type`, `title`, `status`, `detail`, and `instance`.

Crucially, `ResponseEntityExceptionHandler` provides default handling for internal Spring exceptions (like `MethodArgumentNotValidException`). By overriding its methods, you can ensure even framework-level errors are returned as compliant RFC 9457 responses enriched with MDC `traceId`s for distributed tracing.

## 3. Enterprise Scenario

In the FinFlow Payment Platform, the **Payment Service** handles a peak load of 4,000 requests per second. It acts as a facade, receiving payment intents and delegating charges to a downstream Third-Party Payment Gateway.

During payment processing, several failure modes are possible:
*   **Business Rejections:** The card is declined (`PaymentDeclinedException`), or an invalid currency is provided.
*   **Transient Infrastructure Faults:** The downstream gateway times out (`GatewayTimeoutException`), or a database lock times out.
*   **Concurrency Conflicts:** Two identical requests arrive simultaneously (`IdempotencyConflictException`), or a concurrent update modifies the payment intent (`OptimisticLockException`).
*   **System Bugs:** Null pointer exceptions, unhandled SQL grammar errors, etc.

We need to translate these distinct failure domains into RFC 9457 payloads with machine-readable error codes (`PAYMENT_DECLINED`, `GATEWAY_TIMEOUT`, `IDEMPOTENCY_CONFLICT`), provide retry hints (`Retry-After` headers) for transient issues, and ensure strict OpenTelemetry trace correlation.

## 4. Incorrect Implementation

The following implementation is a composite of severe anti-patterns frequently found in enterprise codebases.

```java
package com.finflow.chapter090.incorrect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.finflow.domain.PaymentRequest;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> processPayment(@RequestBody PaymentRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            String transactionId = paymentService.charge(request);
            response.put("status", "SUCCESS");
            response.put("transactionId", transactionId);
            return ResponseEntity.ok(response);
            
        // PROBLEM 2 & 3: Catching Exception directly and swallowing the root cause.
        // This breaks Spring's transaction boundary if not configured properly, 
        // and breaks automated exception translation.
        } catch (Exception e) {
            log.error("Payment failed", e);
            
            // PROBLEM 1: The "200 OK with error payload" anti-pattern
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
            
            // PROBLEM 2 (Cont.): Leaking raw exception details to the client
            if (e.getCause() != null) {
                response.put("rootCause", e.getCause().toString());
            }
            
            return ResponseEntity.ok(response);
        }
    }
    
    // PROBLEM 4: No @RestControllerAdvice is present in this architecture.
    // Every controller repeats this try-catch block, resulting in varying 
    // error formats across the API surface.
}
```

### Analysis of the Anti-patterns:
*   **Problem 1: The 200 OK Anti-pattern.** Returning HTTP 200 for a failure blinds infrastructure. API Gateways, Load Balancers, and Resilience4j circuit breakers look at the HTTP status code. If an error returns 200, the circuit breaker records it as a success, keeping the circuit closed and routing traffic to a failing downstream.
*   **Problem 2: Leaking Internals.** By passing `e.getMessage()` and `e.getCause().toString()` to the client, we risk exposing SQL syntax, internal IPs, or class names, violating PCI-DSS compliance.
*   **Problem 3: Catching `Exception` Blanketly.** This prevents specific handling of transient vs. fatal errors. It also intercepts exceptions that Spring uses for internal control flow.
*   **Problem 4: Missing Centralization.** Without `@RestControllerAdvice`, the error response schema is inconsistent, making client integration a nightmare.

## 5. Production Incident

It is Black Friday. The FinFlow Payment Service is humming at its 4,000 req/sec peak. Suddenly, the downstream Third-Party Payment Gateway begins to degrade, its p99 latency spiking to 2.5 seconds (past the 2-second timeout configured in Resilience4j).

The Payment Service begins throwing `GatewayTimeoutException`. However, the `PaymentController` catches these exceptions in its broad `catch (Exception e)` block and returns them to clients with an HTTP `200 OK` and `{ "status": "ERROR" }`.

**The Cascading Failure:**
1.  **Circuit Breaker Failure:** The Resilience4j circuit breaker wrapping the downstream call is configured to trip on HTTP 5xx errors or specific exceptions. Because the controller eats the exception and returns 200, the circuit breaker sees a 100% success rate. It remains closed.
2.  **Thread Exhaustion:** The API Gateway and clients see HTTP 200s and continue sending the full 4,000 req/sec traffic. Every request blocks for 2 seconds waiting for the timeout. Within 15 seconds, all Tomcat worker threads are exhausted.
3.  **Total Blackout:** The Payment Service becomes unresponsive even to health checks. Kubernetes liveness probes fail, and pods are aggressively killed and restarted, leading to a crash loop.
4.  **Data Leak:** Concurrently, during the chaos, a JPA deadlock occurs on the `payment_intent` table. The `catch (Exception e)` block diligently serializes the `DataAccessException`, and its `rootCause` containing the full PostgreSQL schema and table names is returned in the JSON payload to potentially malicious external clients!

## 6. Logs

**Circuit Breaker (Deceptive Metrics):**
```log
2026-11-27T10:05:12.123Z [metrics-publisher] INFO  io.github.resilience4j.circuitbreaker - CircuitBreaker 'stripeGateway' is CLOSED. Success rate: 100%, Failure rate: 0%. (Total calls: 8500)
```
*(Despite 95% of payments actually failing, the metric shows 0% failure because of the HTTP 200 response).*

**Tomcat Thread Pool Exhaustion:**
```log
2026-11-27T10:05:27.451Z [http-nio-8080-Acceptor] ERROR o.a.tomcat.util.net.NioEndpoint - Socket accept failed
java.io.IOException: Too many open files
...
2026-11-27T10:05:28.001Z [http-nio-8080-exec-200] ERROR o.a.c.c.C.[.[.[/].[dispatcherServlet] - Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception
java.lang.OutOfMemoryError: Java heap space
```

**Leaked Database Internals (Client View of 200 OK Response):**
```json
{
  "status": "ERROR",
  "message": "could not extract ResultSet; SQL [n/a]",
  "rootCause": "org.postgresql.util.PSQLException: ERROR: deadlock detected\n  Detail: Process 1234 waits for ShareLock on transaction 5678; blocked by process 9012.\n  Hint: See server log for query details.\n  Where: while updating tuple (0, 1) in relation \"payment_intent\""
}
```

## 7. Root Cause Analysis

The root cause of the platform blackout was the **impedance mismatch between business logic failure and infrastructure signaling.**

Modern infrastructure (Envoy, Spring Cloud Gateway, Resilience4j, Kubernetes) relies entirely on standard HTTP status codes and protocol-level signals to make routing, load-balancing, and circuit-breaking decisions.

When the application returned `200 OK` for a failure:
1.  **Resilience4j** recorded a success.
2.  **Spring Cloud Gateway** did not trigger its retry mechanism.
3.  **Clients** received a 200, parsed the body, realized it was an error, and immediately retried the request manually, creating a retry storm.

Furthermore, the lack of centralized exception handling meant that the default Spring Boot `BasicErrorController` was circumvented, and no sanitization of exception messages occurred. This directly caused the PCI-DSS compliance violation when the database schema was leaked in the response body.

Spring's `ExceptionHandlerMethodResolver` relies on a clearly defined exception hierarchy. By catching `Exception` directly in the controller, the developer effectively bypassed Spring's routing mechanism, hardcoding a monolithic, unsafe response path.

## 8. Debugging Process

To triage this incident, the SRE team followed these steps:

1.  **Metric Discrepancy:** The team looked at Prometheus. `http_server_requests_seconds_count{status="200"}` showed a massive spike, while business metrics (`finflow_payments_completed_total`) flatlined. This immediately pointed to the "200 OK with error body" anti-pattern.
2.  **Trace Analysis:** Searching for slow requests in Tempo (OpenTelemetry), they found traces that spent 2000ms in the `stripeGateway` span, returning an exception, but the parent HTTP span completed with status 200.
3.  **Header Inspection:** Checking the responses at the API Gateway layer, they noticed the absence of standard HTTP headers like `Retry-After`, which could have helped back off the clients.
4.  **Code Audit:** A quick `grep` for `catch (Exception` in the controllers revealed the problematic `PaymentController`.

## 9. Correct Implementation

The correct implementation establishes a robust, stackless exception hierarchy, leverages RFC 9457 `ProblemDetail`, and centralizes handling with `@RestControllerAdvice`.

### Domain Exception Hierarchy

We define a base exception, and then categorize exceptions by their HTTP semantic equivalent. We override `fillInStackTrace()` to prevent the JVM from capturing the stack trace for expected business exceptions, drastically improving performance.

```java
package com.finflow.chapter090.domain.exceptions;

public abstract class FinFlowException extends RuntimeException {
    private final String errorCode;
    private final boolean writableStackTrace;

    protected FinFlowException(String message, String errorCode, Throwable cause, boolean writableStackTrace) {
        super(message, cause, true, writableStackTrace);
        this.errorCode = errorCode;
        this.writableStackTrace = writableStackTrace;
    }

    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        // Performance optimization: skip stack trace for business exceptions
        if (!writableStackTrace) {
            return this;
        }
        return super.fillInStackTrace();
    }
}

// 4xx Domain Exceptions (No stack trace needed)
public class DomainException extends FinFlowException {
    public DomainException(String message, String errorCode) {
        super(message, errorCode, null, false); 
    }
}

public class PaymentDeclinedException extends DomainException {
    public PaymentDeclinedException(String reason) {
        super("Payment declined: " + reason, "PAYMENT_DECLINED");
    }
}

public class IdempotencyConflictException extends DomainException {
    public IdempotencyConflictException(String idempotencyKey) {
        super("Conflict for idempotency key: " + idempotencyKey, "IDEMPOTENCY_CONFLICT");
    }
}

// 5xx Infrastructure Exceptions (Stack trace preserved)
public class InfrastructureException extends FinFlowException {
    public InfrastructureException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, cause, true);
    }
}

public class GatewayTimeoutException extends InfrastructureException {
    public GatewayTimeoutException(String message, Throwable cause) {
        super(message, "GATEWAY_TIMEOUT", cause);
    }
}
```

### Global Controller Advice

We extend `ResponseEntityExceptionHandler` to ensure Spring's internal exceptions are also converted to `ProblemDetail`.

```java
package com.finflow.chapter090.correct;

import com.finflow.chapter090.domain.exceptions.DomainException;
import com.finflow.chapter090.domain.exceptions.GatewayTimeoutException;
import com.finflow.chapter090.domain.exceptions.IdempotencyConflictException;
import com.finflow.chapter090.domain.exceptions.PaymentDeclinedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TRACE_ID_KEY = "traceId";

    @ExceptionHandler(PaymentDeclinedException.class)
    public ProblemDetail handlePaymentDeclined(PaymentDeclinedException ex, WebRequest request) {
        log.warn("Payment declined: {}", ex.getMessage());
        return createProblemDetail(ex, HttpStatus.UNPROCESSABLE_ENTITY, "Payment Declined", URI.create("https://docs.finflow.com/errors/payment-declined"));
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ProblemDetail handleIdempotencyConflict(IdempotencyConflictException ex, WebRequest request) {
        log.warn("Idempotency conflict: {}", ex.getMessage());
        return createProblemDetail(ex, HttpStatus.CONFLICT, "Idempotency Conflict", URI.create("https://docs.finflow.com/errors/idempotency"));
    }

    @ExceptionHandler(GatewayTimeoutException.class)
    public ResponseEntity<ProblemDetail> handleGatewayTimeout(GatewayTimeoutException ex, WebRequest request) {
        log.error("Gateway timeout", ex);
        ProblemDetail problem = createProblemDetail(ex, HttpStatus.SERVICE_UNAVAILABLE, "Upstream Gateway Timeout", URI.create("https://docs.finflow.com/errors/timeout"));
        problem.setProperty("retryable", true);
        
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.RETRY_AFTER, "5"); // Hint to clients: wait 5 seconds

        return new ResponseEntity<>(problem, headers, HttpStatus.SERVICE_UNAVAILABLE);
    }

    // Fallback for unhandled exceptions to prevent leaking internals
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleAllUncaughtException(Exception ex, WebRequest request) {
        log.error("Unknown error occurred", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An internal system error occurred.");
        problem.setType(URI.create("https://docs.finflow.com/errors/internal-error"));
        problem.setTitle("Internal Server Error");
        problem.setProperty("errorCode", "INTERNAL_ERROR");
        problem.setProperty("timestamp", Instant.now().toString());
        problem.setProperty("traceId", MDC.get(TRACE_ID_KEY));
        return problem;
    }

    private ProblemDetail createProblemDetail(com.finflow.chapter090.domain.exceptions.FinFlowException ex, HttpStatus status, String title, URI type) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setType(type);
        problem.setTitle(title);
        problem.setProperty("errorCode", ex.getErrorCode());
        problem.setProperty("timestamp", Instant.now().toString());
        
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
        return problem;
    }
}
```

## 10. Performance Comparison

**(Illustrative) Stack Trace Generation Cost**

When throwing thousands of exceptions per second (e.g., heavily validating incoming batches), the JVM cost of `Throwable.fillInStackTrace()` becomes a massive bottleneck. It requires walking the thread's call stack and allocating memory for `StackTraceElement[]`.

By utilizing stackless exceptions (`writableStackTrace = false`) for expected domain flows (like 4xx validation errors or payment declines), we bypass this entirely.

| Exception Type | `fillInStackTrace` | Allocations per 10k throws | Throughput (req/sec) |
| :--- | :--- | :--- | :--- |
| Standard `RuntimeException` | True | ~45 MB | 2,800 |
| Stackless `DomainException` | False | < 1 MB | 3,950 |

Using stackless exceptions for business logic routing is one of the most effective micro-optimizations in high-throughput Spring Boot applications.

## 11. Best Practices

1.  **Always Map to HTTP Status Codes:** Use 4xx for client errors (validation, lack of funds, idempotency) and 5xx for server errors (database down, null pointers, downstream timeouts).
2.  **Extend `ResponseEntityExceptionHandler`:** Do not implement `@RestControllerAdvice` from scratch. Extending this base class ensures you correctly handle framework-level exceptions like `HttpMediaTypeNotSupportedException` and `MethodArgumentNotValidException` with the same `ProblemDetail` format.
3.  **Use RFC 9457 `ProblemDetail`:** Standardize on this specification. It provides a uniform contract for frontend and API consumers.
4.  **Inject Trace IDs:** Always include the `traceId` (from MDC) in the error payload. It is the only way a client can report an issue that you can actually find in your logs.
5.  **Use `Retry-After`:** For transient 5xx or 429 (Too Many Requests) errors, provide the `Retry-After` header to manage client backoff behavior.
6.  **Sanitize 500 Errors:** Never leak stack traces, database schemas, or internal configuration details in the response body. Map generic exceptions to a sanitized "Internal Server Error" message.

## 12. Common Mistakes

*   **The 200 OK Anti-pattern:** Returning an HTTP 200 with an error object in the body, blinding gateways and circuit breakers.
*   **Swallowing Exceptions:** Catching an exception, logging it, and returning `null`, causing NullPointerExceptions further up the chain.
*   **Catching `Throwable`:** Catching `Throwable` instead of `Exception`. This intercepts severe JVM errors like `OutOfMemoryError` or `StackOverflowError` which should cause the application to crash, not be handled gracefully.
*   **Forgetting `spring.mvc.problemdetails.enabled=true`:** In Spring Boot 3, this property is required to automatically convert default Spring exceptions into the RFC 9457 format.
*   **Logging Exceptions in Multiple Places:** Logging the exception in the service layer, re-throwing it, and logging it again in the ControllerAdvice creates excessive log noise and hurts APM ingestion costs.

## 13. Interview Questions

*   **Junior:** What is the difference between `@ExceptionHandler` and `@RestControllerAdvice`?
*   **Mid:** Why should you avoid returning HTTP 200 for a failed request? How does this impact API Gateways?
*   **Senior:** Explain the matching algorithm `ExceptionHandlerMethodResolver` uses when an exception is thrown that matches multiple handler signatures.
*   **Staff:** How would you design an exception hierarchy to prevent performance degradation caused by `fillInStackTrace()` during high-volume validation failures?
*   **Principal:** Describe a strategy to standardize error responses across 50 microservices, ensuring backward compatibility, proper RFC 9457 compliance, and automated circuit-breaker configuration based on error codes.

## 14. Hands-on Exercise

**Task:** Implement a custom RFC 9457 `ProblemDetail` handler for a new `RateLimitExceededException`.
**Requirements:**
1. Create a `RateLimitExceededException` extending `DomainException` (HTTP 429).
2. Add a global handler in `GlobalExceptionHandler`.
3. The response must include a `Retry-After` header calculated dynamically based on a property inside the exception (e.g., `ex.getSecondsUntilReset()`).
4. Ensure the `ProblemDetail` payload includes a custom property `quota` indicating the allowed requests per minute.

## 15. Advanced Challenge

**Lightweight Stackless Business Exceptions Framework:**
Design a pattern using a `static final` cached instance of a stackless exception for zero-allocation business rejections (e.g., `public static final InvalidCurrencyException INSTANCE = new InvalidCurrencyException(...)`). Modify your controller advice to handle these singletons safely, realizing that they cannot contain dynamic properties (like "Currency XYZ is invalid"). Propose a way to pass the dynamic context (perhaps via a thread-local or request attribute) so the advice can still render a meaningful message while maintaining zero allocations.

## 16. Production Checklist

*   [ ] `spring.mvc.problemdetails.enabled=true` is set in `application.yml`.
*   [ ] `@RestControllerAdvice` extends `ResponseEntityExceptionHandler`.
*   [ ] A "catch-all" handler for `Exception.class` exists and sanitizes output (no stack traces).
*   [ ] Business exceptions (4xx) override `fillInStackTrace()` to disable stack capture.
*   [ ] All `ProblemDetail` payloads include the OpenTelemetry/MDC `traceId`.
*   [ ] Transient errors (429, 503) include the `Retry-After` HTTP header.
*   [ ] No controller uses a raw `catch (Exception e)` block to format HTTP responses.
*   [ ] Error codes are documented and stable for API consumers.
