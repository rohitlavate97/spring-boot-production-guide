# Module 02: Configuration & Environment Drift

## Overview
This module explores configuration precedence, profile management, and environment drift across local, CI/CD, and Kubernetes container runtimes.

## Key Scenarios Covered
1. **17-Level Property Precedence Hierarchy:** Demonstrates how CLI args, Java system properties, OS environment variables, and profile YAML files override each other.
2. **Type-Safe Validation with JSR-380:** Enforces fail-fast configuration startup validation using `@ConfigurationProperties` and `@Validated` constraints (`@NotBlank`, `@Min`, `@Max`).
3. **Relaxed Binding 2.0:** Resolves kebab-case, camelCase, and uppercase underscore environment mapping.
4. **Environment Inspector Service:** Programmatically traces which property source supplied a given configuration value at runtime.

## Project Structure
- `src/main/java/.../config/`: `FinFlowCoreProperties.java` (`@ConfigurationProperties`).
- `src/main/java/.../service/`: `EnvironmentInspectorService.java`.
- `src/main/java/.../controller/`: `ConfigDiagnosticsController.java`.
- `src/test/java/.../`:
  - `ConfigurationPropertiesValidationTest.java`
  - `PropertyPrecedenceHierarchyTest.java`
  - `RelaxedBindingTest.java`
  - `Module02IntegrationTest.java`

## How to Run Tests
```bash
mvn clean test
```

## Complete Documentation
For the full 12-section technical incident guide, see [Module 02 Documentation](../../docs/module-02-configuration-problems.md).
