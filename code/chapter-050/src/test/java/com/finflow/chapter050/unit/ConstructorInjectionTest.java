package com.finflow.chapter050.unit;

import com.finflow.chapter050.correct.IdempotencyServiceCorrect;
import com.finflow.chapter050.correct.PaymentOrchestratorCorrect;
import com.finflow.chapter050.domain.PaymentGateway;
import com.finflow.chapter050.domain.PaymentRequest;
import com.finflow.chapter050.domain.PaymentResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConstructorInjectionTest {

    @Test
    void canInstantiateWithoutSpringContext() {
        // Pure unit test without booting a Spring Context
        PaymentGateway stubGateway = new PaymentGateway() {
            @Override
            public PaymentResult charge(PaymentRequest request) {
                return new PaymentResult("ch_123", "SUCCEEDED", "MOCK_GATEWAY", null);
            }
            @Override
            public com.finflow.chapter050.domain.RefundResult refund(com.finflow.chapter050.domain.RefundRequest request) {
                return null;
            }
            @Override
            public String gatewayName() {
                return "MOCK_GATEWAY";
            }
            @Override
            public boolean isAvailable() {
                return true;
            }
        };
        
        IdempotencyServiceCorrect realIdempotency = new IdempotencyServiceCorrect();

        // Constructor injection enables trivial setup of dependencies
        PaymentOrchestratorCorrect orchestrator = new PaymentOrchestratorCorrect(stubGateway, realIdempotency);

        PaymentRequest request = new PaymentRequest("pi_123", "cus_456", 1000, "USD", "idem_789");
        PaymentResult result = orchestrator.processPayment(request);

        assertNotNull(result);
        assertEquals("SUCCEEDED", result.status());
        assertEquals("MOCK_GATEWAY", result.gatewayName());
    }
}
