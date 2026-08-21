# Module 11: Spring Security, JWT & Filter Chain Breakages

## Overview
This module explores Spring Security 6 architecture, custom `OncePerRequestFilter` JWT validation, filter-level exception propagation bypassing `@ControllerAdvice`, RFC-7807 `ProblemDetail` responses via `AuthenticationEntryPoint`, CORS preflight traps, and role-based authorization.

## Key Scenarios Covered
1. **Filter Exceptions Bypassing `@ControllerAdvice`:**
   - Why servlet filter exceptions (e.g. `ExpiredJwtException`, `BadCredentialsException`) are not caught by Spring MVC's `@ExceptionHandler`, and how routing them directly to `AuthenticationEntryPoint` restores RFC-7807 compliance.
2. **CORS Preflight `OPTIONS` Request Interception:**
   - Preventing Spring Security filter chains from rejecting unauthenticated browser preflight requests.
3. **JWT Signature & Clock Skew Validation:**
   - HMAC-SHA256 token decoding, claims extraction, and expired token rejection.
4. **Role-Based Authorization (`hasRole` vs `hasAuthority`):**
   - Verifying `ROLE_USER` vs `ROLE_ADMIN` access control matrices on secure REST endpoints.

## Project Structure
- `src/main/java/.../security/`:
  - `JwtTokenProvider.java`: Generates and parses HMAC-SHA256 JWT tokens.
  - `JwtAuthenticationFilter.java`: `OncePerRequestFilter` extracting Bearer token and setting `SecurityContextHolder`.
  - `CustomAuthenticationEntryPoint.java`: Renders RFC-7807 `ProblemDetail` JSON on 401 Unauthorized.
  - `SecurityConfig.java`: Spring Security 6 `SecurityFilterChain` bean.
- `src/main/java/.../controller/`: `AuthController.java`.
- `src/main/java/.../service/`: `AsyncSecurityContextService.java`.
- `src/test/java/.../`:
  - `JwtAuthenticationFilterTest.java`
  - `CorsPreflightSecurityTest.java`
  - `RoleBasedAuthorizationTest.java`
  - `AsyncSecurityContextPropagationTest.java`

## How to Run Tests
```bash
mvn clean test
```

## Complete Documentation
For the full 12-section technical incident guide, see [Module 11 Documentation](../../docs/module-11-spring-security-jwt-filter-chain.md).
