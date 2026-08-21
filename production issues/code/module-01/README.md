# Module 01: Startup & ApplicationContext Failures

## Overview
This module demonstrates common production startup failures in Spring Boot applications, how the `ApplicationContext` initializes beans, and how to debug and resolve bean instantiation hazards.

## Key Scenarios Covered
1. **Circular Constructor Dependencies:** Reproduces `BeanCurrentlyInCreationException` when two beans inject each other in constructors.
2. **Ambiguous Bean Definitions:** Resolves `NoUniqueBeanDefinitionException` using `@Qualifier` and `@Primary`.
3. **Missing Bean Definitions:** Resolves `NoSuchBeanDefinitionException` when configuration is conditionally skipped.
4. **Conditional Bean Registration:** Demonstrates `@ConditionalOnProperty` evaluation mechanics.
5. **Lazy Initialization Trap:** Shows how `@Lazy` masks startup errors and defers catastrophic failures to user runtime requests.
6. **Decoupled Architecture Resolution:** Resolves circular bean graphs using `ApplicationEventPublisher` and `@EventListener`.

## Project Structure
- `src/main/java/.../domain/`: Decoupled order and payment event listeners.
- `src/test/java/.../`:
  - `CircularDependencyReproductionTest.java`
  - `DecoupledOrderFlowIntegrationTest.java`
  - `NoUniqueBeanDefinitionReproductionTest.java`
  - `NoSuchBeanDefinitionReproductionTest.java`
  - `ConditionalBeanEvaluationTest.java`
  - `LazyInitializationRuntimeTrapTest.java`

## How to Run Tests
```bash
mvn clean test
```

## Complete Documentation
For the full 12-section technical incident guide, see [Module 01 Documentation](../../docs/module-01-startup-failures.md).
