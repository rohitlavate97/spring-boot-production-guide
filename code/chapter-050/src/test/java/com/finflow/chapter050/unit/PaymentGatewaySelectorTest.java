package com.finflow.chapter050.unit;

import com.finflow.chapter050.correct.PaymentGatewaySelector;
import com.finflow.chapter050.domain.PaymentGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class PaymentGatewaySelectorTest {

    @Autowired
    private PaymentGatewaySelector gatewaySelector;

    @Test
    void selectsAvailableGateway() {
        PaymentGateway gateway = gatewaySelector.selectAvailableGateway();
        assertNotNull(gateway);
        // Assuming at least one is available
    }

    @Test
    void getsGatewayByName() {
        PaymentGateway stripe = gatewaySelector.getGatewayByName("STRIPE");
        assertEquals("STRIPE", stripe.gatewayName());

        PaymentGateway adyen = gatewaySelector.getGatewayByName("ADYEN");
        assertEquals("ADYEN", adyen.gatewayName());
    }
}
