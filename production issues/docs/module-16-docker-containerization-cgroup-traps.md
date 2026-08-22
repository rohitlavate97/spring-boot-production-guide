# Module 16: Docker Containerization & Cgroup Traps

## Issue 16.1: Container OOMKills (Exit Code 137), CFS Bandwidth CPU Throttling, and PID 1 Signal Swallowing

---

### 1. Scenario

During a major flash-sale event on the **FinFlow Global Payment Authorization & Tokenization Gateway**:
1. Newly containerized Spring Boot 3.3.5 microservices running on Kubernetes with `resources.limits.memory: "1Gi"` and `resources.limits.cpu: "1.0"` begin sporadically dying with **Kubernetes Exit Code 137 (`OOMKilled`)**.
2. Investigation revealed that the microservice container was launched with hardcoded `-Xmx1024m` (or default JVM Ergonomics misdetecting container memory). While the JVM heap was only at **62% utilization (635MB)**, non-heap allocations (Metaspace, Direct ByteBuffers, thread stacks, and JIT CodeCache) pushed the total process RSS past 1024MB, causing the **Linux kernel cgroup OOM Killer to instantly issue `SIGKILL`**.
3. Concurrently, API P99 latency spiked from **35ms to 2,850ms**. Thread dumps and Prometheus metrics revealed severe **CFS (Completely Fair Scheduler) CPU Quota Throttling**: because `Runtime.getRuntime().availableProcessors()` reported the **host machine's 64 physical cores** rather than the container quota, Tomcat and application thread pools spawned 200+ active threads. These threads exhausted the container's 100ms CFS quota in the first 8ms of each period, leaving all worker threads **completely frozen for the remaining 92ms**!
4. To mitigate, SREs triggered a rolling deployment. However, because the legacy container image used shell-form `ENTRYPOINT java -jar app.jar`, `/bin/sh` ran as **PID 1** and **swallowed the `SIGTERM` signal**. Spring Boot never received the shutdown signal to execute `server.shutdown=graceful`. After Kubernetes' 30-second `terminationGracePeriodSeconds` expired, Kubernetes issued `SIGKILL`, **abruptly terminating 1,200 active payment capture transactions and triggering financial discrepancy alarms**.

---

### 2. Symptoms

```text
1. Linux Kernel cgroup OOM Killer Termination:
   Pod restarts with "Last State: Terminated, Reason: OOMKilled, Exit Code: 137".
   Kernel dmesg logs: "Memory cgroup out of memory: Killed process 1421 (java) total-vm:1842300kB, anon-rss:1044120kB".

2. Severe CFS CPU Bandwidth Throttling:
   container_cpu_cfs_throttled_periods_total / container_cpu_cfs_periods_total > 50%.
   Application P99 latency spikes by 50x-80x despite low average CPU utilization (<35%).

3. Abrupt Payment Transaction Termination on Rollout:
   Zero graceful shutdown logs ("Commencing graceful shutdown...").
   Pods abruptly vanish after exactly 30s during rolling deployments, leaving open TCP sockets and dropping inflight HTTP requests.

4. Container Image Bloat & Attack Surface:
   Monolithic 1.4GB container image containing full JDK, build tools, package managers, and root user execution.

5. Zombie Process Accumulation:
   Spawned child processes (e.g. ImageMagick/PDF rendering or native CLI forks) accumulate as <defunct> zombie processes inside long-running containers.
```

---

### 3. Possible Root Causes

1. **The "100% Heap in Container" Fallacy:** Allocating `-Xmx1g` inside a 1Gi container leaves 0MB headroom for native process memory. A JVM process total memory footprint equals:
   $$\text{Total Memory} = \text{Heap} + \text{Metaspace} + \text{CodeCache} + (\text{Thread Count} \times \text{Stack Size}) + \text{Direct Buffers} + \text{Native Overhead}$$
2. **CFS Bandwidth Quota Exhaustion:** When multiple worker threads execute concurrently on a multi-core host under a fractional container CPU limit (e.g. `1.0` core = 100ms CPU quota per 100ms CFS period), 10 active threads consume the 100ms quota in just 10ms of wall-clock time, causing 90ms of hard throttling.
3. **PID 1 Signal Swallowing in Shell-Form Entrypoint:** In Unix, PID 1 is special: the kernel does not apply default signal actions (like terminating on `SIGTERM`) unless the process explicitly registers a signal handler. `/bin/sh` does not forward signals to child processes by default.
4. **Cgroup v1 vs v2 Accounting Gaps:** Relying on host-level `/proc/meminfo` or misinterpreting Linux Page Cache (`inactive_file`) memory as active JVM memory leak.

---

### 4. Architecture Context: Linux Cgroups, CFS Quotas & JVM Container Mechanics

```text
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        LINUX CGROUP & CONTAINER RESOURCE ISOLATION                     │
│                                                                                        │
│  Host OS (e.g. 64 CPU Cores, 256GB RAM)                                                │
│  ┌──────────────────────────────────────────────────────────────────────────────────┐  │
│  │ Kubernetes Pod Container (Limits: Memory=1024MB, CPU=1.0 Core)                   │  │
│  │                                                                                  │  │
│  │  Linux Cgroup Boundaries (/sys/fs/cgroup):                                       │  │
│  │  - memory.max / memory.limit_in_bytes = 1,073,741,824 bytes (1024MB)             │  │
│  │  - cpu.max / cpu.cfs_quota_us = 100,000 / cpu.cfs_period_us = 100,000 (1.0 Core) │  │
│  │                                                                                  │  │
│  │  ┌────────────────────────────────────────────────────────────────────────────┐  │  │
│  │  │ JVM Process (HotSpot Java 21)                                              │  │  │
│  │  │                                                                            │  │  │
│  │  │  ┌────────────────────────────────────────┐ ┌───────────────────────────┐  │  │  │
│  │  │  │ HEAP MEMORY (MaxRAMPercentage = 75.0%) │ │ OFF-HEAP & NATIVE MEMORY  │  │  │  │
│  │  │  │ -Xmx = 768MB (75% of 1024MB)           │ │ - Metaspace: 128MB        │  │  │  │
│  │  │  │                                        │ │ - CodeCache: 64MB         │  │  │  │
│  │  │  │ [Used Heap: ~500MB]                    │ │ - Thread Stacks: 40MB     │  │  │  │
│  │  │  │                                        │ │ - Direct Buffers: 20MB    │  │  │  │
│  │  │  │                                        │ │ - Native Malloc / GC: 4MB │  │  │  │
│  │  │  └────────────────────────────────────────┘ └───────────────────────────┘  │  │  │
│  │  │  Total Process RSS: 768MB + 256MB = 1024MB (EXACTLY FITS CONTAINER BOUNDARY) │  │
│  │  └────────────────────────────────────────────────────────────────────────────┘  │  │
│  └──────────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                        │
│  ❌ ANTI-PATTERN BREAKDOWN (When -Xmx1024m is used):                                  │
│     Heap (1024MB) + Non-Heap (256MB) = 1280MB Total RSS                                │
│     Kernel Cgroup Limit = 1024MB                                                       │
│     ==> KERNEL OOM KILLER ISSUES SIGKILL (Exit Code 137)!                              │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 5. How to Reproduce the Issues

#### ❌ Anti-Pattern 1: Shell Form ENTRYPOINT Swallowing `SIGTERM`
```dockerfile
# ❌ ANTI-PATTERN: Spawns /bin/sh as PID 1; SIGTERM from Kubernetes is ignored!
ENTRYPOINT java -jar /app/app.jar
```

#### ❌ Anti-Pattern 2: Hardcoded Heap Exceeding Container Boundary
```dockerfile
# ❌ ANTI-PATTERN: Hardcoded -Xmx equal to container limit guarantees OOMKill
ENV JAVA_OPTS="-Xmx1024m -Xms1024m"
ENTRYPOINT ["java", "-Xmx1024m", "-jar", "/app/app.jar"]
```

#### ❌ Anti-Pattern 3: Massive Thread Pool Sizing on Fractional CPU Container
```java
// ❌ ANTI-PATTERN: Spawns 64 threads based on host cores, exhausting 1-core CFS quota in 10ms!
int hostCores = Runtime.getRuntime().availableProcessors(); // Returns 64 on bare metal
ExecutorService executor = Executors.newFixedThreadPool(hostCores * 4); // 256 threads!
```

---

### 6. Evidence Collection & Diagnostic Probing

#### Method 1: Check Kernel OOM Killer Invocations
```bash
dmesg -T | grep -E -i "oom|killed process"
```
**Diagnostic Output:**
```text
[Fri Aug 22 14:00:12 2026] Memory cgroup out of memory: Killed process 1421 (java) total-vm:1842300kB, anon-rss:1044120kB, file-rss:28340kB, shmem-rss:0kB
[Fri Aug 22 14:00:12 2026] oom_reaper: reaped process 1421 (java), now anon-rss:0kB, file-rss:0kB, shmem-rss:0kB
```

#### Method 2: Inspect Kubernetes Pod Termination Reason
```bash
kubectl describe pod finflow-payment-gateway-7b94cd-x4q2z -n production
```
**Diagnostic Output:**
```yaml
Last State:     Terminated
  Reason:       OOMKilled
  Exit Code:    137
  Started:      Fri, 22 Aug 2026 13:45:00 +0000
  Finished:     Fri, 22 Aug 2026 14:00:12 +0000
```

#### Method 3: Measure CFS CPU Quota Throttling in Prometheus
```promql
# CFS Throttling Rate (Percentage of periods throttled)
sum(rate(container_cpu_cfs_throttled_periods_total[5m])) by (pod)
/
sum(rate(container_cpu_cfs_periods_total[5m])) by (pod) * 100
```

#### Method 4: Verify PID 1 Inside Container
```bash
kubectl exec -it <pod-name> -- ps aux
```
**Diagnostic Output:**
```text
# ❌ INCORRECT (Shell Form):
PID   USER     TIME  COMMAND
  1   root     0:00  /bin/sh -c java -jar app.jar
  7   root     1:15  java -jar app.jar

# ✅ CORRECT (Exec Form):
PID   USER     TIME  COMMAND
  1   appuser  1:15  java -XX:+UseContainerSupport ... org.springframework.boot.loader.launch.JarLauncher
```

---

### 7. Step-by-Step Debugging Procedure

```text
Step 1: Check Container Exit Code.
        If exit code is 137, determine if it was OOMKilled by cgroup or forcefully SIGKILL'd after grace period timeout.

Step 2: Inspect Cgroup Resource Limits & Usage.
        Check /sys/fs/cgroup/memory.max (cgroup v2) or /sys/fs/cgroup/memory/memory.limit_in_bytes (cgroup v1).
        Inspect /sys/fs/cgroup/cpu.stat for nr_throttled and throttled_usec.

Step 3: Analyze JVM Native Memory Tracking (NMT).
        Launch JVM with -XX:NativeMemoryTracking=summary and run `jcmd <PID> VM.native_memory summary`
        to check non-heap consumption (Metaspace, Thread stacks, DirectByteBuffers).

Step 4: Audit Dockerfile Entrypoint.
        Ensure ENTRYPOINT uses JSON exec array syntax `["java", ...]` or `exec java ...` so Java receives SIGTERM.

Step 5: Verify Spring Boot Graceful Shutdown.
        Ensure `server.shutdown=graceful` and `spring.lifecycle.timeout-per-shutdown-phase=20s` are enabled.
```

---

### 8. Technical Root Cause Deep-Dive

#### 1. Why `-Xmx1g` in a 1Gi Container Kills the Pod
The Linux cgroup memory controller limits the **Resident Set Size (RSS)** of the entire container process tree.
A Java 21 process consists of multiple memory pools outside the Java Heap:
- **Metaspace (`-XX:MaxMetaspaceSize`):** Default unbounded! Loaded classes and reflection metadata.
- **CodeCache (`-XX:ReservedCodeCacheSize`):** Default 240MB on 64-bit JVMs! JIT compiled native code.
- **Thread Stacks (`-Xss`):** Default 1MB per thread. 200 Tomcat worker threads + 50 system threads = **250MB**.
- **Direct ByteBuffers (`-XX:MaxDirectMemorySize`):** Used by Netty, NIO, and Tomcat for zero-copy I/O.
- **JVM Internal Native Allocations:** GC card tables, mark bitmaps, malloc arenas in `glibc`.

If `-Xmx1024m` is set in a 1024MB container:
$$\text{RSS} = 1024\text{MB (Heap)} + 128\text{MB (Metaspace)} + 64\text{MB (CodeCache)} + 200\text{MB (Stacks)} = 1416\text{MB}$$
The kernel invokes `mem_cgroup_out_of_memory()`, selects the highest memory consumer (`java`), and sends `SIGKILL` (`Exit Code 137`).

#### 2. The Mechanics of CFS Quota CPU Throttling
Linux CFS allocates CPU time in periods (default: `cpu.cfs_period_us = 100,000` $\mu s$ = 100ms).
When a container is given `cpu: "1.0"`, `cpu.cfs_quota_us = 100,000` $\mu s$.
- If an application spawns 10 threads running at 100% CPU on a 64-core node, all 10 threads run simultaneously.
- 10 threads $\times$ 10ms of elapsed time = **100ms of CPU time consumed in 10ms!**
- For the remaining **90ms of the period, the Linux kernel halts all threads in the cgroup**.
- To an API client, an operation that should take 5ms suddenly takes **95ms to 200ms**, causing massive P99 latency jitter.

---

### 9. Production-Grade Fixes

#### ✅ Fix 1: Production Multi-Stage Hardened Dockerfile
```dockerfile
# Stage 1: Build & Layer Extraction
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B
WORKDIR /build/target
RUN java -Djarmode=tools -jar module-16-docker-containerization-cgroup-traps-1.0.0-SNAPSHOT.jar extract --layers --launcher

# Stage 2: Minimal Hardened Runtime
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl tzdata && \
    addgroup -S appgroup && \
    adduser -S appuser -G appgroup -u 10001
WORKDIR /app
RUN mkdir -p /tmp/dumps && chown -R appuser:appgroup /app /tmp/dumps

COPY --from=builder --chown=appuser:appgroup /build/target/module-16-docker-containerization-cgroup-traps-1.0.0-SNAPSHOT/dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /build/target/module-16-docker-containerization-cgroup-traps-1.0.0-SNAPSHOT/spring-boot-loader/ ./
COPY --from=builder --chown=appuser:appgroup /build/target/module-16-docker-containerization-cgroup-traps-1.0.0-SNAPSHOT/snapshot-dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /build/target/module-16-docker-containerization-cgroup-traps-1.0.0-SNAPSHOT/application/ ./

USER appuser:appgroup
EXPOSE 8080

# Production JVM Container Flags:
# - MaxRAMPercentage=75.0 reserves 25% for Metaspace, stacks, CodeCache, and native allocations
# - ExitOnOutOfMemoryError ensures immediate pod restart rather than lingering in zombie state
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport \
                       -XX:MaxRAMPercentage=75.0 \
                       -XX:InitialRAMPercentage=75.0 \
                       -XX:MaxMetaspaceSize=256m \
                       -XX:ReservedCodeCacheSize=128m \
                       -XX:+ExitOnOutOfMemoryError \
                       -XX:+HeapDumpOnOutOfMemoryError \
                       -XX:HeapDumpPath=/tmp/dumps/oom.hprof"

HEALTHCHECK --interval=15s --timeout=3s --retries=3 --start-period=20s \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```

#### ✅ Fix 2: Container Memory Budgeting Service
```java
@Service
public class ContainerMemoryCalculator {

    public MemoryBudgetResult calculateBudget(long containerLimitMb, double maxRamPercentage, 
                                              int threadCount, long metaspaceMb, long directMemoryMb) {
        long codeCacheMb = 128;
        long threadStackMb = (long) Math.ceil((threadCount * 1024L) / 1024.0); // 1MB per thread (-Xss1m)
        long nativeOverheadMb = 64;

        long maxHeapMb = Math.round(containerLimitMb * (maxRamPercentage / 100.0));
        long totalJvmMemoryMb = maxHeapMb + threadStackMb + metaspaceMb + codeCacheMb + directMemoryMb + nativeOverheadMb;
        long headroomMb = containerLimitMb - totalJvmMemoryMb;

        String safetyStatus = (totalJvmMemoryMb > containerLimitMb) ? "CRITICAL_OOM_KILL_GUARANTEED"
                : (headroomMb < (containerLimitMb * 0.10)) ? "HIGH_RISK_NARROW_HEADROOM" : "SAFE_CONTAINER_BUDGET";

        return new MemoryBudgetResult(containerLimitMb, maxRamPercentage, maxHeapMb, threadStackMb,
                metaspaceMb, codeCacheMb, directMemoryMb, nativeOverheadMb, totalJvmMemoryMb, headroomMb, safetyStatus);
    }
}
```

#### ✅ Fix 3: Graceful Shutdown Configuration (`application.yml`)
```yaml
server:
  port: 8080
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 20s
```

---

### 10. Verification

1. **Memory Budget Unit Test:** Run `ContainerMemoryCalculatorTest.java` to verify memory calculations and OOM risk detection.
2. **Cgroup Diagnostics Service Test:** Run `CgroupDiagnosticsServiceTest.java` to verify cgroup limits parsing and direct buffer tracking.
3. **Controller API Test:** Run `ContainerDiagnosticsControllerTest.java` to verify REST endpoints for container inspection.
4. **Integration Test:** Run `Module16IntegrationTest.java` to verify Spring Boot Actuator health and metrics exposure under container configuration.

---

### 11. Prevention & Production Readiness

1. **Always Set `requests.memory == limits.memory` in Kubernetes:**
   Guarantees the `Guaranteed` Quality of Service (QoS) class, minimizing the pod's `oom_score_adj` and preventing it from being killed first during node memory pressure.
2. **Avoid Fractional CPU Limits on Latency-Critical Microservices:**
   Either specify whole integer CPU limits (e.g. `2.0`, `4.0`) or use Kubernetes `CPU Manager` with `static` policy to pin pods to dedicated CPU cores and completely eliminate CFS quota throttling.
3. **Configure Prometheus CFS Alerting Rule:**
```yaml
- alert: ContainerCpuThrottlingHigh
  expr: sum(rate(container_cpu_cfs_throttled_periods_total[5m])) by (container, pod, namespace)
        / sum(rate(container_cpu_cfs_periods_total[5m])) by (container, pod, namespace) * 100 > 25
  for: 3m
  labels:
    severity: warning
  annotations:
    summary: "Container {{ $labels.container }} in pod {{ $labels.pod }} is CPU throttled > 25%"
```

---

### 12. Interview & Production Questions

#### Interview Questions
1. **Q: What is the difference between Linux cgroup v1 and cgroup v2, and how does Java 21 detect them?**
   *Answer:* Cgroup v1 used separate hierarchies for each resource controller (`/sys/fs/cgroup/memory`, `/sys/fs/cgroup/cpu`), whereas cgroup v2 uses a single unified tree (`/sys/fs/cgroup/memory.max`, `/sys/fs/cgroup/cpu.max`). Java 21 HotSpot automatically inspects `/proc/self/cgroup` and `/sys/fs/cgroup/cgroup.controllers` via `-XX:+UseContainerSupport` to determine memory limits and available processors.
2. **Q: Why does `-XX:MaxRAMPercentage=75.0` provide better container safety than hardcoding `-Xmx`?**
   *Answer:* Hardcoding `-Xmx` breaks elasticity—if the container memory limit is scaled from 2GB to 4GB in Kubernetes, the JVM remains stuck at 1GB, or if downscaled to 1GB, the JVM crashes. `MaxRAMPercentage` dynamically computes heap based on container cgroup limits while reserving 25% for Metaspace, stacks, CodeCache, and native allocations.
3. **Q: What happens if a Spring Boot container uses shell-form `ENTRYPOINT java -jar app.jar` during a Kubernetes deployment?**
   *Answer:* The shell `/bin/sh` runs as PID 1. It swallows `SIGTERM` signals sent by Kubernetes. Spring Boot never receives the shutdown signal, failing to finish inflight requests. After 30s (`terminationGracePeriodSeconds`), Kubernetes forcefully kills the container with `SIGKILL` (`Exit Code 137`), dropping active transactions.
4. **Q: How does CFS CPU bandwidth quota cause latency spikes even when container CPU utilization is low?**
   *Answer:* CFS evaluates quota over 100ms periods. If multiple worker threads execute concurrently on a multi-core host, they consume the full period quota in a fraction of that time (e.g. 10ms), causing the kernel to pause (throttle) all threads for the remaining 90ms.
5. **Q: What is the purpose of `-XX:+ExitOnOutOfMemoryError` in containerized applications?**
   *Answer:* When an `OutOfMemoryError` occurs, the JVM may leave threads deadlocked or in inconsistent states while keeping the process alive. Setting `-XX:+ExitOnOutOfMemoryError` forces the JVM to terminate immediately, allowing Kubernetes to restart the container and resume serving traffic cleanly.

#### Production Incident Questions
1. **Incident:** A pod is terminated with Exit Code 137, but the JVM heap graph in Prometheus showed only 60% memory usage. What caused the crash?
   *Diagnosis:* The kernel cgroup OOM Killer terminated the container because non-heap memory (Metaspace, thread stacks, direct memory, or native C library allocations) caused the total process RSS to exceed the container limit.
2. **Incident:** A high-throughput REST service has an average CPU usage of 25%, but P99 latency is 1,500ms. How do you confirm CFS throttling?
   *Diagnosis:* Query Prometheus metric `container_cpu_cfs_throttled_periods_total / container_cpu_cfs_periods_total`. If throttled periods exceed 20%, remove fractional CPU limits or configure integer CPU requests with CPU pinning.
3. **Incident:** After rolling out a new release, customer payments are intermittently dropped during deployments. How do you fix this?
   *Diagnosis:* Change Dockerfile `ENTRYPOINT` to JSON exec array `["java", "-jar", ...]` and configure `server.shutdown=graceful` in `application.yml`.
4. **Incident:** A Java microservice running in Docker accumulates hundreds of `<defunct>` zombie processes. Why?
   *Diagnosis:* The JVM spawned native subprocesses that terminated, but the parent process (PID 1) failed to invoke `wait()`/`waitpid()` to reap child exit statuses. Solution: Use `tini` or `dumb-init` as container init system.
5. **Incident:** A container agent reports 98% memory usage, but `jcmd VM.native_memory` reports only 600MB on a 1GB container. Why the discrepancy?
   *Diagnosis:* The container agent is reading `memory.current` which includes Linux Page Cache (inactive cached files). Inspect `anon` (anonymous memory) vs `file` in `/sys/fs/cgroup/memory.stat`.

#### Trick Questions
1. **Trick:** Does `Runtime.getRuntime().availableProcessors()` return the CPU limit or the host CPU count in Java 21?
   *Answer:* In Java 21 with `-XX:+UseContainerSupport` (default), HotSpot rounds up the CFS quota (e.g. `cpu.cfs_quota_us / cpu.cfs_period_us`) to determine `availableProcessors()`. However, if quota is `< 1.0` (e.g. `0.5` cores), HotSpot returns `1` processor minimum.
2. **Trick:** If a container memory limit is 1024MB and you set `-XX:MaxRAMPercentage=80.0`, will `-Xmx` be exactly 819.2MB?
   *Answer:* HotSpot rounds `-Xmx` down to the nearest memory page/alignment boundary (typically a multiple of 2MB or 4MB).
3. **Trick:** What is the difference between container Exit Code 143 and Exit Code 137?
   *Answer:* Exit Code 143 is `128 + 15 (SIGTERM)`—a graceful termination signal handled cleanly. Exit Code 137 is `128 + 9 (SIGKILL)`—an uncatchable force-kill issued by the Linux OOM Killer or Kubernetes grace period timeout.

---

*(Answers and detailed explanations are provided in the Master Answer Guide)*
