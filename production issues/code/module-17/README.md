# Module 17: Kubernetes Pod Lifecycle, Probes & OOMKilled

## Overview
This module explores Kubernetes pod lifecycle orchestration, the mechanics of Startup, Liveness, and Readiness Probes, zero-downtime rolling updates with `preStop` hooks, graceful shutdown request draining, Quality of Service (QoS) classes, and how to avoid the catastrophic **Liveness Probe Death Spiral**.

## Key Scenarios Covered
1. **The Liveness Probe Death Spiral:**
   - Why pointing liveness probes at `/actuator/health` causes cluster-wide restart storms when downstream dependencies (PostgreSQL/Redis) experience transient latency.
   - Decoupling JVM internal health (`/actuator/health/liveness`) from traffic readiness (`/actuator/health/readiness`).
2. **Slow Bootstrap & Premature Traffic Routing:**
   - Protecting applications with slow initialization (Flyway/Liquibase schema migrations, Hibernate metamodel, JIT warmup) using Kubernetes `startupProbe`.
3. **Zero-Downtime Rolling Deployments & `preStop` Hooks:**
   - Solving the race condition between Kubernetes Service Endpoints/iptables propagation and pod termination using a `preStop` `sleep 10` hook combined with Spring Boot's `server.shutdown=graceful`.
4. **Kubernetes QoS Classes & OOMScoreAdj:**
   - `Guaranteed` vs `Burstable` vs `BestEffort` and how the Linux kernel selects victims during node-level memory pressure.

## Project Structure
- `k8s/`:
  - `deployment-production.yaml` (Hardened deployment manifest with startup, liveness, readiness probes, `preStop` hook, `Guaranteed` QoS).
  - `deployment-bad.yaml` (Anti-pattern manifest illustrating the 4 fatal probe pitfalls).
- `src/main/java/.../service/`:
  - `PodLifecycleService.java` (Manages Spring `ApplicationAvailability` and `AvailabilityChangeEvent`).
  - `SimulatedDownstreamDependencyService.java` (Simulates downstream DB/cache failure to prove probe isolation).
- `src/main/java/.../controller/`:
  - `PodLifecycleController.java` (REST endpoints for manual traffic draining and failure simulation).
- `src/test/java/.../`:
  - `PodLifecycleServiceTest.java`
  - `KubernetesProbesTest.java`
  - `PodLifecycleControllerTest.java`
  - `Module17IntegrationTest.java`

## How to Run Tests
```bash
mvn clean test
```

## Complete Documentation
For the full deep-dive technical incident guide, see [Module 17 Documentation](../../docs/module-17-kubernetes-pod-lifecycle-probes-oomkilled.md).
