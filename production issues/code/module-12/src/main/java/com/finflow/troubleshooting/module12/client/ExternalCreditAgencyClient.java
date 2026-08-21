package com.finflow.troubleshooting.module12.client;

import com.finflow.troubleshooting.module12.dto.CreditAssessmentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class ExternalCreditAgencyClient {

    private static final Logger log = LoggerFactory.getLogger(ExternalCreditAgencyClient.class);

    private final RestClient restClient;

    public ExternalCreditAgencyClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(500));
        requestFactory.setReadTimeout(Duration.ofMillis(1000));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl("https://mock-credit-agency.finflow.internal")
                .build();
    }

    public CreditAssessmentResult assessCredit(String customerId, boolean simulateFailure, boolean simulateTimeout) {
        log.info("[ExternalClient] Invoking remote credit scoring service for customer: {}", customerId);

        if (simulateTimeout) {
            log.warn("[ExternalClient] Simulating remote service network timeout...");
            try {
                Thread.sleep(1500); // Exceeds read timeout of 1000ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            throw new ResourceAccessException("I/O error on POST request: Read timed out");
        }

        if (simulateFailure) {
            log.error("[ExternalClient] Remote credit scoring service returned HTTP 503 Service Unavailable");
            throw new IllegalStateException("Remote Credit Agency 503: Service Unavailable");
        }

        // Happy path
        return new CreditAssessmentResult(customerId, 780, "LOW_RISK", false);
    }
}
