# Module 14: JVM Memory Leaks, Metaspace, GC Thrashing & OOM

## Issue 14.1: ThreadLocal Memory Leaks, Unbounded Static Caches, Metaspace Exhaustion, and GC Thrashing

---

### 1. Scenario

During month-end settlement clearing on the **FinFlow Global Settlement Engine**:
1. After running stably for 12 days, the application begins experiencing severe **Stop-The-World (STW) Garbage Collection pauses** lasting between **6 and 14 seconds**.
2. Upstream load balancers declare backend pods unhealthy (`502 Bad Gateway` / `504 Gateway Timeout`).
3. The JVM crashes with `java.lang.OutOfMemoryError: Java heap space`. 
4. A post-mortem heap dump (`heap.hprof`) analysis reveals that **75% of the 8GB Old Generation heap** is consumed by:
   - Uncleaned `ThreadLocalMap` entries attached to long-lived Tomcat worker threads (`http-nio-8080-exec-*`).
   - An unbounded `ConcurrentHashMap` caching merchant metadata without eviction or TTL.
5. In an adjacent dynamic-pricing microservice, runtime CGLIB proxy generation exhausts native memory, crashing the service with `java.lang.OutOfMemoryError: Metaspace`.

---

### 2. Symptoms

```text
1. OutOfMemoryError Exceptions:
   java.lang.OutOfMemoryError: Java heap space
   java.lang.OutOfMemoryError: Metaspace
   java.lang.OutOfMemoryError: GC overhead limit exceeded (JVM spending > 98% CPU time in GC recovering < 2% heap)
2. Kubernetes Pod Termination:
   Pod killed by container runtime: exit code 137 (OOMKilled).
3. Old Gen Sawtooth Degradation:
   Old Generation memory consumption steadily climbs in a sawtooth pattern where Full GCs reclaim diminishing amounts of memory.
4. Stop-The-World (STW) Latency Spikes:
   Application 99th percentile response times spike from 25ms to 12,000ms during Full GC events.
5. Cross-Tenant Context Pollution:
   Tenant B's HTTP request reading Tenant A's user data because a pooled thread was reused without clearing its ThreadLocal state.
```

---

### 3. Possible Root Causes

1. **`ThreadLocal` Retention in Worker Thread Pools:** Tomcat worker threads are reused across thousands of HTTP requests. Failing to call `ThreadLocal.remove()` keeps context objects strongly reachable via the thread's `ThreadLocalMap`.
2. **Unbounded Static In-Memory Collections:** Accumulating entries in static `Map`, `List`, or `Set` collections without capacity limits, LRU eviction, or time-to-live (TTL) policies.
3. **Metaspace Dynamic Class Generation Leaks:** Uncontrolled runtime bytecode generation (e.g. CGLIB, ByteBuddy, Groovy, dynamic Spring proxies) without reusing classloaders or unloading unreferenced classes.
4. **Direct Buffer Leaks (Off-Heap):** Unreleased Netty or Java NIO `ByteBuffer.allocateDirect()` allocations causing `OutOfMemoryError: Direct buffer memory`.

---

### 4. Architecture Context: JVM Memory Pools & Garbage Collection

```text
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                 JVM RUNTIME MEMORY MODEL                               │
│                                                                                        │
│  ┌──────────────────────────────────────────────────────────────────────────────────┐  │
│  │                                  HEAP MEMORY                                     │  │
│  │                                                                                  │  │
│  │  ┌─────────────────────────────────────────┐  ┌───────────────────────────────┐  │  │
│  │  │            YOUNG GENERATION             │  │        OLD GENERATION         │  │  │
│  │  │  ┌──────────────┐  ┌──────┐  ┌──────┐   │  │  ┌─────────────────────────┐  │  │  │
│  │  │  │  Eden Space  │  │  S0  │  │  S1  │   │  │  │ Tenured Long-Lived Objs │  │  │  │
│  │  │  │ (New Objs)   │  │ (Sur)│  │ (Sur)│   │  │  │ (Leaked ThreadLocals,   │  │  │  │
│  │  │  │              │  │      │  │      │   │  │  │  Static Maps, Caches)   │  │  │  │
│  │  │  └──────────────┘  └──────┘  └──────┘   │  │  └─────────────────────────┘  │  │  │
│  │  └─────────────────────────────────────────┘  └───────────────────────────────┘  │  │
│  └──────────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                        │
│  ┌──────────────────────────────────────────────────────────────────────────────────┐  │
│  │                                NON-HEAP MEMORY                                   │  │
│  │                                                                                  │  │
│  │  ┌─────────────────────────┐  ┌───────────────────────┐  ┌─────────────────────┐ │  │
│  │  │        Metaspace        │  │      CodeCache        │  │    Off-Heap/Direct  │ │  │
│  │  │ (Class Metadata, CGLIB) │  │ (JIT Compiled Code)   │  │ (NIO DirectBuffers) │ │  │
│  │  └─────────────────────────┘  └───────────────────────┘  └─────────────────────┘ │  │
│  └──────────────────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 5. How to Reproduce the Issues

#### Step 1: Omit `ThreadLocal.remove()` in a Pooled Thread
```java
// ❌ ANTI-PATTERN: ThreadLocal value persists on worker thread forever!
public void processRequest(String tenantId) {
    ThreadLocalContextHolder.setUser(tenantId);
    doBusinessLogic();
    // Missing ThreadLocalContextHolder.clear()!
    // Next request dispatched to this thread inherits tenantId!
}
```

#### Step 2: Accumulate Data in an Unbounded Static Cache
```java
// ❌ ANTI-PATTERN: Unbounded Map with no eviction
public static final Map<String, byte[]> CACHE = new ConcurrentHashMap<>();

public void cacheData(String id, byte[] payload) {
    CACHE.put(id, payload); // Memory continuously grows until OOM!
}
```

---

### 6. Evidence Collection & Diagnostic Probing

#### Method 1: JVM Diagnostic Flags for Automatic Heap Dumps
Add these flags to production JVM arguments:
```bash
-XX:+HeapDumpOnOutOfMemoryError \
-XX:HeapDumpPath=/var/dumps/app-oom.hprof \
-Xlog:gc*,gc+phases=debug:file=/var/log/gc.log:time,uptime,pid:filecount=5,filesize=100M
```

#### Method 2: Trigger On-Demand Heap Dump via `jcmd`
```bash
jcmd <PID> GC.heap_dump /tmp/manual_heap.hprof
```

#### Method 3: Analyze Heap Dump in Eclipse Memory Analyzer (MAT)
1. Open `app-oom.hprof` in Eclipse MAT.
2. Run the **Leak Suspects Report**.
3. Inspect the **Dominator Tree**:
   - **Shallow Heap:** Memory consumed by the object itself (e.g. 32 bytes for a `Thread` object).
   - **Retained Heap:** Total memory that would be freed if this object were garbage collected (e.g. 4.2 GB held by `ThreadLocalMap` attached to `Thread`).

---

### 7. Step-by-Step Debugging Procedure

```text
Step 1: Inspect GC Logs for STW Pauses and Full GC Frequency.
        Look for "Pause Full (Allocation Failure)" or "GC overhead limit exceeded".

Step 2: Identify Memory Leak Candidates in Eclipse MAT.
        Open Dominator Tree -> Sort by Retained Heap -> Expand GC Roots path.
        Look for java.lang.ThreadLocal$ThreadLocalMap or org.springframework.context.* beans holding large HashMaps.

Step 3: Enforce ThreadLocal try-with-resources with AutoCloseable.
        Wrap all ThreadLocal assignments in a try-with-resources block to guarantee .remove() executes on exit.

Step 4: Replace Static Maps with Bounded Caches.
        Refactor unbounded collections to Caffeine Cache or LinkedHashMap with LRU eviction and maximumSize(N).

Step 5: Cap Metaspace Memory and Enable Class Unloading.
        Configure -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m to prevent unbounded native memory expansion.
```

---

### 8. Technical Root Cause Deep-Dive

#### 1. Why `ThreadLocal` Leaks in Thread Pools
- Every `Thread` object maintains a reference to its own `ThreadLocal.ThreadLocalMap`.
- In `ThreadLocalMap`, keys are `WeakReference<ThreadLocal<?>>`, but **values are strong references** (`Entry.value`).
- When a thread is returned to a pool (e.g. Tomcat `http-nio-exec`), the `Thread` object remains alive indefinitely as an active GC Root.
- If `ThreadLocal.remove()` is not called:
  1. The value object (e.g. user session, tenant configuration, byte buffer) remains strongly reachable and cannot be garbage collected.
  2. Any subsequent HTTP request processed by that thread can inadvertently read stale data from previous users (Security Context Leak).

#### 2. Shallow Heap vs. Retained Heap
$$\text{Retained Heap}(X) = \text{Shallow Heap}(X) + \sum_{Y \in \text{Exclusive Descendants}(X)} \text{Shallow Heap}(Y)$$
An object with a shallow heap of only 48 bytes can retain 2GB of objects in memory if it is the sole surviving reference to a large collection.

---

### 9. Production-Grade Fixes

#### ✅ Fix 1: Safe `AutoCloseable` Context Manager for `ThreadLocal`
```java
public class ThreadLocalContextHolder {

    private static final ThreadLocal<String> USER_CONTEXT = new ThreadLocal<>();

    public static void setUser(String username) { USER_CONTEXT.set(username); }
    public static String getUser() { return USER_CONTEXT.get(); }
    public static void clear() { USER_CONTEXT.remove(); }

    public static class ContextScope implements AutoCloseable {
        public ContextScope(String username) { setUser(username); }
        @Override
        public void close() { clear(); } // Guaranteed to execute on scope exit!
    }

    public static ContextScope withUser(String username) {
        return new ContextScope(username);
    }
}

// Usage in Filter or Service:
public void handleRequest(String user) {
    try (var scope = ThreadLocalContextHolder.withUser(user)) {
        // Business logic runs with valid context
    } // ThreadLocal.remove() automatically called here!
}
```

#### ✅ Fix 2: Bounded LRU Cache with Automatic Eviction
```java
@Service
public class BoundedCacheService {

    private static final int MAX_ENTRIES = 10_000;

    // Thread-safe Bounded LRU Cache: Evicts oldest entries once capacity is reached
    private final Map<String, String> cache = Collections.synchronizedMap(
        new LinkedHashMap<String, String>(MAX_ENTRIES, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > MAX_ENTRIES;
            }
        }
    );

    public void put(String key, String value) { cache.put(key, value); }
    public String get(String key) { return cache.get(key); }
}
```

#### ✅ Fix 3: Production JVM GC & Metaspace Flags
```bash
java -server \
  -Xms4g -Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:InitiatingHeapOccupancyPercent=45 \
  -XX:G1ReservePercent=15 \
  -XX:MetaspaceSize=256m \
  -XX:MaxMetaspaceSize=512m \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/var/log/dumps/app.hprof \
  -Xlog:gc*,gc+phases=debug:file=/var/log/gc.log:time,uptime,pid:filecount=5,filesize=100M \
  -jar app.jar
```

---

### 10. Verification

1. **ThreadLocal Isolation & Cleanup Test:** Run `ThreadLocalLeakPreventionTest.java` to verify that `try-with-resources` cleans up context and prevents data bleeding across reused thread pool workers.
2. **Bounded Cache Eviction Test:** Run `BoundedCacheEvictionTest.java` to verify that cache entries beyond the configured threshold are evicted.
3. **Memory Pool Telemetry Test:** Run `MemoryPoolMetricsTest.java` to verify `MemoryMXBean` telemetry reporting.
4. **Integration Test:** Run `Module14IntegrationTest.java` to verify REST endpoints and diagnostics.

---

### 11. Prevention & Production Readiness

1. **Never Allocate Unbounded In-Memory Collections:**
   Use Caffeine Cache with `maximumSize()` and `expireAfterWrite()` rather than raw `HashMap` or `ConcurrentHashMap`.
2. **Enforce `try-with-resources` for `ThreadLocal` in CI:**
   Use ArchUnit rules or SonarQube rules to fail builds if `ThreadLocal.set()` is called outside of an `AutoCloseable` structure or `finally` block.
3. **Configure Memory Limits in Container Manifests:**
   Ensure Kubernetes `resources.limits.memory` is set to $1.25 \times \text{JVM Max Heap}$ to leave headroom for Metaspace, CodeCache, and native thread stacks.

---

### 12. Interview & Production Questions

#### Interview Questions
1. **Q: What is the difference between Shallow Heap and Retained Heap in heap dump analysis?**
2. **Q: Why does `ThreadLocal` leak memory in pooled thread environments like Tomcat or Netty?**
3. **Q: What causes `java.lang.OutOfMemoryError: Metaspace`, and how does it differ from Heap OOM?**
4. **Q: How does the G1 Garbage Collector divide the JVM heap, and what is a "Humongous Allocation"?**
5. **Q: What JVM flags should always be enabled in production to ensure forensic data is captured during an OOM event?**

#### Production Incident Questions
1. **Incident:** An application crashed with `OutOfMemoryError: Java heap space`. The container restarted immediately, destroying the pod filesystem. How do you ensure the heap dump is preserved across container restarts?
2. **Incident:** You observe Full GC pauses taking 15 seconds every 10 minutes. Top consumers in MAT are `byte[]` arrays. How do you trace the GC Root back to the owning service?
3. **Incident:** A Spring Boot service using dynamic `@Scope("prototype")` beans exhibits growing Metaspace usage until crash. What caused the ClassLoader to retain these classes?
4. **Incident:** Kubernetes terminated a pod with exit code 137, but the JVM never generated a heap dump despite having `-XX:+HeapDumpOnOutOfMemoryError`. Why? *(Hint: Linux cgroup OOM Killer vs JVM Heap limit!)*
5. **Incident:** When should you choose ZGC (`-XX:+UseZGC`) over G1GC (`-XX:+UseG1GC`) in high-throughput financial applications?

#### Trick Questions
1. **Trick:** If an object has a `WeakReference` pointing to it, when is it collected by the garbage collector?
2. **Trick:** Does `System.gc()` guarantee that all unused memory will be freed immediately?
3. **Trick:** If a `ThreadLocal` variable is declared `static`, does `ThreadLocal.remove()` clear the value for ALL threads or ONLY the calling thread?

---

*(Answers and detailed explanations are provided in the Master Answer Guide)*
