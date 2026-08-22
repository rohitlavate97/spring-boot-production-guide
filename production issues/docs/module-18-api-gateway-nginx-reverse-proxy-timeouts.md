# Module 18: API Gateway, Nginx & Reverse Proxy Timeouts

## Issue 18.1: Intermittent 502 Bad Gateway (Keep-Alive Race), 504 Gateway Timeout Cascades, and Nginx Buffer Disk I/O Throttling

---

### 1. Scenario

During peak morning payment settlement on the **FinFlow Distributed Gateway & Clearing Pipeline**:
1. External merchants and mobile clients experience **intermittent `502 Bad Gateway` errors** affecting ~0.3% of all checkout transactions. Centralized gateway logs show Nginx recording:
   `upstream prematurely closed connection while reading response header from upstream`.
2. Packet capture (`tcpdump`) reveals a classic **TCP Keep-Alive Race Condition**: Nginx had `keepalive_timeout 65s`, while the downstream Spring Boot Tomcat container had `server.tomcat.keep-alive-timeout: 20000` (20s). When Nginx attempted to reuse a 20.001-second-old idle TCP socket, Tomcat simultaneously sent a TCP `FIN` packet to close the socket. Tomcat responded to Nginx's incoming HTTP request with a **TCP `RST` (Reset)**. Because the HTTP request was a non-idempotent `POST /api/v1/payments/authorize`, Nginx **refused to retry** and immediately returned `502 Bad Gateway` to the client.
3. Concurrently, a minor network blip at a partner card network (Visa/Mastercard) caused payment authorization calls to hang. Because Spring Boot's internal `RestClient` had no explicit read timeout (defaulting to infinite / 120s), and Nginx had `proxy_read_timeout 60s`, all 200 Tomcat worker threads became **blocked waiting for downstream sockets**. Upstream Ingress routers timed out after 30s, returning a massive wave of **`504 Gateway Timeout` errors** and starving connection pools across the entire cluster.
4. When merchants requested large batch statement exports (`GET /api/v1/statements/export`), P99 latency exploded from **40ms to 4,200ms**. Nginx had default 4KB proxy buffers (`proxy_buffers 4 4k`), forcing Nginx to buffer 500KB JSON payloads to disk (`/var/cache/nginx/proxy_temp`), causing severe disk I/O contention on the reverse proxy nodes.
5. In addition, redirected authentication requests failed because Nginx stripped `X-Forwarded-Proto` and Spring Boot was not configured with `server.forward-headers-strategy: framework`, causing OAuth2 redirects to send users to `http://10.0.1.10:8080/login` (internal private HTTP) instead of `https://api.finflow.com/login`.

---

### 2. Symptoms

```text
1. Intermittent 502 Bad Gateway on Reused Idle Connections:
   Nginx error.log: "recv() failed (104: Connection reset by peer) while reading response header from upstream".
   Occurs randomly on low-frequency endpoints or after periods of brief traffic lulls.

2. Cascading 504 Gateway Timeout Waves:
   Ingress / Cloudflare returns "504 Gateway Time-out".
   Application thread pools completely saturated in WAITING/TIMED_WAITING on SocketInputStream.read().

3. Severe P99 Latency Spikes on Large JSON Payloads:
   Nginx error.log: "an upstream response is buffered to a temporary file /var/cache/nginx/proxy_temp/...".
   Disk IOPS saturation on reverse proxy instances while CPU and network utilization remain low.

4. OAuth2 / HTTPS Redirect URI Protocol Mismatch:
   Browser displays "Mixed Content" or redirects from HTTPS public domain to private internal HTTP IP address.
```

---

### 3. Possible Root Causes

1. **The TCP Keep-Alive Race Condition (The Deadly Timeout Mismatch):**
   When `Proxy Keep-Alive Timeout >= Upstream Tomcat Keep-Alive Timeout`, the proxy sends an HTTP request on a connection the server is already closing, causing TCP `RST`.
2. **Missing Outbound HTTP Client Timeout Hierarchy:**
   When `Downstream Client Timeout >= Reverse Proxy Timeout`, a hung dependency blocks Tomcat threads until the proxy gives up with `504`, leaving orphan backend threads executing uselessly.
3. **Undersized Nginx Proxy Buffers:**
   When `proxy_buffering` is enabled with tiny buffers (e.g. 4KB), any response larger than the buffer is written to disk, turning high-speed network I/O into slow disk operations.
4. **Missing Forwarded Headers Filter:**
   When Spring Boot sits behind TLS-terminating proxies, failure to set `server.forward-headers-strategy: framework` prevents `HttpServletRequest` from recognizing `X-Forwarded-Proto: https` and `X-Forwarded-Host`.

---

### 4. Architecture Context: Reverse Proxy Timeout & Keep-Alive State Machine

```text
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                     THE TCP KEEP-ALIVE RACE CONDITION & TIMEOUT HIERARCHY                       │
│                                                                                                 │
│  Client (Browser / Mobile App)                                                                  │
│           │                                                                                     │
│           ▼ [HTTPS / Client Timeout: 15s]                                                       │
│  ┌───────────────────────────────────────────────────────────────────────────────────────────┐  │
│  │ Nginx Reverse Proxy / API Gateway (keepalive_timeout = 65s, proxy_read_timeout = 10s)     │  │
│  │                                                                                           │  │
│  │  ❌ KEEP-ALIVE COLLISION SCENARIO (When Tomcat keepalive = 20s):                          │  │
│  │     1. Connection idle for 20.000s.                                                      │  │
│  │     2. Tomcat sends TCP [FIN] packet to close socket.                                     │  │
│  │     3. Nginx simultaneously sends HTTP POST request on "reused" connection (within 65s).  │  │
│  │     4. Packets cross in flight! Tomcat receives POST on closing socket -> Sends TCP [RST]│  │
│  │     5. Nginx receives RST -> Fails request with HTTP 502 BAD GATEWAY!                     │  │
│  │                                                                                           │  │
│  │  ✅ HARDENED TIMEOUT & KEEP-ALIVE HIERARCHY:                                              │  │
│  │     Tomcat Keep-Alive (70s)  >  Nginx Keep-Alive (65s)  (NO PACKET COLLISIONS!)           │  │
│  │     Gateway Read Timeout (10s)  >  Spring Outbound RestClient Timeout (8s)               │  │
│  └────────────────────────────────────────┬──────────────────────────────────────────────────┘  │
│                                           │                                                     │
│                                           ▼ [HTTP Keep-Alive Socket Pool / Keep-Alive: 70s]     │
│  ┌───────────────────────────────────────────────────────────────────────────────────────────┐  │
│  │ Spring Boot Microservice (Tomcat keep-alive-timeout = 70s, forward-headers = framework)  │  │
│  │                                                                                           │  │
│  │  Outbound RestClient: Connect = 2s, Read = 8s                                             │  │
│  └────────────────────────────────────────┬──────────────────────────────────────────────────┘  │
│                                           │                                                     │
│                                           ▼ [Connect: 2s, Read: 8s]                             │
│  ┌───────────────────────────────────────────────────────────────────────────────────────────┐  │
│  │ Downstream Core Banking / Visa Payment Authorization API                                  │  │
│  └───────────────────────────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 5. How to Reproduce the Issues

#### ❌ Anti-Pattern 1: Keep-Alive Timeout Mismatch in `application.yml`
```yaml
# ❌ FATAL: Tomcat closes connections after 20s, but upstream proxy thinks they last 65s!
server:
  tomcat:
    keep-alive-timeout: 20000 # 20 seconds
```

#### ❌ Anti-Pattern 2: Nginx HTTP/1.0 Upstream Default (Missing Connection Header)
```nginx
# ❌ ANTI-PATTERN: Nginx defaults to HTTP/1.0 and Connection: close for upstreams!
location / {
    proxy_pass http://spring_boot_cluster;
    # Missing proxy_http_version 1.1;
    # Missing proxy_set_header Connection "";
}
```

#### ❌ Anti-Pattern 3: Unbounded Outbound RestTemplate
```java
// ❌ ANTI-PATTERN: RestTemplate without timeouts blocks Tomcat worker threads forever!
@Bean
public RestTemplate defaultRestTemplate() {
    return new RestTemplate(); // No connect or read timeout!
}
```

---

### 6. Evidence Collection & Diagnostic Probing

#### Method 1: Capture TCP RST Packets During 502 Spikes
```bash
tcpdump -nn -vvv -i any 'tcp[tcpflags] & (tcp-rst|tcp-fin) != 0 and port 8080'
```
**Diagnostic Output:**
```text
14:00:20.104210 IP 10.0.1.10.8080 > 10.0.1.5.42180: Flags [F.], seq 1842, ack 921, win 502
14:00:20.104280 IP 10.0.1.5.42180 > 10.0.1.10.8080: Flags [P.], seq 921:1450, ack 1842 (HTTP POST /payments)
14:00:20.104350 IP 10.0.1.10.8080 > 10.0.1.5.42180: Flags [R], seq 1843, win 0
```

#### Method 2: Inspect Nginx Upstream Response Times and Buffering
```bash
tail -f /var/log/nginx/access.log | awk '{print "status="$9, "req_time="$NF, "upstream_time="$(NF-1)}'
```
**Diagnostic Output:**
```text
status=502 req_time=0.002 upstream_time=0.001  <-- Instant 502 caused by TCP RST on reused socket!
status=504 req_time=60.001 upstream_time=60.000 <-- Gateway timeout cascade!
```

---

### 7. Step-by-Step Debugging Procedure

```text
Step 1: Check Nginx error.log for Exact Error Message.
        - "Connection reset by peer" -> Keep-Alive race condition.
        - "Connection timed out" -> proxy_connect_timeout too aggressive or network partition.
        - "upstream timed out" -> proxy_read_timeout exceeded by backend service.
        - "upstream response is buffered to a temporary file" -> Undersized proxy_buffers.

Step 2: Compare Keep-Alive Settings Across All Layers.
        Verify: Tomcat Keep-Alive (70s) > Nginx Upstream Keep-Alive (65s) > Edge/ALB Keep-Alive (60s).

Step 3: Audit Outbound HTTP Client Timeouts.
        Ensure every RestClient, WebClient, and Feign client specifies explicit connect and read timeouts.

Step 4: Tune Nginx Proxy Buffers.
        Set `proxy_buffer_size 16k; proxy_buffers 16 16k; proxy_max_temp_file_size 0;` to eliminate disk writes.

Step 5: Verify Forwarded Headers.
        Ensure `server.forward-headers-strategy=framework` is set in Spring Boot and Nginx passes `X-Forwarded-Proto $scheme`.
```

---

### 8. Technical Root Cause Deep-Dive

#### 1. The Anatomy of the TCP FIN/RST Race Condition
- Under HTTP/1.1 Persistent Connections, a client (Nginx) and server (Tomcat) maintain open TCP sockets to avoid handshake latency.
- Both sides maintain an idle timeout timer.
- If Tomcat's timer is 20s and Nginx's timer is 65s, at $t = 20.000\text{s}$, Tomcat decides to close the connection and transmits a TCP `FIN` packet.
- At $t = 20.001\text{s}$, before the `FIN` packet reaches Nginx over the network, Nginx receives an incoming client request and writes HTTP bytes onto the same TCP socket.
- When Tomcat receives HTTP data on a socket that is already transitioning to `CLOSE_WAIT` / `CLOSED`, the TCP specification (RFC 793) mandates sending a **TCP `RST`**.
- When Nginx receives `RST`, it aborts the request with `502 Bad Gateway`.
- **The Golden Rule:** The server (downstream) MUST ALWAYS have a LONGER keep-alive timeout than the client/proxy (upstream).

#### 2. The Timeout Inversion Disaster
When a downstream database or third-party API hangs:
- If `Spring RestClient Read Timeout = 30s`
- But `Nginx proxy_read_timeout = 10s`
- Nginx cuts the connection after 10s, returning `504 Gateway Timeout` to the user.
- **The Tomcat worker thread remains stuck executing for the remaining 20s!**
- Under high concurrency, all 200 Tomcat threads become clogged with "zombie" requests whose responses will never be delivered, starving new incoming traffic.

---

### 9. Production-Grade Fixes

#### ✅ Fix 1: Hardened `application.yml` Keep-Alive & Forwarded Headers
```yaml
server:
  port: 8080
  shutdown: graceful
  forward-headers-strategy: framework
  tomcat:
    # 70s is strictly greater than Nginx 65s keep-alive!
    connection-timeout: 70000
    keep-alive-timeout: 70000
    max-keep-alive-requests: 1000
```

#### ✅ Fix 2: Production `nginx.conf`
```nginx
upstream spring_boot_cluster {
    server 10.0.1.10:8080 max_fails=3 fail_timeout=10s;
    server 10.0.1.11:8080 max_fails=3 fail_timeout=10s;
    keepalive 64;
}

server {
    listen 80;
    server_name api.finflow.com;

    keepalive_timeout 65s;

    proxy_connect_timeout 2s;
    proxy_read_timeout 10s;
    proxy_send_timeout 10s;

    proxy_buffering on;
    proxy_buffer_size 16k;
    proxy_buffers 16 16k;
    proxy_max_temp_file_size 0;

    location / {
        proxy_pass http://spring_boot_cluster;
        proxy_http_version 1.1;
        proxy_set_header Connection "";

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host $host;
        proxy_set_header X-Forwarded-Port $server_port;

        proxy_next_upstream error timeout invalid_header http_502 http_503 http_504;
        proxy_next_upstream_tries 2;
        proxy_next_upstream_timeout 5s;
    }
}
```

#### ✅ Fix 3: Outbound Client Timeout Configuration (`GatewayProxyTimeoutConfig.java`)
```java
@Configuration
public class GatewayProxyTimeoutConfig {

    @Bean
    public RestClient outboundGatewayRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000); // 2s Connect
        factory.setReadTimeout(8000);    // 8s Read (< 10s Gateway Timeout)
        return RestClient.builder().requestFactory(factory).build();
    }
}
```

---

### 10. Verification

1. **Keep-Alive Validation Test:** Run `TimeoutHierarchyCalculatorTest.java` to verify that keep-alive race condition risks are detected mathematically.
2. **REST Endpoints & Buffer Test:** Run `GatewayProxyDiagnosticsControllerTest.java` to test timeout diagnostics, payment processing, and large payload generation.
3. **Forwarded Headers Test:** Run `ForwardedHeadersIntegrationTest.java` to verify that `X-Forwarded-Proto: https` rewrites the request scheme to HTTPS.
4. **Integration Test:** Run `Module18IntegrationTest.java` to verify Spring Boot context and Actuator health endpoints.

---

### 11. Prevention & Production Readiness

1. **The Keep-Alive Cardinal Rule:**
   $$\text{Downstream Server Keep-Alive} \ge \text{Upstream Proxy Keep-Alive} + 5\text{s}$$
2. **The Timeout Hierarchy Cardinal Rule:**
   $$\text{Downstream API Read Timeout} < \text{Spring Boot App Timeout} < \text{Gateway Timeout} < \text{Edge Timeout} < \text{Client Timeout}$$
3. **Prometheus Alerting Rule for 502/504 Rates:**
```yaml
- alert: HighGatewayErrorRate
  expr: sum(rate(nginx_http_requests_total{status=~"502|504"}[5m]))
        / sum(rate(nginx_http_requests_total[5m])) * 100 > 1.0
  for: 2m
  labels:
    severity: critical
  annotations:
    summary: "Nginx 502/504 error rate exceeded 1.0% (Current: {{ $value }}%)"
```

---

### 12. Interview & Production Questions

#### Interview Questions
1. **Q: Why does a Keep-Alive timeout mismatch between Nginx and Tomcat cause intermittent HTTP 502 Bad Gateway errors?**
   *Answer:* If Tomcat closes an idle connection before Nginx does, Nginx may dispatch a new request on that socket while Tomcat's `FIN` packet is in flight. Tomcat replies with a TCP `RST`, which Nginx interprets as an unexpected connection abort, returning `502 Bad Gateway`.
2. **Q: Why is `proxy_http_version 1.1` and `proxy_set_header Connection ""` mandatory in Nginx upstream configurations?**
   *Answer:* Nginx defaults to HTTP/1.0 and sends `Connection: close` to upstream servers. Setting `proxy_http_version 1.1` and clearing the `Connection` header enables persistent HTTP Keep-Alive pooling to upstream Spring Boot instances, avoiding costly TCP handshakes on every request.
3. **Q: What is the risk of having `Downstream API Read Timeout > Gateway Read Timeout`?**
   *Answer:* When the downstream API is slow, the Gateway aborts the request and returns `504 Gateway Timeout` to the client. However, the Spring Boot worker thread continues running until its own timeout expires, creating orphan processing threads that starve the thread pool under load.
4. **Q: What happens when an API response exceeds Nginx `proxy_buffers`?**
   *Answer:* Nginx buffers the overflow response to disk (`/var/cache/nginx/proxy_temp`). Under heavy concurrent load, synchronous disk writes cause massive latency spikes (P99 degradation) and can trigger `502 Bad Gateway` if disk space exhausts.
5. **Q: How does `server.forward-headers-strategy: framework` prevent OAuth2 / Security redirect bugs?**
   *Answer:* When TLS is terminated at a reverse proxy, the proxy sends `X-Forwarded-Proto: https`. Spring Boot's `ForwardedHeaderFilter` intercepts the request and overrides `request.getScheme()` and `request.isSecure()`, ensuring Spring Security generates HTTPS redirect URLs instead of internal HTTP URLs.

#### Production Incident Questions
1. **Incident:** 502 Bad Gateway errors occur exclusively on `POST` requests after 60 seconds of traffic inactivity. Why?
   *Diagnosis:* Keep-Alive race condition. Nginx does not retry failed `POST` requests by default (to avoid duplicate payment charges), so a TCP `RST` on a reused idle connection immediately returns 502. Fix: Set `server.tomcat.keep-alive-timeout: 70000` (greater than Nginx 65s).
2. **Incident:** After putting an Nginx proxy in front of Spring Boot, all users are redirected to `http://localhost:8080/login` during OAuth2 login. Why?
   *Diagnosis:* Missing `server.forward-headers-strategy: framework` and missing `proxy_set_header X-Forwarded-Proto $scheme`.
3. **Incident:** An endpoint returning a 5MB report causes Nginx to log `an upstream response is buffered to a temporary file` and latency jumps from 50ms to 3s. How do you resolve this?
   *Diagnosis:* Increase `proxy_buffers` (e.g. `16 32k`) or stream the response directly using chunked transfer encoding with `X-Accel-Buffering: no`.
4. **Incident:** An external KYC verification API experiences an outage, and within 30 seconds all internal microservices stop responding with 504. Why?
   *Diagnosis:* Unbounded outbound HTTP clients exhausted Tomcat worker threads. Fix: Configure strict connect/read timeouts (e.g. 2s/5s) and apply Resilience4j circuit breakers.
5. **Incident:** Why should `proxy_next_upstream` NEVER include `POST` or `PATCH` methods without an idempotency key?
   *Diagnosis:* If an upstream server successfully processes a payment but crashes before sending the response headers, retrying a non-idempotent `POST` will double-charge the customer.

#### Trick Questions
1. **Trick:** Does Nginx's `keepalive` directive inside an `upstream` block limit the maximum number of concurrent connections?
   *Answer:* No! `keepalive <N>` in an upstream block only defines the maximum number of *idle* keep-alive connections cached per worker process. Active connections can exceed this number.
2. **Trick:** If `proxy_read_timeout` is 10s, does that mean the entire HTTP request must finish within 10 seconds?
   *Answer:* No! `proxy_read_timeout` is the timeout *between two successive read operations*, not for the transmission of the entire response.
3. **Trick:** Can Spring Boot read `X-Forwarded-For` correctly if `server.forward-headers-strategy: native` is used with Tomcat?
   *Answer:* Yes, but `native` uses Tomcat's `RemoteIpValve`, which requires strict configuration of `internal-proxies` CIDR matching, whereas `framework` uses Spring's `ForwardedHeaderFilter` which is portable across all servlet containers.

---

*(Answers and detailed explanations are provided in the Master Answer Guide)*
