package com.finflow.chapter050.correct;

import com.finflow.chapter050.domain.PaymentGateway;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaymentGatewaySelector {

    // CORRECT: Collection injection allows you to easily inject all implementations
    private final List<PaymentGateway> gateways;

    public PaymentGatewaySelector(List<PaymentGateway> gateways) {
        this.gateways = gateways;
    }

    public PaymentGateway selectAvailableGateway() {
        return gateways.stream()
                .filter(PaymentGateway::isAvailable)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No gateways available"));
    }

    public PaymentGateway getGatewayByName(String name) {
        return gateways.stream()
                .filter(g -> g.gatewayName().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown gateway: " + name));
    }
}
