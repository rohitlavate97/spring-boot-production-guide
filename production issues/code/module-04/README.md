# Module 04: REST, MVC, HTTP & API Network Errors (4xx/5xx)

## Overview
This module explores HTTP status code generation across reverse proxies, API gateways, Spring Security, DispatcherServlet, and application services, alongside Jackson JSON serialization/deserialization traps.

## Key Scenarios Covered
1. **HTTP Error Origin Matrix:** Identifies which architectural layer produces 400, 401, 403, 404, 405, 415, 429, 502, 503, and 504 status codes.
2. **Jackson Enum Casing Resilience:** Custom `@JsonCreator` and `@JsonValue` mappings to prevent 400 Bad Request on case variations.
3. **RFC-7807 `ProblemDetail` Specification:** Standardized, machine-readable API error payloads in Spring Boot 3.
4. **Content Negotiation & Verb Mapping:** Reproduces 415 (Unsupported Media Type), 405 (Method Not Allowed), and 404 (Resource Not Found).

## Project Structure
- `src/main/java/.../model/`: `PaymentRequest.java`, `PaymentMethod.java`.
- `src/main/java/.../exception/`: `GlobalRestExceptionHandler.java` (RFC-7807 ProblemDetail).
- `src/main/java/.../controller/`: `PaymentApiController.java`.
- `src/test/java/.../`:
  - `ContentNegotiationAndStatusTest.java`
  - `JacksonSerializationTrapTest.java`
  - `Module04IntegrationTest.java`

## How to Run Tests
```bash
mvn clean test
```

## Complete Documentation
For the full 12-section technical incident guide, see [Module 04 Documentation](../../docs/module-04-rest-mvc-http-problems.md).
