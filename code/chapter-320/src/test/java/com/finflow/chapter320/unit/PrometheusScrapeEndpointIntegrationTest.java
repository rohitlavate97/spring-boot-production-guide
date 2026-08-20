package com.finflow.chapter320.unit;

import com.finflow.chapter320.Chapter320Application;
import com.finflow.chapter320.domain.PaymentTransaction;
import com.finflow.chapter320.service.PaymentMetricsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Chapter320Application.class)
@AutoConfigureMockMvc
@AutoConfigureObservability
public class PrometheusScrapeEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentMetricsService metricsService;

    @Test
    public void testPrometheusScrapeEndpoint_exposesFormattedMetrics() throws Exception {
        // Record a transaction
        PaymentTransaction tx = new PaymentTransaction(
                "TX-PROM-1", "MERCH-99", "USD", BigDecimal.valueOf(149.99), "CREDIT_CARD", "SUCCESS", 20
        );
        metricsService.processPayment(tx);

        // Fetch /actuator/prometheus
        MvcResult result = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn();

        String content = result.getResponse().getContentAsString();

        // Verify Prometheus metric names and formatted output
        assertThat(content).contains("payment_transactions_total");
        assertThat(content).contains("payment_processing_duration_seconds");
        assertThat(content).contains("application=\"payment-service\"");
        assertThat(content).contains("currency=\"USD\"");
        assertThat(content).contains("status=\"SUCCESS\"");
    }
}
