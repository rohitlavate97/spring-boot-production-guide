---
chapter: 80
topic: Validation — Bean Validation, Custom Validators, Validation Groups, Error Response Contracts
prerequisite_chapters: [30, 40, 50, 60, 70]
reference_system_node: Payment Service (HTTP boundary validation, Jakarta Validation 3.0 / Hibernate Validator, custom constraint validators, validation groups)
---

# Chapter 080: Validation — Bean Validation, Custom Validators, Validation Groups, Error Response Contracts

## 1. Concept

At the outer edge of any robust enterprise architecture lies the first line of defense: syntactic and semantic boundary validation. The principle of defense in depth dictates that the application must never process malformed, incomplete, or malicious data. Boundary validation protects the application's internal domain logic from edge cases, NullPointerExceptions, and logic errors while shielding the database from constraints violations that trigger expensive transaction rollbacks. 

Validation is traditionally categorized into two tiers:
1. **Syntactic Validation:** Does the request conform to the expected format? Are mandatory fields present? Are string lengths within limits? Does the value match a predefined regular expression?
2. **Semantic (Business) Validation:** Does this syntactically valid data make sense in the current business context? For instance, is the account balance sufficient? Does the provided foreign key exist in the database? 

The Jakarta Validation 3.0 specification (formerly Bean Validation 2.0/JSR 380) and its reference implementation, Hibernate Validator 8.x, provide a standardized, annotation-driven approach for declarative syntactic validation. It integrates seamlessly into Spring Boot via the `spring-boot-starter-validation` dependency. 

### `@Valid` vs `@Validated`
Spring offers two primary annotations to trigger validation, which often confuse engineers:
- **`@Valid` (Jakarta Specification):** Standard marker annotation for cascading validation. When applied to method parameters (like `@RequestBody`), Spring MVC instructs the `WebDataBinder` to validate the object before invoking the controller method. When placed on a field inside an object, it triggers nested validation (cascading validation on child objects).
- **`@Validated` (Spring-Specific):** A Spring-specific variant that supports **Validation Groups**. It can be applied at the class level (to enable method-level validation via Spring AOP) or the method parameter level (to trigger validation for specific groups). It does not support cascading validation on fields; you must still use `@Valid` for nested collections or child objects.

## 2. Internal Working

To understand how validation behaves under load, we must unpack its integration into the Spring IoC container and the request processing lifecycle.

### The Spring Validation Infrastructure
When you include the validation starter, Spring Boot autoconfigures a `LocalValidatorFactoryBean`. This bean implements both the Jakarta `jakarta.validation.Validator` and the Spring `org.springframework.validation.Validator` interfaces, acting as a bridge.

It configures a `ConstraintValidatorFactory` tied to the Spring `ApplicationContext`. This is a crucial detail: it allows custom `ConstraintValidator` implementations to use Spring's `@Autowired` for dependency injection. However, this flexibility can be abused, as we will see in the incident analysis.

### Controller Argument Validation Lifecycle
For Spring MVC controllers, validation occurs during data binding:
1. The `DispatcherServlet` routes the request to a `HandlerMethod`.
2. The `RequestMappingHandlerAdapter` invokes the `HandlerMethodArgumentResolver` (specifically `RequestResponseBodyMethodProcessor` for JSON payloads).
3. The payload is deserialized by Jackson into a Java object.
4. The resolver detects the `@Valid` or `@Validated` annotation and invokes the `WebDataBinder` to validate the populated object.
5. Hibernate Validator builds a constraint tree and executes the matching `ConstraintValidator` for each annotation.
6. If violations are found, they are collected into a `BindingResult`.
7. If the controller method signature does not include a `BindingResult` parameter, Spring throws a `MethodArgumentNotValidException`, aborting the request before it reaches your controller code.

### Service Method Validation Lifecycle
When using `@Validated` on a service class, validation is enforced via Spring AOP using the `MethodValidationPostProcessor`.
1. Spring creates a JDK Dynamic Proxy or CGLIB proxy for the service.
2. A `MethodValidationInterceptor` intercepts method calls.
3. It validates the method arguments using the `Validator` API.
4. If violations occur, it throws a `ConstraintViolationException`.

Because AOP proxying involves reflection and interceptor chains, method-level validation is measurably slower than data binding validation in controllers.

## 3. Enterprise Scenario

In the FinFlow Payment Platform, the **Payment Service** exposes REST APIs for processing payments. During peak shopping events, the API receives approximately 4,000 requests per second. 

The API accepts a highly complex `PaymentIntentRequest` payload. The requirements mandate strict multi-tier validation:
- **Basic constraints:** Amounts must be positive; currencies must follow ISO-4217 (3-letter uppercase).
- **Algorithmic constraints:** Credit card numbers must pass the Luhn algorithm checksum to prevent basic typos before hitting external payment gateways.
- **Cross-field constraints:** If the payment method is `CREDIT_CARD`, the payload must contain card details. If the method is `ACH_BANK_TRANSFER`, it must contain routing and account numbers.
- **Group-based constraints:** The same DTO is used for both `CREATE` and `CAPTURE` phases. `CREATE` requires a customer ID, but `CAPTURE` only requires an intent ID and final amount.

To handle this cleanly, the team decided to build custom `ConstraintValidator` implementations for specialized business rules, aiming to keep controllers clean.

## 4. Incorrect Implementation

The engineering team implemented custom validators to enforce stringent checks. Below is the code deployed to production before the incident.

```java
package com.finflow.chapter080.incorrect;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import java.util.regex.Pattern;

// 1. DTO with missing nested validation and poorly designed validators
public class PaymentIntentRequest {

    @NotNull
    private String paymentId;

    @ValidCurrency // Problem 2: Regex compilation per request
    private String currency;

    @ValidBinRouting // Problem 1: Remote I/O inside Validator
    private String cardNumber;

    // Problem 3: Missing @Valid on nested collection! 
    // The items inside the list are NEVER validated.
    private List<SplitItem> splits;

    // Getters and setters omitted
}

public class SplitItem {
    @NotNull
    private String destinationAccountId; // Will be ignored because parent lacks @Valid
}

// ----------------------------------------------------------------------
// Problem 1: I/O and Blocking Calls inside ConstraintValidator
// ----------------------------------------------------------------------
public class BinRoutingValidator implements ConstraintValidator<ValidBinRouting, String> {
    
    @Autowired
    private RestTemplate restTemplate;
    
    // Problem 4: Stateful ConstraintValidator! Thread safety hazard.
    private String lastCheckedBin;

    @Override
    public void initialize(ValidBinRouting constraintAnnotation) {
        // Initialization
    }

    @Override
    public boolean isValid(String cardNumber, ConstraintValidatorContext context) {
        if (cardNumber == null || cardNumber.length() < 6) return false;
        
        String bin = cardNumber.substring(0, 6);
        this.lastCheckedBin = bin; // RACE CONDITION

        try {
            // TERRIBLE PRACTICE: Synchronous remote HTTP call inside a Validator!
            String url = "http://internal-bin-service:8080/api/bins/" + bin;
            Boolean isValidBin = restTemplate.getForObject(url, Boolean.class);
            return isValidBin != null && isValidBin;
        } catch (Exception e) {
            // Fails closed on network error
            return false; 
        }
    }
}

// ----------------------------------------------------------------------
// Problem 2: Expensive CPU operations in Validator
// ----------------------------------------------------------------------
public class CurrencyValidator implements ConstraintValidator<ValidCurrency, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return false;
        
        // TERRIBLE PRACTICE: Compiling a Regex Pattern on EVERY method call
        Pattern pattern = Pattern.compile("^[A-Z]{3}$");
        return pattern.matcher(value).matches();
    }
}

// Controller
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    @PostMapping
    public PaymentResponse createPayment(@Valid @RequestBody PaymentIntentRequest request) {
        // Business logic assuming data is perfectly validated
        return new PaymentResponse("SUCCESS");
    }
}
```

### Analysis of the Flaws

1. **Remote I/O in Validator (`BinRoutingValidator`):** The validator makes a synchronous REST call to an external service. Validation runs on the Tomcat HTTP worker thread handling the request. If the external service slows down, the validator blocks the worker thread, entirely circumventing timeouts configured at the Service or Repository layers.
2. **Stateful Validator:** The `lastCheckedBin` field introduces a critical thread-safety bug. `ConstraintValidator` instances are singletons by default (managed by Spring). Concurrent requests will overwrite this state, leading to unpredictable behavior if the state is used for logic.
3. **Regex Compilation Hotspot (`CurrencyValidator`):** `Pattern.compile()` is a CPU-intensive operation that constructs a finite state automaton. Compiling it on every single request across 4,000 req/sec destroys CPU caches, causes massive object allocation, and triggers frequent Garbage Collection pauses.
4. **Missing `@Valid` on Nested Collections:** The `splits` list lacks the `@Valid` annotation. Consequently, Hibernate Validator ignores the `SplitItem` objects entirely. Null account IDs slip through to the database layer, causing transaction rollbacks deep in the service layer.

## 5. Production Incident

During the Black Friday peak, traffic surged to 4,500 req/sec. The system initially held up well. However, at 10:15 AM, the `internal-bin-service` experienced a minor GC pause, causing its response time to degrade from 5ms to 600ms. 

In the FinFlow Payment Service, the `BinRoutingValidator` faithfully executed on every incoming request. Because the validation phase occurs *before* any service-layer resilience patterns (like Resilience4j Circuit Breakers or TimeLimiters) can take effect, the Tomcat worker threads simply waited for the `RestTemplate` call.

With 4,500 requests arriving per second and each request taking 600ms in the validation phase, Tomcat's default `max-threads` pool of 200 was exhausted in exactly 45 milliseconds.

Once the thread pool saturated, new incoming requests queued up in the OS TCP backlog. When the backlog filled, the reverse proxy (Nginx) began returning `504 Gateway Timeout` and `502 Bad Gateway` to upstream clients. 

Within minutes, twenty FinFlow Payment Service pods were completely locked up. The Kubernetes liveness probes, which were configured to hit a simple Spring Boot actuator endpoint, also timed out because no Tomcat threads were available to serve the health check HTTP requests. Kubernetes began mercilessly killing and restarting the pods in a catastrophic crash loop.

## 6. Logs

**Tomcat Thread Exhaustion (APM Warning):**
```text
[2024-11-29 10:15:00.105] [WARN ] [Tomcat-metrics-logger] - [ThreadPool] Maximum pooled threads (200) exhausted! Queuing requests.
```

**Nginx Edge Gateway Logs:**
```text
10.42.5.12 - - [29/Nov/2024:10:15:04 +0000] "POST /api/v1/payments HTTP/1.1" 504 167 "-" "FinFlow-Checkout-Client"
10.42.5.13 - - [29/Nov/2024:10:15:05 +0000] "POST /api/v1/payments HTTP/1.1" 502 150 "-" "FinFlow-Checkout-Client"
```

**Application Log (Database Constraint Violation due to Missing Nested Validation):**
```text
[2024-11-29 10:02:14.333] [ERROR] [http-nio-8080-exec-45] - [SqlExceptionHelper] Cannot insert the value NULL into column 'destination_account_id', table 'finflow.split_items'; column does not allow nulls. UPDATE fails.
[2024-11-29 10:02:14.335] [ERROR] [http-nio-8080-exec-45] - [GlobalExceptionHandler] DataIntegrityViolationException occurred: could not execute statement; SQL [n/a]; constraint [null]
```

**Thread Dump Excerpt (APM showing threads blocked in Validator):**
```text
"http-nio-8080-exec-11" #45 daemon prio=5 os_prio=0 tid=0x00007f8a94000000 nid=0x1a2b runnable [0x00007f8a8bfff000]
   java.lang.Thread.State: RUNNABLE
	at java.net.SocketInputStream.socketRead0(Native Method)
	at java.net.SocketInputStream.read(SocketInputStream.java:150)
	at java.net.SocketInputStream.read(SocketInputStream.java:121)
	at org.apache.http.impl.io.SessionInputBufferImpl.streamRead(SessionInputBufferImpl.java:137)
    ... (RestTemplate HTTP execution trace) ...
	at com.finflow.chapter080.incorrect.BinRoutingValidator.isValid(BinRoutingValidator.java:25)
	at org.hibernate.validator.internal.engine.constraintvalidation.ConstraintTree.validateSingleConstraint(ConstraintTree.java:281)
	at org.hibernate.validator.internal.engine.ValidatorImpl.validateMetaConstraints(ValidatorImpl.java:625)
	at org.springframework.validation.beanvalidation.SpringValidatorAdapter.validate(SpringValidatorAdapter.java:335)
	at org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor.validateIfApplicable(RequestResponseBodyMethodProcessor.java:272)
```

## 7. Root Cause Analysis

The root cause of the cascading failure was a fundamental misunderstanding of the architectural role of Jakarta Validation.

### Syntactic vs. Semantic Boundaries
Jakarta Bean Validation is designed for **syntactic validation**—ensuring the shape and format of the data are correct. It assumes that validation rules execute purely in memory and are heavily CPU-bound with negligible latency. 

By injecting network I/O into the `isValid` method, the engineering team mutated syntactic validation into **semantic validation**. Semantic validation (does this BIN route exist?) belongs in the Service Layer, where it can be properly wrapped in transaction scopes, circuit breakers, caching mechanisms, and asynchronous futures. 

Because Spring's `WebDataBinder` executes validation synchronously on the HTTP request thread before the controller method is even invoked, a slow validator turns the perimeter of the application into a bottleneck. 

### Secondary Causes
- **Missing Nested Validation:** The failure to add `@Valid` to the `List<SplitItem>` meant malicious or malformed nested objects bypassed the application layer entirely. The database was forced to throw `DataIntegrityViolationExceptions`, which are expensive to generate (involving heavy stack traces) and consume unnecessary database connection pool time.
- **CPU Spikes from Regex:** Prior to the total collapse, APM tools showed excessive CPU utilization. Profiling revealed that `Pattern.compile("^[A-Z]{3}$")` in `CurrencyValidator` accounted for 14% of total application CPU cycles. Java `Pattern` objects are thread-safe and must be compiled once as static constants.

## 8. Debugging Process

1. **Triage:** PagerDuty alerted the team to a massive spike in 5xx errors on the Payment Service API. 
2. **Metrics Observation:** Grafana dashboards showed JVM heap memory was stable, but CPU utilization was elevated, and Tomcat Active Threads sat pegged at 200/200.
3. **Thread Dump Analysis:** The team triggered a thread dump using APM tooling. The trace clearly showed over 190 threads blocked in socket reads originating from `BinRoutingValidator.isValid`.
4. **Immediate Mitigation:** The team rolled back to a previous deployment that did not include the `BinRoutingValidator`. Traffic normalized immediately.
5. **Code Review:** A post-incident code review identified the stateful variable in the validator, the missing `@Valid` on the collection, and the inline regex compilation.

## 9. Correct Implementation

The team rewrote the validation layer with strict architectural boundaries. All remote calls were moved to the Service layer. The validators were rewritten to be stateless, in-memory, zero-allocation (where possible), and blazingly fast.

### Clean DTOs and Validation Groups

```java
package com.finflow.chapter080.correct.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

// Class-level validation for cross-field checks
@ValidPaymentMethodDetails 
public class PaymentIntentRequest {

    // Validation Groups markers
    public interface CreateGroup {}
    public interface CaptureGroup {}

    @NotBlank(groups = CaptureGroup.class, message = "Payment ID required for capture")
    private String paymentId;

    @NotBlank(groups = CreateGroup.class, message = "Customer ID required for creation")
    private String customerId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be strictly positive")
    private BigDecimal amount;

    @ValidCurrency(message = "Invalid ISO-4217 currency code")
    private String currency;

    private PaymentMethodType methodType;

    @ValidCardNumber(groups = CreateGroup.class) // Custom Luhn check
    private String cardNumber;

    private String routingNumber;

    // CORRECT: @Valid cascades validation to nested items
    @Valid
    private List<SplitItem> splits;

    // Getters / Setters
}

public class SplitItem {
    @NotBlank(message = "Destination account is required for splits")
    private String destinationAccountId;
}
```

### High-Performance Custom Validators

```java
package com.finflow.chapter080.correct.validator;

import com.finflow.chapter080.correct.domain.ValidCurrency;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Set;

// 1. O(1) Memory-efficient Currency Validator
public class CurrencyValidator implements ConstraintValidator<ValidCurrency, String> {

    // Pre-computed, immutable set. Much faster than regex for finite sets.
    private static final Set<String> ALLOWED_CURRENCIES = Set.of(
        "USD", "EUR", "GBP", "JPY", "CAD", "AUD", "INR"
    );

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true; // Let @NotNull handle null checks!
        return ALLOWED_CURRENCIES.contains(value);
    }
}
```

```java
package com.finflow.chapter080.correct.validator;

import com.finflow.chapter080.correct.domain.ValidCardNumber;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

// 2. Zero-allocation Mathematical Validator (Luhn Algorithm)
public class LuhnValidator implements ConstraintValidator<ValidCardNumber, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.length() < 13 || value.length() > 19) return true; // Defer to @NotNull/Length
        
        int sum = 0;
        boolean alternate = false;
        
        // Traverse backwards without creating substring allocations
        for (int i = value.length() - 1; i >= 0; i--) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') return false; // Non-numeric
            
            int n = c - '0';
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n = (n % 10) + 1;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
    }
}
```

```java
package com.finflow.chapter080.correct.validator;

import com.finflow.chapter080.correct.domain.PaymentIntentRequest;
import com.finflow.chapter080.correct.domain.PaymentMethodType;
import com.finflow.chapter080.correct.domain.ValidPaymentMethodDetails;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

// 3. Class-Level Cross-Field Validator
public class PaymentMethodDetailsValidator implements ConstraintValidator<ValidPaymentMethodDetails, PaymentIntentRequest> {

    @Override
    public boolean isValid(PaymentIntentRequest request, ConstraintValidatorContext context) {
        if (request == null) return true;

        if (request.getMethodType() == PaymentMethodType.CREDIT_CARD) {
            if (request.getCardNumber() == null) {
                // Customizing the error message path to point to specific fields
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Card number required for CREDIT_CARD")
                       .addPropertyNode("cardNumber")
                       .addConstraintViolation();
                return false;
            }
        } else if (request.getMethodType() == PaymentMethodType.ACH) {
            if (request.getRoutingNumber() == null) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Routing number required for ACH")
                       .addPropertyNode("routingNumber")
                       .addConstraintViolation();
                return false;
            }
        }
        return true;
    }
}
```

### Controller with Groups and Structured Error Responses

```java
package com.finflow.chapter080.correct.web;

import com.finflow.chapter080.correct.domain.PaymentIntentRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    // Trigger validation for CreateGroup
    @PostMapping("/intents")
    public String createIntent(@Validated(PaymentIntentRequest.CreateGroup.class) @RequestBody PaymentIntentRequest request) {
        return "CREATED";
    }

    // Trigger validation for CaptureGroup
    @PostMapping("/intents/{id}/capture")
    public String captureIntent(@Validated(PaymentIntentRequest.CaptureGroup.class) @RequestBody PaymentIntentRequest request) {
        return "CAPTURED";
    }
}
```

### Standardized Error Handling (`@ControllerAdvice`)

When validation fails, Spring throws `MethodArgumentNotValidException`. It is crucial to intercept this and return a standardized, machine-readable JSON structure (like RFC 7807 Problem Details) rather than Spring's verbose internal stack traces.

```java
package com.finflow.chapter080.correct.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ValidationExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidationExceptions(MethodArgumentNotValidException ex) {
        List<FieldErrorDetail> errors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> new FieldErrorDetail(
                error.getField(),
                error.getDefaultMessage(),
                error.getRejectedValue()
            ))
            .collect(Collectors.toList());

        return new ErrorResponse("VALIDATION_FAILED", "Invalid request payload", errors);
    }
    
    // Error Response Records (Java 16+)
    public record ErrorResponse(String code, String message, List<FieldErrorDetail> details) {}
    public record FieldErrorDetail(String field, String message, Object rejectedValue) {}
}
```

## 10. Performance Comparison

Understanding the mechanical sympathy of validation logic is critical for high-throughput systems.

| Metric (Illustrative) | Incorrect Implementation (Remote I/O) | Incorrect Implementation (Regex per request) | Correct Implementation (Set lookup + Math) |
|-------------------|--------------------------------|-------------------------------------|-----------------------------------|
| **Latency (p99)** | 250ms - 600ms (Network bound) | 2.1ms (CPU Bound) | **0.005ms (In-Memory)** |
| **Throughput** | 300 req/sec (Thread starved) | 1,200 req/sec (GC paused) | **8,500 req/sec (CPU optimized)** |
| **Allocations/Req** | 4.5 MB (Jackson + HTTP Client) | 120 KB (`Pattern` / `Matcher`) | **0 Bytes (Luhn alg)** / **O(1) (Set)**|
| **Thread State** | BLOCKED / WAITING | RUNNABLE (High CPU) | **RUNNABLE (Microseconds)** |

By eliminating network calls and minimizing object instantiation (like static Sets and primitive math arrays), the validation layer overhead drops into the low microseconds, virtually disappearing from profiling tools.

## 11. Best Practices

1. **Keep Validators Pure and Synchronous:** `ConstraintValidator.isValid` must be a pure, CPU-bound, synchronous function. Never execute database queries, HTTP calls, or disk I/O inside a validator.
2. **Compile Patterns Statically:** If you must use regex, compile the `Pattern` object once into a `private static final` field.
3. **Respect `@NotNull` Boundaries:** A custom validator should usually return `true` if the value is `null`. Let the explicit `@NotNull` annotation handle null enforcement. This ensures separation of concerns.
4. **Use Class-Level Validators for Cross-Field Logic:** Do not attempt to use reflection inside field-level validators to inspect sibling fields. Use class-level validators to check relationships between `startDate` and `endDate`, or conditional fields.
5. **Standardize Error Responses:** Always map `MethodArgumentNotValidException` to a structured, API-friendly JSON payload. Do not leak Spring internal class names to clients.
6. **Prefer Sets over Regex for Enums/Lookups:** Checking membership in a statically initialized `Set` or `HashSet` is orders of magnitude faster than evaluating regex patterns for finite lists of values.

## 12. Common Mistakes

- **Forgetting `@Valid` on Iterables/Objects:** Marking a `List<ChildDto>` with `@NotNull` does not validate the children. You must annotate it with `@Valid` to trigger cascading validation.
- **Stateful Validators:** Storing temporary variables as class fields in a `ConstraintValidator`. These are singletons managed by Spring. Instance variables cause massive thread safety and race condition vulnerabilities.
- **Mixing Tiers of Validation:** Attempting to check if an "email exists in the database" inside a custom annotation. This is semantic logic and belongs in the service layer where it can leverage transactions and async non-blocking drivers.
- **Leaking `ConstraintViolationException`:** Failing to catch `ConstraintViolationException` (thrown by method-level `@Validated` AOP validation). This returns an ugly HTTP 500 instead of a clean HTTP 400 Bad Request.

## 13. Interview Questions

* **Junior:** What is the difference between `@Valid` and `@Validated`? 
  * *Answer:* `@Valid` is the Jakarta standard for cascading validation and controller parameters. `@Validated` is Spring-specific and supports validation groups and AOP method-level validation.
* **Mid:** Why is it dangerous to inject a Repository into a custom `ConstraintValidator`?
  * *Answer:* It introduces blocking I/O into the WebDataBinder phase, tying up HTTP worker threads before the controller even starts. It bypasses service-layer timeout/circuit breaker logic.
* **Senior:** How do you validate that `endDate` is strictly after `startDate` in a DTO?
  * *Answer:* Create a custom class-level annotation `@ValidDateRange` and apply it to the DTO class. The validator will receive the entire DTO object, allowing comparison of both fields, and can modify the `ConstraintValidatorContext` to bind the error to the specific `endDate` property.
* **Staff:** How does Hibernate Validator process nested validation, and what is its performance impact on deeply nested collections?
  * *Answer:* It recursively traverses the object graph for any field marked `@Valid`. For massive collections, this can cause significant CPU and memory overhead as a `ConstraintViolation` set is built. Heavy data grids should be validated via batch logic in the service layer rather than pure Bean Validation.
* **Principal:** If we transition our Spring Boot application to WebFlux (Reactive), how does Bean Validation behavior change, and what are the risks?
  * *Answer:* Bean Validation is fundamentally synchronous. In WebFlux, if a validator blocks (e.g., regex DOS or poor I/O design), it will block an EventLoop thread, instantly crippling the entire reactive pipeline. Validation must remain strictly non-blocking CPU operations.

## 14. Hands-on Exercise

**Objective:** Implement a custom zero-allocation validator for a credit card expiration date format (`MM/YY`).
1. Create the annotation `@ValidExpirationDate`.
2. Create the validator class implementing `ConstraintValidator`.
3. Ensure the validator checks that the length is exactly 5.
4. Ensure index 2 is a forward slash `/`.
5. Extract the month and year using mathematical operations or `charAt` (do not use `String.split` or `Regex`).
6. Validate that the month is between `01` and `12`.
7. (Bonus) Validate that the expiration date is not in the past relative to the current year/month.

## 15. Advanced Challenge

**Multi-field Conditional Validation with SpEL (Spring Expression Language):**
Create a generic class-level annotation called `@ConditionalValidation`. It should take two parameters: `condition` (a SpEL expression) and `fields` (an array of fields to validate if the condition is true). 
Implement the `ConstraintValidator` that dynamically evaluates the SpEL expression against the DTO instance. If true, dynamically invoke the Validator API to validate the specified fields. 
*Hint:* You will need to inject an `ExpressionParser` and manipulate the `StandardEvaluationContext`.

## 16. Production Checklist

- [ ] All `@RequestBody` incoming DTOs have `@Valid` applied.
- [ ] Nested collections and object fields inside DTOs are annotated with `@Valid` to ensure cascading.
- [ ] No `ConstraintValidator` contains autowired DB Repositories, HTTP clients, or blocking I/O calls.
- [ ] No `ConstraintValidator` contains instance variables storing request state (thread safety).
- [ ] Regular Expressions used in validators are compiled statically, and protected against ReDoS (Regex Denial of Service).
- [ ] `@ControllerAdvice` maps `MethodArgumentNotValidException` to a clean HTTP 400 JSON standard.
- [ ] Error messages do not leak internal stack traces or class names to API consumers.
- [ ] Semantic validations (database state checks, business rules) are moved to the Service Layer, outside of Bean Validation.
