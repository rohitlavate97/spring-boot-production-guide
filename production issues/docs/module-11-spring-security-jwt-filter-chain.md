# Module 11: Spring Security, JWT & Filter Chain Breakages

## Issue 11.1: Security Filter Chain Bypasses, Filter Exception Traps, and CORS Preflight Breakages

---

### 1. Scenario

Following a migration to **Spring Boot 3.3 / Spring Security 6** in the **FinFlow Merchant Checkout Gateway**:
1. All browser-based Single Page Applications (React/Vue) suddenly break during checkout with:
   ```text
   Access to fetch at 'https://api.finflow.com/api/v1/secure/profile' from origin 'https://checkout.finflow.com' 
   has been blocked by CORS policy: Response to preflight request doesn't pass access control check: 
   It does not have HTTP ok status (401 Unauthorized).
   ```
2. When users submit expired JWT tokens, the API crashes with an unformatted **HTTP 500 error** instead of standard RFC-7807 `ProblemDetail` JSON because `ExpiredJwtException` thrown inside `JwtAuthenticationFilter` completely **bypasses `@ControllerAdvice`**!
3. Background `@Async` report generation tasks fail with `NullPointerException` because `SecurityContextHolder.getContext().getAuthentication()` is `null` inside worker threads.

---

### 2. Symptoms

```text
1. CORS Preflight Rejection:
   Browser preflight OPTIONS requests receiving HTTP 401 Unauthorized or 403 Forbidden.
2. Filter Exceptions Bypassing @ControllerAdvice:
   io.jsonwebtoken.ExpiredJwtException or BadCredentialsException resulting in raw Tomcat 500 HTML error pages instead of JSON ProblemDetail.
3. Role Prefix Authorization Mismatch:
   Access to @PreAuthorize("hasRole('ADMIN')") returning 403 Forbidden even when JWT payload contains "ADMIN".
4. SecurityContext Loss in Asynchronous Threads:
   SecurityContextHolder.getContext().getAuthentication() returning null in @Async or CompletableFuture threads.
5. Inadvertent Filter Chain Bypass:
   Public endpoints incorrectly matching authenticated filter chains due to AntPathMatcher syntax vs Spring Security 6 MvcRequestMatcher changes.
```

---

### 3. Possible Root Causes

1. **Filter-Level Exceptions Bypassing Spring MVC:** Servlet filters execute *outside* and *before* `DispatcherServlet`. Any runtime exception thrown in a `OncePerRequestFilter` cannot be caught by `@ExceptionHandler` or `@ControllerAdvice`.
2. **CORS Filter Ordered After Authentication Filter:** If Spring Security's authorization rules evaluate before CORS preflight validation, the unauthenticated `OPTIONS` request is rejected.
3. **`hasRole()` vs `hasAuthority()` Prefix Requirement:** Spring Security's `hasRole("ADMIN")` strictly requires the `GrantedAuthority` string to be prefixed with `ROLE_` (e.g. `ROLE_ADMIN`). If the JWT claims provide raw `"ADMIN"`, `hasRole("ADMIN")` fails.
4. **ThreadLocal Scope of `SecurityContextHolder`:** By default, `SecurityContextHolder` uses `MODE_THREADLOCAL`. Asynchronous tasks dispatched to thread pools do not inherit the calling thread's security context.

---

### 4. Architecture Context: Servlet Filter Chain vs. DispatcherServlet Pipeline

```text
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        SERVLET FILTER CHAIN & EXCEPTION ROUTING                        │
│                                                                                        │
│  [HTTP Request from Client / Browser]                                                  │
│           │                                                                            │
│           ▼                                                                            │
│  ┌──────────────────────────────────────────────────────────────────────────────────┐  │
│  │                              SERVLET FILTER CHAIN                                │  │
│  │                                                                                  │  │
│  │  1. CorsFilter (Evaluates CORS headers & OPTIONS preflight)                      │  │
│  │        │                                                                         │  │
│  │  2. JwtAuthenticationFilter (Custom OncePerRequestFilter)                        │  │
│  │        ├── Valid Token? ──► Set SecurityContext & proceed                        │  │
│  │        └── Expired/Bad? ──► 💥 Throws ExpiredJwtException!                        │  │
│  │                                 │                                                │  │
│  │                                 ▼                                                │  │
│  │                    ❌ CANNOT REACH @ControllerAdvice!                             │  │
│  │                    (DispatcherServlet has not been invoked yet!)                 │  │
│  │                                 │                                                │  │
│  │                                 ▼                                                │  │
│  │                    ✅ MUST CATCH & FORWARD TO:                                    │  │
│  │                    AuthenticationEntryPoint ──► Returns RFC-7807 JSON             │  │
│  │                                                                                  │  │
│  │  3. FilterSecurityInterceptor / AuthorizationFilter (Checks roles & permits)    │  │
│  └────────────────────────────────────────┬─────────────────────────────────────────┘  │
│                                           │                                            │
│                                           ▼                                            │
│  ┌──────────────────────────────────────────────────────────────────────────────────┐  │
│  │                               SPRING MVC PIPELINE                                │  │
│  │                                                                                  │  │
│  │  4. DispatcherServlet ──► HandlerMapping ──► Controller (@RestController)        │  │
│  │                                                       │                          │  │
│  │                                                       ▼                          │  │
│  │                                            @ControllerAdvice                     │  │
│  │                                            (Only handles MVC exceptions!)        │  │
│  └──────────────────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 5. How to Reproduce the Issues

#### Step 1: Throw Exception in Filter Without Handler Delegation
```java
// ❌ ANTI-PATTERN: Letting filter exceptions bubble uncaught to servlet container
@Override
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) {
    String token = extractToken(request);
    // If token is expired, validateToken() throws ExpiredJwtException.
    // Uncaught exception bubbles up to Tomcat, generating a raw 500 HTML page!
    tokenProvider.validateToken(token); 
    chain.doFilter(request, response);
}
```

#### Step 2: Block CORS Preflight in SecurityFilterChain
```java
// ❌ ANTI-PATTERN: Forgetting HttpMethod.OPTIONS or proper CorsConfigurationSource
http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/v1/secure/**").authenticated() // Blocks OPTIONS preflight!
);
```

---

### 6. Evidence Collection & Diagnostic Probing

#### Method 1: Enable Spring Security Debug Logging
```yaml
logging:
  level:
    org.springframework.security: TRACE
    org.springframework.security.web.FilterChainProxy: DEBUG
```

**Stdout Log Output (Tracing Filter Order & Preflight Rejection):**
```text
DEBUG o.s.security.web.FilterChainProxy - Securing OPTIONS /api/v1/secure/profile
TRACE o.s.s.w.a.AnonymousAuthenticationFilter - Set SecurityContextHolder to anonymous
TRACE o.s.s.w.a.AuthorizationFilter - Authorizing SecurityContextHolder [Anonymous]
DEBUG o.s.s.w.a.Http403ForbiddenEntryPoint - Pre-authenticated entry point called. Rejecting access
```

#### Method 2: Test CORS Preflight via cURL
```bash
curl -i -X OPTIONS http://localhost:8091/api/v1/secure/profile \
  -H "Origin: https://checkout.finflow.com" \
  -H "Access-Control-Request-Method: GET" \
  -H "Access-Control-Request-Headers: Authorization"
```

---

### 7. Step-by-Step Debugging Procedure

```text
Step 1: Inspect Preflight HTTP Response Status.
        If OPTIONS returns 401 or 403, verify if .cors() is enabled and CorsConfigurationSource is registered.

Step 2: Trace Filter Exception Handling.
        Catch ExpiredJwtException in JwtAuthenticationFilter and delegate explicitly to AuthenticationEntryPoint:
        authenticationEntryPoint.commence(request, response, authException).

Step 3: Verify Role and Authority Formatting.
        Ensure JWT claim list contains "ROLE_ADMIN" if using hasRole("ADMIN"), or use hasAuthority("ADMIN").

Step 4: Audit Async Security Context Propagation.
        Wrap Executors with DelegatingSecurityContextExecutorService or set
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL).
```

---

### 8. Technical Root Cause Deep-Dive

#### 1. Why Filter Exceptions Bypass `@ControllerAdvice`
- The Servlet Container (Tomcat/Jetty) maintains an array of `Filter` instances (the Filter Chain).
- Spring MVC's `DispatcherServlet` is itself just a single `HttpServlet` situated at the **tail end** of the Filter Chain.
- `@ControllerAdvice` and `@ExceptionHandler` annotations are managed by Spring MVC's `ExceptionHandlerExceptionResolver`, which is invoked *only when an exception is thrown inside `DispatcherServlet.doDispatch()`*.
- If an exception occurs inside a `Filter` before `DispatcherServlet` is called, Spring MVC never executes, and the container renders a generic 500 error page.

#### 2. Spring Security 6 `hasRole` vs `hasAuthority`
- `hasRole("USER")` evaluates: `authority.getAuthority().equals("ROLE_USER")`.
- `hasAuthority("USER")` evaluates: `authority.getAuthority().equals("USER")`.
- If your OAuth2 / JWT identity provider issues claims like `{"roles": ["ADMIN"]}`, you must map them with `new SimpleGrantedAuthority("ROLE_" + role)` during token parsing!

---

### 9. Production-Grade Fixes

#### ✅ Fix 1: Filter Exception Delegation to `AuthenticationEntryPoint`
```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider,
                                   CustomAuthenticationEntryPoint authenticationEntryPoint) {
        this.tokenProvider = tokenProvider;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = extractJwtFromRequest(request);
            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                String username = tokenProvider.getUsernameFromToken(jwt);
                List<String> roles = tokenProvider.getRolesFromToken(jwt);
                List<SimpleGrantedAuthority> authorities = roles.stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(username, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
            filterChain.doFilter(request, response);
        } catch (ExpiredJwtException | BadCredentialsException e) {
            // Forward directly to AuthenticationEntryPoint for RFC-7807 ProblemDetail response!
            authenticationEntryPoint.commence(request, response,
                    new InsufficientAuthenticationException(e.getMessage(), e));
        }
    }
}
```

#### ✅ Fix 2: RFC-7807 `ProblemDetail` via `CustomAuthenticationEntryPoint`
```java
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, authException.getMessage()
        );
        problemDetail.setTitle("Unauthorized Access");
        problemDetail.setType(URI.create("https://finflow.com/errors/unauthorized"));
        problemDetail.setProperty("path", request.getRequestURI());

        response.getWriter().write(objectMapper.writeValueAsString(problemDetail));
    }
}
```

#### ✅ Fix 3: Spring Security 6 `SecurityFilterChain` & CORS Configuration
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtFilter,
                                                   CustomAuthenticationEntryPoint authEntryPoint) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex.authenticationEntryPoint(authEntryPoint))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // Permit CORS Preflight
                .requestMatchers("/api/v1/auth/**", "/actuator/**").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/secure/**").authenticated()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

---

### 10. Verification

1. **JWT Filter Validation Test:** Run `JwtAuthenticationFilterTest.java` to verify valid JWTs allow access (200 OK), missing headers return 401 ProblemDetail, and expired tokens return 401 ProblemDetail.
2. **CORS Preflight Test:** Run `CorsPreflightSecurityTest.java` to confirm unauthenticated `OPTIONS` requests succeed with 200 OK and CORS headers.
3. **Role-Based Authorization Test:** Run `RoleBasedAuthorizationTest.java` to confirm `ROLE_USER` is forbidden from admin endpoints (403) while `ROLE_ADMIN` succeeds.
4. **Async Security Context Test:** Run `AsyncSecurityContextPropagationTest.java` to verify principal propagation across asynchronous threads.

---

### 11. Prevention & Production Readiness

1. **Never Let Filter Exceptions Escape Uncaught:**
   Always wrap `doFilterInternal` in a `try-catch` and route authentication/authorization exceptions to `AuthenticationEntryPoint` or `HandlerExceptionResolver`.
2. **Standardize Role Authority Prefixes:**
   Enforce consistent mapping of claims to `ROLE_<ROLE_NAME>` in `JwtAuthenticationFilter`.
3. **Always Include `HttpMethod.OPTIONS` in Security Matchers:**
   Ensure browser preflight requests are explicitly permitted before authentication filters evaluate.

---

### 12. Interview & Production Questions

#### Interview Questions
1. **Q: Why are exceptions thrown inside a servlet filter unable to be caught by `@ControllerAdvice`?**
2. **Q: What is the exact difference between `hasRole('ADMIN')` and `hasAuthority('ADMIN')` in Spring Security?**
3. **Q: How does a browser CORS preflight request work, and why does Spring Security often block it?**
4. **Q: What strategy should be used to propagate `SecurityContextHolder` to `@Async` child threads?**
5. **Q: What is the purpose of `DelegatingFilterProxy` in the Spring Security filter chain?**

#### Production Incident Questions
1. **Incident:** After upgrading to Spring Boot 3, all browser requests fail with CORS errors, but Postman requests succeed. Why?
2. **Incident:** An API gateway forwards an expired JWT. The backend returns an HTML 500 page with a full stack trace to the frontend. How do you fix this at the filter layer?
3. **Incident:** A developer added `@Order(1)` on a new `SecurityFilterChain` bean matching `/**`. All existing security rules on other filter chains stopped working. Why?
4. **Incident:** A multi-tenant application uses tenant IDs in JWT claims. In a high-throughput async pipeline, Tenant A sees Tenant B's data. How did `MODE_INHERITABLETHREADLOCAL` cause this leak?
5. **Incident:** How do you configure clock skew tolerance (e.g. 60 seconds) in JJWT / Spring Security to prevent token rejection due to NTP drift across servers?

#### Trick Questions
1. **Trick:** If `SecurityContextHolder.getContext().setAuthentication(null)` is called, does Spring Security treat the user as unauthenticated or anonymous?
2. **Trick:** Does disabling CSRF (`csrf.disable()`) pose a security vulnerability for stateless REST APIs using `Authorization: Bearer` headers?
3. **Trick:** If a custom filter is annotated with `@Component` AND registered via `http.addFilterBefore(...)`, will it execute once or twice? *(Hint: Spring Boot Servlet Filter auto-registration!)*

---

*(Answers and detailed explanations are provided in the Master Answer Guide)*
