package com.finflow.chapter350.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Non-blocking Authentication GlobalFilter at the API Gateway Edge.
 * Validates Bearer token / API Key, injects identity headers downstream,
 * or short-circuits with HTTP 401 Unauthorized without blocking Netty EventLoop.
 */
@Component
public class AuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationGlobalFilter.class);

    private static final List<String> PUBLIC_PATHS = List.of(
            "/actuator",
            "/fallback",
            "/favicon.ico"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. Skip authentication for public / actuator / fallback paths
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // 2. Extract Authorization header or API Key
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String apiKeyHeader = request.getHeaders().getFirst("X-API-Key");

        if ((authHeader == null || !authHeader.startsWith("Bearer ")) && apiKeyHeader == null) {
            log.warn("[Gateway-Auth] Missing or invalid credentials for path: {}", path);
            return handleUnauthorized(exchange, "Missing or invalid Authorization Bearer token or X-API-Key");
        }

        // 3. Validate Token / Key (Simulated JWT claims extraction)
        String principalId;
        String merchantId;
        String userTier;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if ("invalid_token".equalsIgnoreCase(token) || token.isEmpty()) {
                log.warn("[Gateway-Auth] Rejected invalid Bearer token: {}", token);
                return handleUnauthorized(exchange, "Bearer token is expired or revoked");
            }
            // Mock decoded JWT claims
            principalId = "usr_" + Integer.toHexString(token.hashCode());
            merchantId = "MERCH-" + (Math.abs(token.hashCode()) % 1000);
            userTier = token.contains("premium") ? "PREMIUM" : "STANDARD";
        } else {
            // API Key Authentication
            if ("invalid_api_key".equalsIgnoreCase(apiKeyHeader) || apiKeyHeader.isEmpty()) {
                log.warn("[Gateway-Auth] Rejected invalid API Key: {}", apiKeyHeader);
                return handleUnauthorized(exchange, "Provided X-API-Key is invalid");
            }
            principalId = "api_usr_" + Integer.toHexString(apiKeyHeader.hashCode());
            merchantId = "MERCH-PARTNER";
            userTier = "ENTERPRISE";
        }

        // 4. Enrich downstream request with verified identity headers
        ServerHttpRequest mutatedRequest = request.mutate()
                .header("X-User-Id", principalId)
                .header("X-Merchant-Id", merchantId)
                .header("X-User-Tier", userTier)
                .header("X-Auth-Timestamp", Instant.now().toString())
                .build();

        log.debug("[Gateway-Auth] Authenticated request for user: {}, merchant: {}, tier: {}",
                principalId, merchantId, userTier);

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> handleUnauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> errorDetails = Map.of(
                "status", HttpStatus.UNAUTHORIZED.value(),
                "error", "Unauthorized",
                "message", message,
                "path", exchange.getRequest().getURI().getPath(),
                "timestamp", Instant.now().toString()
        );

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorDetails);
        } catch (JsonProcessingException e) {
            bytes = "{\"error\":\"Unauthorized\"}".getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // High precedence: Must execute before route routing and business filters
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
