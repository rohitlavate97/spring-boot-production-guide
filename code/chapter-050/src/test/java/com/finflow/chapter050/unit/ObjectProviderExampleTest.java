package com.finflow.chapter050.unit;

import com.finflow.chapter050.correct.ObjectProviderExample;
import com.finflow.chapter050.domain.PaymentGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ObjectProviderExampleTest {

    @Autowired
    private ObjectProviderExample objectProviderExample;

    @Test
    void canGetPrimaryGatewaySafely() {
        PaymentGateway gateway = objectProviderExample.getPrimaryGatewaySafely();
        assertNotNull(gateway);
        assertEquals("STRIPE", gateway.gatewayName());
    }

    @Test
    void canIterateAllGatewaysSafely() {
        List<String> gatewayNames = objectProviderExample.getAllAvailableGatewayNames();
        assertTrue(gatewayNames.contains("STRIPE"));
        assertTrue(gatewayNames.contains("ADYEN"));
    }

    private void assertEquals(String expected, String actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
