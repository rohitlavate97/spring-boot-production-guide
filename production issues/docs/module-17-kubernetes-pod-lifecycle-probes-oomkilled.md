# Module 17: Kubernetes Pod Lifecycle, Probes & OOMKilled

## Issue 17.1: The Liveness Probe Death Spiral, Premature Traffic Routing, and Endpoint Termination Race Conditions

---

### 1. Scenario

During peak morning trading on the **FinFlow Core Banking & Clearing Engine**:
1. A brief 4-second lock contention spike occurred on the downstream PostgreSQL database cluster.
2. Because the Kubernetes Deployment configured its **Liveness Probe** to poll the general `/actuator/health` endpoint (which executed database `SELECT 1` queries and Redis `PING` commands), the probe returned `503 Service Unavailable (DOWN)`.
3. The Kubernetes Kubelet assumed the Spring Boot JVM had deadlocked and **immediately sent `SIGKILL` to restart the pod**.
4. When 50 replica pods simultaneously restarted, their startup sequence hit the already strained PostgreSQL database with **50 concurrent connection bursts and schema checks**. This forced all 50 pods into `503 DOWN` status again, triggering a **Cluster-Wide Liveness Probe Restart Death Spiral (Thundering Herd)** that completely took down the clearing engine for 45 minutes!
5. Concurrently, during a rolling deployment intended to patch the issue, customer checkouts experienced a **surge of `502 Bad Gateway` and `Connection Refused` errors**. The pod terminated immediately upon receiving `SIGTERM`, while Kube-Proxy and Ingress controllers took 3 to 5 seconds to remove the terminating pod from iptables/IPVS routing tables, sending 3,400 active payments to a dead IP.
6. Under node-level memory pressure, Kubernetes worker nodes evicted and killed the critical clearing pods first because they were configured with `Burstable` QoS (`requests.memory: 256Mi`, `limits.memory: 2048Mi`), assigning them an aggressive `oom_score_adj` of `+875`.

---

### 2. Symptoms

```text
1. Cascading Pod Restart Storm (Liveness Death Spiral):
   All replicas cycle endlessly in CrashLoopBackOff or restart every 30-60 seconds during downstream DB latency.
   Kubernetes Events: "Liveness probe failed: HTTP probe failed with statuscode: 503".

2. Ingress 502 Bad Gateway / Connection Refused Spikes:
   During rolling deployments or autoscaling scale-down events, 0.5% to 3% of all HTTP requests fail with 502/504.
   Ingress logs: "upstream connect error or disconnect/reset before headers. reset reason: connection failure".

3. Premature Traffic Routing to Cold Pods:
   New pods receive customer traffic while Flyway migrations or JIT warmups are still executing, causing 10-second response latency.

4. Unfair Kernel OOM Eviction:
   Critical payment processing pods killed by Linux OOM Killer before non-critical batch jobs due to Burstable QoS class.
```

---

### 3. Possible Root Causes

1. **Coupled Liveness Probes (The Fatal Anti-Pattern):** Pointing `livenessProbe` to `/actuator/health` instead of `/actuator/health/liveness`. Liveness MUST ONLY check if the JVM process itself is alive and responsive. If a database is down, restarting the application pod will NOT fix the database—it only magnifies the overload.
2. **Missing Startup Probe for Slow Bootstrap:** Applications with large Spring contexts, Hibernate entity mappings, and database migrations take 20–40 seconds to boot. Without a `startupProbe`, aggressive liveness probes kill the pod before it ever reaches ready state.
3. **The Endpoint Propagation Race Condition:** When a pod terminates, Kubelet sends `SIGTERM` concurrently with the Endpoints controller updating the Service. Because iptables / IPVS rule distribution across worker nodes is asynchronous (takes 2–5 seconds), Ingress routes traffic to a terminating pod that has already closed its listening socket.
4. **Burstable QoS Penalty (`oom_score_adj`):** When `requests.memory < limits.memory`, Kubernetes sets a high `oom_score_adj` (up to 999), making the pod the first candidate for termination when the node runs low on RAM.

---

### 4. Architecture Context: Zero-Downtime Pod Termination & Probe Isolation

```text
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                    ZERO-DOWNTIME POD TERMINATION & ENDPOINT PROPAGATION                         │
│                                                                                                 │
│  [Deployment Rollout / Pod Termination Triggered]                                               │
│                           │                                                                     │
│           ┌───────────────┴──────────────────────────────┐                                      │
│           ▼                                              ▼                                      │
│  ┌─────────────────────────────────┐    ┌────────────────────────────────────────────────────┐  │
│  │ K8s Control Plane               │    │ Kubelet on Worker Node                             │  │
│  │ 1. Pod marked "Terminating"     │    │ 1. Executes preStop Hook: ["sleep", "10"]          │  │
│  │ 2. Removed from Endpoints list  │    │    (Keeps Spring Boot listening & serving requests)│  │
│  │ 3. Propagates to kube-proxy &   │    └────────────────────────┬───────────────────────────┘  │
│  │    Ingress controllers (2-4s)   │                             │ (10s elapsed; routing is)    │
│  └────────────────┬────────────────┘                             │ (now 100% updated in K8s)    │
│                   │                                              ▼                              │
│                   │                     ┌────────────────────────────────────────────────────┐  │
│                   │                     │ 2. Kubelet sends SIGTERM to PID 1                  │  │
│                   │                     │    Spring Boot Graceful Shutdown Initiated:        │  │
│                   │                     │    - ReadinessState -> REFUSING_TRAFFIC (503)      │  │
│                   │                     │    - Stops accepting new connections               │  │
│                   │                     │    - Drains in-flight requests (up to 30s)         │  │
│                   │                     └────────────────────────┬───────────────────────────┘  │
│                   ▼                                              ▼                              │
│  ┌─────────────────────────────────┐    ┌────────────────────────────────────────────────────┐  │
│  │ Ingress no longer sends traffic │    │ 3. JVM terminates cleanly (Exit Code 0 / 143)      │  │
│  │ to terminating Pod IP           │    │    (Zero dropped transactions!)                    │  │
│  └─────────────────────────────────┘    └────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 5. How to Reproduce the Issues

#### ❌ Anti-Pattern 1: Liveness Probe Pointed to Full Actuator Health
```yaml
# ❌ FATAL ANTI-PATTERN: If PostgreSQL or Redis stutters, ALL PODS ARE KILLED!
livenessProbe:
  httpGet:
    path: /actuator/health
    port: 8080
  periodSeconds: 5
```

#### ❌ Anti-Pattern 2: Terminating Without `preStop` Hook
```yaml
# ❌ ANTI-PATTERN: Ingress will route requests to this pod for 3s after it receives SIGTERM!
spec:
  terminationGracePeriodSeconds: 30
  containers:
    - name: app
      # Missing lifecycle.preStop
```

#### ❌ Anti-Pattern 3: Burstable QoS Memory Trap
```yaml
# ❌ ANTI-PATTERN: oom_score_adj = 1000 - (1000 * 256 / 32768) = ~992 (Instant target for eviction)
resources:
  requests:
    memory: "256Mi"
  limits:
    memory: "2048Mi"
```

---

### 6. Evidence Collection & Diagnostic Probing

#### Method 1: Check Pod Termination Reason & Probe Failure History
```bash
kubectl describe pod finflow-clearing-service-6789b-x89w2 -n production
```
**Diagnostic Output:**
```text
Events:
  Type     Reason     Age                From               Message
  ----     ------     ----               ----               -------
  Warning  Unhealthy  3m (x3 over 3m)    kubelet            Liveness probe failed: HTTP probe failed with statuscode: 503
  Normal   Killing    3m                 kubelet            Container clearing-service failed liveness probe, will be restarted
```

#### Method 2: Inspect Linux OOM Killer Score Adjustment
```bash
kubectl exec -it <pod-name> -- cat /proc/self/oom_score_adj
```
- `Guaranteed` QoS (`requests == limits`): `-997` (Protected)
- `Burstable` QoS (`requests < limits`): `+100` to `+999` (Vulnerable to eviction)
- `BestEffort` QoS (no requests/limits): `+1000` (First to be killed)

#### Method 3: Query Prometheus for Probe Failure Rates
```promql
# Rate of Liveness Probe Failures by Pod
sum(rate(prober_probe_total{probe_type="Liveness", result="failed"}[5m])) by (pod)
```

---

### 7. Step-by-Step Debugging Procedure

```text
Step 1: Determine If Pod Restarts Were Caused by Liveness or OOM.
        Inspect `kubectl describe pod`. Look for "failed liveness probe" vs "OOMKilled (Exit Code 137)".

Step 2: Inspect Probe Configuration.
        Ensure liveness is mapped to `/actuator/health/liveness` and readiness to `/actuator/health/readiness`.

Step 3: Check Startup Duration & Add startupProbe.
        If pod is killed during cold start, configure `startupProbe` with `failureThreshold: 30` and `periodSeconds: 2`.

Step 4: Verify preStop Hook & Graceful Shutdown Settings.
        Ensure `lifecycle.preStop.exec.command: ["/bin/sh", "-c", "sleep 10"]` is present and
        `server.shutdown=graceful` with `timeout-per-shutdown-phase=30s` is configured.

Step 5: Enforce Guaranteed QoS Class.
        Set `resources.requests.memory == resources.limits.memory` to prevent kernel OOM eviction.
```

---

### 8. Technical Root Cause Deep-Dive

#### 1. Why Kubelet Restarts on Liveness Failure (and Why It Destroys Downstream Systems)
- The Kubelet is an autonomous node agent. When a container's `livenessProbe` fails `failureThreshold` consecutive times, Kubelet invokes the container runtime (`containerd`/`CRI-O`) to forcefully stop and recreate the container.
- If `/actuator/health` checks PostgreSQL, and PostgreSQL slows down, all 50 application pods report `DOWN`.
- Kubelet kills all 50 containers at once.
- When 50 Spring Boot applications start simultaneously, they establish new TCP connections, initialize connection pools, execute Flyway checks, and flood the database CPU with authentication handshakes.
- This creates an unrecoverable **positive feedback loop** that guarantees 100% downtime until human intervention stops the deployment.

#### 2. The Endpoint Asynchronous Propagation Delay
- When a pod transitions to `Terminating`, the Kubernetes master node updates the `Endpoints` object.
- The `kube-proxy` daemonset running on every worker node watches the Endpoints API and updates local `iptables` / `IPVS` rules.
- In large clusters (100+ nodes), this propagation takes between **1,000ms and 5,000ms**.
- If a pod immediately stops its HTTP listener upon receiving `SIGTERM`, any request dispatched by Ingress during those 5 seconds encounters a closed port, returning `502 Bad Gateway` or `ECONNREFUSED`.
- The `preStop: sleep 10` hook delays `SIGTERM` delivery to the JVM for 10 seconds, allowing all node routing tables to flush completely while the JVM continues servicing in-flight requests.

---

### 9. Production-Grade Fixes

#### ✅ Fix 1: Hardened Kubernetes Deployment (`deployment-production.yaml`)
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: finflow-clearing-service
  namespace: production
spec:
  replicas: 4
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 25%
      maxUnavailable: 0
  template:
    spec:
      terminationGracePeriodSeconds: 60
      containers:
        - name: clearing-service
          image: finflow/clearing-service:1.0.0
          resources:
            requests:
              memory: "1536Mi"
              cpu: "1000m"
            limits:
              memory: "1536Mi"
              cpu: "1000m"
          lifecycle:
            preStop:
              exec:
                command: ["/bin/sh", "-c", "sleep 10"]
          startupProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            periodSeconds: 2
            failureThreshold: 30
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            periodSeconds: 10
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            periodSeconds: 5
            failureThreshold: 2
```

#### ✅ Fix 2: Spring Boot Probes & Graceful Shutdown (`application.yml`)
```yaml
server:
  port: 8080
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s

management:
  endpoint:
    health:
      probes:
        enabled: true
      group:
        liveness:
          include: livenessState
        readiness:
          include: readinessState,downstreamDependencyHealth
  health:
    livenessstate:
      enabled: true
    readinessstate:
      enabled: true
```

#### ✅ Fix 3: Dynamic Traffic Draining via `ApplicationAvailability`
```java
@Service
public class PodLifecycleService {

    private final ApplicationEventPublisher eventPublisher;

    public PodLifecycleService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void drainTraffic() {
        // Publishes REFUSING_TRAFFIC so /actuator/health/readiness immediately returns 503
        AvailabilityChangeEvent.publish(eventPublisher, this, ReadinessState.REFUSING_TRAFFIC);
    }
}
```

---

### 10. Verification

1. **Probe Isolation Unit Test:** Run `KubernetesProbesTest.java` to verify that downstream database failure turns `/actuator/health/readiness` DOWN (503) while `/actuator/health/liveness` stays UP (200).
2. **Lifecycle State Test:** Run `PodLifecycleServiceTest.java` to verify programmatic `REFUSING_TRAFFIC` transitions.
3. **Integration Test:** Run `Module17IntegrationTest.java` to verify Actuator health and Prometheus metrics exposure.

---

### 11. Prevention & Production Readiness

1. **Rule: Liveness Probes Must Be Self-Contained:**
   Never include external network dependencies in a liveness probe. If it requires a network call outside the pod, it belongs in `readinessProbe`, NEVER `livenessProbe`.
2. **Rule: Set `terminationGracePeriodSeconds >= preStop + timeout-per-shutdown-phase + 10s`:**
   Ensure the pod grace period accommodates the `preStop` sleep (10s) and Spring Boot graceful shutdown timeout (30s) with safety margin.
3. **Prometheus Alerting Rule for Liveness Failures:**
```yaml
- alert: KubernetesPodLivenessProbeFailing
  expr: rate(prober_probe_total{probe_type="Liveness", result="failed"}[2m]) > 0
  for: 1m
  labels:
    severity: critical
  annotations:
    summary: "Pod {{ $labels.pod }} is failing liveness probe and at risk of restart storm"
```

---

### 12. Interview & Production Questions

#### Interview Questions
1. **Q: What is the exact semantic difference between Kubernetes `livenessProbe`, `readinessProbe`, and `startupProbe`?**
   *Answer:* `startupProbe` determines if the container has initialized (disabling liveness/readiness checks until it succeeds). `livenessProbe` checks if the JVM is alive; failure causes Kubelet to kill and restart the container. `readinessProbe` checks if the application can accept user traffic; failure removes the pod from Service Endpoints without restarting the container.
2. **Q: Why is pointing a Kubernetes `livenessProbe` at `/actuator/health` considered a dangerous production anti-pattern?**
   *Answer:* `/actuator/health` includes health indicators for external dependencies like databases, Kafka, and Redis. If the database experiences a latency spike, the liveness probe fails across all pods, triggering a simultaneous cluster-wide restart loop (Death Spiral / Thundering Herd) that overwhelms the database further.
3. **Q: Why is a `preStop` hook with `sleep 10` required for zero-downtime rolling updates even when `server.shutdown=graceful` is enabled?**
   *Answer:* Endpoint removal and iptables rule propagation across the cluster takes 2 to 5 seconds. Without `preStop`, the JVM receives `SIGTERM` and stops accepting new connections immediately, while Ingress is still routing incoming traffic to that pod IP, resulting in `502 Bad Gateway` / `Connection Refused` errors.
4. **Q: How does Kubernetes determine the Quality of Service (QoS) class for a pod, and how does it affect the Linux OOM Killer?**
   *Answer:* If `requests == limits` for all resources, the QoS is `Guaranteed` (`oom_score_adj = -997`). If `requests < limits`, it is `Burstable` (positive `oom_score_adj`). If no requests/limits are set, it is `BestEffort` (`oom_score_adj = 1000`). Under memory pressure, the Linux kernel terminates pods with the highest `oom_score_adj` first.
5. **Q: How does Spring Boot 3 expose Kubernetes availability states?**
   *Answer:* Spring Boot provides `ApplicationAvailability` with `LivenessState` (`CORRECT`, `BROKEN`) and `ReadinessState` (`ACCEPTING_TRAFFIC`, `REFUSING_TRAFFIC`). They are exposed via `/actuator/health/liveness` and `/actuator/health/readiness` and can be manipulated by publishing `AvailabilityChangeEvent`.

#### Production Incident Questions
1. **Incident:** During a database failover, 100% of microservice pods entered `CrashLoopBackOff`. Why?
   *Diagnosis:* Liveness probe was configured against full `/actuator/health`. When the database went down, Kubelet killed all pods. Fix: Switch liveness probe path to `/actuator/health/liveness`.
2. **Incident:** During a CI/CD deployment, users report intermittent `502 Bad Gateway` errors for 15 seconds. How do you eliminate this?
   *Diagnosis:* Add a `lifecycle.preStop.exec.command: ["/bin/sh", "-c", "sleep 10"]` to the pod spec and enable `server.shutdown=graceful`.
3. **Incident:** A Spring Boot pod with large Flyway migrations takes 45 seconds to boot and is repeatedly killed after 15 seconds. How do you fix it?
   *Diagnosis:* Add a `startupProbe` with `failureThreshold: 30` and `periodSeconds: 2`, allowing up to 60s for initialization before liveness checks begin.
4. **Incident:** A Kubernetes node runs out of memory, and kills your core payment service instead of a background log processor. Why?
   *Diagnosis:* The payment service was configured with `Burstable` QoS while the log processor had `Guaranteed` QoS. Fix: Set `requests.memory == limits.memory` on critical services.
5. **Incident:** You need to perform maintenance on a specific pod without killing active long-running payments. How do you drain traffic?
   *Diagnosis:* Trigger `AvailabilityChangeEvent.publish(publisher, this, ReadinessState.REFUSING_TRAFFIC)`. Wait for in-flight requests to finish, then terminate the pod.

#### Trick Questions
1. **Trick:** If a pod's `readinessProbe` fails, does Kubernetes restart the container?
   *Answer:* No. Readiness probe failure ONLY removes the pod IP from the Service's active Endpoints list. The container continues running.
2. **Trick:** Does a container's `preStop` hook run before or after the Kubelet sends `SIGTERM`?
   *Answer:* Before. Kubelet blocks until the `preStop` hook completes (or until `terminationGracePeriodSeconds` expires) before sending `SIGTERM`.
3. **Trick:** Can a `startupProbe` run concurrently with a `livenessProbe`?
   *Answer:* No. Kubernetes disables both `livenessProbe` and `readinessProbe` until the `startupProbe` has successfully completed for the first time.

---

*(Answers and detailed explanations are provided in the Master Answer Guide)*
