package com.finflow.chapter350.integration;

import com.finflow.chapter350.Chapter350Application;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(classes = Chapter350Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
public class SpringCloudGatewayIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void testUnauthorizedRequestReturns401AtGatewayEdge() {
        webTestClient.post()
                .uri("/api/v1/payments/process")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.error").isEqualTo("Unauthorized")
                .jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Missing or invalid Authorization"));
    }

    @Test
    void testFallbackPaymentEndpointReturns503() {
        webTestClient.get()
                .uri("/fallback/payments")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.service").isEqualTo("payment-service")
                .jsonPath("$.circuitBreaker").isEqualTo("paymentCircuitBreaker");
    }

    @Test
    void testFallbackOrdersEndpointReturns503() {
        webTestClient.get()
                .uri("/fallback/orders")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.service").isEqualTo("order-service")
                .jsonPath("$.circuitBreaker").isEqualTo("orderCircuitBreaker");
    }

    @Test
    void testCorrelationIdHeaderInjectedInResponse() {
        webTestClient.post()
                .uri("/api/v1/payments/process")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test_token")
                .exchange()
                .expectHeader().exists("X-Correlation-ID");
    }
}
