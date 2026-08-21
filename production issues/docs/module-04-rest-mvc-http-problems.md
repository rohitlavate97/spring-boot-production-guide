# Module 04: REST, MVC, HTTP, and API Problems

## Issue 4.1: HTTP 4xx & 5xx Diagnostic Protocol & Jackson Deserialization Traps

---

### 1. Scenario

During a global release of the **FinFlow Merchant Checkout API**, mobile clients in iOS and Android suddenly encounter widespread checkout failures:
- 30% of requests receive **HTTP 400 Bad Request** with empty bodies.
- 20% of requests receive **HTTP 415 Unsupported Media Type**.
- Third-party webhook integrations report **HTTP 504 Gateway Timeout** during flash-sale traffic surges.

The frontend teams claim the backend is broken. The backend teams claim the mobile clients are sending invalid payloads. 

You are summoned to investigate which layer is generating each HTTP error status and resolve the failures permanently.

---

### 2. Symptoms & Error Matrix

```text
1. Mobile App POST /api/v1/payments/process ──► 415 Unsupported Media Type
   (Client sent 'Content-Type: text/plain;charset=UTF-8' instead of 'application/json').
2. Mobile App POST /api/v1/payments/process ──► 400 Bad Request
   (Payload sent enum value "credit_card" in lowercase, while server Java enum expected "CREDIT_CARD").
3. Webhook POST /api/v1/payments/process ──► 504 Gateway Timeout
   (Downstream banking API took 32 seconds to respond; Nginx proxy_read_timeout cut the connection at 30 seconds).
4. Monitoring shows a spike in HttpMessageNotReadableException and HttpMediaTypeNotSupportedException.
```

---

### 3. Possible Root Causes by Architectural Layer

To troubleshoot HTTP errors systematically, determine **which component generated the HTTP status code**:

```text
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                               HTTP ERROR ORIGIN MATRIX                                 │
│                                                                                        │
│  🌐 NGINX / INGRESS       ──► 502 Bad Gateway (upstream crashed or connection refused) │
│                               504 Gateway Timeout (upstream exceeded proxy_read_timeout)│
│                               413 Request Entity Too Large (client_max_body_size)      │
│                                                                                        │
│  🛡️ API GATEWAY          ──► 401 Unauthorized (JWT missing or expired at the edge)    │
│                               403 Forbidden (insufficient gateway scope)               │
│                               429 Too Many Requests (Token bucket rate-limit exceeded) │
│                               503 Service Unavailable (Circuit breaker OPEN)           │
│                                                                                        │
│  🔒 SPRING SECURITY      ──► 401 Unauthorized (AuthenticationEntryPoint triggered)     │
│                               403 Forbidden (AccessDeniedHandler triggered)            │
│                                                                                        │
│  🚦 DISPATCHER SERVLET   ──► 400 Bad Request (HttpMessageNotReadableException)         │
│                               404 Not Found (NoHandlerFoundException / unmapped path)  │
│                               405 Method Not Allowed (HttpRequestMethodNotSupported)   │
│                               415 Unsupported Media Type (Content-Type mismatch)       │
│                                                                                        │
│  💼 APPLICATION SERVICE  ──► 400/422 Unprocessable Entity (Business validation failure)│
│                               409 Conflict (Optimistic locking / duplicate resource)   │
│                               500 Internal Server Error (Uncaught RuntimeException)    │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 4. Architecture Context: Spring MVC Request Lifecycle

```text
Client Request
      │
      ▼
Nginx / Ingress (SSL Termination, Buffer limits, 30s timeout)
      │
      ▼
Spring Security Filter Chain (Authentication & Authorization)
      │
      ▼
DispatcherServlet (Route resolution via HandlerMapping)
      │
      ├─► [No mapping found] ──► 404 Not Found
      ├─► [Wrong HTTP Verb]  ──► 405 Method Not Allowed
      │
      ▼
HandlerAdapter & RequestResponseBodyMethodProcessor
      │
      ├─► [Wrong Content-Type] ──► 415 Unsupported Media Type
      ├─► [Unparseable JSON]   ──► 400 HttpMessageNotReadableException
      │
      ▼
Controller Action (Business logic execution)
      │
      ├─► [Unhandled Exception] ──► GlobalRestExceptionHandler (ProblemDetail 400/500)
      └─► [Success]             ──► 200 OK + JSON Response
```

---

### 5. How to Reproduce the Issue

#### Step 1: Define DTO with Enum and Instant Date Fields
```java
public record PaymentRequest(
        String orderId,
        BigDecimal amount,
        String currency,
        PaymentMethod method,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant timestamp
) {}
```

#### Step 2: Configure Jackson Fail on Unknown Properties
```yaml
spring:
  jackson:
    deserialization:
      fail-on-unknown-properties: true
```

#### Step 3: Run Reproductions
- Send `POST /api/v1/payments/process` with `Content-Type: text/plain` $\implies$ **415 Unsupported Media Type**.
- Send `POST /api/v1/payments/process` with `{"method": "INVALID_ENUM"}` $\implies$ **400 Bad Request**.
- Send `PUT /api/v1/payments/process` $\implies$ **405 Method Not Allowed**.

---

### 6. Evidence Collection & Diagnostic Probing

#### Method 1: `curl -v` Verbose Header Inspection
```bash
curl -v -X POST http://localhost:8084/api/v1/payments/process \
  -H "Content-Type: text/plain" \
  -d '{"orderId":"123"}'
```
**Response Headers:**
```text
< HTTP/1.1 415 Unsupported Media Type
< Content-Type: application/problem+json
< Content-Length: 218
```

#### Method 2: Enable Spring Web DEBUG Logging in `application.yml`
```yaml
logging:
  level:
    org.springframework.web.servlet.DispatcherServlet: DEBUG
    org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping: TRACE
```
**Stdout Log Trace:**
```text
DEBUG o.s.w.s.DispatcherServlet - POST "/api/v1/payments/process", parameters={}, headers={content-type:[text/plain]}
DEBUG o.s.w.s.m.m.a.HttpEntityMethodProcessor - No match for [text/plain], supported: [application/json]
WARN  c.f.t.m.e.GlobalRestExceptionHandler - [415] Content-Type 'text/plain' is not supported. Expected 'application/json'
```

---

### 7. Step-by-Step Debugging Procedure

```text
Step 1: Inspect Response Headers (Server & Via).
        Check if the Server header says "nginx", "Envoy", or "Apache-Coyote/Tomcat".
        This immediately proves which layer generated the response.

Step 2: Inspect Content-Type & Accept Headers.
        Verify that the client sent Content-Type: application/json and Accept: application/json.

Step 3: Audit Jackson Deserialization Error Details.
        Check if an unknown property, invalid enum value, or invalid ISO-8601 date string caused
        HttpMessageNotReadableException.

Step 4: Check Request Timeout Chain.
        Compare client timeout vs Nginx proxy_read_timeout vs Gateway timeout vs Spring MVC async timeout.
        Ensure timeouts INCREASE as you move closer to the database:
        Client (10s) < Ingress (15s) < Gateway (20s) < Spring Boot Service (25s) < DB Lock Timeout (30s).
```

---

### 8. Technical Root Cause Deep-Dive

#### Jackson Enum Deserialization & `@JsonCreator` Robustness

By default, Jackson's `EnumDeserializer` matches enum constants using exact `Enum.name()` string equality. If a mobile client sends lowercase `"credit_card"` instead of `"CREDIT_CARD"`, Jackson fails with `InvalidFormatException` and Spring MVC converts this to `HttpMessageNotReadableException` (400 Bad Request).

To make APIs resilient to casing variations without breaking type safety, implement a custom `@JsonCreator` factory method:

```java
public enum PaymentMethod {
    CREDIT_CARD("CREDIT_CARD"),
    DEBIT_CARD("DEBIT_CARD"),
    CRYPTO("CRYPTO"),
    WIRE_TRANSFER("WIRE_TRANSFER");

    private final String value;

    PaymentMethod(String value) { this.value = value; }

    @JsonValue
    public String getValue() { return value; }

    @JsonCreator
    public static PaymentMethod fromString(String text) {
        for (PaymentMethod m : PaymentMethod.values()) {
            if (m.value.equalsIgnoreCase(text) || m.name().equalsIgnoreCase(text)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Unknown payment method: " + text + ". Allowed: CREDIT_CARD, DEBIT_CARD, CRYPTO, WIRE_TRANSFER");
    }
}
```

---

### 9. Production-Grade Fix: Standardized RFC-7807 `ProblemDetail` Global Handler

Spring Boot 3 introduces native RFC-7807 `ProblemDetail` support for standardized, machine-readable API error payloads:

```java
@RestControllerAdvice
public class GlobalRestExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(HttpMessageNotReadableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Malformed JSON request body or unparseable field type: " + ex.getMessage()
        );
        problem.setTitle("Bad Request Body (400)");
        problem.setType(URI.create("https://api.finflow.com/errors/bad-request-body"));
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleUnsupportedMedia(HttpMediaTypeNotSupportedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Content-Type '" + ex.getContentType() + "' is not supported. Expected 'application/json'"
        );
        problem.setTitle("Unsupported Media Type (415)");
        problem.setType(URI.create("https://api.finflow.com/errors/unsupported-media-type"));
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(problem);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.METHOD_NOT_ALLOWED,
                "HTTP method '" + ex.getMethod() + "' is not supported. Supported methods: " + ex.getSupportedHttpMethods()
        );
        problem.setTitle("Method Not Allowed (405)");
        problem.setType(URI.create("https://api.finflow.com/errors/method-not-allowed"));
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(problem);
    }
}
```

---

### 10. Verification

1. **Automated Status Code Tests:** Run `ContentNegotiationAndStatusTest.java` to verify that 415, 405, and 404 scenarios return RFC-7807 compliant problem structures.
2. **Jackson Serialization Tests:** Run `JacksonSerializationTrapTest.java` to assert that malformed JSON and illegal enum strings are rejected with descriptive 400 responses.
3. **End-to-End Test:** Run `Module04IntegrationTest.java` to confirm happy-path settlement.

---

### 11. Prevention & Production Readiness

1. **Adopt OpenAPI Contract-First Generation:** Generate client SDKs directly from backend OpenAPI schemas.
2. **Set Ingress Max Body Limits:** Configure `client_max_body_size 10m;` in Nginx to prevent memory denial-of-service attacks from enormous JSON payloads.
3. **Enable Spring Boot Problem Details Property:**
   ```yaml
   spring:
     mvc:
       problemdetails:
         enabled: true
   ```

---

### 12. Interview & Production Questions

#### Interview Questions
1. **Q: What is the exact difference between HTTP 400, 422, and 500 when designing RESTful APIs?**
2. **Q: How does Spring MVC's `ContentNegotiationManager` decide which `HttpMessageConverter` to use for request bodies vs response bodies?**
3. **Q: What is the RFC-7807 `ProblemDetail` specification and how does Spring Boot 3 natively support it?**
4. **Q: How does Jackson handle `java.time.Instant` and `LocalDateTime` serialization by default vs with `jackson-datatype-jsr310`?**
5. **Q: Why does Nginx return 504 Gateway Timeout while the Spring Boot service log shows no error at all?**

#### Production Incident Questions
1. **Incident:** After releasing a new version, users report that GET requests to `/api/v1/orders/123` work, but POST requests to `/api/v1/orders/` return 405 Method Not Allowed with an empty body. How do you troubleshoot the trailing slash mapping?
2. **Incident:** An external partner sends JSON requests containing unexpected extra fields. The API fails with HTTP 400 due to `UnrecognizedPropertyException`. How do you configure Jackson to be forward-compatible without breaking strict validation on critical fields?
3. **Incident:** High memory alarms trigger in production. Heap dump analysis shows large `byte[]` arrays inside Tomcat's `Request` buffer during file uploads. How do you stream multipart uploads directly to object storage without buffering in JVM memory?
4. **Incident:** In an API gateway architecture, random requests to a heavy reporting endpoint receive 502 Bad Gateway. The backend service was OOMKilled by the Linux kernel while building a 100MB JSON response. How do you prevent large response buffering crashes?
5. **Incident:** A mobile client sends an HTTP request with `Accept: application/xml`. The server only supports JSON and throws `HttpMediaTypeNotAcceptableException` (406). How do you configure default fallback content negotiation in Spring MVC?

#### Trick Questions
1. **Trick:** If a client sends a `GET` request with a JSON request body (`Content-Type: application/json`), does Spring MVC's `@RequestBody` parse it, and is this allowed by the HTTP/1.1 specification?
2. **Trick:** If `@RequestMapping(value = "/api/items/{id}")` and `@RequestMapping(value = "/api/items/recent")` both exist in a controller, which one matches a GET request to `/api/items/recent`?
3. **Trick:** Does `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")` on `Instant` work without specifying the `timezone = "UTC"` attribute?

---

*(Answers and detailed explanations are provided in the Master Answer Guide)*
