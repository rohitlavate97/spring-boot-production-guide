# Module 14: JVM Memory Leaks, Metaspace, GC Thrashing & OOM

## Overview
This module explores JVM memory management, heap generation mechanics (Eden, Survivor, Old Gen), non-heap memory pools (Metaspace, CodeCache, Direct Buffers), `ThreadLocal` context memory leaks across thread pools, bounded cache eviction strategies, and garbage collection diagnostics.

## Key Scenarios Covered
1. **`ThreadLocal` Memory & Context Leaks in Thread Pools:**
   - Why missing `ThreadLocal.remove()` causes memory bloat and cross-tenant data corruption in thread pools, and how `AutoCloseable` `try-with-resources` guarantees cleanup.
2. **Unbounded Static Cache Heap Exhaustion:**
   - Demonstrating how unbounded static collections cause `java.lang.OutOfMemoryError: Java heap space`, and refactoring to bounded LRU cache with eviction.
3. **JVM Memory Pool Telemetry (`MemoryMXBean`):**
   - Querying real-time Heap, Non-Heap (Metaspace), and GC pause duration statistics.
4. **Garbage Collection Optimization & Heap Dump Triage:**
   - Analyzing heap dumps (`.hprof`) with Eclipse MAT (Shallow Heap vs Retained Heap, Dominator Tree, GC Roots).

## Project Structure
- `src/main/java/.../context/`: `ThreadLocalContextHolder.java` (Safe `AutoCloseable` scope manager).
- `src/main/java/.../cache/`: `BoundedCacheService.java` (Bounded LRU cache with eviction).
- `src/main/java/.../service/`: `MemoryDiagnosticsService.java` (`MemoryMXBean` telemetry).
- `src/main/java/.../controller/`: `MemoryDiagnosticsController.java`.
- `src/test/java/.../`:
  - `ThreadLocalLeakPreventionTest.java`
  - `BoundedCacheEvictionTest.java`
  - `MemoryPoolMetricsTest.java`
  - `Module14IntegrationTest.java`

## How to Run Tests
```bash
mvn clean test
```

## Complete Documentation
For the full 12-section technical incident guide, see [Module 14 Documentation](../../docs/module-14-jvm-memory-leaks-metaspace-gc-oom.md).
