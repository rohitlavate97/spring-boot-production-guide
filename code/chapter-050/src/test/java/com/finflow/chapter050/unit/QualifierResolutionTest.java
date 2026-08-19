package com.finflow.chapter050.unit;

import com.finflow.chapter050.correct.AdyenPaymentGatewayCorrect;
import com.finflow.chapter050.correct.StripePaymentGatewayCorrect;
import com.finflow.chapter050.domain.PaymentGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class QualifierResolutionTest {

    @Autowired
    private PaymentGateway primaryGateway;

    @Autowired
    @Qualifier("adyen")
    private PaymentGateway adyenGateway;

    @Autowired
    private List<PaymentGateway> allGateways;

    @Test
    void resolvesPrimaryGatewayWhenNoQualifier() {
        // The @Primary annotation on StripePaymentGatewayCorrect resolves this
        assertTrue(primaryGateway instanceof StripePaymentGatewayCorrect);
        assertEquals("STRIPE", primaryGateway.gatewayName());
    }

    @Test
    void resolvesQualifiedGateway() {
        // The @Qualifier annotation specifies Adyen
        assertTrue(adyenGateway instanceof AdyenPaymentGatewayCorrect);
        assertEquals("ADYEN", adyenGateway.gatewayName());
    }

    @Test
    void collectionInjectionGetsAllGateways() {
        // List injection gets all beans implementing the interface
        assertTrue(allGateways.size() >= 2);
        
        boolean hasStripe = allGateways.stream().anyMatch(g -> g instanceof StripePaymentGatewayCorrect);
        boolean hasAdyen = allGateways.stream().anyMatch(g -> g instanceof AdyenPaymentGatewayCorrect);
        
        assertTrue(hasStripe);
        assertTrue(hasAdyen);
    }
}
