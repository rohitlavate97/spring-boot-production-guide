package com.finflow.chapter050.correct;

import com.finflow.chapter050.domain.PaymentGateway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ObjectProviderExample {

    // CORRECT: Using ObjectProvider for lazy and optional resolution
    private final ObjectProvider<PaymentGateway> gatewayProvider;

    public ObjectProviderExample(ObjectProvider<PaymentGateway> gatewayProvider) {
        this.gatewayProvider = gatewayProvider;
    }

    /**
     * Resolves the primary or unique bean if available, otherwise handles missing bean.
     */
    public PaymentGateway getPrimaryGatewaySafely() {
        return gatewayProvider.getIfAvailable();
    }

    /**
     * Iterates over all implementations, similar to List<PaymentGateway> injection,
     * but resolves beans lazily, which can help with initialization order issues.
     */
    public List<String> getAllAvailableGatewayNames() {
        return gatewayProvider.stream()
                .filter(PaymentGateway::isAvailable)
                .map(PaymentGateway::gatewayName)
                .collect(Collectors.toList());
    }
}
