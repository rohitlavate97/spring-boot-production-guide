---
chapter: 70
topic: Spring MVC Request Lifecycle — DispatcherServlet, HandlerMapping, Interceptors, Argument Resolvers
prerequisite_chapters: [30, 40, 50, 60]
reference_system_node: Payment Service (HTTP ingress, DispatcherServlet dispatch loop, filter chain, interceptors, argument resolvers)
---

# Chapter 070: Spring MVC Request Lifecycle — DispatcherServlet, HandlerMapping, Interceptors, Argument Resolvers

## 1. Concept

In any Spring Boot web application, understanding the lifecycle of an incoming HTTP request is critical for implementing cross-cutting concerns like security, audit logging, and payload validation. The journey involves multiple layers: the underlying web server (like Tomcat or Jetty), the Servlet Container, and finally the Spring MVC framework.

When a client sends an HTTP request, it first hits the web server, which parses the raw bytes into an `HttpServletRequest`. This request is then passed through a chain of **Servlet Filters**. Filters are part of the Java Servlet API, meaning they operate outside the Spring MVC context. They are ideal for coarse-grained tasks like CORS, global authentication, and logging.

After the filters, the request reaches the `DispatcherServlet`, the heart of Spring MVC. The `DispatcherServlet` is the front controller that dispatches the request to the appropriate controller method. Before the controller executes, the request passes through **HandlerInterceptors**. Interceptors are Spring-specific and have access to the target handler (the controller method), making them suitable for fine-grained authorization and state setup.

Next, **HandlerMethodArgumentResolvers** come into play. They inspect the controller method's parameters and resolve their values from the request (e.g., converting a JSON body into a Java object). Finally, the controller method executes. If an exception occurs, **ControllerAdvice** handles it globally.

## 2. Internal Working

The core of Spring MVC's request processing happens inside `DispatcherServlet.doDispatch()`. Let's break down this crucial method.

1. **Handler Lookup**: The `DispatcherServlet` iterates over registered `HandlerMapping`s (like `RequestMappingHandlerMapping`) to find a matching handler for the request URL. It returns a `HandlerExecutionChain`, containing the chosen handler (typically a `HandlerMethod` wrapping your controller method) and a list of `HandlerInterceptor`s.

2. **Pre-Handle Interceptors**: `DispatcherServlet` calls `preHandle()` on all interceptors. If any returns `false`, execution stops.

3. **Handler Adapter**: `DispatcherServlet` delegates the actual invocation to a `HandlerAdapter` (usually `RequestMappingHandlerAdapter`).

4. **Argument Resolution**: Inside the adapter, `InvocableHandlerMethod.getMethodArgumentValues()` is called. It uses a `HandlerMethodArgumentResolverComposite` to iterate through registered resolvers. For JSON bodies, `RequestResponseBodyMethodProcessor` delegates to an `HttpMessageConverter` (like Jackson) to deserialize the payload.

5. **Controller Execution**: The actual controller method is invoked.

6. **Post-Handle Interceptors**: `postHandle()` is called on interceptors (bypassed if an exception occurred).

7. **After Completion**: `afterCompletion()` is called, **guaranteed** to execute even if exceptions occurred, making it the right place for cleanup (e.g., clearing `ThreadLocal`).

### Threading Model
In a standard Spring Boot setup with Tomcat, requests are handled by Tomcat NIO worker threads. A single thread typically handles the entire request synchronously from the filter chain down to the controller and back up. This thread is pooled and reused for subsequent requests.

### Flowchart
```
Client Request -> Tomcat Worker Thread
  -> Servlet Filter Chain (e.g., Security, Caching)
    -> DispatcherServlet.doDispatch()
      -> HandlerMapping (finds Controller)
      -> HandlerExecutionChain (Controller + Interceptors)
        -> Interceptor.preHandle()
          -> HandlerAdapter
            -> ArgumentResolvers (e.g., HttpMessageConverters)
              -> Controller Method
            -> Return Value Handlers
          <- Interceptor.postHandle()
      <- Interceptor.afterCompletion() (Always executes)
  <- Servlet Filter Chain
<- HTTP Response
```

## 3. Enterprise Scenario

In the FinFlow Payment Platform, the **Payment Service** handles high-throughput inbound traffic, peaking at ~4,000 req/sec. These include `POST /v1/payments`, `POST /v1/refunds`, and incoming webhooks from external payment gateways.

For PCI compliance and security auditing, every incoming request payload must be hashed and logged. Additionally, the service operates in a multi-tenant environment. Controllers require a typed `MerchantContext` injected into their methods to ensure operations are isolated per tenant. Furthermore, a Correlation ID (trace ID) must be tracked in the MDC (Mapped Diagnostic Context) for distributed log tracing.

To achieve this, the team decided to:
1. Audit the request payload.
2. Extract merchant credentials and establish a `MerchantContext` in a `ThreadLocal`.
3. Provide the `MerchantContext` to controller methods.

## 4. Incorrect Implementation

The team implemented the requirements using an interceptor to read the body and set the context. 

```java
package com.finflow.chapter070.incorrect;

import com.finflow.chapter070.domain.MerchantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class BadSecurityAndAuditInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(BadSecurityAndAuditInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Problem 1: Reading request body directly exhausts the InputStream
        String payload = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        log.info("Auditing request payload: {}", payload);

        // Problem 2: Setting ThreadLocal but not cleaning it up in afterCompletion
        String merchantId = request.getHeader("X-Merchant-Id");
        if (merchantId != null) {
            MerchantContext.setMerchantId(merchantId);
        }
        
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
        // Problem 2 cont: This is bypassed if an exception occurs in the controller!
        MerchantContext.clear();
    }
}
```

```java
package com.finflow.chapter070.incorrect;

import com.finflow.chapter070.domain.MerchantContext;
import com.finflow.chapter070.domain.PaymentRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/incorrect/payments")
public class BadPaymentController {

    @PostMapping
    public ResponseEntity<String> processPayment(@RequestBody PaymentRequest request) {
        // Problem 3: Cluttered method signature or relying on static ThreadLocal access
        String merchantId = MerchantContext.getMerchantId();
        
        // This will fail because the interceptor already read the InputStream!
        // Jackson will throw an IllegalStateException.
        return ResponseEntity.ok("Processed for " + merchantId);
    }
}
```

## 5. Production Incident

At 11:30 UTC, a spike in webhook traffic hits the Payment Service. 
First, clients start receiving HTTP 500 errors. The logs indicate Jackson cannot parse the JSON body because the input stream has already been read. 
Simultaneously, a more insidious issue occurs. A request from Tenant A (merchant 101) throws an exception early in the controller. The `postHandle()` method in the interceptor is bypassed, and `MerchantContext.clear()` is never called. The Tomcat worker thread is returned to the pool with Tenant A's `merchantId` still in the `ThreadLocal`.
Moments later, Tenant B (merchant 202) makes a request without the `X-Merchant-Id` header (or it's an internal endpoint that doesn't enforce it). The request is handled by the same poisoned thread, and Tenant B inadvertently queries Tenant A's settlement data. A severe data privacy breach!

## 6. Logs

```
2026-08-19T11:30:01,123 [http-nio-8080-exec-15] INFO  c.f.c.i.BadSecurityAndAuditInterceptor - Auditing request payload: {"amount": 1000, "currency": "USD"}
2026-08-19T11:30:01,125 [http-nio-8080-exec-15] ERROR o.a.c.c.C.[.[.[/].[dispatcherServlet] - Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception
java.lang.IllegalStateException: getInputStream() has already been called for this request
    at org.apache.catalina.connector.Request.getReader(Request.java:1222)
    at org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter.readJavaType(AbstractJackson2HttpMessageConverter.java:406)

2026-08-19T11:30:05,441 [http-nio-8080-exec-15] INFO  c.f.c.s.SettlementService - traceId=abc-123 spanId=xyz-456 - Executing settlement query for Merchant: 101
2026-08-19T11:30:05,442 [http-nio-8080-exec-15] WARN  c.f.c.s.SettlementService - traceId=abc-123 spanId=xyz-456 - UNAUTHORIZED ACCESS DETECTED: Tenant 202 attempting to read Tenant 101 data.
```

## 7. Root Cause Analysis

There are two primary root causes for this incident:

1. **Single-Read InputStream**: The `ServletInputStream` provided by the `HttpServletRequest` can only be read once. When `BadSecurityAndAuditInterceptor` reads the stream using `request.getInputStream().readAllBytes()`, the stream pointer moves to the end. Later, when the `DispatcherServlet` invokes the `HttpMessageConverter` (Jackson) to deserialize the `@RequestBody`, Jackson attempts to read the stream again, resulting in an `IllegalStateException`.
2. **ThreadLocal Leak in Tomcat Worker Pool**: Tomcat utilizes a pool of worker threads (e.g., `http-nio-8080-exec-*`) to handle incoming requests. When a thread finishes processing a request, it is returned to the pool without resetting its thread-local state. The `BadSecurityAndAuditInterceptor` placed the `merchantId` in a `ThreadLocal` and attempted to clean it up in `postHandle()`. However, `postHandle()` is **not** guaranteed to execute if an exception occurs during controller execution. Thus, the thread returned to the pool was "poisoned" with Tenant A's state.

## 8. Debugging Process

1. **Identify the 500 Errors**: Monitoring dashboards showed a spike in 500 errors. Inspecting the logs revealed `IllegalStateException: getInputStream() has already been called`.
2. **Trace the Stream Read**: A search for `.getInputStream()` or `.getReader()` in the codebase pointed directly to `BadSecurityAndAuditInterceptor.preHandle()`.
3. **Investigate Cross-Tenant Data Access**: Audit logs flagged unauthorized access attempts. Correlation IDs linked these anomalies to specific Tomcat worker threads (e.g., `http-nio-8080-exec-15`).
4. **Analyze Thread Lifecycle**: By tracing the lifecycle of thread `exec-15`, it became clear that an earlier request on the same thread had failed with an exception, bypassing the `MerchantContext.clear()` in `postHandle()`. The next request on that thread inherited the lingering context.

## 9. Correct Implementation

We fix this by using `ContentCachingRequestWrapper` in a Filter to cache the payload, a `HandlerMethodArgumentResolver` for clean controller signatures, and robust `afterCompletion()` cleanup in the Interceptor.

```java
package com.finflow.chapter070.correct;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

@Component
public class PayloadCachingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        // Wrap the request to cache the input stream as it's read by Jackson
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        
        filterChain.doFilter(wrappedRequest, response);
        
        // Payload can now be safely read AFTER it has been consumed by the controller
        // byte[] payload = wrappedRequest.getContentAsByteArray();
    }
}
```

```java
package com.finflow.chapter070.correct;

import com.finflow.chapter070.domain.MerchantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class GoodSecurityAndAuditInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(GoodSecurityAndAuditInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String merchantId = request.getHeader("X-Merchant-Id");
        if (merchantId != null) {
            MerchantContext.setMerchantId(merchantId);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // Guaranteed to run, even if controller throws an exception
        MerchantContext.clear();
        log.debug("Cleared MerchantContext for thread {}", Thread.currentThread().getName());
    }
}
```

```java
package com.finflow.chapter070.correct;

import com.finflow.chapter070.domain.MerchantInfo;
import com.finflow.chapter070.domain.MerchantContext;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class MerchantInfoArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(MerchantInfo.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String merchantId = MerchantContext.getMerchantId();
        if (merchantId == null) {
            throw new IllegalArgumentException("Merchant context is missing");
        }
        return new MerchantInfo(merchantId);
    }
}
```

```java
package com.finflow.chapter070.correct;

import com.finflow.chapter070.domain.MerchantInfo;
import com.finflow.chapter070.domain.PaymentRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    @PostMapping
    public ResponseEntity<String> processPayment(
            @RequestBody PaymentRequest request,
            MerchantInfo merchantInfo) { // Cleanly resolved by ArgumentResolver
        
        return ResponseEntity.ok("Processed for " + merchantInfo.getMerchantId());
    }
}
```

## 10. Performance Comparison

| Metric | Filter Payload Caching | Interceptor Direct Read |
|--------|------------------------|-------------------------|
| Memory Allocation | Higher (caches entire payload) | Lower (but breaks app) |
| Latency Overhead | (illustrative) ~2ms | (illustrative) ~1ms |
| GC Impact | Moderate (byte array caching) | Low |

Using `ContentCachingRequestWrapper` introduces a slight memory overhead because the entire payload is held in a byte array in memory. For large payloads, this can cause GC pressure. However, for typical FinFlow JSON payloads (< 4KB), the impact is negligible and necessary for correct functionality.

## 11. Best Practices

*   **Cleanup in `afterCompletion`**: Always clean up `ThreadLocal` variables, MDC contexts, and other thread-bound state in `afterCompletion()`, never in `postHandle()`.
*   **Use `OncePerRequestFilter`**: Prefer extending `OncePerRequestFilter` for Spring filters to ensure they are only executed once per request dispatch, especially during error forwarding or async dispatches.
*   **Leverage Argument Resolvers**: Keep controllers clean. If you find yourself repeatedly parsing headers, tokens, or contexts in multiple controllers, abstract that logic into a `HandlerMethodArgumentResolver`.
*   **Filter vs. Interceptor**: Use Filters for request manipulation (wrapping, encoding, CORS) and early rejection. Use Interceptors for application-specific logic that needs access to the mapped `HandlerMethod` (e.g., checking annotations on the controller).

## 12. Common Mistakes

*   **Modifying the Response in `afterCompletion`**: By the time `afterCompletion()` runs, the response has likely been committed (written to the client). Attempting to add headers or change the status code here will be silently ignored or throw an exception.
*   **Assuming `postHandle` Always Runs**: `postHandle()` is skipped if an exception is thrown in the controller or a previous interceptor.
*   **Heavy Processing in Interceptors**: Interceptors block the thread. Do not make synchronous downstream database or API calls in them unless absolutely necessary for authorization.
*   **Forgetting `@EnableWebMvc` Side Effects**: Manually adding `@EnableWebMvc` switches off Spring Boot's web auto-configuration, forcing you to define all converters and static resource handlers manually. Let Spring Boot auto-configure MVC unless you need complete control.

## 13. Interview Questions

*   **Junior**: What is the difference between a Servlet Filter and a Spring HandlerInterceptor?
*   **Mid**: Why does calling `request.getInputStream().readAllBytes()` in an interceptor cause Jackson to fail in the controller?
*   **Senior**: Explain the contract of `postHandle` vs `afterCompletion` in `HandlerInterceptor`. Where should you clean up `ThreadLocal` variables and why?
*   **Staff**: How would you implement payload auditing without breaking Jackson, while minimizing memory overhead for very large file uploads?
*   **Principal**: In a high-throughput reactive application (Spring WebFlux), how does the request lifecycle differ from Spring MVC, and how do you handle Context propagation across reactive streams?

## 14. Hands-on Exercise

**Task**: Implement a `RateLimitArgumentResolver` and an idempotency interceptor.
1. Create an annotation `@Idempotent` to mark specific controller methods.
2. Write a `HandlerInterceptor` that checks if the handler method has the `@Idempotent` annotation. If it does, verify the presence of an `Idempotency-Key` header.
3. Write a `HandlerMethodArgumentResolver` that resolves a custom `RateLimitContext` object containing the client's current rate limit quota.

## 15. Advanced Challenge

**Asynchronous Request Handling**:
Modify the `PaymentController` to return a `DeferredResult<ResponseEntity<String>>`. 
Investigate how this changes the interceptor lifecycle. Does `preHandle` run on the same thread as the controller? What about `afterCompletion`? Implement an `AsyncHandlerInterceptor` to correctly trace and manage state during asynchronous dispatch.

## 16. Production Checklist

*   [ ] `ThreadLocal` contexts are cleared in `afterCompletion()`.
*   [ ] MDC variables are populated in a Filter and cleared in a `finally` block or `OncePerRequestFilter`.
*   [ ] Request body caching uses `ContentCachingRequestWrapper` and specifies maximum cache sizes to prevent OOM.
*   [ ] Interceptors do not perform heavy blocking I/O (e.g., long DB queries).
*   [ ] Cross-cutting concerns are factored out of controllers into `HandlerMethodArgumentResolver` or Interceptors.
