---
chapter: 310
topic: Kubernetes for Spring Boot — Probes, Resource Limits, ConfigMaps/Secrets, Rolling Updates, PDB, HPA, Graceful Shutdown
prerequisite_chapters: [10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200, 210, 220, 230, 240, 250, 260, 270, 280, 290, 300]
reference_system_node: Payment Service Deployment Fleet ↔ Kubernetes Cluster Orchestration Engine (Kubelet, Kube-Proxy, Ingress, Actuator Probes, LivenessState, ReadinessState, PreStop Hook, PDB, HPA)
---

# Chapter 310: Kubernetes for Spring Boot — Probes, Resource Limits, ConfigMaps/Secrets, Rolling Updates, PDB, HPA, Graceful Shutdown

## 1. Concept

Deploying Spring Boot applications into **Kubernetes** requires deep architectural alignment between the Spring Boot application lifecycle and Kubernetes cluster primitives (Kubelet, Kube-Proxy, and the Ingress Controller).

Running default configurations without cloud-native tuning leads to major operational failures:
1. **The Cascading Liveness Trap**: Checking external dependencies (database, Redis, Kafka) inside `livenessProbe`, causing cluster-wide pod restart loops when a downstream dependency experiences momentary latency.
2. **The Rolling Update HTTP 502 Race Condition**: Kubernetes `kube-proxy` asynchronously updates iptables/IPVS routing tables. If a pod terminates immediately upon receiving `SIGTERM`, clients and load balancers send requests to a dead pod, causing intermittent HTTP 502/504 errors during every CI/CD deployment.
3. **The Premature Startup Kill**: Kubelet killing slow-starting JVM pods before Spring Boot finishes initializing beans.
4. **Disruption Outages**: Node drains and cluster auto-scaling evicting all application pods simultaneously without a **PodDisruptionBudget (PDB)**.

```
+-------------------------------------------------------------------------------------------------+
|                                 The Golden Rules of Production Kubernetes                       |
|                                                                                                 |
|  1. Never Check External Dependencies in Liveness Probes: Liveness must ONLY check internal     |
|     JVM state (deadlocks). Use Readiness probes for traffic routing.                            |
|  2. Always Configure a 3-Tier Probe Strategy (Startup, Liveness, Readiness):                    |
|     • startupProbe: Protects slow cold starts (up to 60s) from premature liveness kills.       |
|     • livenessProbe: Restarts container on unrecoverable deadlocks.                             |
|     • readinessProbe: Pulls pod from load balancer when overloaded or draining.                 |
|  3. Always Configure a preStop Hook with 'sleep 5': Gives kube-proxy and Ingress 5 seconds to   |
|     remove the pod IP from routing tables BEFORE Spring Boot starts shutting down Tomcat.      |
|  4. Enforce maxUnavailable: 0 in RollingUpdate: Guarantees zero dropped capacity on deploy.     |
|  5. Always Declare a PodDisruptionBudget (PDB): Guarantees minimum pod quorum during node       |
|     drains, cluster upgrades, and spot instance evictions.                                      |
+-------------------------------------------------------------------------------------------------+
```

---

## 2. Internal Working

### The 3-Tier Probe Architecture

```
Container Starts
       │
       ▼
[1. startupProbe: /actuator/health/liveness]
       │
       ├── Failing? ──► Kubelet waits (failureThreshold: 30 × periodSeconds: 2 = 60s grace period)
       └── Succeeded!
              │
              ├──► [2. livenessProbe: /actuator/health/liveness] ──► Fails? ──► KILL & RESTART POD!
              │
              └──► [3. readinessProbe: /actuator/health/readiness] ──► Fails? ──► REMOVE FROM ENDPOINTS!
                                                                                (NO RESTART!)
```

---

### The Fatal Liveness Probe Trap

| Probe Type | Actuator Endpoint | Action on Failure | Correct Check | Lethal Mistake |
|---|---|---|---|---|
| **`livenessProbe`** | `/actuator/health/liveness` | **Restarts Container** (`SIGKILL`) | Internal JVM thread state, deadlock checks | Checking PostgreSQL, Redis, or Kafka |
| **`readinessProbe`** | `/actuator/health/readiness` | **Removes Pod from Endpoints** | Queue capacity, circuit breakers, warmup | Hardcoded static 200 OK |
| **`startupProbe`** | `/actuator/health/liveness` | **Waits until complete** | Initial Spring ApplicationContext boot | Omitting startup probe |

```
The Cascading Cluster Meltdown
Downstream DB Flaps (2s) ──► All 50 Pods Fail Liveness ──► Kubelet Kills All 50 Pods Simultaneously
                                                                    │
                                                                    ▼
                                                    Complete 15-Minute Cluster Blackout!
```

---

### The Zero-Downtime Pod Termination Anatomy & The `preStop` Race Condition

When Kubernetes initiates a rolling update or pod termination, two actions happen **asynchronously and in parallel**:

```
Kubernetes API Server Triggers Pod Termination
            │
            ├──────────────────────────────────────────────────┐
            ▼ (Action A: Async Network Drain)                  ▼ (Action B: Pod Lifecycle)
Endpoint Controller updates Endpoints object             Kubelet triggers container shutdown
            │                                                  │
kube-proxy updates iptables across worker nodes          WITHOUT preStop:
            │                                            Sends SIGTERM immediately to Spring Boot!
Ingress Controller removes pod IP from pool                    │
            │                                            Tomcat stops accepting new connections!
    (Takes ~2 to 5 seconds!)                                  │
            │                                            IN-FLIGHT REQUESTS RECEIVE HTTP 502 / TCP RST!
            │
            ▼                                            WITH preStop: ["sleep", "5"]:
Network routing fully updated!                           Kubelet waits 5s -> Ingress routes update ->
                                                         Then sends SIGTERM -> Zero dropped requests!
```

---

### Spring Boot Application Availability API

Spring Boot provides first-class Kubernetes integration via `ApplicationAvailability` and `AvailabilityChangeEvent`:

```java
// Programmatically mark the pod as REFUSING_TRAFFIC during heavy queue saturation
AvailabilityChangeEvent.publish(eventPublisher, this, ReadinessState.REFUSING_TRAFFIC);

// Actuator /actuator/health/readiness immediately responds with HTTP 503 OUT_OF_SERVICE
// Kubernetes removes the pod from the service endpoints without restarting the JVM!
```

---

## 3. Enterprise Scenario: FinFlow Payment Service Kubernetes Fleet

In the **FinFlow Reference Architecture**:

```
Client Checkouts (15,000 req/sec) ──► AWS ALB / Ingress Controller
                                                │
                                                ▼ (iptables / IPVS Service Routing)
                                  Kubernetes Deployment (payment-service)
                                       ├── Namespace: finflow
                                       ├── Replicas: 4 (Scales to 20 via HPA)
                                       ├── RollingUpdate: maxSurge 25%, maxUnavailable 0
                                       ├── PodDisruptionBudget: minAvailable 75%
                                       └── Graceful Drain: preStop sleep 5s + 30s timeout
```

---

## 4. Incorrect Implementation

Below is a flawed Kubernetes manifest typical of unhardened Spring Boot deployments:

```yaml
# =========================================================================
# ANTI-PATTERN KUBERNETES MANIFEST (DO NOT USE IN PRODUCTION)
# =========================================================================
apiVersion: apps/v1
kind: Deployment
metadata:
  name: payment-service-flawed
spec:
  replicas: 4
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 50% # Flaw 1: Drops 50% cluster capacity during rolling deploy!
  template:
    spec:
      containers:
        - name: payment-service
          image: finflow/payment-service:latest
          # Flaw 2: Missing startupProbe (Kubelet kills slow JVM cold-start)
          # Flaw 3: Liveness checks full /actuator/health including DB (Cascading restart trap)
          livenessProbe:
            httpGet:
              path: /actuator/health # Checks DB, Redis, RabbitMQ!
              port: 8080
            initialDelaySeconds: 5
            periodSeconds: 5
          # Flaw 4: Missing preStop sleep hook (Drops in-flight requests during rolling updates)
          # Flaw 5: Missing resources requests/limits (Causes noisy neighbor CPU throttling)
```

---

## 5. Production Incident

### Incident Timeline

| Time (UTC) | Event / Telemetry |
|---|---|
| **00:00:00** | DevOps triggers a mid-day rolling deployment of `payment-service` across 32 Kubernetes pods. |
| **00:00:05** | The deployment manifest used `/actuator/health` (which included database health checks) as its `livenessProbe` and lacked a `preStop` hook. |
| **00:00:15** | AWS RDS PostgreSQL experiences a planned Multi-AZ 3-second failover. |
| **00:00:18** | During the 3-second database blip, all 32 pods fail their `livenessProbe` simultaneously. |
| **00:00:20** | Kubelet marks all 32 pods unhealthy and dispatches `SIGKILL`, initiating a cluster-wide restart loop. |
| **00:00:30** | Concurrently, as pods terminate without a `preStop` hook, in-flight checkouts hitting the Ingress controller fail with **HTTP 502 Bad Gateway**. |
| **00:01:00** | PagerDuty SEV-0 fired: Complete checkout outage affecting 100% of payment traffic. |
| **00:12:00** | Total downtime reaches 12 minutes before manual intervention stabilizes the cluster, resulting in **$31.8M in lost checkouts**. |
| **00:30:00** | SREs deploy hardened manifests: Decoupled liveness to `/actuator/health/liveness`, added `startupProbe`, configured `preStop` sleep 5s, and set `maxUnavailable: 0`. |

---

## 6. Logs & Diagnostics

### 1. Kubelet Cascading Liveness Probe Failure Log
```text
2026-08-20T00:00:18.421Z Warning  Unhealthy  pod/payment-service-7f89d-4x2z1  Liveness probe failed: HTTP probe failed with statuscode: 503
2026-08-20T00:00:18.425Z Warning  Unhealthy  pod/payment-service-7f89d-8b9y2  Liveness probe failed: HTTP probe failed with statuscode: 503
2026-08-20T00:00:20.112Z Normal   Killing    pod/payment-service-7f89d-4x2z1  Container payment-service failed liveness probe, will be restarted
```

### 2. Ingress HTTP 502 Bad Gateway Log (Missing `preStop` hook)
```text
2026-08-20T00:00:30.884Z [error] 142#142: *894101 connect() failed (111: Connection refused) while connecting to upstream, client: 198.51.100.42, server: api.finflow.io, request: "POST /api/v1/payments/checkout HTTP/2.0", upstream: "http://10.244.3.84:8080/api/v1/payments/checkout", host: "api.finflow.io"
```

---

## 7. Root Cause Analysis

```
+-------------------------------------------------------------------------------------------------+
|                               Kubernetes Outage Root Cause Chain                                |
|                                                                                                 |
|  1. Coupled Liveness Probe (/actuator/health)                                                   |
|     └── Database latency caused liveness probe to fail on all 32 pods simultaneously.           |
|                                                                                                 |
|  2. Mass Container Termination Loop                                                             |
|     └── Kubelet killed all pods concurrently, taking down the entire payment cluster.           |
|                                                                                                 |
|  3. Asynchronous Routing Race Condition (No preStop Hook)                                       |
|     └── Pods stopped Tomcat before iptables updated, dropping in-flight traffic with HTTP 502.  |
|                                                                                                 |
|  4. Remediation: 3-Tier Probes + preStop sleep 5s + maxUnavailable: 0 + PDB                     |
|     └── Isolated liveness to internal JVM state; preStop hook guaranteed zero-downtime deploys. |
+-------------------------------------------------------------------------------------------------+
```

---

## 8. Debugging Process

```
[1. Inspect Pod Events] Run: kubectl describe pod <pod_name> -n finflow
       │
[2. Verify Probe Endpoints] Run: kubectl exec -it <pod_name> -- curl http://localhost:8080/actuator/health/liveness
       │
[3. Check Pod Disruption Budget] Run: kubectl get pdb -n finflow
       │
[4. Inspect Ingress 502 Errors] Verify preStop sleep 5s is configured in deployment.yaml
       │
[5. Rollout Validation] Run: kubectl rollout status deployment/payment-service -n finflow
```

---

## 9. Correct Implementation

### 1. Production Kubernetes Deployment: `k8s/deployment.yaml`

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: payment-service
  namespace: finflow
  labels:
    app.kubernetes.io/name: payment-service
    app.kubernetes.io/part-of: finflow-platform
spec:
  replicas: 4
  revisionHistoryLimit: 5
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 25%
      maxUnavailable: 0 # Zero Downtime: Never drop below 100% capacity during rolling deploys!
  selector:
    matchLabels:
      app.kubernetes.io/name: payment-service
  template:
    metadata:
      labels:
        app.kubernetes.io/name: payment-service
    spec:
      terminationGracePeriodSeconds: 35 # Must be > timeout-per-shutdown-phase (30s) + preStop (5s)
      securityContext:
        runAsNonRoot: true
        runAsUser: 10001
        runAsGroup: 10001
        fsGroup: 10001
      containers:
        - name: payment-service
          image: finflow/payment-service:1.0.0
          imagePullPolicy: IfNotPresent
          ports:
            - name: http
              containerPort: 8080
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: false
            capabilities:
              drop:
                - ALL
          env:
            - name: JAVA_OPTS
              value: "-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError -XX:+UseG1GC"
            - name: SPRING_PROFILES_ACTIVE
              value: "prod"
          resources:
            requests:
              cpu: "500m"
              memory: "1024Mi"
            limits:
              cpu: "2000m"
              memory: "2048Mi"
          # PreStop Hook: Pauses 5 seconds to let kube-proxy and Ingress drain endpoint routes before SIGTERM
          lifecycle:
            preStop:
              exec:
                command: ["/bin/sh", "-c", "sleep 5"]
          # Startup Probe: Grants up to 60s for slow cold-starts without premature liveness kills
          startupProbe:
            httpGet:
              path: /actuator/health/liveness
              port: http
            initialDelaySeconds: 5
            periodSeconds: 2
            failureThreshold: 30
          # Liveness Probe: Detects unrecoverable deadlocks (Restarts container on failure)
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: http
            periodSeconds: 10
            failureThreshold: 3
            timeoutSeconds: 2
          # Readiness Probe: Detects traffic-handling ability (Removes from load balancer on failure)
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: http
            periodSeconds: 5
            failureThreshold: 2
            timeoutSeconds: 2
```

---

### 2. Pod Disruption Budget: `k8s/pdb.yaml`

```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: payment-service-pdb
  namespace: finflow
spec:
  minAvailable: 75%
  selector:
    matchLabels:
      app.kubernetes.io/name: payment-service
```

---

### 3. Horizontal Pod Autoscaler: `k8s/hpa.yaml`

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: payment-service-hpa
  namespace: finflow
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: payment-service
  minReplicas: 4
  maxReplicas: 20
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
```

---

### 4. Kubernetes Application Availability Service: `KubernetesAvailabilityService.java`

```java
package com.finflow.chapter310.service;

import com.finflow.chapter310.domain.PodHealthState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class KubernetesAvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(KubernetesAvailabilityService.class);

    private final ApplicationAvailability availability;
    private final ApplicationEventPublisher eventPublisher;
    private final AtomicInteger activeConnections = new AtomicInteger(0);

    public KubernetesAvailabilityService(ApplicationAvailability availability,
                                         ApplicationEventPublisher eventPublisher) {
        this.availability = availability;
        this.eventPublisher = eventPublisher;
    }

    public LivenessState getLivenessState() { return availability.getLivenessState(); }
    public ReadinessState getReadinessState() { return availability.getReadinessState(); }

    public void setReadiness(ReadinessState state, String reason) {
        log.warn("Mutating Kubernetes ReadinessState to: {} | Reason: {}", state, reason);
        AvailabilityChangeEvent.publish(eventPublisher, this, state);
    }

    public void setLiveness(LivenessState state, String reason) {
        log.error("Mutating Kubernetes LivenessState to: {} | Reason: {}", state, reason);
        AvailabilityChangeEvent.publish(eventPublisher, this, state);
    }

    public PodHealthState getPodHealthState() {
        long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        return new PodHealthState(
                System.getenv().getOrDefault("HOSTNAME", "payment-service-local-pod"),
                getLivenessState().name(),
                getReadinessState().name(),
                activeConnections.get(),
                uptimeSeconds
        );
    }
}
```

---

## 10. Performance Comparison

Benchmarked across a 32-pod deployment handling 15,000 checkout req/sec.

| Metric | Flawed Kubernetes Deployment | Production Hardened Deployment |
|---|---|---|
| **Rolling Deploy HTTP 502 Rate** | $\sim 4.2\%$ *(Connection resets)* | **0.00% (Zero dropped requests via `sleep 5`)** |
| **Database Hiccup Resilience** | 100% Cluster Crash Loop | **100% Stable (Readiness unroutes traffic)** |
| **Cold-Start Startup Kills** | Frequent *(Killed at 10s)* | **0% (Startup probe grants up to 60s)** |
| **Node Drain Quorum Loss** | Unprotected *(Potential 100% loss)* | **Guaranteed (PDB enforces 75% minAvailable)** |
| **Rolling Update Capacity Drop** | Dropped by 50% | **0% Drop (`maxUnavailable: 0`)** |

---

## 11. Best Practices

### The Do's
- **DO use `/actuator/health/liveness` for `livenessProbe` and `startupProbe`**: Keeps probe isolated from third-party outages.
- **DO use `/actuator/health/readiness` for `readinessProbe`**: Controls ingress traffic routing safely.
- **DO include a `preStop` hook with `sleep 5`**: Eliminates HTTP 502 errors during rolling updates.
- **DO set `maxUnavailable: 0` in rolling update strategies**: Guarantees new pods are healthy before old pods are terminated.
- **DO configure `terminationGracePeriodSeconds`**: Must exceed `timeout-per-shutdown-phase + preStop sleep`.
- **DO define a `PodDisruptionBudget`**: Protects pod quorum during voluntary disruptions.

### The Don'ts
- **DON'T point `livenessProbe` to generic `/actuator/health`**: Triggers cascading cluster crashes on database blips.
- **DON'T omit `startupProbe` on slow-starting microservices**: Causes Kubelet to kill pods prematurely.
- **DON'T omit resource `requests` and `limits`**: Leads to noisy neighbor CPU starvation and out-of-control memory consumption.
- **DON'T hardcode pod IPs in client configurations**: Always route traffic through Kubernetes Service DNS.

---

## 12. Common Mistakes

### Mistake 1: The Generic `/actuator/health` Liveness Probe
Setting `livenessProbe.httpGet.path: /actuator/health`.
**Why it fails**: Generic `/actuator/health` aggregates health indicators from PostgreSQL, Redis, Kafka, and RabbitMQ. If any one downstream system blips, all application pods fail their liveness probe simultaneously, triggering mass container restarts.
**Production Fix**: Set `path: /actuator/health/liveness`.

### Mistake 2: Missing `preStop` Hook on Rolling Deploys
Deploying without `lifecycle.preStop.exec.command: ["/bin/sh", "-c", "sleep 5"]`.
**Why it fails**: When a pod is terminated, `kube-proxy` asynchronously updates iptables across the cluster. If Tomcat shuts down immediately upon receiving `SIGTERM`, in-flight requests routed during the 2–5 second propagation window receive TCP connection resets (HTTP 502).
**Production Fix**: Add `sleep 5` to the `preStop` lifecycle hook.

---

## 13. Interview Questions

### Junior Tier
**Q: What is the difference between a Liveness Probe and a Readiness Probe in Kubernetes?**
> **Answer**: 
> - **Liveness Probe**: Determines if the container is running and healthy. If the liveness probe fails, Kubelet terminates and restarts the container (`SIGKILL`). It is used to recover from unrecoverable deadlocks.
> - **Readiness Probe**: Determines if the container is ready to accept network traffic. If the readiness probe fails, Kubernetes removes the pod IP from the Service Endpoints and load balancer pool without restarting the container. It is used during startup warmup, graceful draining, or temporary overload.

### Mid Tier
**Q: Why is a `preStop` hook necessary for zero-downtime Spring Boot deployments in Kubernetes?**
> **Answer**: When a pod termination is initiated, Kubernetes removes the pod from Endpoints and sends `SIGTERM` simultaneously and asynchronously. The `kube-proxy` iptables/IPVS routing update takes 2–5 seconds to propagate across all cluster worker nodes. If Spring Boot receives `SIGTERM` immediately, it stops accepting new connections on Tomcat while Ingress controllers and client pods are still routing requests to it, causing intermittent HTTP 502/504 errors. A `preStop` hook executing `sleep 5` pauses pod shutdown for 5 seconds, allowing routing tables to update before Tomcat begins graceful connection termination.

### Senior Tier
**Q: Explain how Spring Boot's Application Availability API integrates with Kubernetes probes.**
> **Answer**: Spring Boot exposes two `AvailabilityState` interfaces: `LivenessState` (`CORRECT`, `BROKEN`) and `ReadinessState` (`ACCEPTING_TRAFFIC`, `REFUSING_TRAFFIC`). Spring Boot Actuator maps these states directly to health probe groups: `/actuator/health/liveness` and `/actuator/health/readiness`. When the application context finishes initialization, Spring automatically publishes `ReadinessState.ACCEPTING_TRAFFIC`. Developers can also publish `AvailabilityChangeEvent` programmatically (e.g. during circuit breaker trips or queue saturation) to refuse traffic without killing the JVM.

### Staff Tier
**Q: Design the resource configuration strategy (`requests` vs `limits`, CPU throttling, and QoS classes) for a high-throughput financial transaction pod.**
> **Answer**: 
> 1. **Quality of Service (QoS)**: Set `requests.memory == limits.memory` to achieve the **Guaranteed QoS** class, ensuring the pod is never evicted under host memory pressure.
> 2. **Memory Buffer**: Container limit set to 2048Mi, with `-XX:MaxRAMPercentage=75.0` (1536MB Heap + 512MB Off-heap buffer).
> 3. **CPU Requests & Limits**: Set `requests.cpu: "1000m"` for guaranteed compute scheduling. Omit CPU limits or set a generous limit (`limits.cpu: "4000m"`) to prevent Linux CFS bandwidth throttling (`cpu.cfs_quota_us`) from inducing artificial latency spikes during sudden checkout bursts.

### Principal Tier
**Q: Design a Zero-Downtime, Multi-Region Progressive Canary Deployment Architecture with Automated Rollbacks for 500 Spring Boot microservices.**
> **Answer**: A Principal-level architecture leverages **Argo Rollouts with Service Mesh (Istio / Envoy) and Prometheus Metric Analysis**:
> 1. **Canary Routing**: Istio VirtualService splits live ingress traffic (95% Stable, 5% Canary).
> 2. **Automated Analysis Template**: Argo Rollouts runs background Prometheus metric queries every 60 seconds:
>    - **SLO 1 (Error Rate)**: $\frac{\text{HTTP 5xx}}{\text{Total}} < 0.05\%$.
>    - **SLO 2 (Latency)**: $P_{99} < 120\text{ms}$.
> 3. **Progressive Promotion**: Traffic weight shifts automatically: $5\% \to 20\% \to 50\% \to 100\%$ over 1 hour.
> 4. **Automated Rollback**: If error rate breaches 0.1% for $> 30\text{s}$, Istio immediately zeroes Canary traffic weight, terminating Canary pods with zero human intervention.

---

## 14. Hands-on Exercise

### Objective
Implement programmatic readiness state management using Spring Boot's `AvailabilityChangeEvent` to temporarily pull a pod from the Kubernetes load balancer during queue congestion.

### Solution

```java
@Service
public class QueueCongestionMonitor {

    private final ApplicationEventPublisher eventPublisher;

    public QueueCongestionMonitor(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void checkQueuePressure(int queueSize) {
        if (queueSize > 5000) {
            // Refuse traffic -> Kubernetes removes pod from load balancer pool
            AvailabilityChangeEvent.publish(eventPublisher, this, ReadinessState.REFUSING_TRAFFIC);
        } else if (queueSize < 1000) {
            // Restore traffic -> Kubernetes adds pod back to load balancer pool
            AvailabilityChangeEvent.publish(eventPublisher, this, ReadinessState.ACCEPTING_TRAFFIC);
        }
    }
}
```

---

## 15. Advanced Challenge: Automated Canary Deployment Manifest with Argo Rollouts

### Enterprise Problem Statement
Create an `Argo Rollout` custom resource for `payment-service` with step-based canary traffic weight shifting and Prometheus metric analysis.

### Enterprise Solution

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Rollout
metadata:
  name: payment-service-rollout
  namespace: finflow
spec:
  replicas: 8
  strategy:
    canary:
      analysis:
        templates:
          - templateName: success-rate-analysis
        args:
          - name: service-name
            value: payment-service
      steps:
        - setWeight: 10
        - pause: { duration: 5m }
        - setWeight: 25
        - pause: { duration: 10m }
        - setWeight: 50
        - pause: { duration: 10m }
```

---

## 16. Production Checklist

Before approving any Kubernetes deployment pull request:

- [ ] **Startup, Liveness, and Readiness Probes Configured**: Confirm 3-tier probe strategy is active.
- [ ] **Liveness Points to `/actuator/health/liveness`**: Verify no external database checks are in liveness probe.
- [ ] **`preStop` Hook Configured**: Verify `sleep 5` is present in container lifecycle.
- [ ] **`maxUnavailable: 0` in RollingUpdate**: Ensure zero capacity drop during deployments.
- [ ] **`terminationGracePeriodSeconds` > 30s**: Confirm grace period exceeds Spring shutdown timeout.
- [ ] **Resource Requests and Limits Defined**: Verify memory and CPU bounds are set.
- [ ] **PodDisruptionBudget Declared**: Ensure PDB guarantees minimum availability during node drains.
