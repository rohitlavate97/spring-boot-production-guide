# Module 05: Validation & Global Exception Handling

## Overview
This module covers Jakarta Bean Validation (JSR-380), cascading validation on nested object collections, custom constraint validators, and sanitizing error responses to prevent database schema and SQL information leakage.

## Key Scenarios Covered
1. **Nested Collection Validation Bypass:** Why Hibernate Validator skips items in `List<Item>` if `@Valid` is missing on the field.
2. **Custom `@StrongPassword` Constraint:** Implements `ConstraintValidator` with regex validation rules.
3. **Information Disclosure Shielding (CWE-209):** Centralizes `@RestControllerAdvice` to sanitize `DataIntegrityViolationException` and shield internal PostgreSQL table and constraint names.
4. **RFC-7807 Structured Field Errors:** Returns machine-readable field error maps on `MethodArgumentNotValidException`.

## Project Structure
- `src/main/java/.../validation/`: `@StrongPassword`, `StrongPasswordValidator.java`.
- `src/main/java/.../model/`: `CreateOrderRequest.java` (`@Valid List<OrderItemRequest>`), `OrderItemRequest.java`.
- `src/main/java/.../exception/`: `ProductionSafeExceptionHandler.java`, `ResourceConflictException.java`.
- `src/main/java/.../controller/`: `OrderValidationController.java`.
- `src/test/java/.../`:
  - `NestedCascadingValidationTest.java`
  - `CustomConstraintValidatorTest.java`
  - `SqlLeakageSanitizationTest.java`
  - `Module05IntegrationTest.java`

## How to Run Tests
```bash
mvn clean test
```

## Complete Documentation
For the full 12-section technical incident guide, see [Module 05 Documentation](../../docs/module-05-validation-exception-handling.md).
