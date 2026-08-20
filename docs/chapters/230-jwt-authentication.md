---
chapter: 230
topic: JWT Authentication — Token Lifecycle, Refresh Tokens, Key Rotation, Common Vulnerabilities
prerequisite_chapters: [10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200, 210, 220]
reference_system_node: Payment Service & Merchant Auth Gateway ↔ Spring Security 6.3 / Nimbus JOSE (RS256 Asymmetric Verification, JWKS Key Rotation, Refresh Token Rotation with Reuse Detection)
---

# Chapter 230: JWT Authentication — Token Lifecycle, Refresh Tokens, Key Rotation, Common Vulnerabilities

## 1. Concept

In high-throughput distributed microservice architectures like FinFlow, centralized session storage (e.g. database lookups on every HTTP request) introduces latency bottlenecks and single points of failure. **JSON Web Tokens (JWT, RFC 7519)** provide a stateless, cryptographically signed mechanism for identity propagation across microservices.

A JWT consists of three Base64URL-encoded parts separated by periods:

$$\text{JWT} = \underbrace{\text{Header}}_{\text{Algorithm \& Key ID}} \;.\; \underbrace{\text{Payload}}_{\text{Claims \& Identity}} \;.\; \underbrace{\text{Signature}}_{\text{Cryptographic Proof}}$$

```
+-------------------------------------------------------------------------------------------------+
|                                 The Golden Rules of Enterprise JWT                              |
|                                                                                                 |
|  1. JWT is SIGNED, NOT ENCRYPTED: Never store raw passwords, card numbers (PAN), or sensitive  |
|     PII in standard JWT claims. Any client can decode Base64URL payload strings!               |
|  2. Asymmetric over Symmetric: Use RS256 or ES256 in microservices. Only the Auth Gateway     |
|     holds the Private Key; downstream Resource Servers verify tokens using the Public Key JWKS. |
|  3. Short Access Tokens + Refresh Token Rotation: Access tokens must expire in 5–15 minutes.    |
|     Refresh tokens must rotate on every use with Automatic Reuse / Replay Detection.            |
+-------------------------------------------------------------------------------------------------+
```

---

## 2. Internal Working

### Asymmetric Cryptographic Signing (RS256 / JWKS)

In enterprise setups, symmetric signing (`HS256`) requires every microservice to share the exact same secret key. If a single microservice is compromised, an attacker can forge tokens for the entire ecosystem.

**Asymmetric Signing (`RS256` / `ES256`)** eliminates this vulnerability:

```
┌─────────────────────────────────────────────────────────────┐
│                FinFlow Auth Gateway (Issuer)                │
│  - Holds RSA Private Key (2048-bit)                         │
│  - Signs JWT Access Tokens: JWS Header + Claims + Sign()    │
│  - Exposes Public Key via JWKS: GET /.well-known/jwks.json   │
└─────────────────────────────────────────────────────────────┘
                             │
                             ▼ (Issues Bearer Token: eyJhbGciOiJSUzI1NiIs...)
┌─────────────────────────────────────────────────────────────┐
│                 Client App / Merchant POS                   │
└─────────────────────────────────────────────────────────────┘
                             │
                             ▼ (Authorization: Bearer <token>)
┌─────────────────────────────────────────────────────────────┐
│             Payment Service (Resource Server)               │
│  - Fetches Public Key from JWKS (Cached with TTL)           │
│  - Cryptographically verifies RS256 Signature               │
│  - Never has access to Private Key! Cannot forge tokens!    │
└─────────────────────────────────────────────────────────────┘
```

#### JSON Web Key Set (JWKS, RFC 7517)
The Auth Gateway exposes public keys at `/.well-known/jwks.json`:
```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "alg": "RS256",
      "kid": "finflow-rsa-2026-v1",
      "n": "u1...[RSA Modulus]...",
      "e": "AQAB"
    }
  ]
}
```
Downstream Resource Servers inspect the token's `kid` (Key ID) header, retrieve the corresponding public key from their in-memory JWKS cache, and verify the signature in $< 0.1\text{ ms}$ without making network calls.

---

### Refresh Token Rotation (RTR) with Automatic Reuse Detection

Because access tokens are stateless and cannot be easily revoked without distributed blacklists, their lifetime is kept very short (e.g. 15 minutes). Long-term sessions are maintained using **Refresh Tokens**.

Under **Refresh Token Rotation (RTR)**, every time a refresh token is used, it is invalidated and replaced with a brand-new refresh token.

#### Token Family & Replay Attack State Machine

```
Legitimate Flow:
[Auth Login] ──► Issues Token Family F1: Access Token A1 + Refresh Token R1
                      │
[Client Refreshes] ──► Submits R1 ──► Server marks R1 as USED
                                  ──► Issues A2 + Refresh Token R2 (Family F1)
                      │
[Client Refreshes] ──► Submits R2 ──► Server marks R2 as USED
                                  ──► Issues A3 + Refresh Token R3 (Family F1)

---------------------------------------------------------------------------

Replay Attack / Theft Anomaly Flow:
[Attacker intercepted R1 earlier]
[Attacker Submits R1] ──► Server checks R1 state -> ALREADY USED!
                      │
                      ▼
        CRITICAL SECURITY BREACH DETECTED!
        1. Invalidate and REVOKE ALL tokens belonging to Family F1 (including R2, R3)!
        2. Invalidate all active sessions for that User.
        3. Reject attacker request with HTTP 401.
```

---

### Critical JWT Vulnerabilities & Hardened Mitigations

#### 1. The Algorithm Confusion Attack (`alg=none`)
- **Vulnerability**: Attackers modify the JWT header to `{"alg": "none"}` and strip the signature. Vulnerable libraries treat the token as valid unsigned data and grant administrative privileges.
- **Hardened Defense**: The parser must **strictly whitelist** allowed algorithms (`JWSAlgorithm.RS256`) and unconditionally reject any token with `alg=none` or unexpected algorithms.

#### 2. The RSA-to-HMAC Key Confusion Attack
- **Vulnerability**: An attacker takes the server's public RSA key (which is public knowledge) and uses it as the secret key to sign a token with `HS256` (symmetric HMAC). If the server parser uses a generic verify method that blindly accepts the algorithm specified in the token header, it verifies the signature using its RSA public key as the HMAC secret!
- **Hardened Defense**: Hardcode the expected signature verification algorithm in the `NimbusJwtDecoder` / parser configuration.

#### 3. Distributed Clock Skew & Expiration Handling
- **Vulnerability**: In distributed clusters, microservice clocks may drift by a few seconds. A token created on Server A might appear "not yet valid" (`nbf`) or "prematurely expired" (`exp`) on Server B.
- **Hardened Defense**: Configure an explicit **Clock Skew Leeway** (e.g., 30 to 60 seconds) in token validation:
  $$\text{Valid if } \text{now} \le \text{exp} + \text{leeway}$$

---

## 3. Enterprise Scenario: FinFlow Merchant Auth Gateway

In the **FinFlow Reference Architecture**:

```
Merchant POS / Web Portal
       │
       ▼ (POST /api/v1/auth/login)
Merchant Auth Gateway
       │ ├── Signs RS256 JWT Access Token (15-min TTL)
       │ └── Issues Opaque Refresh Token with Token Family UUID (7-day TTL)
       │
       ▼ (Returns AuthTokens: accessToken, refreshToken)
Payment Service & Ledger Service (20 Kubernetes Pods)
       │
       ▼ (GET /api/v1/payments/MERCHANT_ACME/summary with Bearer JWT)
JwtAuthenticationFilter
       ├── Validates RS256 Signature against JWKS Key (finflow-rsa-2026-v1)
       ├── Enforces Expiration (exp + 30s leeway), Issuer (https://auth.finflow.io)
       └── Populates SecurityContextHolder with MerchantPrincipal
```

---

## 4. Incorrect Implementation

Below is an insecure JWT implementation typical of vulnerable legacy systems:

```java
package com.finflow.chapter230.incorrect;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;

/**
 * INCORRECT IMPLEMENTATION:
 * 1. Accepts 'alg=none' and does not enforce expected algorithm.
 * 2. Decodes claims without verifying cryptographic signature!
 * 3. 30-day static access tokens without refresh rotation.
 */
@Service
public class InsecureJwtParserIncorrect {

    public JWTClaimsSet parseTokenUnsafe(String rawToken) {
        try {
            // FATAL FLAW: SignedJWT.parse() parses Base64 strings WITHOUT verifying signature!
            SignedJWT signedJWT = SignedJWT.parse(rawToken);

            // FATAL FLAW: Missing signedJWT.verify(verifier)!
            // Attacker can pass any payload with forged roles!
            return signedJWT.getJWTClaimsSet();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid token", e);
        }
    }
}
```

---

## 5. Production Incident

### Incident Timeline

| Time (UTC) | Event / Telemetry |
|---|---|
| **04:00:00** | An attacker targets the legacy internal Settlement Service. |
| **04:10:00** | Attacker takes a legitimate expired JWT, modifies the payload to `"roles": ["ROLE_SUPER_ADMIN"]`, changes the header to `{"alg": "none"}`, and strips the signature. |
| **04:12:00** | Attacker invokes `POST /api/v1/settlements/disburse` with the forged token. |
| **04:12:05** | The unhardened legacy parser decodes the payload without enforcing `RS256` signature verification and authorizes the request. |
| **04:30:00** | Attacker initiates 32 bulk ACH disbursements, transferring **$3.1M** to offshore shell accounts. |
| **05:00:00** | Automated treasury reconciliation triggers SEV-0 Alert: `Unexpected_ACH_Liquidity_Depletion`. |
| **05:15:00** | Security engineers inspect API gateway and service logs, discover `alg=none` tokens bypassing authentication, and block all outgoing ACH rails. |
| **05:45:00** | Emergency patch deployed replacing the insecure parser with strict `RS256` JWKS verification and Refresh Token Rotation. |
| **06:30:00** | All tokens invalidated, banking rails reopened safely. |

---

## 6. Logs & Diagnostics

### 1. Hardened Parser Rejecting `alg=none` Attempt
```text
2026-08-20T05:15:22.102Z WARN [settlement-service,trace_id=5f6a7b,span_id=3c4d5e] 1 --- [http-nio-8080-exec-18] c.f.c.s.JwtTokenProvider                 : JWT validation failed: Invalid JWT algorithm: none. Expected RS256.

java.lang.SecurityException: Invalid JWT algorithm: none
	at com.finflow.chapter230.security.JwtTokenProvider.parseAndValidateToken(JwtTokenProvider.java:82)
	at com.finflow.chapter230.security.JwtAuthenticationFilter.doFilterInternal(JwtAuthenticationFilter.java:34)
```

### 2. Refresh Token Reuse / Replay Detection Alert
```text
2026-08-20T05:16:04.912Z ERROR [auth-service,trace_id=112233,span_id=445566] 1 --- [http-nio-8080-exec-04] c.f.c.s.RefreshTokenRotationService     : SECURITY ALERT: Refresh token reuse detected! Compromised familyId=e3b8a8b1-5912-4cf4-b81b-7a32c2560000, userId=USER_ALICE. Revoking entire token family and terminating sessions!
```

---

## 7. Root Cause Analysis

```
+-------------------------------------------------------------------------------------------------+
|                                 JWT Breach Root Cause Chain                                     |
|                                                                                                 |
|  1. Trusting Unvalidated Token Header Algorithm ('alg')                                         |
|     └── Legacy parser allowed the client to dictate the verification algorithm ('none').        |
|                                                                                                 |
|  2. Omission of Cryptographic Signature Verification                                            |
|     └── Service extracted claims from Base64 payload without executing signedJWT.verify().     |
|                                                                                                 |
|  3. Absence of Key ID Pinning and Algorithm Whitelisting                                        |
|     └── Parser did not enforce JWSAlgorithm.RS256 key matching against trusted JWKS keystore.   |
|                                                                                                 |
|  4. Remediation: Hardened Verification Pipeline + RTR                                            |
|     └── Enforce RS256 only, verify cryptographic signature, rotate refresh tokens on every use. |
+-------------------------------------------------------------------------------------------------+
```

---

## 8. Debugging Process

```
[1. Token Inspection] Decode header and payload via CLI / jwt.io (Verify 'alg', 'exp', 'kid')
       │
[2. Signature Verification Check] Validate public key modulus and exponent from /.well-known/jwks.json
       │
[3. Clock Skew Audit] Check NTP time synchronization across all Kubernetes cluster nodes
       │
[4. Replay Audit] Query Token Family store for duplicate refresh token redemption attempts
       │
[5. Rollout] Deploy hardened NimbusJwtDecoder with strict RS256 validator
```

---

## 9. Correct Implementation

### 1. Cryptographic Key Manager: `JwtKeyManager.java`

```java
package com.finflow.chapter230.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Component
public class JwtKeyManager {

    private final String keyId = "finflow-rsa-2026-v1";
    private final RSAKey rsaJwk;
    private final JWSSigner signer;
    private final JWSVerifier verifier;

    public JwtKeyManager() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();

            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

            this.rsaJwk = new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyID(keyId)
                    .build();

            this.signer = new RSASSASigner(privateKey);
            this.verifier = new RSASSAVerifier(publicKey);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize RSA KeyPair for JWT signing", e);
        }
    }

    public String getKeyId() { return keyId; }
    public JWSSigner getSigner() { return signer; }
    public JWSVerifier getVerifier() { return verifier; }
    public RSAKey getPublicJwk() { return rsaJwk.toPublicJWK(); }
}
```

---

### 2. Hardened Token Provider: `JwtTokenProvider.java`

```java
package com.finflow.chapter230.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final String ISSUER = "https://auth.finflow.io";
    private static final String AUDIENCE = "finflow-api";
    private static final long ACCESS_TOKEN_EXPIRATION_MINUTES = 15;

    private final JwtKeyManager keyManager;

    public record JwtClaims(
            String subject,
            String merchantId,
            List<String> roles,
            String jwtId,
            Instant issuedAt,
            Instant expiresAt
    ) {}

    public JwtTokenProvider(JwtKeyManager keyManager) {
        this.keyManager = keyManager;
    }

    public String generateAccessToken(String userId, String merchantId, List<String> roles) {
        Instant now = Instant.now();
        Instant exp = now.plus(ACCESS_TOKEN_EXPIRATION_MINUTES, ChronoUnit.MINUTES);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(userId)
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp))
                .jwtID(UUID.randomUUID().toString())
                .claim("merchantId", merchantId)
                .claim("roles", roles)
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(keyManager.getKeyId())
                .build();

        SignedJWT signedJWT = new SignedJWT(header, claimsSet);
        try {
            signedJWT.sign(keyManager.getSigner());
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign JWT access token", e);
        }
    }

    public JwtClaims parseAndValidateToken(String rawToken) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(rawToken);

            // Hardened Check 1: Strict Algorithm Enforcement (blocks alg=none & HMAC confusion)
            if (!JWSAlgorithm.RS256.equals(signedJWT.getHeader().getAlgorithm())) {
                throw new SecurityException("Invalid JWT algorithm: " + signedJWT.getHeader().getAlgorithm());
            }

            // Hardened Check 2: Cryptographic Signature Verification
            if (!signedJWT.verify(keyManager.getVerifier())) {
                throw new SecurityException("JWT signature verification failed");
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            Instant now = Instant.now();

            // Hardened Check 3: Expiration with 30s Clock Skew Leeway
            Date expDate = claims.getExpirationTime();
            if (expDate == null || expDate.toInstant().plusSeconds(30).isBefore(now)) {
                throw new SecurityException("JWT access token has expired");
            }

            // Hardened Check 4: Issuer Verification
            if (!ISSUER.equals(claims.getIssuer())) {
                throw new SecurityException("Invalid token issuer: " + claims.getIssuer());
            }

            String subject = claims.getSubject();
            String merchantId = claims.getStringClaim("merchantId");
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) claims.getClaim("roles");
            String jti = claims.getJWTID();

            return new JwtClaims(
                    subject,
                    merchantId,
                    roles != null ? roles : List.of(),
                    jti,
                    claims.getIssueTime().toInstant(),
                    expDate.toInstant()
            );
        } catch (SecurityException se) {
            throw se;
        } catch (Exception e) {
            throw new SecurityException("Malformed or unparseable JWT token", e);
        }
    }
}
```

---

### 3. Refresh Token Rotation Service: `RefreshTokenRotationService.java`

```java
package com.finflow.chapter230.service;

import com.finflow.chapter230.model.AuthTokens;
import com.finflow.chapter230.model.RefreshTokenRecord;
import com.finflow.chapter230.security.JwtTokenProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RefreshTokenRotationService {

    private static final long REFRESH_TOKEN_EXPIRATION_DAYS = 7;
    private final Map<String, RefreshTokenRecord> tokenStore = new ConcurrentHashMap<>();
    private final JwtTokenProvider jwtTokenProvider;

    public RefreshTokenRotationService(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public AuthTokens issueInitialTokens(String userId, String merchantId, List<String> roles) {
        String familyId = UUID.randomUUID().toString();
        String rawRefreshToken = UUID.randomUUID().toString();

        RefreshTokenRecord record = new RefreshTokenRecord(
                rawRefreshToken,
                userId,
                merchantId,
                familyId,
                Instant.now().plus(REFRESH_TOKEN_EXPIRATION_DAYS, ChronoUnit.DAYS)
        );
        tokenStore.put(rawRefreshToken, record);

        String accessToken = jwtTokenProvider.generateAccessToken(userId, merchantId, roles);
        return new AuthTokens(accessToken, rawRefreshToken, "Bearer", 900);
    }

    public synchronized AuthTokens rotateTokens(String rawRefreshToken, List<String> roles) {
        RefreshTokenRecord existing = tokenStore.get(rawRefreshToken);

        if (existing == null) {
            throw new SecurityException("Invalid or unknown refresh token");
        }

        if (existing.isRevoked()) {
            throw new SecurityException("Refresh token is revoked");
        }

        // CRITICAL: Replay Attack Detection!
        if (existing.isUsed()) {
            revokeTokenFamily(existing.getFamilyId());
            throw new SecurityException("Security Alert: Refresh token reuse detected! Revoking token family: " + existing.getFamilyId());
        }

        if (existing.getExpiresAt().isBefore(Instant.now())) {
            throw new SecurityException("Refresh token has expired");
        }

        existing.setUsed(true);

        String newRefreshToken = UUID.randomUUID().toString();
        RefreshTokenRecord newRecord = new RefreshTokenRecord(
                newRefreshToken,
                existing.getUserId(),
                existing.getMerchantId(),
                existing.getFamilyId(),
                Instant.now().plus(REFRESH_TOKEN_EXPIRATION_DAYS, ChronoUnit.DAYS)
        );
        tokenStore.put(newRefreshToken, newRecord);

        String newAccessToken = jwtTokenProvider.generateAccessToken(
                existing.getUserId(),
                existing.getMerchantId(),
                roles
        );

        return new AuthTokens(newAccessToken, newRefreshToken, "Bearer", 900);
    }

    public void revokeTokenFamily(String familyId) {
        for (RefreshTokenRecord record : tokenStore.values()) {
            if (record.getFamilyId().equals(familyId)) {
                record.setRevoked(true);
            }
        }
    }

    public boolean isTokenRevoked(String rawToken) {
        RefreshTokenRecord record = tokenStore.get(rawToken);
        return record != null && record.isRevoked();
    }
}
```

---

## 10. Performance Comparison

Benchmarked under 6,000 req/sec payment traffic on FinFlow infrastructure.

| Metric | Database-Backed Sessions (Stateful) | Hardened RS256 JWT + RTR (Stateless) |
|---|---|---|
| **Request Verification Latency** | 3.40ms *(DB lookup per request)* | **0.08ms (In-memory crypto verification)** |
| **Database Read IOPS** | 6,000 queries/sec | **0 queries/sec (Zero database load)** |
| **Token Replay Vulnerability** | N/A | **Zero (Automatic Family Invalidation)** |
| **Key Compromise Blast Radius** | Global | **Isolated (Public Key Only on Resource Servers)** |
| **Memory Allocation per Request** | 2.8 KB | **0.9 KB** |
| **Clock Drift False Rejections** | 0.0% | **0.0% (30s Leeway protected)** |

---

## 11. Best Practices

### The Do's
- **DO use RS256 or ES256 asymmetric signing**: Protects private keys on the Auth server while allowing stateless verification across services.
- **DO keep Access Token TTL short (5–15 minutes)**: Minimizes the vulnerability window if an access token is intercepted.
- **DO implement Refresh Token Rotation (RTR) with Token Family tracking**: Detects token theft immediately and invalidates all compromised sessions.
- **DO include Key ID (`kid`) in headers**: Enables seamless, zero-downtime cryptographic key rotation.
- **DO configure Clock Skew Leeway (30–60s)**: Eliminates false expiration errors caused by microsecond cluster clock drift.

### The Don'ts
- **DON'T store PII or credit card numbers in JWT claims**: Base64URL encoding is readable by anyone who intercepts the token.
- **DON'T trust the `alg` header blindly**: Always hardcode the expected signature verification algorithm.
- **DON'T use long-lived access tokens (e.g., 30 days)**: Cannot be revoked easily without heavy distributed blacklists.
- **DON'T store JWTs in insecure client `localStorage` for browser web apps**: Susceptible to Cross-Site Scripting (XSS); use `HttpOnly`, `Secure`, `SameSite=Strict` cookies.

---

## 12. Common Mistakes

### Mistake 1: The Base64 "Encryption" Fallacy
A developer places full card numbers (`PAN`) or Social Security Numbers in a JWT payload, believing it is "encrypted".
**Why it fails**: JWTs are signed and Base64URL-encoded, NOT encrypted. Any client or intermediate proxy can decode the string with `Base64.decode()`.
**Production Fix**: Only store non-sensitive identifiers (`userId`, `merchantId`, `roles`). If sensitive payloads must be sent, use **JSON Web Encryption (JWE, RFC 7516)**.

### Mistake 2: Missing Token Reuse Detection
Implementing refresh token rotation by generating a new token, but failing to detect when an older token is replayed.
**Why it fails**: If an attacker intercepts the refresh token, both the legitimate user and the attacker can branch off separate valid sessions unnoticed.
**Production Fix**: Link tokens by `familyId` and revoke the entire family if an already-used token is submitted.

---

## 13. Interview Questions

### Junior Tier
**Q: What are the three parts of a JSON Web Token (JWT), and how are they delimited?**
> **Answer**: A JWT consists of Header, Payload, and Signature, separated by periods (`.`):
> 1. **Header**: Contains metadata about the token, including token type (`typ: "JWT"`) and the signing algorithm (`alg: "RS256"`), plus key identifier (`kid`).
> 2. **Payload**: Contains claims—statements about an entity (typically the user) and additional metadata such as subject (`sub`), issuer (`iss`), expiration (`exp`), and custom business claims.
> 3. **Signature**: Cryptographic hash generated by signing the encoded header and encoded payload using a private key (RS256) or secret key (HS256) to ensure tamper-resistance.

### Mid Tier
**Q: Explain the difference between symmetric (`HS256`) and asymmetric (`RS256`) JWT signing in a microservice architecture.**
> **Answer**: 
> - **HS256 (HMAC with SHA-256)** uses a single shared symmetric secret key for both signing and verifying tokens. Every microservice that needs to verify tokens must possess the secret key. If one service is compromised, an attacker can forge valid tokens for all other services.
> - **RS256 (RSA Signature with SHA-256)** uses an asymmetric key pair. The central Authentication Server holds the Private Key to sign tokens. Downstream microservices (Resource Servers) only need the Public Key (via JWKS) to verify signatures. They can never forge tokens, drastically reducing the security blast radius.

### Senior Tier
**Q: How does Refresh Token Rotation (RTR) with Reuse Detection protect against token theft?**
> **Answer**: Under RTR, every refresh token is single-use. When a client presents refresh token $R_1$, the server marks $R_1$ as used and issues a new access token and new refresh token $R_2$, grouped under a common `familyId`. If an attacker intercepts $R_1$ and attempts to replay it after the legitimate client already used it to obtain $R_2$, the server detects that $R_1$ is marked `used = true`. The server immediately triggers a **Token Family Invalidation**, revoking $R_2$ and all active sessions for that user, locking out the attacker.

### Staff Tier
**Q: Describe the Algorithm Confusion Attack (`alg=none` and RSA-to-HMAC) and how a production JWT parser must be hardened against it.**
> **Answer**: 
> 1. **`alg=none`**: Attackers change the header algorithm to `none` and remove the signature. Flawed parsers that dynamically instantiate verifiers based on the header will skip verification and trust the forged payload.
> 2. **RSA-to-HMAC Confusion**: Attackers take the server's public RSA key and sign a forged token using `HS256` (HMAC). If the server parser accepts `HS256` and verifies it using its configured key object (the RSA public key bytes as HMAC secret), the verification succeeds.
> **Hardened Mitigation**: The parser must strictly whitelist allowed algorithms (`JWSAlgorithm.RS256`), reject all other algorithms regardless of what the token header specifies, and verify signatures against an immutable `RSASSAVerifier` loaded with the verified RSA public key.

### Principal Tier
**Q: How do you design an ultra-low-latency Token Revocation architecture that supports instant logout of stateless JWTs across 50 microservices at 100,000 req/sec?**
> **Answer**: A Principal-level solution uses **Hybrid Short-Lived JWTs with Redis Bloom Filters & Ephemeral Revocation TTLs**:
> 1. **Short TTL Access Tokens**: Access tokens expire in 5 minutes, limiting exposure.
> 2. **Bloom Filter for Fast Negative Checks**: On logout/revocation, the JWT ID (`jti`) or `userId` is added to a Redis Bloom Filter with a TTL matching the token's remaining lifespan. Downstream microservices evaluate the in-memory/cached Bloom Filter in $< 0.02\text{ ms}$. If the Bloom filter returns *false* (99.9% of requests), the token is definitively not revoked, avoiding network calls.
> 3. **Redis Key Fallback**: If the Bloom Filter returns *true* (potential match), the service performs a sub-millisecond check against Redis (`EXISTS blacklist:{jti}`).
> 4. **Self-Pruning**: Blacklist entries in Redis expire automatically when the token's natural `exp` timestamp passes, maintaining a lean footprint.

---

## 14. Hands-on Exercise

### Objective
Implement a Spring Security `JwtAuthenticationFilter` that:
1. Rejects tokens signed with `alg=none`.
2. Validates RS256 signature using `JwtKeyManager`.
3. Sets `SecurityContextHolder` with `MerchantPrincipal`.

### Solution

```java
@Component
public class CustomJwtFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    public CustomJwtFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                JwtTokenProvider.JwtClaims claims = tokenProvider.parseAndValidateToken(token);
                
                var authorities = claims.roles().stream().map(SimpleGrantedAuthority::new).toList();
                var principal = new MerchantPrincipal(claims.jwtId(), claims.merchantId(), authorities);
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            } catch (Exception ex) {
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
```

---

## 15. Advanced Challenge: Distributed Token Blacklist with Redis Bloom Filters

### Enterprise Problem Statement
Design an instant token revocation component that validates whether an access token `jti` is blacklisted without querying PostgreSQL or overloading Redis.

### Enterprise Solution

```java
package com.finflow.chapter230.security;

import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DistributedTokenBlacklistService {

    // Simulates Redis TTL Key Store for Revoked JTIs
    private final Map<String, Instant> blacklistTtlStore = new ConcurrentHashMap<>();

    public void revokeToken(String jti, Instant expirationTime) {
        blacklistTtlStore.put(jti, expirationTime);
    }

    public boolean isRevoked(String jti) {
        Instant exp = blacklistTtlStore.get(jti);
        if (exp == null) {
            return false;
        }
        if (exp.isBefore(Instant.now())) {
            blacklistTtlStore.remove(jti); // Self-prune expired blacklists
            return false;
        }
        return true;
    }
}
```

---

## 16. Production Checklist

Before approving any pull request involving JWT authentication:

- [ ] **Asymmetric Algorithm Whitelisted**: Ensure parser explicitly enforces `RS256` or `ES256` and strictly rejects `none`.
- [ ] **Short Access Token TTL**: Verify Access Tokens expire in $\le 15$ minutes.
- [ ] **Refresh Token Rotation (RTR) Enabled**: Confirm refresh tokens rotate upon every consumption.
- [ ] **Token Reuse Detection Implemented**: Ensure compromised token families are revoked upon duplicate refresh token presentation.
- [ ] **No Sensitive PII in Payload**: Confirm card numbers, SSNs, or passwords are never embedded in JWT claims.
- [ ] **Clock Skew Leeway Configured**: Verify 30–60 second clock skew tolerance is enabled for distributed clusters.
- [ ] **`kid` Header Present**: Ensure all issued tokens contain a Key ID for zero-downtime JWKS key rotation.
