# Module 05: Validation and Exception Handling

## Issue 5.1: Missing Nested `@Valid` Cascading & Production SQL Information Leakage

---

### 1. Scenario

During a major checkout release of the **FinFlow Core Payment Service**:
1. Fraudulent users discover an exploit: by sending an order with a nested list of items where `quantity: 0` and `price: -50.00`, the server accepts and confirms the order without error. The checkout service saves corrupt orders to the database, resulting in financial loss.
2. When a duplicate order is submitted, the application crashes with an unhandled **HTTP 500 Internal Server Error** and returns a response payload containing:
   ```json
   {
     "message": "ERROR: duplicate key value violates unique constraint 'uk_orders_reference_id' ON TABLE finflow_orders_tbl"
   }
   ```
   Security auditors flag this as a **High-Severity Information Disclosure Vulnerability (CWE-209)** because it exposes internal database schema and constraint names to public clients.

---

### 2. Symptoms

```text
1. Invalid nested items in List<OrderItemRequest> bypass validation rules (@Min(1), @DecimalMin("0.01")) completely.
2. The controller has @Valid @RequestBody CreateOrderRequest request, yet invalid items inside the items list are processed.
3. Database constraint collisions return HTTP 500 containing internal SQL queries and table names.
4. Client receives unstructured, inconsistent error bodies across different endpoints.
```

---

### 3. Possible Root Causes

1. **Missing Nested `@Valid` Annotation (Most Likely for Validation Bypass):** Putting `@Valid` on the controller method argument `@Valid @RequestBody CreateOrderRequest` only validates the root object's immediate fields. It does **NOT** cascade into elements of `List<OrderItemRequest>` unless the field in `CreateOrderRequest` is explicitly annotated with `@Valid List<OrderItemRequest> items`!
2. **Missing `@Validated` on Service Layer:** Method parameter validation on `@Service` beans (e.g. `public void update(@Min(1) int id)`) is ignored unless the service class has `@Validated` at the class level to activate `MethodValidationPostProcessor`.
3. **Uncaught Persistence Exceptions:** Failing to catch `DataIntegrityViolationException` or `OptimisticLockException` in a `@RestControllerAdvice` handler causes Spring Boot's `BasicErrorController` to serialize the raw exception message into the HTTP response.

---

### 4. Architecture Context: Validation Lifecycle Across Application Layers

```text
HTTP Request Body
       │
       ▼
[Layer 1: Controller Layer] ──► DispatcherServlet + RequestResponseBodyMethodProcessor
       │                         ├── Validates root fields (@NotBlank, @NotNull)
       │                         └── CASCADES to nested objects ONLY IF @Valid is present!
       │                             (Throws MethodArgumentNotValidException if invalid)
       ▼
[Layer 2: Service Layer]    ──► Spring AOP (MethodValidationPostProcessor)
       │                         └── Validates method parameters ONLY IF @Validated is on class!
       │                             (Throws ConstraintViolationException if invalid)
       ▼
[Layer 3: JPA / Entity]     ──► Hibernate PreInsert / PreUpdate Event Listeners
       │                         └── Validates entity annotations before flushing to SQL
       ▼
[Layer 4: Relational DB]    ──► Database Engine (Unique constraints, Foreign keys)
                                 └── Throws DataIntegrityViolationException on collision
                                     MUST BE SANITIZED by @RestControllerAdvice!
```

---

### 5. How to Reproduce the Issue

#### Step 1: Create Request DTO Missing `@Valid` on Nested List
```java
// BUGGY DTO: Missing @Valid on the list field
public record CreateOrderRequest(
        @NotBlank String customerId,
        List<OrderItemRequest> items // <--- BUG: Without @Valid, items inside the list are NEVER validated!
) {}

public record OrderItemRequest(
        @NotBlank String sku,
        @Min(1) int quantity,
        @NotNull BigDecimal price
) {}
```

#### Step 2: Send Request with Invalid Nested Elements
```bash
curl -X POST http://localhost:8085/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-1","items":[{"sku":"","quantity":0,"price":-10}]}'
```
**Outcome:** Spring accepts the request and returns `200 OK`, ignoring all `@Min(1)` and `@NotBlank` annotations on `OrderItemRequest`!

---

### 6. Evidence Collection & Diagnostic Probing

#### Method 1: Inspect Controller Advice Stack Traces in Logs
Look for `MethodArgumentNotValidException`:
```text
2026-08-21T23:38:00.123 WARN  c.f.t.m.e.ProductionSafeExceptionHandler - Validation failed on field 'items[0].quantity': Item quantity must be at least 1
```

#### Method 2: Inspect Security Headers & Error Response Body
Audit the HTTP error response to ensure no internal SQL or stack traces are present:
```bash
curl -s -X POST http://localhost:8085/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"DB_COLLISION","authPin":"FinFlow@2026","items":[{"sku":"SKU-1","quantity":1,"price":10.00}]}' | jq .
```
**Sanitized Output (Safe):**
```json
{
  "type": "https://api.finflow.com/errors/data-integrity-conflict",
  "title": "Data Integrity Conflict (409)",
  "status": 409,
  "detail": "The requested operation violates a unique data constraint (e.g. duplicate key or conflicting entity state).",
  "timestamp": "2026-08-21T18:08:00.000Z"
}
```

---

### 7. Step-by-Step Debugging Procedure

```text
Step 1: Verify Nested DTO Annotation Cascading.
        Inspect every collection or nested object field in the request DTO.
        Ensure @Valid is placed directly on the field (e.g. @Valid List<ItemDto> items).

Step 2: Check Service-Level Method Validation.
        If validation is placed on service methods, verify that @Validated is present on the
        @Service class.

Step 3: Review Global @RestControllerAdvice Coverage.
        Ensure handlers exist for:
        - MethodArgumentNotValidException (Spring MVC @Valid body failures)
        - ConstraintViolationException (Service-level @Validated failures)
        - DataIntegrityViolationException (DB unique constraint collisions)
        - HttpMessageNotReadableException (Malformed JSON)

Step 4: Audit Server Error Configuration.
        Set server.error.include-stacktrace=never and server.error.include-message=never in application.yml.
```

---

### 8. Technical Root Cause Deep-Dive

#### Why Hibernate Validator Requires Explicit `@Valid` for Cascading

Jakarta Bean Validation (JSR-380) specifies that bean validation graphs are **shallow by default**.  
When Hibernate Validator inspects `CreateOrderRequest`:
1. It validates constraints declared directly on `CreateOrderRequest` (`@NotBlank`, `@NotEmpty`).
2. It checks if fields are marked with `@Valid` (the cascading validation trigger).
3. If `@Valid` is **absent**, Hibernate Validator treats the `List<OrderItemRequest>` simply as a generic Java object reference and does **not** inspect or validate the elements inside the collection!

Adding `@Valid` to the field instructs `CascadingPropertyMetaData` to traverse every element inside the collection and execute their constraint validators.

---

### 9. Production-Grade Fixes

#### ✅ Fix 1: Add `@Valid` for Cascading Validation & Custom Constraints
```java
public record CreateOrderRequest(
        @NotBlank(message = "Customer ID must not be blank")
        String customerId,

        @StrongPassword(message = "Authorization PIN/Password is weak")
        String authPin,

        @NotEmpty(message = "Order must contain at least one item")
        @Size(max = 50, message = "Order cannot contain more than 50 items")
        @Valid // <--- ACTIVATES CASCADING VALIDATION ON EVERY ITEM IN THE LIST
        List<OrderItemRequest> items
) {}
```

#### ✅ Fix 2: Implement Production-Safe Global `@RestControllerAdvice`
```java
@RestControllerAdvice
public class ProductionSafeExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProductionSafeExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Request validation failed on " + fieldErrors.size() + " field(s)"
        );
        problem.setTitle("Validation Failure (400)");
        problem.setType(URI.create("https://api.finflow.com/errors/validation-failed"));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("invalidFields", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        // Log technical details internally for SREs/engineers
        log.error("[DataIntegrityViolation] Database constraint error: {}", ex.getMessage(), ex);

        // Return a clean, sanitized error message to external clients
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "The requested operation violates a unique data constraint (e.g. duplicate key or conflicting entity state)."
        );
        problem.setTitle("Data Integrity Conflict (409)");
        problem.setType(URI.create("https://api.finflow.com/errors/data-integrity-conflict"));
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }
}
```

#### ✅ Fix 3: Strict Error Configuration in `application.yml`
```yaml
server:
  error:
    include-stacktrace: never
    include-message: never
    include-exception: false
    include-binding-errors: never
```

---

### 10. Verification

1. **Automated Nested Validation Test:** Run `NestedCascadingValidationTest.java` to verify that invalid items inside `items[0]` trigger 400 Bad Request with field path `items[0].quantity`.
2. **Custom Validator Test:** Run `CustomConstraintValidatorTest.java` to assert that `@StrongPassword` enforces security rules.
3. **Data Leakage Sanitization Test:** Run `SqlLeakageSanitizationTest.java` to confirm that database constraint exceptions return sanitized 409 Conflict bodies without table or constraint names.

---

### 11. Prevention & Production Readiness

1. **ArchUnit Architecture Rule for Request DTOs:**
   Enforce that every `Collection` or custom object field in request DTOs contains `@Valid`.
2. **Never Return Raw Exception Messages to Clients:** Always sanitize exceptions through centralized `@RestControllerAdvice`.
3. **Automate DTO Validation in Unit Tests:** Test invalid constraints with `Validation.buildDefaultValidatorFactory().getValidator()`.

---

### 12. Interview & Production Questions

#### Interview Questions
1. **Q: What is the exact difference between `@Valid` (Jakarta Standard) and `@Validated` (Spring-specific)?**
2. **Q: How does cascading validation work on `List<@Valid OrderItem>` vs `@Valid List<OrderItem>` in Java 21?**
3. **Q: Why does method parameter validation on `@Service` methods fail to execute if `@Validated` is missing from the class?**
4. **Q: How can you inject Spring beans into a custom `ConstraintValidator<A, T>` implementation?**
5. **Q: What is the purpose of Validation Groups in Jakarta Bean Validation?**

#### Production Incident Questions
1. **Incident:** A user submits an order with an empty address. The `@NotBlank` annotation on `AddressDto.street` was ignored because `@Valid` was missing on `OrderDto.address`. How do you audit your codebase for similar missing cascading annotations?
2. **Incident:** In production, an attacker intentionally triggers `BadSqlGrammarException` to extract table schemas from error responses. How do you prevent information leakage across all controllers?
3. **Incident:** A service method has `@Validated` and `@Min(1) int count`. When called internally from another method in the same service class (`this.updateCount(0)`), validation is skipped. Why?
4. **Incident:** You need to validate that `endDate` is strictly after `startDate` in a booking request DTO. How do you implement a class-level custom constraint validator?
5. **Incident:** A high-throughput REST API experiences high GC overhead due to generating thousands of `MethodArgumentNotValidException` objects under invalid traffic spikes. How do you optimize validation performance?

#### Trick Questions
1. **Trick:** Does `@NotNull` on a `String` field reject empty strings (`""`) or whitespace strings (`"   "`)?
2. **Trick:** If a field has both `@NotNull` and `@Size(min = 5, max = 10)`, what happens if the client passes `null`? Does `@Size` throw an error?
3. **Trick:** If you put `@Valid` on a method returning a DTO in a `@RestController`, what happens if the returned DTO violates validation rules?

---

*(Answers and detailed explanations are provided in the Master Answer Guide)*
