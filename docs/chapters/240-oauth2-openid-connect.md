---
chapter: 240
topic: OAuth2 & OpenID Connect — Authorization Server, Resource Server, Token Introspection
prerequisite_chapters: [10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200, 210, 220, 230]
reference_system_node: FinFlow OAuth2 Authorization Server & Payment Resource Server ↔ Spring Security 6.3 (RFC 6749, RFC 7636 PKCE, RFC 7662 Introspection, Scope vs Authority Mapping)
---

# Chapter 240: OAuth2 & OpenID Connect — Authorization Server, Resource Server, Token Introspection

## 1. Concept

In modern financial ecosystems, third-party software (e.g. accounting tools, POS hardware, analytical dashboards) must interact with banking APIs without ever obtaining user or merchant passwords. 

- **OAuth 2.0 (RFC 6749)** is a delegated authorization framework: it allows a client application to access resources on behalf of a resource owner with specific **scopes** (e.g. `payment:write`, `payout:read`).
- **OpenID Connect (OIDC Core 1.0)** is an identity layer built on top of OAuth 2.0: it authenticates users and provides verifiable identity assertions via **ID Tokens**.

```
+-------------------------------------------------------------------------------------------------+
|                                 The Golden Distinction: OIDC vs OAuth 2.0                       |
|                                                                                                 |
|  • OpenID Connect (OIDC) = IDENTITY ("Who is this user?"): Uses ID Tokens (JWT) for the client. |
|  • OAuth 2.0 = DELEGATED AUTHORIZATION ("What can this client do?"): Uses Access Tokens for     |
|    Resource Servers.                                                                            |
|  • RFC 7636 PKCE is MANDATORY: All Authorization Code flows must use Proof Key for Code         |
|    Exchange (PKCE) to prevent code interception attacks.                                        |
+-------------------------------------------------------------------------------------------------+
```

---

## 2. Internal Working

### OAuth 2.0 Roles & Architecture

```
                               +-------------------------------+
                               |         Resource Owner        |
                               |      (Merchant / Customer)    |
                               +-------------------------------+
                                          ▲         ▲
                                         /           \
                 1. User Consents & Auth/             \ 2. Redirects with
                                       /               \   Auth Code
                                      ▼                 ▼
+-----------------------+     +-------------------------------+
|      Client App       |────►|      Authorization Server     |
| (POS / Third-Party)   |     |    (FinFlow Auth Server)      |
+-----------------------+     +-------------------------------+
       │                             ▲                 ▲
       │ 3. Exchanges Code + PKCE    │                 │
       │    for Access Token         │                 │
       ▼                             │                 │
       │ 4. Bearer Access Token      │                 │
       ▼                             │                 │
+-------------------------------+    │                 │
|        Resource Server        |────┘                 │
|   (FinFlow Payment Service)   | 5. Token Introspect  │
+-------------------------------+    (RFC 7662 / JWKS) ┘
```

1. **Resource Owner**: The entity capable of granting access to a protected resource (e.g. the merchant).
2. **Client**: The application making protected resource requests on behalf of the Resource Owner (e.g. mobile POS app or backend accounting system).
3. **Authorization Server**: The server issuing access tokens to the client after successfully authenticating the resource owner (FinFlow Auth Gateway).
4. **Resource Server**: The server hosting the protected financial resources, capable of accepting and responding to protected resource requests using access tokens (FinFlow Payment Service).

---

### Authorization Code Grant with PKCE (RFC 7636)

The legacy Implicit Grant is deprecated and forbidden in modern security standards. **Authorization Code with PKCE (Proof Key for Code Exchange)** is required for all applications:

```
Client App                                       Authorization Server
    │                                                     │
    ├── 1. Generate code_verifier (128-byte random)        │
    ├── 2. Compute code_challenge = S256(code_verifier)   │
    │                                                     │
    ├── 3. GET /oauth2/authorize?                         │
    │      response_type=code&client_id=...&              │
    │      code_challenge=...&code_challenge_method=S256 ─►│
    │                                                     ├── Authenticate User
    │                                                     ├── Store code_challenge
    │◄── 4. Redirect with authorization_code ─────────────┘
    │
    ├── 5. POST /oauth2/token                             │
    │      grant_type=authorization_code&                 │
    │      code=...&code_verifier=... ───────────────────►│
    │                                                     ├── Verify: S256(verifier) == challenge
    │                                                     ├── Issue Access + ID Token
    │◄── 6. Returns Access Token + ID Token ──────────────┘
```

If an attacker intercepts the `authorization_code` from the redirect URI, they cannot exchange it for a token because they do not possess the unguessable `code_verifier` stored only in the legitimate client's memory.

---

### Token Introspection (RFC 7662) for Opaque Tokens

While stateless JWTs allow local signature verification, highly sensitive banking rails often issue **Opaque Reference Tokens** (random strings) requiring real-time revocation guarantees.

Downstream Resource Servers query the Authorization Server's `/oauth2/introspect` endpoint:

```http
POST /oauth2/introspect HTTP/1.1
Host: auth.finflow.io
Authorization: Basic cG9zLWNsaWVudDpzZWNyZXQ=
Content-Type: application/x-www-form-urlencoded

token=opaque_token_acme_pos_valid
```

```json
HTTP/1.1 200 OK
Content-Type: application/json

{
  "active": true,
  "scope": "payment:write payout:execute",
  "client_id": "pos-client-app-1",
  "sub": "USER-MERCHANT-ACME",
  "merchant_id": "MERCHANT_ACME",
  "authorities": ["SCOPE_payment:write", "SCOPE_payout:execute", "ROLE_MERCHANT_ADMIN"],
  "exp": 1755712800,
  "iat": 1755709200,
  "iss": "https://auth.finflow.io"
}
```

---

### Spring Security 6.3 Resource Server Architecture

In Spring Security 6.3:
1. `oauth2ResourceServer(oauth2 -> oauth2.jwt(...))` intercepts incoming requests with `BearerTokenAuthenticationFilter`.
2. `NimbusJwtDecoder` verifies the signature using the Authorization Server's JWKS.
3. A custom `JwtAuthenticationConverter` transforms OAuth2 `scope` claims into `SCOPE_` authorities and custom role claims into `ROLE_` authorities.
4. Method Security evaluates scopes and tenant parameters simultaneously:

```java
// Method Security: Scope + Role + Multi-Tenant Isolation
@PreAuthorize("hasAuthority('SCOPE_payout:execute') and hasRole('MERCHANT_ADMIN') and #merchantId == authentication.tokenAttributes['merchant_id']")
public PayoutResponse executeHighValuePayout(String merchantId, BigDecimal amount) { ... }
```

---

## 3. Enterprise Scenario: FinFlow App Marketplace & Payment Resource Server

In the **FinFlow Reference System**:

```
Third-Party App (QuickBooks / Clover POS)
       │
       ▼ (OAuth2 Authorization Code + PKCE)
FinFlow Auth Server (issues scoped Bearer Token)
       │
       ▼ (Authorization: Bearer eyJhbGciOiJSUzI1NiIs...)
FinFlow Payment Resource Server (20 Kubernetes pods)
       ├── JwtDecoder (validates signature via JWKS)
       ├── JwtScopeAndRoleConverter (maps scope -> SCOPE_*, finflow_roles -> ROLE_*)
       └── Method Security:
             ├── Standard Payout: Requires SCOPE_payment:write + merchant_id match
             └── High-Value Payout: Requires SCOPE_payout:execute + ROLE_MERCHANT_ADMIN + merchant_id match
```

---

## 4. Incorrect Implementation

Below is a vulnerable OAuth2 implementation typical of flawed third-party integrations:

```java
package com.finflow.chapter240.incorrect;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * INCORRECT IMPLEMENTATION:
 * 1. Checks scopes but ignores tenant boundaries (IDOR / Multi-tenant leak).
 * 2. Accepts tokens issued to any client without audience / client_id validation.
 */
@Service
public class InsecureResourceServerServiceIncorrect {

    /**
     * VULNERABILITY: Checks SCOPE_payment:write, but omits merchantId check!
     * Client authorized for Merchant A can submit payouts for Merchant B!
     */
    @PreAuthorize("hasAuthority('SCOPE_payment:write')")
    public void executePayoutUnsafe(String merchantId, BigDecimal amount) {
        // Dangerously executes payout without tenant isolation check!
    }
}
```

---

## 5. Production Incident

### Incident Timeline

| Time (UTC) | Event / Telemetry |
|---|---|
| **08:00:00** | A third-party point-of-sale vendor registers an integration with FinFlow using custom URI schemes on Android tablets. |
| **08:15:00** | The vendor configures the client without PKCE (`code_challenge_method`). |
| **08:30:00** | Malware on a shared merchant tablet registers the same custom URI scheme, intercepts the `authorization_code`, and exchanges it with the Auth Server. |
| **08:45:00** | The attacker uses the stolen access token to query `/api/v1/payouts/{merchantId}` across multiple competitor accounts. |
| **09:00:00** | The Resource Server's insecure `@PreAuthorize("hasAuthority('SCOPE_payment:write')")` permits the requests, leaking **$4.2M** in proprietary merchant settlement archives. |
| **09:30:00** | FinFlow SOC detects anomalous bulk data export from single client ID. SEV-0 Incident declared. |
| **09:45:00** | Security engineers revoke the rogue client credentials, mandate PKCE on all OAuth2 endpoints, and deploy multi-tenant SpEL checks across all Resource Server methods. |
| **10:30:00** | Auth server rejects all authorization requests missing PKCE `S256` challenges. Outage resolved. |

---

## 6. Logs & Diagnostics

### 1. Spring Security Insufficient Scope Access Denied Log
```text
2026-08-20T09:00:15.201Z WARN [payment-service,trace_id=8a7b6c,span_id=1d2e3f] 1 --- [http-nio-8080-exec-12] o.s.s.o.s.r.a.JwtAuthenticationProvider : Authentication failed: Access Denied: User possesses authorities [SCOPE_payment:read] but requires [SCOPE_payout:execute]

org.springframework.security.access.AccessDeniedException: Access Denied: missing scope SCOPE_payout:execute
	at org.springframework.security.access.prepost.PreInvocationAuthorizationAdviceVoter.vote(PreInvocationAuthorizationAdviceVoter.java:82)
	at com.finflow.chapter240.service.MerchantPayoutService.executeHighValuePayout(MerchantPayoutService.java:32)
```

### 2. Authorization Server PKCE Verification Failure Log
```text
2026-08-20T09:45:10.884Z WARN [auth-server,trace_id=334455,span_id=778899] 1 --- [http-nio-8080-exec-06] o.s.s.o.s.a.OAuth2TokenEndpointFilter    : Token exchange failed: Code verifier failed S256 challenge check for client_id='pos-client-app-1'
```

---

## 7. Root Cause Analysis

```
+-------------------------------------------------------------------------------------------------+
|                               OAuth2 Breach Root Cause Chain                                    |
|                                                                                                 |
|  1. Omission of PKCE (Proof Key for Code Exchange)                                              |
|     └── Allowed malware to intercept the authorization code and complete the token exchange.   |
|                                                                                                 |
|  2. Missing Multi-Tenant Parameter Validation in Resource Server                                |
|     └── Service checked OAuth2 scopes, but failed to ensure token belonged to target merchant.  |
|                                                                                                 |
|  3. Overly Broad Client Scopes                                                                  |
|     └── Client was granted full admin scopes rather than least-privilege scoped permissions.    |
|                                                                                                 |
|  4. Remediation: Enforce PKCE S256 + Scope & Tenant SpEL Binding                                |
|     └── Reject all auth requests missing PKCE; enforce tenant checks on all Resource Server APIs|
+-------------------------------------------------------------------------------------------------+
```

---

## 8. Debugging Process

```
[1. Token Inspection] Verify scopes in Bearer JWT via jwt.io or Spring Security Context
       │
[2. Trace Logging] Enable level.org.springframework.security.oauth2: TRACE
       │
[3. PKCE Verification] Ensure client sends code_challenge_method=S256 and code_verifier
       │
[4. Introspection Audit] For opaque tokens, measure latency of /oauth2/introspect endpoint
       │
[5. Rollout] Apply Scope + Tenant SpEL validation on all Resource Server endpoints
```

---

## 9. Correct Implementation

### 1. RFC 7636 PKCE Utility: `PkceUtil.java`

```java
package com.finflow.chapter240.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public final class PkceUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PkceUtil() {}

    public static String generateCodeVerifier() {
        byte[] codeVerifierBytes = new byte[64];
        SECURE_RANDOM.nextBytes(codeVerifierBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(codeVerifierBytes);
    }

    public static String generateCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public static boolean verifyCodeChallenge(String codeVerifier, String codeChallenge) {
        if (codeVerifier == null || codeChallenge == null) {
            return false;
        }
        String calculatedChallenge = generateCodeChallenge(codeVerifier);
        return MessageDigest.isEqual(
                calculatedChallenge.getBytes(StandardCharsets.UTF_8),
                codeChallenge.getBytes(StandardCharsets.UTF_8)
        );
    }
}
```

---

### 2. Custom Scope & Role Converter: `JwtScopeAndRoleConverter.java`

```java
package com.finflow.chapter240.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Component
public class JwtScopeAndRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        // 1. Convert OAuth2 scopes to SCOPE_ authorities
        Object scopeClaim = jwt.getClaims().get("scope");
        if (scopeClaim instanceof String scopes) {
            for (String scope : scopes.split(" ")) {
                if (!scope.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope.trim()));
                }
            }
        }

        // 2. Convert custom finflow_roles to ROLE_ authorities
        Object rolesClaim = jwt.getClaims().get("finflow_roles");
        if (rolesClaim instanceof Collection<?> rolesList) {
            for (Object role : rolesList) {
                String roleStr = role.toString().trim();
                String authorityName = roleStr.startsWith("ROLE_") ? roleStr : "ROLE_" + roleStr;
                authorities.add(new SimpleGrantedAuthority(authorityName));
            }
        }

        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
```

---

### 3. Resource Server Configuration: `SecurityConfig.java`

```java
package com.finflow.chapter240.config;

import com.finflow.chapter240.security.JwtScopeAndRoleConverter;
import com.finflow.chapter240.security.MockJwtTokenFactory;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
public class SecurityConfig {

    private final MockJwtTokenFactory tokenFactory;
    private final JwtScopeAndRoleConverter scopeAndRoleConverter;

    public SecurityConfig(MockJwtTokenFactory tokenFactory, JwtScopeAndRoleConverter scopeAndRoleConverter) {
        this.tokenFactory = tokenFactory;
        this.scopeAndRoleConverter = scopeAndRoleConverter;
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withPublicKey(tokenFactory.getPublicKey()).build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/public/**").permitAll()
                .requestMatchers("/oauth2/introspect").permitAll()
                .requestMatchers("/api/v1/payouts/**").authenticated()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(scopeAndRoleConverter)
                )
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\": \"invalid_token\", \"error_description\": \"" + authException.getMessage() + "\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\": \"insufficient_scope\", \"error_description\": \"Access denied: missing scope or role\"}");
                })
            );

        return http.build();
    }
}
```

---

### 4. Scope & Tenant Isolated Method Security Service: `MerchantPayoutService.java`

```java
package com.finflow.chapter240.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class MerchantPayoutService {

    public record PayoutResponse(String payoutId, String merchantId, BigDecimal amount, String status) {}

    /**
     * Standard Payout: Requires SCOPE_payment:write AND matching merchant_id claim in JWT!
     */
    @PreAuthorize("hasAuthority('SCOPE_payment:write') and #merchantId == authentication.tokenAttributes['merchant_id']")
    public PayoutResponse processPayout(String merchantId, BigDecimal amount) {
        return new PayoutResponse(
                "PO-" + UUID.randomUUID().toString().substring(0, 8),
                merchantId,
                amount,
                "PROCESSED"
        );
    }

    /**
     * High Value Payout: Requires SCOPE_payout:execute AND ROLE_MERCHANT_ADMIN AND matching merchant_id!
     */
    @PreAuthorize("hasAuthority('SCOPE_payout:execute') and hasRole('MERCHANT_ADMIN') and #merchantId == authentication.tokenAttributes['merchant_id']")
    public PayoutResponse executeHighValuePayout(String merchantId, BigDecimal amount) {
        return new PayoutResponse(
                "PO-HV-" + UUID.randomUUID().toString().substring(0, 8),
                merchantId,
                amount,
                "EXECUTED_HIGH_VALUE"
        );
    }
}
```

---

## 10. Performance Comparison

Benchmarked on 6,000 req/sec OAuth2 traffic in FinFlow environment.

| Metric | Remote Introspection per Request (RFC 7662) | Stateless JWT + JWKS Caching (Spring Security) |
|---|---|---|
| **Resource Server Verification Latency** | 12.80ms *(HTTP call to Auth Server)* | **0.09ms (Local cryptographic check)** |
| **Auth Server Network & CPU Load** | Extreme (6,000 introspection calls/sec) | **Near Zero (Periodic JWKS fetch only)** |
| **Instant Token Revocation Speed** | Instant | Requires TTL Expiration or Bloom Filter |
| **Authorization Code Security** | Vulnerable without PKCE | **Cryptographically Immune (PKCE S256)** |
| **Multi-Tenant Leak Prevention** | Coarse | **100% Isolated (SpEL Tenant Binding)** |

---

## 11. Best Practices

### The Do's
- **DO mandate PKCE (`S256`) on all Authorization Code flows**: Eliminates authorization code interception vulnerabilities.
- **DO map OAuth2 scopes to `SCOPE_` authorities in Spring Security**: Follows standard Spring Security OAuth2 naming conventions.
- **DO combine Scope checks with Tenant ID checks in `@PreAuthorize`**: Never assume possessing `SCOPE_payment:write` entitles access to all tenants.
- **DO use Client Credentials Grant for M2M communication**: Avoid using user credentials for service-to-service automation.
- **DO cache JWKS public keys in Resource Servers**: Avoid making HTTP requests to the Auth Server on every incoming API call.

### The Don'ts
- **DON'T use the Implicit Grant flow**: Inherently insecure; tokens are exposed in browser history and referrer headers.
- **DON'T treat ID Tokens as Access Tokens**: ID Tokens are meant for the Client application to establish identity; Resource Servers must only accept Access Tokens.
- **DON'T omit `code_challenge_method=S256`**: The plain method (`code_challenge_method=plain`) is insecure against eavesdropping.
- **DON'T issue wildcard scopes (`scope=*`)**: Adhere strictly to the Principle of Least Privilege.

---

## 12. Common Mistakes

### Mistake 1: Client ID Substitution in Multi-Tenant Environments
Allowing an authenticated client to query data belonging to a different tenant simply because the client holds valid scopes.
**Why it fails**: OAuth2 scopes define *capabilities* (`payment:write`), NOT *data tenancy*.
**Production Fix**: Bind `merchant_id` into the JWT claims and validate `#merchantId == authentication.tokenAttributes['merchant_id']` in SpEL.

### Mistake 2: Missing `SCOPE_` Prefix in Security Checks
```java
// SEVERE MISTAKE:
@PreAuthorize("hasAuthority('payment:write')")
// Spring Security's JwtGrantedAuthoritiesConverter automatically prefixes scopes with 'SCOPE_'!
// The above check looks for raw 'payment:write' and fails even when the client holds the scope!

// CORRECT USAGE:
@PreAuthorize("hasAuthority('SCOPE_payment:write')")
```

---

## 13. Interview Questions

### Junior Tier
**Q: What is the primary difference between an OAuth2 Access Token and an OpenID Connect (OIDC) ID Token?**
> **Answer**: 
> - An **Access Token** is an authorization credential issued to the Client application to access protected APIs on the Resource Server. The Resource Server parses the access token to check scopes and permissions.
> - An **ID Token** is a verifiable security assertion (always a JWT) issued specifically for the Client application to authenticate the user and retrieve identity information (`sub`, `email`, `name`). The Resource Server should not accept ID Tokens for API authorization.

### Mid Tier
**Q: How does PKCE (Proof Key for Code Exchange) prevent authorization code interception attacks?**
> **Answer**: In the Authorization Code flow, the client generates a high-entropy secret `code_verifier` and sends its SHA-256 hash (`code_challenge` under `S256`) to the Authorization Server. The Authorization Server saves the challenge and returns an `authorization_code` via browser redirect. When the client exchanges the code for tokens, it sends the raw `code_verifier`. The server computes `SHA256(code_verifier)` and verifies it matches the original challenge. Even if malware intercepts the `authorization_code`, it cannot redeem it without the secret `code_verifier`.

### Senior Tier
**Q: Compare JWT-based Access Tokens with RFC 7662 Token Introspection for Opaque Tokens.**
> **Answer**: 
> - **JWT Tokens**: Self-contained and stateless. Resource Servers verify signatures locally using the Auth Server's public JWKS in $< 0.1\text{ ms}$, eliminating network overhead. However, immediate token revocation is difficult without distributed blacklists.
> - **Opaque Tokens (RFC 7662)**: Random strings containing no claims. Resource Servers must call the Auth Server's `/oauth2/introspect` endpoint on every request. This provides instantaneous revocation and central auditing at the cost of increased network latency and heavy load on the Authorization Server.

### Staff Tier
**Q: How does Spring Security 6.3 configure and map OAuth2 JWT claims to GrantedAuthorities?**
> **Answer**: Spring Security's `oauth2ResourceServer().jwt()` uses a `JwtAuthenticationConverter`. By default, `JwtGrantedAuthoritiesConverter` extracts either the `scope` or `scp` claim and maps each space-delimited string into a `GrantedAuthority` with a `SCOPE_` prefix (e.g. `SCOPE_read`). To support custom role hierarchies, developers supply a custom `Converter<Jwt, AbstractAuthenticationToken>` that parses additional claims (e.g. `finflow_roles`) into `ROLE_` authorities and instantiates a custom `OAuth2MerchantPrincipal` containing domain attributes.

### Principal Tier
**Q: Design a high-security Sender-Constrained Token architecture using DPoP (RFC 9449) and mTLS (RFC 8705) for high-value banking APIs.**
> **Answer**: Standard Bearer tokens are bearer-vulnerable: anyone who steals the token string can use it. A Principal-level solution implements **Sender-Constrained Tokens**:
> 1. **DPoP (Demonstrating Proof-of-Possession, RFC 9449)**: The client generates an ephemeral public/private key pair. On every API request, the client signs a DPoP proof JWT containing the HTTP method, URL, timestamp, and unique `jti`. The Resource Server verifies that the Bearer token's embedded public key thumbprint (`cnf.jkt`) matches the public key that signed the DPoP proof header.
> 2. **Mutual TLS (mTLS, RFC 8705)**: For M2M communication, tokens are cryptographically bound to the client's mTLS certificate (`cnf.x5t#S256`). If an attacker intercepts the token, it cannot be used over any TLS connection that does not present the matching client certificate.

---

## 14. Hands-on Exercise

### Objective
Implement an RFC 7636 PKCE generator and validator:
1. Generate a 128-byte URL-safe code verifier.
2. Compute the SHA-256 S256 code challenge.
3. Validate verifier matching and tamper detection.

### Solution

```java
public class PkceExercise {

    public static String createVerifier() {
        byte[] bytes = new byte[64];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String createChallenge(String verifier) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    public static boolean verify(String verifier, String challenge) throws Exception {
        return MessageDigest.isEqual(
            createChallenge(verifier).getBytes(StandardCharsets.UTF_8),
            challenge.getBytes(StandardCharsets.UTF_8)
        );
    }
}
```

---

## 15. Advanced Challenge: OAuth2 Token Exchange (RFC 8693) for Microservice Delegation

### Enterprise Problem Statement
Implement a secure Token Exchange pattern where an edge API Gateway exchanges an incoming end-user token for a downscoped, audience-restricted internal microservice token.

### Enterprise Solution

```java
package com.finflow.chapter240.security;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class TokenExchangeService {

    public record TokenExchangeRequest(
            String subjectToken,
            String subjectTokenType, // urn:ietf:params:oauth:token-type:access_token
            String requestedAudience, // payment-ledger-service
            String requestedScope // ledger:write
    ) {}

    public record TokenExchangeResponse(
            String accessToken,
            String issuedTokenType,
            String tokenType,
            long expiresIn
    ) {}

    public TokenExchangeResponse exchangeToken(TokenExchangeRequest request, MockJwtTokenFactory tokenFactory) {
        // Issue a down-scoped internal token restricted specifically to requestedAudience
        String downscopedToken = tokenFactory.createToken(
                "INTERNAL-DELEGATED-ACTOR",
                "MERCHANT_ACME",
                request.requestedAudience(),
                request.requestedScope(),
                List.of("ROLE_INTERNAL_SERVICE")
        );

        return new TokenExchangeResponse(downscopedToken, "urn:ietf:params:oauth:token-type:access_token", "Bearer", 300);
    }
}
```

---

## 16. Production Checklist

Before approving any pull request involving OAuth2 or OpenID Connect:

- [ ] **PKCE (`S256`) Enforced**: Confirm Authorization Code flows require `code_challenge_method=S256`.
- [ ] **No Implicit Grant**: Verify the deprecated Implicit Grant is completely disabled.
- [ ] **Scope Authorities Prefixed with `SCOPE_`**: Ensure SpEL checks use `hasAuthority('SCOPE_xyz')`.
- [ ] **Tenant Parameter Isolation**: Confirm `@PreAuthorize` checks enforce `#tenantId == authentication.tokenAttributes['merchant_id']`.
- [ ] **JWKS Public Key Caching**: Ensure Resource Servers cache public keys with appropriate TTL.
- [ ] **Client Credentials for M2M**: Verify backend service-to-service communication uses Client Credentials Grant.
- [ ] **ID Tokens Separated from Access Tokens**: Confirm Resource Servers reject ID Tokens and only accept Access Tokens.
