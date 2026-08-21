package com.finflow.chapter350.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom Route GatewayFilterFactory implementing Token Bucket Rate Limiting at the Edge.
 * Configured per-route with tokensPerSecond and burstCapacity.
 */
@Component
public class EdgeRateLimiterGatewayFilterFactory extends AbstractGatewayFilterFactory<EdgeRateLimiterGatewayFilterFactory.Config> {

    private static final Logger log = LoggerFactory.getLogger(EdgeRateLimiterGatewayFilterFactory.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory token bucket tracking per client key
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public EdgeRateLimiterGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String clientKey = resolveClientKey(exchange);
            TokenBucket bucket = buckets.computeIfAbsent(clientKey,
                    k -> new TokenBucket(config.getTokensPerSecond(), config.getBurstCapacity()));

            if (bucket.tryConsume()) {
                // Permit granted
                exchange.getResponse().getHeaders().add("X-RateLimit-Remaining", String.valueOf(bucket.getAvailableTokens()));
                return chain.filter(exchange);
            } else {
                // Rate limit exceeded - Short circuit with HTTP 429
                log.warn("[Gateway-RateLimit] Client '{}' exceeded rate limit ({}/s, burst {}) on path {}",
                        clientKey, config.getTokensPerSecond(), config.getBurstCapacity(),
                        exchange.getRequest().getURI().getPath());

                return handleTooManyRequests(exchange, config);
            }
        };
    }

    private String resolveClientKey(ServerWebExchange exchange) {
        String merchantId = exchange.getRequest().getHeaders().getFirst("X-Merchant-Id");
        if (merchantId != null && !merchantId.isBlank()) {
            return "merchant:" + merchantId;
        }

        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            return "user:" + userId;
        }

        if (exchange.getRequest().getRemoteAddress() != null) {
            return "ip:" + exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }

        return "anonymous";
    }

    private Mono<Void> handleTooManyRequests(ServerWebExchange exchange, Config config) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().add("Retry-After", "1");
        response.getHeaders().add("X-RateLimit-Limit", String.valueOf(config.getTokensPerSecond()));

        Map<String, Object> errorDetails = Map.of(
                "status", HttpStatus.TOO_MANY_REQUESTS.value(),
                "error", "Too Many Requests",
                "message", "Rate limit exceeded at API Gateway edge. Please back off.",
                "path", exchange.getRequest().getURI().getPath(),
                "timestamp", Instant.now().toString()
        );

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorDetails);
        } catch (JsonProcessingException e) {
            bytes = "{\"error\":\"Too Many Requests\"}".getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * Lock-free Token Bucket algorithm.
     */
    public static class TokenBucket {
        private final long capacity;
        private final double refillTokensPerNano;
        private final AtomicLong availableTokens;
        private final AtomicLong lastRefillTime;

        public TokenBucket(long tokensPerSecond, long capacity) {
            this.capacity = capacity;
            this.refillTokensPerNano = (double) tokensPerSecond / 1_000_000_000.0;
            this.availableTokens = new AtomicLong(capacity);
            this.lastRefillTime = new AtomicLong(System.nanoTime());
        }

        public synchronized boolean tryConsume() {
            refill();
            if (availableTokens.get() >= 1) {
                availableTokens.decrementAndGet();
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            long last = lastRefillTime.get();
            long elapsedNanos = now - last;

            if (elapsedNanos > 0) {
                long tokensToAdd = (long) (elapsedNanos * refillTokensPerNano);
                if (tokensToAdd > 0) {
                    long current = availableTokens.get();
                    long updated = Math.min(capacity, current + tokensToAdd);
                    availableTokens.set(updated);
                    lastRefillTime.set(now);
                }
            }
        }

        public long getAvailableTokens() {
            return availableTokens.get();
        }
    }

    public static class Config {
        private long tokensPerSecond = 10;
        private long burstCapacity = 20;

        public long getTokensPerSecond() {
            return tokensPerSecond;
        }

        public void setTokensPerSecond(long tokensPerSecond) {
            this.tokensPerSecond = tokensPerSecond;
        }

        public long getBurstCapacity() {
            return burstCapacity;
        }

        public void setBurstCapacity(long burstCapacity) {
            this.burstCapacity = burstCapacity;
        }
    }
}
