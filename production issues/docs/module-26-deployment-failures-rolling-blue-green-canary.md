# Module 26: Deployment Failures: Rolling, Blue-Green & Canary

## Issue 26.1: Rolling Update Session Invalidation Waves, Canary Split-Brain Routing, and Blue-Green Database Drift

---

### 1. Scenario

During a major Friday release on the **FinFlow Global Retail Banking & Payments API**:
1. A rolling deployment was initiated to upgrade the banking service from `v1.0.0` to `v2.0.0` across 12 Kubernetes pod replicas over a 15-minute window.
2. Because the application stored HTTP session state in local in-memory Tomcat session managers rather than Redis or stateless JWTs, **every pod termination abruptly destroyed the active session state of 12,500 connected retail users**.
3. Over 15 minutes, **150,000 active banking users were forcefully logged out mid-transfer**. The resulting wave of desperate re-logins created a **15x login traffic surge that melted the OAuth2 identity provider**, crashing the authentication gateway for 45 minutes (**The Rolling Deployment Session Invalidation Storm**).
4. Concurrently, a Canary release was configured to route 10% of traffic to `v2.0.0` using raw Nginx Ingress weight annotations without sticky cookie affinity.
5. A user navigating the single-page application (SPA) initiated a transfer: `/api/v1/auth` was routed to `v1.0.0` (returning a legacy session token format), while the subsequent `/api/v1/transfers` request landed on `v2.0.0` (which rejected the legacy token as invalid). 10% of users suffered from intermittent **random API failures due to Split-Brain Canary State Mismatches**.
6. To make matters worse, a Blue-Green deployment on the settlement cluster switched traffic to the Green environment while Green's database migrations had deleted a column expected by active Blue transactions, causing **$4.8M in payment settlement transactions to fail with 500 Internal Server Errors**.

---

### 2. Symptoms

```text
1. Massive Re-Authentication Traffic Spikes During Rolling Updates:
   Sudden 10x-15x spike in POST /oauth/token or /api/v1/auth/login during deployment.
   Identity provider connection pool exhaustion and 504 Gateway Timeouts.

2. Intermittent 401 Unauthorized / 400 Bad Request on Canary Deployments:
   Frontend SPA requests randomly fail on some endpoints while succeeding on others for the same user.

3. Ingress Routing Flapping Between Versions:
   Nginx Ingress logs show consecutive requests from the same IP alternating between v1 and v2 upstreams.

4. High Canary Error Rates & Premature Rollbacks:
   Cold-start connection latency on new canary pods triggers false-positive automated rollbacks.

5. Blue-Green Dual-Cluster Database Collision:
   Active transactions on the Blue cluster fail with database schema errors caused by Green DDL updates.
```

---

### 3. Possible Root Causes

1. **Stateful In-Memory Session Management:** Storing session state in JVM memory so pod termination during rolling updates destroys active user sessions.
2. **Missing Canary Cookie / Hash Affinity:** Relying solely on stateless random traffic weighting (`canary-weight: 10`) without sticky cookie binding (`canary-by-cookie: canary_affinity`), causing multi-request workflows to jump between versions.
3. **Violating the Expand & Contract Database Rule:** Deploying Green code with destructive database schema changes while Blue is still actively handling production traffic.
4. **Lack of Automated Canary Analysis (ACA) Baselines:** Comparing Canary metrics against static thresholds rather than running statistical differential analysis against an identical Baseline control group.
5. **Missing Pre-Warming (Warmup Probes):** Routing live Canary traffic to cold JVM instances before the JIT compiler and HikariCP connection pools have warmed up.

---

### 4. Architecture Context: Canary Routing Pipeline & Automated Canary Analysis (ACA)

```text
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                     CANARY ROUTING & AUTOMATED CANARY ANALYSIS (ACA)                            │
│                                                                                                 │
│  [User Request: Client Browser / Mobile App]                                                    │
│                        │                                                                        │
│                        ▼                                                                        │
│  ┌───────────────────────────────────────────────────────────────────────────────────────────┐  │
│  │ Nginx Ingress Controller (Canary Routing Layer)                                           │  │
│  │ 1. Check Cookie: `Cookie: canary_affinity=canary` ──► Route 100% to Canary (v2.0.0)      │  │
│  │ 2. Check Cookie: `Cookie: canary_affinity=baseline` ──► Route 100% to Baseline (v1.0.0)  │  │
│  │ 3. If No Cookie: Hash(User ID) % 100 < 10% ──► Assign Canary & Set `canary_affinity`      │  │
│  │    (Guarantees all sub-requests for this user STAY on v2.0.0!)                            │  │
│  └─────────────────────────────┬─────────────────────────────────────────────┬───────────────┘  │
│                                │ (90% Traffic)                               │ (10% Traffic)    │
│                                ▼                                             ▼                  │
│  ┌──────────────────────────────────────────┐ ┌──────────────────────────────────────────────┐  │
│  │ BASELINE (v1.0.0 - 9 Replicas)           │ │ CANARY (v2.0.0 - 1 Replica)                  │  │
│  │ Error Rate: 0.05% | P99 Latency: 22ms    │ │ Error Rate: 0.08% | P99 Latency: 25ms        │  │
│  └─────────────────────────────┬────────────┘ └──────────────────────────────┬───────────────┘  │
│                                │                                             │                  │
│                                └──────────────────────┬──────────────────────┘                  │
│                                                       ▼                                         │
│  ┌───────────────────────────────────────────────────────────────────────────────────────────┐  │
│  │ Automated Canary Analysis (ACA) Engine                                                    │  │
│  │ - Δ Error Rate = Canary (0.08%) - Baseline (0.05%) = 0.03% (< 1.0% Max Threshold)         │  │
│  │ - P99 Latency = 25ms (< 250ms Max Threshold)                                             │  │
│  │ Decision: PROCEED_WITH_PROGRESSIVE_ROLLOUT (10% ──► 25% ──► 50% ──► 100%)                 │  │
│  └───────────────────────────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 5. How to Reproduce the Issues

#### ❌ Anti-Pattern 1: In-Memory HTTP Session in Rolling Deploy
```java
// ❌ ANTI-PATTERN: Session stored in local JVM heap; destroyed when pod restarts!
@PostMapping("/login")
public String login(HttpServletRequest request, @RequestParam String user) {
    HttpSession session = request.getSession(true);
    session.setAttribute("USER", user); // Lost during rolling deployment!
    return "OK";
}
```

#### ❌ Anti-Pattern 2: Ingress Canary Without Sticky Cookie Affinity
```yaml
# ❌ ANTI-PATTERN: Sub-requests bounce randomly between v1 and v2!
metadata:
  annotations:
    nginx.ingress.kubernetes.io/canary: "true"
    nginx.ingress.kubernetes.io/canary-weight: "10"
    # Missing canary-by-cookie causes split-brain state corruption!
```

---

### 6. Evidence Collection & Diagnostic Probing

#### Method 1: Inspect Ingress Access Logs for Flapping Upstreams
```bash
kubectl logs -n ingress-nginx -l app.kubernetes.io/name=ingress-nginx \
  | grep "api.finflow.internal" | awk '{print $1, $7, $11, $NF}'
```

#### Method 2: Compare Canary vs Baseline Error Rates in Prometheus
```promql
# Canary Error Rate vs Baseline Error Rate
sum(rate(http_requests_total{status=~"5..", version="v2.0.0"}[5m])) 
/ sum(rate(http_requests_total{version="v2.0.0"}[5m])) * 100
-
sum(rate(http_requests_total{status=~"5..", version="v1.0.0"}[5m])) 
/ sum(rate(http_requests_total{version="v1.0.0"}[5m])) * 100
```

---

### 7. Step-by-Step Debugging Procedure

```text
Step 1: Verify Stateless Session Management.
        Ensure applications use Spring Session (Redis) or stateless JWT tokens so pod restarts do not invalidate logins.

Step 2: Add Sticky Cookie Affinity to Ingress Canary.
        Add `nginx.ingress.kubernetes.io/canary-by-cookie: canary_affinity` to canary manifests.

Step 3: Enforce Automated Canary Analysis Gating in CI/CD.
        Compare Canary error rates against Baseline; automatically trigger rollback if delta exceeds 1.0%.

Step 4: Synchronize Blue-Green Database Migrations via Expand & Contract.
        Never deploy Green code with breaking database changes before Blue is fully drained.

Step 5: Configure Pre-Warming Warmup Probes.
        Ensure Canary pods execute warm-up queries before receiving live user traffic.
```

---

### 8. Technical Root Cause Deep-Dive

#### 1. Why Random Canary Weighting Causes Split-Brain State Failures
- If Canary is set to `canary-weight: 10%` without cookie affinity:
- A user loading a web page makes 10 sub-requests (HTML, JS, User Profile, Accounts, Transactions).
- The probability that *at least one* sub-request hits the canary while others hit the baseline is:
  $$P(\text{Split-Brain}) = 1 - (0.90^{10} + 0.10^{10}) \approx 1 - (0.3487 + 0.0000000001) \approx 65.1\%$$
- **65% of all users experience a broken split-brain session** where part of their request executes on v1 and part on v2!
- Adding `canary-by-cookie: canary_affinity` locks the user to either 100% v1 or 100% v2.

#### 2. Automated Canary Analysis (ACA) Mechanics
- ACA compares two simultaneously deployed versions: **Baseline** (current stable version with 1 replica) and **Canary** (new version with 1 replica).
- Because both receive live traffic under identical external network and database conditions, transient global network blips affect both equally.
- **Rollback Condition:**
  $$\Delta \text{ErrorRate} = \text{ErrorRate}_{\text{Canary}} - \text{ErrorRate}_{\text{Baseline}} > \theta_{\text{threshold}}$$
- If $\Delta \text{ErrorRate} > 1.0\%$ or $\text{P99}_{\text{Canary}} > 250\text{ms}$, the deployment controller immediately scales Canary to 0 and alerts on-call SREs.

---

### 9. Production-Grade Fixes

#### ✅ Fix 1: Nginx Ingress Canary with Sticky Cookie Affinity (`k8s/canary-ingress.yaml`)
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: finflow-banking-canary-ingress
  annotations:
    nginx.ingress.kubernetes.io/canary: "true"
    nginx.ingress.kubernetes.io/canary-weight: "10"
    nginx.ingress.kubernetes.io/canary-by-cookie: "canary_affinity"
spec:
  rules:
    - host: api.finflow.internal
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: finflow-banking-v2-canary-service
                port:
                  number: 8080
```

#### ✅ Fix 2: Automated Canary Analysis Engine (`CanaryTrafficRouterService.java`)
```java
@Service
public class CanaryTrafficRouterService {

    public CanaryHealthReport evaluateCanaryHealth() {
        double deltaRate = calculateCanaryErrorRate() - calculateBaselineErrorRate();
        double p99Latency = calculateP99Latency();

        if (deltaRate > 1.0) {
            return new CanaryHealthReport("AUTOMATED_ROLLBACK_TRIGGERED", "Error rate delta exceeded 1.0%");
        } else if (p99Latency > 250.0) {
            return new CanaryHealthReport("AUTOMATED_ROLLBACK_TRIGGERED", "P99 latency exceeded 250ms");
        } else {
            return new CanaryHealthReport("PROCEED_WITH_PROGRESSIVE_ROLLOUT", "Canary healthy");
        }
    }
}
```

---

### 10. Verification

1. **Canary Sticky Affinity Test:** Run `CanaryAffinityRoutingTest.java` to verify that requests with the same User ID or cookie consistently route to the exact same version.
2. **Automated Canary Rollback Test:** Run `AutomatedCanaryRollbackTest.java` to verify that an elevated error rate or high latency triggers `AUTOMATED_ROLLBACK_TRIGGERED`.
3. **Controller API Test:** Run `DeploymentDiagnosticsControllerTest.java` to test REST routing and ACA health endpoints.
4. **Integration Test:** Run `Module26IntegrationTest.java` to verify Spring context and Actuator health.

---

### 11. Prevention & Production Readiness

1. **Rule: Always Use Cookie Affinity with Weighted Canaries:**
   Never configure raw weight-based canary ingress without `canary-by-cookie`.
2. **Rule: Always Enforce Stateless Authentication:**
   Use JWT or centralized Redis session storage to guarantee zero user logouts during rolling updates.
3. **Prometheus Alerting Rule for Canary Error Deviation:**
```yaml
- alert: CanaryErrorRateDeviationHigh
  expr: (sum(rate(http_requests_total{status=~"5..",version="canary"}[5m])) / sum(rate(http_requests_total{version="canary"}[5m])))
        - (sum(rate(http_requests_total{status=~"5..",version="baseline"}[5m])) / sum(rate(http_requests_total{version="baseline"}[5m]))) > 0.01
  for: 2m
  labels:
    severity: critical
  annotations:
    summary: "Canary deployment error rate exceeds baseline by >1.0%; automated rollback recommended"
```

---

### 12. Interview & Production Questions

#### Interview Questions
1. **Q: Why does a weighted Canary deployment without cookie affinity break single-page applications (SPAs)?**
   *Answer:* An SPA makes multiple sub-requests per user interaction. Without sticky cookie affinity, consecutive requests randomly bounce between Version 1 and Version 2, causing session mismatches, broken state transitions, and random 401/400 errors for over 65% of users.
2. **Q: How does Automated Canary Analysis (ACA) avoid false-positive rollbacks caused by external database or network blips?**
   *Answer:* ACA compares the Canary version against an identical Baseline control version deployed simultaneously under the exact same traffic conditions. If an external database slows down, both Canary and Baseline suffer equally, keeping the *delta error rate* ($\text{Rate}_{\text{Canary}} - \text{Rate}_{\text{Baseline}}$) near zero and preventing false rollbacks.
3. **Q: What is the primary operational difference between Rolling, Blue-Green, and Canary deployments?**
   *Answer:* Rolling updates replace pods incrementally within the same cluster. Blue-Green maintains two identical environments and switches 100% of traffic instantly at the router. Canary routes a small percentage (e.g. 5–10%) of live traffic to the new version to test performance and error rates before full promotion.
4. **Q: Why must Blue-Green deployments strictly follow the Expand & Contract database pattern?**
   *Answer:* Because both Blue and Green environments connect to the same underlying database during cutover. If Green executes a breaking schema change (like dropping a column), the still-active Blue cluster will crash immediately.
5. **Q: How does `nginx.ingress.kubernetes.io/canary-by-cookie` work?**
   *Answer:* When a client sends a request with the specified cookie set to `always` or `canary`, Ingress always routes the request to the canary upstream. If set to `never`, it routes to the primary upstream.

#### Production Incident Questions
1. **Incident:** During a rolling deployment, 100,000 active users were logged out, causing a massive login spike that crashed the auth server. What was the cause?
   *Diagnosis:* Session state was stored in local in-memory JVM heap. Pod terminations destroyed active sessions. Fix: Move to stateless JWTs or Spring Session with Redis.
2. **Incident:** 10% of users reported intermittent "Session Expired" errors during a Canary release. Why?
   *Diagnosis:* Missing cookie affinity on the Canary Ingress allowed sub-requests to hop between v1 and v2. Fix: Configure `canary-by-cookie: canary_affinity`.
3. **Incident:** An automated canary rollback triggered within 30 seconds of deployment due to high latency, but the code had no bugs. Why?
   *Diagnosis:* Cold-start JIT compilation and un-warmed HikariCP connection pools on the new Canary pod. Fix: Add application pre-warming scripts and require minimum sample size (e.g. 500 requests) before evaluating ACA metrics.
4. **Incident:** After switching traffic to the Green environment in Blue-Green deployment, rollback failed because Green wrote data in a format Blue could not read. Why?
   *Diagnosis:* Failure to follow backward-compatible data formats (Expand & Contract). Fix: Ensure all data written by new versions can be read by older versions during the rollback grace period.
5. **Incident:** A Kubernetes rolling deployment left 50% old pods and 50% new pods running indefinitely. Why?
   *Diagnosis:* New pods failed readiness probes or `maxUnavailable` / `maxSurge` parameters blocked further pod replacement. Fix: Check pod logs with `kubectl describe pod` and inspect readiness probe endpoints.

#### Trick Questions
1. **Trick:** Can a Blue-Green deployment achieve zero-downtime if the database migration drops a column in the Green environment?
   *Answer:* No! Blue is still serving live traffic when Green migrations run. Dropping a column immediately breaks active Blue queries.
2. **Trick:** If a Canary deployment is weighted at 0%, does it receive any traffic?
   *Answer:* It receives 0% of random traffic, but clients sending the explicit canary header (`X-Canary: always`) or canary cookie will still be routed to the Canary pod.
3. **Trick:** Does Kubernetes RollingUpdate guarantee that the total number of running pods never drops below the desired replica count?
   *Answer:* Only if `maxUnavailable: 0` is configured in the Deployment strategy. By default, `maxUnavailable: 25%`, meaning pod count can temporarily drop during rollout.

---

*(Answers and detailed explanations are provided in the Master Answer Guide)*
