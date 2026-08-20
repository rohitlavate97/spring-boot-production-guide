---
chapter: 300
topic: Docker for Spring Boot — Multi-Stage Builds, Layered JARs, JVM in Containers, cgroup Limits, Distroless Images
prerequisite_chapters: [10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200, 210, 220, 230, 240, 250, 260, 270, 280, 290]
reference_system_node: Payment Service Containerization & Packaging Engine ↔ Docker Engine & cgroups v2 Runtime (Multi-Stage Dockerfile, Layered JARs, -XX:MaxRAMPercentage=75.0, Distroless Non-Root, OOMKilled 137)
---

# Chapter 300: Docker for Spring Boot — Multi-Stage Builds, Layered JARs, JVM in Containers, cgroup Limits, Distroless Images

## 1. Concept

In traditional bare-metal and virtual machine deployments, the JVM assumes it has access to all physical host memory and CPU cores. However, in modern containerized microservice architectures (Docker and Kubernetes), the Linux kernel restricts container resource consumption using **control groups (cgroups v1 & cgroups v2)**.

Packaging Spring Boot applications improperly for Docker creates severe production vulnerabilities:
1. **The Monolithic Fat JAR Anti-Pattern**: Re-uploading an 80MB–120MB layer to container registries on every 1-line code change, wasting gigabytes of CI/CD network bandwidth.
2. **The OOMKilled (Exit Code 137) Catastrophe**: Hardcoding `-Xmx` or failing to budget for **Off-Heap memory** (Metaspace, Netty Direct Memory, Thread Stacks), causing the Linux kernel to terminate the JVM without warning.
3. **The PID 1 Signal Swallowing Trap**: Running the container with a shell wrapper that ignores `SIGTERM`, preventing Spring Boot's graceful shutdown and dropping active in-flight transactions.
4. **The Root Container Security Hazard**: Running containers as `root` (UID 0), enabling container escape exploits.

```
+-------------------------------------------------------------------------------------------------+
|                                 The Golden Rules of Production Containerization                 |
|                                                                                                 |
|  1. Always Use Layered JARs in Multi-Stage Builds: Extract layers (dependencies,               |
|     spring-boot-loader, snapshot-dependencies, application) so only the tiny ~1MB application  |
|     layer changes between builds.                                                               |
|  2. Never Hardcode -Xmx; Use -XX:MaxRAMPercentage=75.0: Dedicate 75% of container RAM to        |
|     Heap, reserving 25% for Metaspace, Direct Memory, and Thread Stacks to prevent OOMKilled.  |
|  3. Never Run as Root: Create a dedicated non-root user (UID 10001) or use Distroless images.   |
|  4. Always Use 'exec' in Entrypoints: Ensure Java runs as PID 1 to receive SIGTERM signals      |
|     and execute graceful shutdown within the 30-second termination grace period.                |
|  5. Enable ExitOnOutOfMemoryError: Force instant container restart when heap OOM occurs rather  |
|     than lingering in an unresponsive zombie state.                                             |
+-------------------------------------------------------------------------------------------------+
```

---

## 2. Internal Working

### Layered JAR Architecture & Docker Cache Optimization

Spring Boot 3 packages applications into a **Layered JAR** containing four distinct layers organized by update frequency:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           Layered JAR Structure                         │
│                                                                         │
│  Layer 1: dependencies/         (~80MB, Third-party Maven libraries)   │  Changes Rarely
│  Layer 2: spring-boot-loader/   (~300KB, Spring Boot JarLauncher)       │  Changes Rarely
│  Layer 3: snapshot-dependencies/(~0MB, Internal snapshot libraries)    │  Changes Infrequently
│  Layer 4: application/          (~1MB, Your compiled classes & YAML)    │  Changes on Every Commit!
└─────────────────────────────────────────────────────────────────────────┘
```

#### Layer Extraction Mechanics
In a multi-stage Docker build, the builder stage extracts these layers:

```bash
java -Djarmode=layertools -jar app.jar extract
```

When building Docker images, layers are copied from least frequent to most frequent change. When a developer pushes a code fix, Docker reuses cached layers for `dependencies`, transferring **only the 1MB `application` layer** to the registry!

---

### The Anatomy of JVM Memory in Linux Containers

A critical production error is confusing **JVM Heap (`-Xmx`)** with **Total Container Memory (`cgroup memory.max`)**.

```
┌───────────────────────────────────────────────────────────────────────────────────────────────┐
│                                 Linux Container Memory (cgroup Limit)                         │
│                                                                                               │
│  ┌──────────────────────────────────────────────────┐ ┌────────────────────────────────────┐  │
│  │               JVM Heap (-Xmx)                    │ │          Off-Heap Memory           │  │
│  │          -XX:MaxRAMPercentage=75.0               │ │             (25% Buffer)           │  │
│  │                                                  │ │                                    │  │
│  │  • Young Generation (Eden + Survivor)            │ │  • Metaspace (~150MB)              │  │
│  │  • Old Generation                                │ │  • Thread Stacks (1MB × N threads) │  │
│  │                                                  │ │  • Netty Direct Memory (NIO buffers│  │
│  │                                                  │ │  • CodeCache & JIT Compiler        │  │
│  │                                                  │ │  • GC Native Metadata              │  │
│  └──────────────────────────────────────────────────┘ └────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────────────────────────────────────┘
                                                  ▲
                                                  │ Exceeding Container Limit Triggers:
                                                  ▼
                                       Linux Kernel OOM Killer
                                         (SIGKILL / Exit 137)
```

$$\text{Total Memory} = \text{Heap} + \text{Metaspace} + \text{Direct Memory} + (\text{Thread Count} \times \text{ThreadStackSize}) + \text{CodeCache} + \text{Native Overhead}$$

> [!CAUTION]
> If a container has a 4GiB limit and you set `-Xmx4g`, the moment Netty allocates 200MB of direct memory or the JVM creates 300 platform threads (300MB), total memory reaches 4.5GiB. The Linux kernel **immediately terminates the container with `SIGKILL` (Exit Code 137)**.

---

### Container Awareness & cgroups v1 vs v2

Since Java 10+, OpenJDK includes `-XX:+UseContainerSupport` (enabled by default). The JVM reads memory and CPU constraints directly from `/sys/fs/cgroup`:

| Resource | cgroups v1 Path | cgroups v2 Path | JVM Flag |
|---|---|---|---|
| **Memory Limit** | `/sys/fs/cgroup/memory/memory.limit_in_bytes` | `/sys/fs/cgroup/memory.max` | `-XX:MaxRAMPercentage` |
| **CPU Quota** | `/sys/fs/cgroup/cpu/cpu.cfs_quota_us` | `/sys/fs/cgroup/cpu.max` | `-XX:ActiveProcessorCount` |

If a Kubernetes pod specifies `resources.limits.cpu: "2"`, the JVM configures `Runtime.getRuntime().availableProcessors() = 2`, correctly sizing internal ForkJoin pools and garbage collector worker threads.

---

### Signal Handling & The PID 1 Problem in Docker

When Kubernetes shuts down a pod or deploys a rolling update, it sends a `SIGTERM` signal to process ID 1 (PID 1) inside the container.

```
WRONG: Shell Form Entrypoint
CMD java -jar app.jar ──► Spawns /bin/sh as PID 1 ──► Shell ignores SIGTERM!
                                                      Spring never receives shutdown signal!
                                                      Kubelet waits 30s -> Force kills with SIGKILL!

CORRECT: Exec Form Entrypoint with 'exec'
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
   └── 'exec' replaces /bin/sh -> Java becomes PID 1 -> Receives SIGTERM -> Graceful Shutdown!
```

---

## 3. Enterprise Scenario: FinFlow Payment Service Container Packaging

In the **FinFlow Reference Architecture**:

```
Developer Push / CI Pipeline
       │
       ▼
Multi-Stage Docker Build (Eclipse Temurin 21)
       │
       ├── Stage 1 (Builder): Maven Package -> Layertools Extract (dependencies, loader, app)
       │
       └── Stage 2 (Runtime): Non-Root appuser (UID 10001), 75% MaxRAMPercentage, JRE 21 Slim
       │
       ▼
Container Registry (Image size: ~220MB vs ~650MB fat JDK)
       │
       ▼
Kubernetes Cluster (500 Pods of payment-service)
```

- **Container Limit**: 2048MiB RAM, 2 CPU cores.
- **Calculated Heap**: $2048 \times 75\% = 1536\text{ MB}$.
- **Off-Heap Safety Buffer**: $512\text{ MB}$ (Metaspace: 160MB, Netty: 150MB, Threads: 100MB, Native: 102MB).

---

## 4. Incorrect Implementation

Below is a vulnerable Dockerfile demonstrating the anti-patterns described above:

```dockerfile
# ANTI-PATTERN DOCKERFILE (DO NOT USE IN PRODUCTION)
FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app

# Flaw 1: Copies monolithic fat JAR (Invalidates entire layer cache on any 1-line change)
COPY target/*.jar app.jar

# Flaw 2: Runs as root user (Security vulnerability)

# Flaw 3: Hardcodes fixed -Xmx4g (Triggers OOMKilled 137 on a 2GB container)
# Flaw 4: Shell form CMD without 'exec' (Swallows SIGTERM; breaks graceful shutdown)
CMD java -Xmx4g -jar app.jar
```

---

## 5. Production Incident

### Incident Timeline

| Time (UTC) | Event / Telemetry |
|---|---|
| **00:00:00** | Black Friday traffic spikes to 14,000 checkout req/sec on the `payment-service` deployment (16 pods). |
| **00:05:00** | Pods were configured with a Kubernetes limit of `4Gi` and hardcoded JVM flag `-Xmx3840m` (93.75% of container RAM). |
| **00:10:00** | Under heavy SSL handshake and Netty REST client concurrency, Direct Memory grows to 350MB, and 450 platform threads consume 450MB of stack space. |
| **00:12:00** | Total memory consumption reaches **4.3GiB**, breaching the 4GiB Linux cgroup boundary. |
| **00:12:01** | The Linux kernel OOM Killer fires, sending `SIGKILL` to Java processes across all 16 pods simultaneously. |
| **00:12:05** | Kubernetes reports `Exit Code: 137, Reason: OOMKilled`. In-flight checkout requests fail instantly with HTTP 502 Bad Gateway. |
| **00:12:30** | SEV-0 declared: **$22.6M** in payment checkouts dropped. |
| **00:35:00** | SREs replace hardcoded `-Xmx3840m` with `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0` (3072MB Heap, 1024MB Off-Heap buffer). |
| **00:45:00** | Deployment rolled out. Under sustained 16,000 req/sec load, total memory stabilizes at 3.6GiB (well within the 4Gi limit) with zero OOM events. |

---

## 6. Logs & Diagnostics

### 1. Linux Kernel OOM Killer Log (`dmesg -T`)
```text
[Fri Aug 20 00:12:01 2026] Memory cgroup out of memory: Killed process 41280 (java) total-vm:5842100kB, anon-rss:4194304kB, file-rss:32100kB, shmem-rss:0kB, UID:0 pgtables:8420kB oom_score_adj:998
[Fri Aug 20 00:12:01 2026] oom_reaper: reaped process 41280 (java), now anon-rss:0kB, file-rss:0kB, shmem-rss:0kB
```

### 2. Kubernetes Pod Inspection (`kubectl describe pod`)
```text
    State:          Terminated
      Reason:       OOMKilled
      Exit Code:    137
      Started:      Fri, 20 Aug 2026 00:00:00 +0000
      Finished:     Fri, 20 Aug 2026 00:12:01 +0000
```

---

## 7. Root Cause Analysis

```
+-------------------------------------------------------------------------------------------------+
|                               Container OOMKilled Root Cause Chain                              |
|                                                                                                 |
|  1. Fixed -Xmx Allocation (3840MB in 4096MB Container)                                          |
|     └── Left only 256MB for all off-heap native memory allocations.                             |
|                                                                                                 |
|  2. Off-Heap Expansion Under Peak Traffic                                                       |
|     └── Metaspace (160MB) + DirectMemory (350MB) + Thread Stacks (450MB) = 960MB off-heap.     |
|                                                                                                 |
|  3. Linux Kernel cgroup Memory Limit Breach (4.3GiB > 4.0GiB)                                   |
|     └── Kernel OOM Killer immediately dispatched uncatchable SIGKILL (Exit Code 137).           |
|                                                                                                 |
|  4. Remediation: -XX:MaxRAMPercentage=75.0 + ExitOnOutOfMemoryError                             |
|     └── Capped Heap at 3072MB, guaranteeing a 1024MB safety envelope for off-heap allocations.  |
+-------------------------------------------------------------------------------------------------+
```

---

## 8. Debugging Process

```
[1. Check Kernel OOM Logs] Run: dmesg -T | grep -i oom
       │
[2. Inspect Container cgroups] Run: cat /sys/fs/cgroup/memory.max (or memory.limit_in_bytes)
       │
[3. Native Memory Tracking (NMT)] Start JVM with -XX:NativeMemoryTracking=summary
       │ Run: jcmd <pid> VM.native_memory summary
       │
[4. Verify PID 1 Execution] Run: docker exec <container_id> ps -ef (Confirm Java is PID 1)
       │
[5. Rollout] Deploy Multi-Stage Layered Dockerfile with -XX:MaxRAMPercentage=75.0
```

---

## 9. Correct Implementation

### 1. Production Multi-Stage Dockerfile: `docker/Dockerfile.correct`

```dockerfile
# Stage 1: Build & Layer Extraction
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /builder
COPY target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# Stage 2: Hardened Production Runtime (Non-Root)
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /application

# Create dedicated non-root user and group (UID/GID 10001)
RUN groupadd -g 10001 appgroup && \
    useradd -u 10001 -g appgroup -s /bin/bash -m appuser

# Copy extracted layers in order of change frequency (Docker cache optimization)
COPY --from=builder --chown=appuser:appgroup /builder/dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /builder/spring-boot-loader/ ./
COPY --from=builder --chown=appuser:appgroup /builder/snapshot-dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /builder/application/ ./

USER appuser:appgroup

# Production JVM Container Flags:
# -XX:+UseContainerSupport: Detects cgroup v1/v2 memory and CPU limits
# -XX:MaxRAMPercentage=75.0: Dedicates 75% of container RAM to Heap (25% buffer for off-heap)
# -XX:InitialRAMPercentage=50.0: Pre-allocates initial heap
# -XX:+ExitOnOutOfMemoryError: Fail-fast on OOM to trigger instant Kubernetes pod restart
# -XX:+UseG1GC: Standard production low-latency collector
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:InitialRAMPercentage=50.0 \
               -XX:+ExitOnOutOfMemoryError \
               -XX:+UseG1GC \
               -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

# Spring Boot 3.2+ JarLauncher package: org.springframework.boot.loader.launch.JarLauncher
# "exec" replaces the shell process so Java becomes PID 1, receiving SIGTERM for graceful shutdown
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
```

---

### 2. Container Diagnostics Service: `ContainerDiagnosticsService.java`

```java
package com.finflow.chapter300.service;

import com.finflow.chapter300.domain.ContainerRuntimeDiagnostics;
import com.finflow.chapter300.domain.MemoryLimitCalculation;
import org.springframework.stereotype.Service;

@Service
public class ContainerDiagnosticsService {

    private static final long MB = 1024 * 1024;

    public ContainerRuntimeDiagnostics getDiagnostics() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / MB;
        long totalMemory = runtime.totalMemory() / MB;
        long freeMemory = runtime.freeMemory() / MB;
        long usedMemory = totalMemory - freeMemory;

        return new ContainerRuntimeDiagnostics(
                runtime.availableProcessors(),
                maxMemory,
                totalMemory,
                freeMemory,
                usedMemory,
                System.getProperty("java.version"),
                System.getProperty("os.name"),
                ProcessHandle.current().pid()
        );
    }

    public MemoryLimitCalculation calculateMemoryLayout(long containerLimitMb, double maxRamPercentage) {
        if (maxRamPercentage <= 0.0 || maxRamPercentage > 100.0) {
            throw new IllegalArgumentException("maxRamPercentage must be between 0.0 and 100.0");
        }

        long heapLimit = Math.round(containerLimitMb * (maxRamPercentage / 100.0));
        long offHeapBuffer = containerLimitMb - heapLimit;

        String recommendation;
        if (maxRamPercentage > 80.0) {
            recommendation = "DANGEROUS: Heap percentage > 80% risks Linux OOMKilled 137 due to Metaspace, DirectMemory, and Thread Stacks.";
        } else if (maxRamPercentage < 50.0) {
            recommendation = "SUBOPTIMAL: Heap percentage < 50% leaves excessive unused RAM buffer.";
        } else {
            recommendation = "OPTIMAL: 70-75% RAM allocation safely buffers off-heap allocations.";
        }

        return new MemoryLimitCalculation(
                containerLimitMb,
                maxRamPercentage,
                heapLimit,
                offHeapBuffer,
                recommendation
        );
    }
}
```

---

### 3. Graceful Shutdown & Actuator Configuration: `application.yml`

```yaml
server:
  port: 8080
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true # Enables /actuator/health/liveness and /actuator/health/readiness
```

---

## 10. Performance Comparison

Benchmarked on a Spring Boot application running inside a 2048MiB container.

| Metric | Monolithic Fat JAR (JDK Root) | Production Layered Image (JRE Non-Root) |
|---|---|---|
| **Base Image Size** | $\sim 650\text{ MB}$ | **$\sim 220\text{ MB}$ (66% reduction)** |
| **Layer Upload on Code Change** | $85\text{ MB}$ *(Full fat JAR)* | **$1.2\text{ MB}$ (98.5% bandwidth reduction)** |
| **CI/CD Build & Push Time** | $\sim 45\text{ seconds}$ | **$\sim 4\text{ seconds}$** |
| **OOMKilled Risk Under Load** | High *(Fixed `-Xmx` breaches cgroup)* | **Zero (75% Heap + 25% Off-heap buffer)** |
| **Graceful Shutdown Duration** | Abrupt (`SIGKILL` after 30s) | **Clean ($< 1.5\text{s}$ via PID 1 `SIGTERM`)** |
| **Security Execution Context** | `root` (UID 0) | **`appuser` (UID 10001)** |

---

## 11. Best Practices

### The Do's
- **DO use Spring Boot Layered JARs**: Leverage `layertools extract` for Docker cache efficiency.
- **DO set `-XX:MaxRAMPercentage=75.0`**: Provides a safe 25% buffer for native off-heap memory.
- **DO run containers as non-root**: Create a dedicated UID (e.g. 10001) or use Distroless images.
- **DO use `exec` in shell entrypoints**: Ensures Java runs as PID 1 to receive `SIGTERM` signals.
- **DO enable Spring Boot Graceful Shutdown**: Configure `server.shutdown=graceful` and `spring.lifecycle.timeout-per-shutdown-phase=30s`.
- **DO enable Kubernetes Actuator Probes**: Expose `/actuator/health/liveness` and `/actuator/health/readiness`.

### The Don'ts
- **DON'T hardcode `-Xmx` or `-Xms` in Dockerfiles**: Breaks container portability across different pod sizes.
- **DON'T use full JDK images in the runtime stage**: Use slim JRE or Distroless images to reduce CVE attack surface.
- **DON'T copy the fat JAR directly**: Invalidates the Docker layer cache on every commit.
- **DON'T run containers as root (UID 0)**: Violates enterprise security policies and enables privilege escalation.

---

## 12. Common Mistakes

### Mistake 1: The PID 1 Shell Trap
Using `ENTRYPOINT sh -c "java $JAVA_OPTS -jar app.jar"` without `exec`.
**Why it fails**: `/bin/sh` runs as PID 1. When Kubernetes sends `SIGTERM`, `/bin/sh` ignores the signal and does not forward it to Java. After 30 seconds, Kubernetes sends `SIGKILL`, terminating in-flight checkout requests abruptly.
**Production Fix**: Use `exec java ...` to replace the shell process with the JVM.

### Mistake 2: The 90%+ MaxRAMPercentage Trap
Setting `-XX:MaxRAMPercentage=90.0` on a 1GB container.
**Why it fails**: $1024\text{MB} \times 90\% = 921\text{MB}$ Heap. This leaves only $103\text{MB}$ for Metaspace, Direct Memory, and Thread Stacks. Under moderate load, native allocations push total memory past 1024MB, triggering `OOMKilled 137`.
**Production Fix**: Maintain MaxRAMPercentage between 70% and 75%.

---

## 13. Interview Questions

### Junior Tier
**Q: What is the purpose of Spring Boot's Layered JAR feature in Docker builds?**
> **Answer**: A standard fat JAR bundles all third-party dependencies, loader classes, and application code into a single archive. Whenever application code changes, Docker rebuilds and uploads the entire 80–120MB layer. Layered JARs split the archive into four separate layers (`dependencies`, `spring-boot-loader`, `snapshot-dependencies`, `application`). Because dependencies change rarely, Docker caches them permanently, uploading only the 1MB `application` layer on new builds, speeding up CI/CD pipelines by up to 90%.

### Mid Tier
**Q: Why should you use `-XX:MaxRAMPercentage` instead of `-Xmx` in Docker containers?**
> **Answer**: In container environments, pod resource limits can change dynamically across environments (e.g. 1GB in Dev, 4GB in Staging, 8GB in Prod). Hardcoding `-Xmx4g` breaks when deployed on smaller nodes and requires Dockerfile changes for every scaling event. `-XX:MaxRAMPercentage=75.0` leverages JVM Container Support to calculate the heap size dynamically as 75% of the container's active cgroup memory limit, ensuring portability and automatically reserving 25% for off-heap native memory.

### Senior Tier
**Q: What causes Linux `OOMKilled` (Exit Code 137) when JVM Heap usage is below `-Xmx`?**
> **Answer**: Linux cgroups enforce limits on the *entire process memory*, not just the JVM Heap. Total memory includes Off-Heap allocations: Metaspace (class metadata), Netty Direct Memory (NIO byte buffers), Thread Stacks ($\sim 1\text{MB}$ per platform thread), JIT CodeCache, and GC native structures. If `-Xmx` is set too close to the container limit (e.g. `-Xmx3800m` on a 4096MB container), off-heap allocations push total memory beyond the cgroup boundary. The Linux kernel immediately terminates the container with `SIGKILL` (Exit Code 137) without throwing a Java `OutOfMemoryError`.

### Staff Tier
**Q: Explain how to configure graceful shutdown in Spring Boot Docker containers, including the PID 1 problem.**
> **Answer**: Graceful shutdown requires coordination across three layers:
> 1. **Spring Boot**: Set `server.shutdown=graceful` and `spring.lifecycle.timeout-per-shutdown-phase=30s`. When `SIGTERM` is received, Tomcat stops accepting new connections and waits for active requests to finish.
> 2. **Docker Entrypoint**: Java must run as PID 1 to receive `SIGTERM` directly from Kubernetes. If using a shell wrapper, prefix the command with `exec` (`exec java ...`) so the JVM replaces the shell process.
> 3. **Kubernetes**: Ensure `terminationGracePeriodSeconds` (default 30s) exceeds Spring's shutdown phase timeout.

### Principal Tier
**Q: Design a Zero-CVE, Multi-Architecture Minimalist Container Pipeline for 1,000 Spring Boot microservices.**
> **Answer**: A Principal-level architecture uses **jlink custom runtime images with Distroless and CDS (Class Data Sharing)**:
> 1. **Build Stage**: Maven builds the Layered JAR with CDS training (`-XX:ArchiveClassesAtExit=app-cds.jsa`).
> 2. **Custom Minimal JRE via `jlink`**: Inspect application modules with `jdeps` and build a stripped-down JRE containing only required modules (e.g. `java.base`, `java.sql`, `java.net.http`), reducing runtime JRE size from 180MB to $< 45\text{MB}$.
> 3. **Runtime Base**: `gcr.io/distroless/base-nossl-debian12:nonroot`. Contains zero shell, package manager, or OS utilities, eliminating 99% of CVE vulnerabilities.
> 4. **Multi-Arch Compilation**: Build via Docker Buildx targeting `linux/amd64` and `linux/arm64` (AWS Graviton3) for 40% cloud compute cost savings.

---

## 14. Hands-on Exercise

### Objective
Author a production-grade multi-stage Dockerfile utilizing Spring Boot Layered JARs, non-root execution, and container-aware JVM flags.

### Solution

```dockerfile
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /builder
COPY target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /application

RUN groupadd -g 10001 appgroup && \
    useradd -u 10001 -g appgroup -s /bin/bash -m appuser

COPY --from=builder --chown=appuser:appgroup /builder/dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /builder/spring-boot-loader/ ./
COPY --from=builder --chown=appuser:appgroup /builder/snapshot-dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /builder/application/ ./

USER appuser:appgroup

ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:InitialRAMPercentage=50.0 \
               -XX:+ExitOnOutOfMemoryError \
               -XX:+UseG1GC"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
```

---

## 15. Advanced Challenge: Class Data Sharing (CDS) Container Optimization

### Enterprise Problem Statement
Optimize container startup time by generating a Class Data Sharing (CDS) archive during image build, reducing cold-start latency by up to 40% on Kubernetes auto-scaling.

### Enterprise Solution

```dockerfile
# Stage 1: Build, Extract & Train CDS Archive
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /builder
COPY target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# Train CDS Archive
RUN java -XX:ArchiveClassesAtExit=application.jsa \
         -Dspring.context.exit=onRefresh \
         -jar app.jar || true

# Stage 2: Runtime with CDS
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /application
COPY --from=builder /builder/dependencies/ ./
COPY --from=builder /builder/spring-boot-loader/ ./
COPY --from=builder /builder/snapshot-dependencies/ ./
COPY --from=builder /builder/application/ ./
COPY --from=builder /builder/application.jsa ./

ENTRYPOINT ["sh", "-c", "exec java -XX:SharedArchiveFile=application.jsa -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 org.springframework.boot.loader.launch.JarLauncher"]
```

---

## 16. Production Checklist

Before approving any Docker pull request:

- [ ] **Multi-Stage Build**: Confirm build tools (JDK/Maven) are excluded from the runtime image.
- [ ] **Layered JAR Extraction**: Confirm `dependencies/`, `spring-boot-loader/`, and `application/` layers are copied separately.
- [ ] **Non-Root User**: Ensure container executes under dedicated non-root UID (e.g. `USER 10001:10001`).
- [ ] **`-XX:MaxRAMPercentage=75.0` Configured**: Confirm no hardcoded `-Xmx` flags exist.
- [ ] **`exec` Used in Entrypoint**: Ensure Java runs as PID 1 to receive `SIGTERM` signals.
- [ ] **Graceful Shutdown Configured**: Confirm `server.shutdown=graceful` in `application.yml`.
- [ ] **Kubernetes Probes Exposed**: Verify `/actuator/health/liveness` and `/actuator/health/readiness` are active.
