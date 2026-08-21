package com.finflow.chapter360.unit;

import com.finflow.chapter360.config.PaymentGatewayProperties;
import com.finflow.chapter360.model.DiscoveredInstance;
import com.finflow.chapter360.model.FeeCalculationResult;
import com.finflow.chapter360.service.DynamicPaymentRateService;
import com.finflow.chapter360.service.ServiceDiscoveryManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class DynamicPaymentRateServiceUnitTest {

    private PaymentGatewayProperties properties;
    private DynamicPaymentRateService rateService;
    private ServiceDiscoveryManager discoveryManager;

    @BeforeEach
    void setUp() {
        properties = new PaymentGatewayProperties();
        properties.setTransactionFeePercent(BigDecimal.valueOf(2.5));
        properties.setFixedFeeCents(30);
        properties.setEnvironmentTier("PRODUCTION");

        rateService = new DynamicPaymentRateService(properties);
        discoveryManager = new ServiceDiscoveryManager();
    }

    @Test
    void testCalculateTransactionFeeNominalRate() {
        FeeCalculationResult result = rateService.calculateTransactionFee("TX-100", BigDecimal.valueOf(100.00));

        // 2.5% of $100 = $2.50; fixed fee = $0.30; total = $2.80; net = $97.20
        assertThat(result.getTransactionId()).isEqualTo("TX-100");
        assertThat(result.getPercentageFee()).isEqualByComparingTo("2.50");
        assertThat(result.getFixedFee()).isEqualByComparingTo("0.30");
        assertThat(result.getTotalFee()).isEqualByComparingTo("2.80");
        assertThat(result.getNetPayoutAmount()).isEqualByComparingTo("97.20");
        assertThat(result.getEnvironmentTier()).isEqualTo("PRODUCTION");
    }

    @Test
    void testDynamicPropertyUpdateAppliesImmediatelyWithoutRestart() {
        // Step 1: Initial calculation at 2.5% + 30¢
        FeeCalculationResult initialResult = rateService.calculateTransactionFee("TX-200", BigDecimal.valueOf(200.00));
        assertThat(initialResult.getTotalFee()).isEqualByComparingTo("5.30"); // 5.00 + 0.30

        // Step 2: Dynamically update properties (simulating @RefreshScope reload)
        properties.setTransactionFeePercent(BigDecimal.valueOf(3.0));
        properties.setFixedFeeCents(50);

        // Step 3: Recalculate - new rate applies immediately
        FeeCalculationResult updatedResult = rateService.calculateTransactionFee("TX-200", BigDecimal.valueOf(200.00));
        assertThat(updatedResult.getTotalFee()).isEqualByComparingTo("6.50"); // 6.00 + 0.50
        assertThat(updatedResult.getNetPayoutAmount()).isEqualByComparingTo("193.50");
    }

    @Test
    void testServiceDiscoveryRoundRobinBalancing() {
        Optional<DiscoveredInstance> instance1 = discoveryManager.chooseInstance("order-service");
        Optional<DiscoveredInstance> instance2 = discoveryManager.chooseInstance("order-service");
        Optional<DiscoveredInstance> instance3 = discoveryManager.chooseInstance("order-service");

        assertThat(instance1).isPresent();
        assertThat(instance2).isPresent();
        assertThat(instance3).isPresent();

        // Round robin should cycle across pods
        assertThat(instance1.get().getInstanceId()).isNotEqualTo(instance2.get().getInstanceId());
        assertThat(instance1.get().getInstanceId()).isEqualTo(instance3.get().getInstanceId());
    }
}
