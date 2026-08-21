package com.finflow.chapter350.unit;

import com.finflow.chapter350.filter.AuthenticationGlobalFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthenticationGlobalFilterTest {

    private AuthenticationGlobalFilter authFilter;

    @BeforeEach
    void setUp() {
        authFilter = new AuthenticationGlobalFilter();
    }

    @Test
    void testPublicPathBypassesAuthentication() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/fallback/payments").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean filterChainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            filterChainCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(authFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(filterChainCalled.get()).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void testMissingCredentialsReturns401Unauthorized() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/payments/process").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean filterChainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            filterChainCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(authFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(filterChainCalled.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testValidBearerTokenEnrichesDownstreamHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/payments/process")
                .header(HttpHeaders.AUTHORIZATION, "Bearer premium_merchant_token_99")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean filterChainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            filterChainCalled.set(true);
            // Verify mutated headers passed to downstream
            HttpHeaders downstreamHeaders = ex.getRequest().getHeaders();
            assertThat(downstreamHeaders.getFirst("X-User-Id")).isNotNull();
            assertThat(downstreamHeaders.getFirst("X-Merchant-Id")).isNotNull();
            assertThat(downstreamHeaders.getFirst("X-User-Tier")).isEqualTo("PREMIUM");
            return Mono.empty();
        };

        StepVerifier.create(authFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(filterChainCalled.get()).isTrue();
    }

    @Test
    void testValidApiKeyEnrichesEnterpriseHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/orders/101")
                .header("X-API-Key", "api_sec_finflow_enterprise_882")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean filterChainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            filterChainCalled.set(true);
            HttpHeaders downstreamHeaders = ex.getRequest().getHeaders();
            assertThat(downstreamHeaders.getFirst("X-User-Tier")).isEqualTo("ENTERPRISE");
            assertThat(downstreamHeaders.getFirst("X-Merchant-Id")).isEqualTo("MERCH-PARTNER");
            return Mono.empty();
        };

        StepVerifier.create(authFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(filterChainCalled.get()).isTrue();
    }
}
