package com.finflow.chapter350.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Global correlation filter ensuring every inbound request has an X-Correlation-ID
 * injected into downstream headers and returned to the client in response headers.
 */
@Component
public class RequestCorrelationGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestCorrelationGlobalFilter.class);
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);

        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = "corr-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            log.debug("[Gateway-Trace] Generated new correlation ID: {}", correlationId);
        }

        final String finalCorrelationId = correlationId;

        // 1. Add correlation ID to downstream request headers
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(CORRELATION_ID_HEADER, finalCorrelationId)
                .build();

        // 2. Add correlation ID to upstream client response headers
        exchange.getResponse().getHeaders().add(CORRELATION_ID_HEADER, finalCorrelationId);

        long startTime = System.currentTimeMillis();

        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                .doFinally(signalType -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("[Gateway-Access] Method: {} | Path: {} | Status: {} | Duration: {}ms | CorrId: {}",
                            mutatedRequest.getMethod(),
                            mutatedRequest.getURI().getPath(),
                            exchange.getResponse().getStatusCode(),
                            duration,
                            finalCorrelationId);
                });
    }

    @Override
    public int getOrder() {
        // Run first before any other filter
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
