# Module 16: Docker Containerization & Cgroup Traps

## Overview
This module explores containerization mechanics, Linux Control Groups (cgroups v1 vs v2), JVM container awareness (`-XX:+UseContainerSupport`, `-XX:MaxRAMPercentage`), CFS bandwidth quota CPU throttling, PID 1 signal forwarding traps, non-heap memory budgeting, and hardened multi-stage Docker build architecture for Spring Boot microservices.

## Key Scenarios Covered
1. **Linux OOM Killer (Exit Code 137 / SIGKILL):**
   - Why setting `-Xmx1g` inside a 1Gi container guarantees kernel termination due to non-heap memory (Metaspace, thread stacks, CodeCache, DirectByteBuffers, and native glibc memory).
   - Mathematical memory budgeting and configuring `-XX:MaxRAMPercentage=75.0`.
2. **CFS Bandwidth Quota CPU Throttling:**
   - Why fractional CPU limits (e.g. `0.5` or `1.0` cores) cause massive P99 latency spikes when thread pools default to host core counts (e.g. 64 cores).
3. **PID 1 Signal Swallowing & Abrupt Pod Termination:**
   - Why running `ENTRYPOINT java -jar app.jar` in shell form causes `/bin/sh` to swallow `SIGTERM`, preventing Spring Boot graceful shutdown (`server.shutdown=graceful`) and causing `SIGKILL` after grace period timeout.
4. **Cgroup v1 vs v2 Detection:**
   - Reading and parsing `/sys/fs/cgroup` memory and CPU limits directly from Java runtime.
5. **Hardened Multi-Stage Docker Build:**
   - Layer caching, unprivileged `appuser:appgroup` non-root execution, JRE-slim Alpine/Distroless images, layered Spring Boot JAR extraction.

## Project Structure
- `src/main/java/.../service/`:
  - `ContainerMemoryCalculator.java` (Calculates safe JVM memory budget and flags based on cgroup limit).
  - `CgroupDiagnosticsService.java` (Direct inspection of `/sys/fs/cgroup` limits, JVM heap/non-heap metrics).
- `src/main/java/.../config/`: `ContainerResourceConfig.java` (Container-aware dynamic thread pool sizing).
- `src/main/java/.../controller/`: `ContainerDiagnosticsController.java` (REST endpoints for container diagnostics and memory budgeting).
- `Dockerfile`: Production-hardened multi-stage build with non-root security and container JVM flags.
- `Dockerfile.bad`: Antipattern dockerfile illustrating the 5 deadly container traps.
- `src/test/java/.../`:
  - `ContainerMemoryCalculatorTest.java`
  - `CgroupDiagnosticsServiceTest.java`
  - `ContainerDiagnosticsControllerTest.java`
  - `Module16IntegrationTest.java`

## How to Run Tests
```bash
mvn clean test
```

## Complete Documentation
For the full deep-dive technical incident guide, see [Module 16 Documentation](../../docs/module-16-docker-containerization-cgroup-traps.md).
