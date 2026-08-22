# Module 26: Deployment Failures: Rolling, Blue-Green & Canary

## Overview
This module explores enterprise Spring Boot deployment strategies, deep-diving into Rolling update session invalidations, Nginx Ingress Canary sticky routing, Blue-Green database synchronization, and Automated Canary Analysis (ACA) rollback gating.

## Key Scenarios Covered
1. **Rolling Update In-Memory Session Invalidation:**
   - Why storing HTTP session state in local in-memory Tomcat session managers forces 150,000 users to log out during rolling deployments.
   - Solving with stateless JWT bearer tokens or Spring Session with Redis.
2. **Ingress Canary Split-Brain Routing:**
   - Why header-less sub-requests bounce randomly between Version 1 and Version 2 during Canary rollouts.
   - Implementing sticky cookie affinity (`canary-by-cookie: canary_affinity`) and deterministic user hashing.
3. **Automated Canary Analysis (ACA) & Metric Gating:**
   - Comparing Canary vs Baseline error rates ($\Delta \text{ErrorRate} < 1.0\%$) and P99 latency thresholds before progressive rollout.
4. **Blue-Green Controlled Traffic Switch:**
   - Instant zero-downtime traffic switching using Kubernetes Service label selectors.

## Project Structure
- `k8s/`:
  - `canary-ingress.yaml` (Nginx Ingress with cookie affinity and weight annotations).
  - `blue-green-service.yaml` (Service selector for instant traffic switching).
- `src/main/java/.../service/`:
  - `CanaryTrafficRouterService.java` (Deterministic sticky hashing, ACA health evaluation, anomaly triggers).
- `src/main/java/.../controller/`:
  - `DeploymentDiagnosticsController.java` (REST endpoints for route decisions, ACA reports, and anomaly simulation).
- `src/test/java/.../`:
  - `CanaryAffinityRoutingTest.java`
  - `AutomatedCanaryRollbackTest.java`
  - `DeploymentDiagnosticsControllerTest.java`
  - `Module26IntegrationTest.java`

## How to Run Tests
```bash
mvn clean test
```

## Complete Documentation
For the full deep-dive technical incident guide, see [Module 26 Documentation](../../docs/module-26-deployment-failures-rolling-blue-green-canary.md).
