---
chapter: 220
topic: Spring Security Fundamentals — Filter Chain Architecture, SecurityContext, Method Security
prerequisite_chapters: [10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200, 210]
reference_system_node: Payment Service & Merchant Admin API ↔ Spring Security 6.3 (SecurityFilterChain, SecurityContextHolder, Method Security @PreAuthorize, Multi-Tenant SpEL)
---

# Chapter 220: Spring Security Fundamentals — Filter Chain Architecture, SecurityContext, Method Security

## 1. Concept

In enterprise banking and payment infrastructures like FinFlow, security cannot be treated as a superficial gateway filter. Under the **Zero-Trust Security Model**, the internal network is assumed to be hostile. Every service, endpoint, and business method must independently enforce:
- **Authentication**: *Who is the caller?* (Verifying cryptographic API keys, OAuth2 JWTs, or mTLS client certificates).
- **Authorization**: *What actions is the caller permitted to perform?* (Enforcing Role-Based Access Control `RBAC`, Attribute-Based Access Control `ABAC`, and strict **Multi-Tenant Isolation**).

Spring Security 6.3 (in Spring Boot 3.3.x) provides a powerful, decoupled security architecture built on standard Servlet Filters and AOP method interceptors. 

However, misconfiguring Spring Security—such as relying on URL-only security without method-level tenant checks, misunderstanding `SecurityContextHolder` lifecycle across asynchronous thread pools, or misusing SpEL expressions—creates severe vulnerabilities: **cross-tenant data breaches**, **privilege escalations**, and **unauthorized funds transfers**.

```
+-------------------------------------------------------------------------------------------------+
|                                 Zero-Trust Architecture Mandate                                 |
|                                                                                                 |
|  1. Gateway Authorization is NOT enough: Internal microservices must never assume ingress      |
|     requests are pre-authorized.                                                                |
|  2. Defense-in-Depth: Enforce URL-level security in the SecurityFilterChain AND fine-grained    |
|     tenant-aware Method Security (@PreAuthorize) at the service layer.                         |
+-------------------------------------------------------------------------------------------------+
```

---

## 2. Internal Working

### The Servlet Filter Chain Architecture

Spring Security sits between the Servlet Container (Tomcat) and the Spring MVC `DispatcherServlet`:

```
HTTP Request ──► Servlet Container (Tomcat Filter Chain)
                       │
                       ▼
             DelegatingFilterProxy (Standard Servlet Filter)
                       │ (Delegates to Spring Bean)
                       ▼
               FilterChainProxy (Spring Security Master Filter)
                       │ (Matches Request URL against SecurityFilterChains)
                       ▼
              SecurityFilterChain (List of Spring Security Filters)
                       │
                       ├── 1. DisableEncodeUrlFilter
                       ├── 2. SecurityContextHolderFilter (Initializes/Restores SecurityContext)
                       ├── 3. HeaderWriterFilter (Adds X-Frame-Options, CSP, HSTS headers)
                       ├── 4. CorsFilter (Evaluates CORS pre-flight requests)
                       ├── 5. CsrfFilter (CSRF Token validation)
                       ├── 6. ApiKeyAuthenticationFilter / BearerTokenAuthenticationFilter
                       │      └── Invokes AuthenticationManager -> Sets SecurityContextHolder
                       ├── 7. RequestCacheAwareFilter
                       ├── 8. SecurityContextHolderAwareRequestFilter
                       ├── 9. AnonymousAuthenticationFilter (Assigns AnonymousAuthenticationToken)
                       ├── 10. ExceptionTranslationFilter (Catches Auth exceptions -> 401/403)
                       └── 11. AuthorizationFilter (Evaluates URL-level request matchers)
                       │
                       ▼
             DispatcherServlet ──► Controller ──► Service (@PreAuthorize Method Interceptor)
```

1. **`DelegatingFilterProxy`**: A standard Servlet filter registered in Tomcat's `web.xml` or Spring Boot auto-configuration. It has no security logic; it simply bridges the non-Spring Servlet container into the Spring `ApplicationContext` by delegating to the `filterChainProxy` bean.
2. **`FilterChainProxy`**: The master security filter. It maintains a list of `SecurityFilterChain` beans and routes each incoming request to the first matching chain based on `RequestMatcher` patterns.
3. **`SecurityContextHolderFilter`**: In Spring Security 6+, replaces the legacy `SecurityContextPersistenceFilter`. It restores the `SecurityContext` from the `SecurityContextRepository` (or creates an empty context for stateless requests) and guarantees `SecurityContextHolder.clearContext()` is called in a `finally` block when the request completes.

---

### `SecurityContextHolder` Internals & Lifecycle

`SecurityContextHolder` provides access to the current `SecurityContext`, which holds the `Authentication` token representing the authenticated principal:

```java
// Retrieving authenticated identity in application code:
SecurityContext context = SecurityContextHolder.getContext();
Authentication auth = context.getAuthentication();
MerchantPrincipal principal = (MerchantPrincipal) auth.getPrincipal();
```

#### ThreadLocal Storage Strategies

| Strategy | Property Value | Behavior & Concurrency Impact | Production Safety |
|---|---|---|---|
| **`MODE_THREADLOCAL`** *(Default)* | `MODE_THREADLOCAL` | Binds `SecurityContext` to the current `Thread` via `ThreadLocal<SecurityContext>`. | **Safe for standard request-response threads.** |
| **`MODE_INHERITABLETHREADLOCAL`** | `MODE_INHERITABLETHREADLOCAL` | Inherits context to child threads created via `new Thread()`. | **DANGEROUS with thread pools!** Leaks old contexts to recycled threads. |
| **`MODE_GLOBAL`** | `MODE_GLOBAL` | Single static context across all threads (JVM-wide). | Only for standalone desktop/CLI apps. |

> [!WARNING]
> **The Thread Pool Context Contamination Hazard**:
> In a thread pool (e.g. `@Async`, `ExecutorService`, `ForkJoinPool`), worker threads are never destroyed; they are recycled. If a thread executes Request A (authenticated as Merchant 1) and is returned to the pool without clearing `SecurityContextHolder`, a subsequent Request B running on that recycled thread can inherit Merchant 1's credentials, causing a catastrophic **cross-tenant authorization leak**!
> **Production Fix**: Wrap thread pool executors in Spring Security's `DelegatingSecurityContextAsyncTaskExecutor` or `DelegatingSecurityContextExecutorService`.

---

### The Authentication Architecture

```
                       ApiKeyAuthenticationFilter / Controller
                                      │
                                      ▼
             AuthenticationManager (Interface: authenticate(token))
                                      │
                                      ▼
             ProviderManager (Default implementation of AuthenticationManager)
                                      │
               ┌──────────────────────┴──────────────────────┐
               ▼ (Iterates Providers)                        ▼
ApiKeyAuthenticationProvider                   DaoAuthenticationProvider
 (Validates X-API-KEY header)                   (Validates username/password)
       │                                              │
       └──────────────────────┬───────────────────────┘
                              ▼
        Returns Authenticated Authentication Token (authenticated = true)
        Containing: Principal (User/Merchant), Credentials (cleared), Authorities (Roles/Perms)
```

---

### Method Security & Tenant-Isolated SpEL Expressions

While URL security (`authorizeHttpRequests`) validates path prefixes, **Method Security** (`@PreAuthorize`) enforces fine-grained domain logic, data ownership, and multi-tenant isolation.

When `@EnableMethodSecurity(prePostEnabled = true)` is active:
1. Spring creates CGLIB AOP proxies for annotated service beans.
2. Calls are intercepted by `AuthorizationManagerBeforeMethodInterceptor`.
3. Spring evaluates SpEL (Spring Expression Language) expressions against `MethodInvocation` parameters and the current `Authentication`:

```java
// MULTI-TENANT METHOD SECURITY PATTERNS:

// 1. Role + Tenant Isolation Match:
@PreAuthorize("hasRole('MERCHANT_ADMIN') and #merchantId == authentication.principal.merchantId")
public void refundPayment(String merchantId, String chargeId, BigDecimal amount) { ... }

// 2. Specific Permission + Tenant Match:
@PreAuthorize("hasAuthority('PAYMENT:WRITE') and #merchantId == authentication.principal.merchantId")
public ChargeResult executeCharge(String merchantId, BigDecimal amount) { ... }

// 3. Post-Invocation Domain Verification:
@PostAuthorize("returnObject.merchantId == authentication.principal.merchantId")
public PaymentRecord fetchPaymentDetails(UUID paymentId) { ... }
```

---

## 3. Enterprise Scenario: FinFlow Merchant Admin & Payment API

In the **FinFlow Reference Architecture**:

```
Client App / POS
       │
       ▼ (X-API-KEY: key_acme_admin_live)
Spring Cloud API Gateway
       │
       ▼ (Forwards Request)
Payment Service (20 pods in Kubernetes)
  ├── SecurityFilterChain (ApiKeyAuthenticationFilter)
  │     └── Validates API Key -> Sets MerchantPrincipal(merchantId="MERCHANT_ACME", roles=[...])
  └── Service Layer (Method Security)
        ├── POST /api/v1/payments/MERCHANT_ACME/charge  ──► @PreAuthorize: Approved!
        └── POST /api/v1/payments/MERCHANT_BETA/refund  ──► @PreAuthorize: 403 Forbidden! (Tenant Isolation)
```

- **Tenants**: 5,000+ distinct e-commerce merchants.
- **Roles & Permissions**:
  - `ROLE_MERCHANT_ADMIN`: Full access to charge, refund, and view reports for their own merchant.
  - `ROLE_MERCHANT_STAFF`: Limited to charging and reading transactions for their own merchant (`PAYMENT:WRITE`, `PAYMENT:READ`).
  - `ROLE_AUDITOR`: Global read-only compliance access across all merchant audit logs.

---

## 4. Incorrect Implementation

Below is a vulnerable service typical of architectures where developers relied solely on naive role checks without enforcing tenant boundaries:

```java
package com.finflow.chapter220.incorrect;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * INCORRECT IMPLEMENTATION:
 * 1. Missing tenant validation in @PreAuthorize: Any authenticated MERCHANT_ADMIN
 *    can refund ANY other merchant's payments!
 * 2. Hardcoded role prefix errors.
 */
@Service
public class VulnerablePaymentServiceIncorrect {

    /**
     * VULNERABILITY: Checks role, but ignores #merchantId!
     * If Merchant ACME Admin calls this endpoint passing merchantId="MERCHANT_BETA",
     * Spring Security approves the request, draining Beta's funds!
     */
    @PreAuthorize("hasRole('MERCHANT_ADMIN')") // FATAL SECURITY FLAW!
    public void refundPaymentUnsafe(String merchantId, String chargeId, BigDecimal amount) {
        // Business logic refunds money without checking who owns the merchantId!
    }

    /**
     * VULNERABILITY: Mistake with 'ROLE_' prefix in hasRole().
     * hasRole('ROLE_ADMIN') checks for authority 'ROLE_ROLE_ADMIN', locking out real admins!
     */
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public void executeAdminAction() {
        // Unreachable for users with standard ROLE_ADMIN authority
    }
}
```

---

## 5. Production Incident

### Incident Timeline

| Time (UTC) | Event / Telemetry |
|---|---|
| **02:00:00** | A rogue merchant ("Merchant Alpha") notices that the payment service API uses path variables (`/api/v1/payments/{merchantId}/refund`). |
| **02:15:00** | Alpha uses their valid API key (`key_alpha_admin`) to send refund requests substituting competitor merchant IDs: `POST /api/v1/payments/MERCHANT_COMPETITOR_BETA/refund`. |
| **02:15:05** | API Gateway passes the request because Alpha provided a valid API key. |
| **02:15:10** | Backend service's naive `@PreAuthorize("hasRole('MERCHANT_ADMIN')")` approves the call because Alpha has `ROLE_MERCHANT_ADMIN`. |
| **02:30:00** | Alpha executes 480 unauthorized cross-tenant refunds, draining **$1.9M** from competitor merchant escrow accounts into fraudulent test cards. |
| **02:45:00** | Competitor Beta's automated reconciliation alerts fire on negative balance anomalies. PagerDuty SEV-0 Security Incident triggered. |
| **03:00:00** | Security engineers inspect access logs, discover the cross-tenant bypass, revoke Alpha's API keys, and freeze payout gateways. |
| **03:20:00** | Emergency hotfix deployed updating all service methods to enforce `#merchantId == authentication.principal.merchantId`. |
| **03:40:00** | Payout processing resumed with 100% tenant-isolated validation. |

---

## 6. Logs & Diagnostics

### 1. Spring Security Authorization Denial Trace (`AuthorizationDeniedEvent`)
```text
2026-08-20T03:20:12.441Z WARN [payment-service,trace_id=9a8b7c,span_id=1d2e3f] 1 --- [http-nio-8080-exec-22] o.s.s.c.a.MethodSecurityInterceptor     : An AccessDeniedException was thrown while evaluating method: public com.finflow.chapter220.service.MerchantPaymentService$RefundResult com.finflow.chapter220.service.MerchantPaymentService.executeRefund(java.lang.String,java.lang.String,java.math.BigDecimal)

org.springframework.security.access.AccessDeniedException: Access Denied: SpEL expression 'hasRole('MERCHANT_ADMIN') and #merchantId == authentication.principal.merchantId' evaluated to false
	at org.springframework.security.access.prepost.PreInvocationAuthorizationAdviceVoter.vote(PreInvocationAuthorizationAdviceVoter.java:82)
	at org.springframework.security.access.intercept.AbstractSecurityInterceptor.beforeInvocation(AbstractSecurityInterceptor.java:240)
	at org.springframework.security.access.intercept.aopalliance.MethodSecurityInterceptor.invoke(MethodSecurityInterceptor.java:64)
	at com.finflow.chapter220.service.MerchantPaymentService.executeRefund(MerchantPaymentService.java:38)
```

### 2. Custom Security Filter 401 Unauthorized Response
```json
{
  "error": "Unauthorized",
  "message": "Invalid or unrecognized API key",
  "timestamp": "2026-08-20T03:21:05.102Z",
  "path": "/api/v1/payments/MERCHANT_ACME/charge"
}
```

---

## 7. Root Cause Analysis

```
+-------------------------------------------------------------------------------------------------+
|                               Cross-Tenant Breach Root Cause Chain                              |
|                                                                                                 |
|  1. Relying Solely on Coarse Role Checks in @PreAuthorize                                       |
|     └── Service checked hasRole('MERCHANT_ADMIN'), but omitted tenant ownership validation.     |
|                                                                                                 |
|  2. Blind Trust of Client-Supplied URL Path Variables                                          |
|     └── Method accepted String merchantId from URL without comparing it against the Principal.  |
|                                                                                                 |
|  3. Gateway Perimeter Assumption Fallacy                                                       |
|     └── Engineers assumed the API Gateway would block cross-tenant calls; however, Gateway      |
|         only performed JWT verification and did not inspect specific business payload targets.  |
|                                                                                                 |
|  4. Remediation: Zero-Trust Method-Level Tenant SpEL Enforcement                                |
|     └── #merchantId == authentication.principal.merchantId guarantees no cross-tenant actions.  |
+-------------------------------------------------------------------------------------------------+
```

---

## 8. Debugging Process

```
[1. SIEM Log Triage] Search for mismatched principal.merchantId vs request.path.merchantId
       │
[2. Security Tracing] Enable logging: level.org.springframework.security: TRACE
       │
[3. Debug Filter Inspection] Verify filter execution order via SecurityDebugFilter
       │
[4. SpEL Testing] Write unit tests with @WithMockUser / custom SecurityMockMvcRequestPostProcessors
       │
[5. Rollout] Deploy strict SpEL tenant matching rules on all mutable service methods
```

### Step 1: Enable Spring Security Trace Logging
```yaml
logging:
  level:
    org.springframework.security: TRACE
    org.springframework.security.access: TRACE
```
*Traces reveal the exact SpEL expression evaluated, the values extracted from `#methodParameters`, and the contents of `authentication.principal`.*

---

## 9. Correct Implementation

### 1. Authenticated Principal Model: `MerchantPrincipal.java`

```java
package com.finflow.chapter220.model;

import org.springframework.security.core.GrantedAuthority;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class MerchantPrincipal implements Serializable {

    private final String keyId;
    private final String merchantId;
    private final Set<GrantedAuthority> authorities;

    public MerchantPrincipal(String keyId, String merchantId, Collection<? extends GrantedAuthority> authorities) {
        this.keyId = keyId;
        this.merchantId = merchantId;
        this.authorities = Collections.unmodifiableSet(Set.copyOf(authorities));
    }

    public String getKeyId() { return keyId; }
    public String getMerchantId() { return merchantId; }
    public Set<GrantedAuthority> getAuthorities() { return authorities; }
}
```

### 2. Custom API Key Filter & Provider

#### `ApiKeyAuthenticationToken.java`
```java
package com.finflow.chapter220.security;

import com.finflow.chapter220.model.MerchantPrincipal;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final String apiKey;
    private final MerchantPrincipal principal;

    public ApiKeyAuthenticationToken(String apiKey) {
        super(null);
        this.apiKey = apiKey;
        this.principal = null;
        setAuthenticated(false);
    }

    public ApiKeyAuthenticationToken(MerchantPrincipal principal, String apiKey, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.apiKey = apiKey;
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override public Object getCredentials() { return apiKey; }
    @Override public Object getPrincipal() { return principal; }
}
```

#### `ApiKeyAuthenticationProvider.java`
```java
package com.finflow.chapter220.security;

import com.finflow.chapter220.model.MerchantPrincipal;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ApiKeyAuthenticationProvider implements AuthenticationProvider {

    private static final Map<String, MerchantMetadata> VALID_KEYS = Map.of(
            "key_acme_admin_live", new MerchantMetadata("KEY-1", "MERCHANT_ACME", Set.of("ROLE_MERCHANT_ADMIN", "PAYMENT:WRITE", "PAYMENT:READ", "PAYMENT:REFUND")),
            "key_acme_staff_live", new MerchantMetadata("KEY-2", "MERCHANT_ACME", Set.of("ROLE_MERCHANT_STAFF", "PAYMENT:WRITE", "PAYMENT:READ")),
            "key_beta_admin_live", new MerchantMetadata("KEY-3", "MERCHANT_BETA", Set.of("ROLE_MERCHANT_ADMIN", "PAYMENT:WRITE", "PAYMENT:READ", "PAYMENT:REFUND")),
            "key_auditor_global",  new MerchantMetadata("KEY-4", "GLOBAL_AUDIT",  Set.of("ROLE_AUDITOR", "PAYMENT:READ"))
    );

    record MerchantMetadata(String keyId, String merchantId, Set<String> rolesAndPermissions) {}

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String apiKey = (String) authentication.getCredentials();

        if (apiKey == null || !VALID_KEYS.containsKey(apiKey)) {
            throw new BadCredentialsException("Invalid or unrecognized API key");
        }

        MerchantMetadata meta = VALID_KEYS.get(apiKey);
        List<SimpleGrantedAuthority> authorities = meta.rolesAndPermissions().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        MerchantPrincipal principal = new MerchantPrincipal(meta.keyId(), meta.merchantId(), authorities);
        return new ApiKeyAuthenticationToken(principal, apiKey, authorities);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return ApiKeyAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
```

---

### 3. Security Configuration: `SecurityConfig.java`

```java
package com.finflow.chapter220.config;

import com.finflow.chapter220.security.ApiKeyAuthenticationFilter;
import com.finflow.chapter220.security.ApiKeyAuthenticationProvider;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
public class SecurityConfig {

    private final ApiKeyAuthenticationProvider apiKeyAuthenticationProvider;

    public SecurityConfig(ApiKeyAuthenticationProvider apiKeyAuthenticationProvider) {
        this.apiKeyAuthenticationProvider = apiKeyAuthenticationProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(List.of(apiKeyAuthenticationProvider));
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
        ApiKeyAuthenticationFilter apiKeyFilter = new ApiKeyAuthenticationFilter(authenticationManager);

        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/public/**").permitAll()
                .requestMatchers("/api/v1/payments/**").authenticated()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"Full authentication required\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\": \"Forbidden\", \"message\": \"Access denied: insufficient permissions or tenant mismatch\"}");
                })
            )
            .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

---

### 4. Tenant-Isolated Method Security Service: `MerchantPaymentService.java`

```java
package com.finflow.chapter220.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class MerchantPaymentService {

    public record ChargeResult(String chargeId, String merchantId, BigDecimal amount, String status) {}
    public record RefundResult(String refundId, String chargeId, String merchantId, BigDecimal amount, String status) {}

    /**
     * Tenant-Isolated Method Security for Charge Execution:
     * Requires PAYMENT:WRITE authority AND matching merchantId from the authenticated principal!
     */
    @PreAuthorize("hasAuthority('PAYMENT:WRITE') and #merchantId == authentication.principal.merchantId")
    public ChargeResult executeCharge(String merchantId, BigDecimal amount) {
        return new ChargeResult(
                "CHG-" + UUID.randomUUID().toString().substring(0, 8),
                merchantId,
                amount,
                "CHARGED"
        );
    }

    /**
     * Tenant-Isolated Role-Based Method Security for Refunds:
     * Requires ROLE_MERCHANT_ADMIN AND matching merchantId from the authenticated principal!
     */
    @PreAuthorize("hasRole('MERCHANT_ADMIN') and #merchantId == authentication.principal.merchantId")
    public RefundResult executeRefund(String merchantId, String chargeId, BigDecimal amount) {
        return new RefundResult(
                "REF-" + UUID.randomUUID().toString().substring(0, 8),
                chargeId,
                merchantId,
                amount,
                "REFUNDED"
        );
    }

    /**
     * Global Auditor or Merchant Read Permission:
     */
    @PreAuthorize("hasRole('AUDITOR') or (hasAuthority('PAYMENT:READ') and #merchantId == authentication.principal.merchantId)")
    public String getAuditSummary(String merchantId) {
        return "Audit report generated for: " + merchantId;
    }
}
```

---

## 10. Performance Comparison

Benchmarked on 4,000 req/sec payment traffic on FinFlow infrastructure.

| Metric | Without Method Security (URL only) | With Tenant-Isolated Method Security (@PreAuthorize) |
|---|---|---|
| **Filter Chain Overhead** | 0.35ms (illustrative) | **0.38ms** (illustrative) |
| **SpEL Method Evaluation Overhead** | 0.00ms | **0.04ms (Negligible CGLIB proxy)** |
| **Cross-Tenant Data Leak Rate** | **High Risk (100% vulnerable)**| **0.0% (Mathematically isolated)** |
| **Unauthorized Action Prevention**| Coarse (Role-level only) | **Fine-grained (Role + Tenant + Permission)** |
| **Memory Allocation per Request** | < 1.2 KB | **< 1.4 KB** |
| **False Positive Access Denials** | 0.0% | **0.0%** |

---

## 11. Best Practices

### The Do's
- **DO use `@EnableMethodSecurity(prePostEnabled = true)`**: Modern replacement for deprecated `@EnableGlobalMethodSecurity`.
- **DO enforce multi-tenant parameter checks in `@PreAuthorize`**: Always compare `#tenantId == authentication.principal.tenantId`.
- **DO use `SessionCreationPolicy.STATELESS` for REST APIs**: Prevents server memory allocation for HTTP sessions and disables unwanted JSESSIONID cookies.
- **DO configure custom `AuthenticationEntryPoint` and `AccessDeniedHandler`**: Return structured RFC 9457 JSON problem details rather than default HTML redirect pages.
- **DO propagate `SecurityContext` across thread pools**: Use `DelegatingSecurityContextAsyncTaskExecutor`.

### The Don'ts
- **DON'T include the `ROLE_` prefix in `hasRole('ADMIN')`**: `hasRole('ROLE_ADMIN')` checks for `ROLE_ROLE_ADMIN` and will always fail.
- **DON'T use `MODE_INHERITABLETHREADLOCAL` with worker thread pools**: Causes context contamination across recycled threads.
- **DON'T swallow `AccessDeniedException` in service try-catches**: Prevents Spring Security from returning HTTP 403 Forbidden to the client.
- **DON'T rely on URL matching alone for authorization**: Endpoints with dynamic query/path parameters are easily bypassed without Method Security.

---

## 12. Common Mistakes

### Mistake 1: The `ROLE_` Prefix Trap
```java
// SEVERE CONFIGURATION ERROR:
@PreAuthorize("hasRole('ROLE_MERCHANT_ADMIN')") 
// Spring Security's hasRole() automatically prepends 'ROLE_'!
// This actually evaluates: hasAuthority('ROLE_ROLE_MERCHANT_ADMIN') -> ALWAYS FAILS!

// CORRECT USAGE:
@PreAuthorize("hasRole('MERCHANT_ADMIN')")
// Or explicitly:
@PreAuthorize("hasAuthority('ROLE_MERCHANT_ADMIN')")
```

### Mistake 2: Missing SecurityContext in `@Async` Calls
```java
@Async
public void sendAuditNotification() {
    // SecurityContextHolder.getContext().getAuthentication() returns NULL!
    // @Async executes on a different thread where ThreadLocal is empty!
}
```
**Production Fix**: Configure `SecurityContextPropagatingExecutor`:
```java
@Bean
public AsyncTaskExecutor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.initialize();
    return new DelegatingSecurityContextAsyncTaskExecutor(executor);
}
```

---

## 13. Interview Questions

### Junior Tier
**Q: What is the difference between Authentication and Authorization in Spring Security?**
> **Answer**: Authentication verifies the identity of the caller (*"Who are you?"*), validating credentials like passwords, API keys, or JWT tokens, and creating an authenticated `Authentication` object. Authorization determines whether the verified identity has permission to access a specific resource or perform an operation (*"Are you allowed to do this?"*), evaluating granted roles and authorities against URL matchers or `@PreAuthorize` rules.

### Mid Tier
**Q: How does Spring Security intercept requests, and what is the role of `DelegatingFilterProxy` and `FilterChainProxy`?**
> **Answer**: `DelegatingFilterProxy` is a standard Servlet filter registered in the servlet container (Tomcat). It does not contain security logic; it delegates request processing to a Spring Bean called `FilterChainProxy`. `FilterChainProxy` is the master entry point of Spring Security, holding one or more `SecurityFilterChain` instances. It matches the incoming HTTP request against configured `RequestMatcher` patterns and invokes the appropriate chain of security filters (e.g. CORS, CSRF, Authentication, Exception Translation, Authorization).

### Senior Tier
**Q: Why is URL-level security (`authorizeHttpRequests`) insufficient in a multi-tenant SaaS application, and how does Method Security resolve this?**
> **Answer**: URL-level security typically checks only that a user is authenticated or holds a broad role (e.g., `hasRole('MERCHANT_ADMIN')`). It cannot easily inspect method arguments, domain entity ownership, or request payloads. If two merchants both have `ROLE_MERCHANT_ADMIN`, URL security alone allows Merchant A to access `/api/v1/merchants/MERCHANT_B/refund`. Method Security (`@PreAuthorize`) evaluates SpEL expressions at runtime with access to both method arguments and the authenticated principal: `@PreAuthorize("hasRole('MERCHANT_ADMIN') and #merchantId == authentication.principal.merchantId")`, guaranteeing strict tenant isolation.

### Staff Tier
**Q: Explain the lifecycle of `SecurityContextHolder` in a servlet application and the risks associated with thread pools.**
> **Answer**: By default, `SecurityContextHolder` uses `ThreadLocalSecurityContextHolderStrategy`. The `SecurityContextHolderFilter` restores the `SecurityContext` at the beginning of a request and guarantees it is cleared (`SecurityContextHolder.clearContext()`) in a `finally` block when the request finishes. In thread pools (e.g., `@Async` or custom `ExecutorService`), worker threads are recycled. If a thread is not cleared or inherits context via `MODE_INHERITABLETHREADLOCAL`, subsequent tasks executing on that recycled thread inherit stale credentials, causing severe cross-tenant security leaks. Production systems must use `DelegatingSecurityContextAsyncTaskExecutor` to propagate and clean contexts safely.

### Principal Tier
**Q: Design an Attribute-Based Access Control (ABAC) architecture for high-concurrency payment authorization that dynamically evaluates transaction limits, merchant risk scores, and device velocity.**
> **Answer**: A Principal-level architecture implements a custom **`PermissionEvaluator` with a Policy Decision Point (PDP)**:
> 1. **Custom SpEL Permission Evaluator**: Implement `PermissionEvaluator.hasPermission(Authentication auth, Object targetDomainObject, Object permission)`.
> 2. **Policy Enforcement Point (PEP)**: Annotate sensitive methods with `@PreAuthorize("hasPermission(#paymentIntent, 'AUTHORIZE')")`.
> 3. **Dynamic Rule Engine (PDP)**: The `PermissionEvaluator` extracts the authenticated `MerchantPrincipal`, inspects dynamic risk attributes (merchant tier, current 24-hour transaction velocity from Redis, geolocation anomaly flags), and evaluates whether the transaction amount exceeds the merchant's real-time risk limit.
> 4. **Audit Trail**: Every access evaluation (approved or denied) publishes an immutable `SecurityAuditEvent` to Kafka for compliance audit logging.

---

## 14. Hands-on Exercise

### Objective
In FinFlow, implement a multi-tenant payment refund endpoint that:
1. Validates custom `X-API-KEY` headers via `ApiKeyAuthenticationFilter`.
2. Enforces Method Security ensuring only `ROLE_MERCHANT_ADMIN` belonging to the specific merchant can refund.
3. Verifies cross-tenant attempts return HTTP 403 Forbidden.

### Solution

#### Step 1: Service Layer with SpEL Validation
```java
@Service
public class PayoutRefundService {

    @PreAuthorize("hasRole('MERCHANT_ADMIN') and #merchantId == authentication.principal.merchantId")
    public RefundResult processRefund(String merchantId, String chargeId, BigDecimal amount) {
        return new RefundResult(UUID.randomUUID().toString(), chargeId, merchantId, amount, "REFUNDED");
    }
}
```

#### Step 2: Security Filter Chain Configuration
```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AuthenticationManager authManager) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/v1/payments/**").authenticated()
                .anyRequest().permitAll()
            )
            .addFilterBefore(new ApiKeyAuthenticationFilter(authManager), UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

---

## 15. Advanced Challenge: Custom Dynamic `PermissionEvaluator`

### Enterprise Problem Statement
Implement a custom `PermissionEvaluator` bean that dynamically inspects a `PaymentTransaction` entity and allows refund operations only if:
1. The user owns the merchant account, AND
2. The refund amount does not exceed the merchant's per-transaction limit ($10,000.00).

### Enterprise Solution

```java
package com.finflow.chapter220.security;

import com.finflow.chapter220.model.MerchantPrincipal;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.math.BigDecimal;

@Component
public class PaymentPermissionEvaluator implements PermissionEvaluator {

    public record PaymentRefundRequest(String merchantId, BigDecimal amount) {}

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (authentication == null || !(targetDomainObject instanceof PaymentRefundRequest request)) {
            return false;
        }

        if (!(authentication.getPrincipal() instanceof MerchantPrincipal principal)) {
            return false;
        }

        // Rule 1: Tenant Ownership Match
        if (!principal.getMerchantId().equals(request.merchantId())) {
            return false;
        }

        // Rule 2: Transaction Limit Policy Check
        if ("REFUND".equals(permission)) {
            return request.amount().compareTo(BigDecimal.valueOf(10000.00)) <= 0;
        }

        return false;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        return false;
    }
}
```

---

## 16. Production Checklist

Before approving any pull request involving security and authorization:

- [ ] **Method-Level Tenant Checks**: Confirm all service methods modifying tenant resources include `#tenantId == authentication.principal.tenantId` in `@PreAuthorize`.
- [ ] **No `ROLE_` Prefix in `hasRole()`**: Verify SpEL expressions use `hasRole('ADMIN')`, not `hasRole('ROLE_ADMIN')`.
- [ ] **Stateless Session Management**: Verify REST APIs configure `SessionCreationPolicy.STATELESS`.
- [ ] **CSRF Disabled Only for Stateless APIs**: Confirm CSRF is disabled only when session cookies are not used for authentication.
- [ ] **Context Propagation on Async Executors**: Verify all thread pool beans are wrapped in `DelegatingSecurityContextAsyncTaskExecutor`.
- [ ] **Explicit 401 & 403 Handlers**: Ensure custom `AuthenticationEntryPoint` and `AccessDeniedHandler` return structured RFC 9457 JSON responses.
- [ ] **Method Security Enabled**: Confirm `@EnableMethodSecurity(prePostEnabled = true)` is present on security configuration classes.
