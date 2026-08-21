# Module 03: Maven, Gradle, Java & Dependency Conflicts

## Overview
This module covers JVM ClassLoader dynamics, Maven diamond dependency resolution ("Nearest-Wins"), transitive dependency conflicts, and diagnosing runtime `NoSuchMethodError` / `NoClassDefFoundError`.

## Key Scenarios Covered
1. **Maven "Nearest-Wins" Resolution:** How older transitive JARs override newer dependencies and cause runtime method signature mismatches.
2. **`ClassNotFoundException` vs `NoClassDefFoundError`:** Isolates static linking vs dynamic runtime classloader failures.
3. **ClassLoader Physical Origin Diagnosis:** Programmatically inspects `clazz.getProtectionDomain().getCodeSource().getLocation()` to verify the exact JAR file loaded by the JVM.
4. **SHA-256 Checksum Verification:** Validates library stream and file integrity using `DigestUtils`.

## Project Structure
- `src/main/java/.../service/`: `ClassLoaderDiagnosticService.java`, `ChecksumSignatureService.java`.
- `src/main/java/.../controller/`: `DependencyDiagnosticsController.java`.
- `src/test/java/.../`:
  - `ClassLoaderLocationDiagnosticTest.java`
  - `ChecksumSignatureServiceTest.java`
  - `Module03IntegrationTest.java`

## How to Run Tests
```bash
mvn clean test
```

## Complete Documentation
For the full 12-section technical incident guide, see [Module 03 Documentation](../../docs/module-03-dependency-problems.md).
