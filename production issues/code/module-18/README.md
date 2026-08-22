# Module 18: API Gateway, Nginx & Reverse Proxy Timeouts

## Overview
This module explores edge networking, reverse proxy mechanics, HTTP 502 Bad Gateway and 504 Gateway Timeout root causes, TCP Keep-Alive timeout mismatches, response buffer tuning, and forwarded header resolution across Ingress/Nginx proxies and Spring Boot microservices.

## Key Scenarios Covered
1. **The TCP Keep-Alive Race Condition (Intermittent `502 Bad Gateway` / `Connection Reset`):**
   - Why having an upstream proxy keepalive (e.g. Nginx 65s) greater than or equal to downstream Tomcat keepalive (e.g. 20s) leads to TCP FIN/RST packet collisions.
   - The Golden Keep-Alive Rule: `Tomcat Keep-Alive Timeout >= Upstream Proxy Keepalive + 5s`.
2. **The 504 Gateway Timeout Cascade:**
   - Establishing a strict timeout hierarchy: `Downstream API Timeout < Spring Boot App Timeout < Gateway Timeout < Ingress Timeout < Client Timeout`.
3. **Nginx Buffer Overflow & Disk I/O Throttling:**
   - How undersized `proxy_buffers` and `proxy_buffer_size` force large JSON payload responses to be buffered to disk (`/var/cache/nginx/proxy_temp`), causing severe P99 latency spikes.
4. **Forwarded Headers & Scheme Resolution:**
   - Properly passing `X-Forwarded-Proto`, `X-Forwarded-Host`, `X-Forwarded-For` and enabling `server.forward-headers-strategy=framework` to prevent OAuth2 redirect URI protocol mismatches (HTTP vs HTTPS).

## Project Structure
- `nginx/`:
  - `nginx.conf` (Production-hardened reverse proxy configuration with keepalive pools, buffer tuning, and safe retries).
  - `nginx-bad.conf` (Anti-pattern manifest illustrating keepalive mismatches and buffer bottlenecks).
- `src/main/java/.../service/`:
  - `TimeoutHierarchyCalculator.java` (Mathematical timeout and keepalive validator).
- `src/main/java/.../config/`:
  - `GatewayProxyTimeoutConfig.java` (Configures bounded `RestClient` / `RestTemplate` timeouts).
- `src/main/java/.../controller/`:
  - `GatewayProxyDiagnosticsController.java` (REST endpoints for timeout validation, payment simulation, and header echo).
- `src/test/java/.../`:
  - `TimeoutHierarchyCalculatorTest.java`
  - `GatewayProxyDiagnosticsControllerTest.java`
  - `ForwardedHeadersIntegrationTest.java`
  - `Module18IntegrationTest.java`

## How to Run Tests
```bash
mvn clean test
```

## Complete Documentation
For the full deep-dive technical incident guide, see [Module 18 Documentation](../../docs/module-18-api-gateway-nginx-reverse-proxy-timeouts.md).
