package com.finflow.troubleshooting.module27;

import com.finflow.troubleshooting.module27.model.OrderEntity;
import com.finflow.troubleshooting.module27.model.SagaInstance;
import com.finflow.troubleshooting.module27.repository.OrderRepository;
import com.finflow.troubleshooting.module27.saga.PaymentSagaOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PaymentSagaOrchestratorTest {

    @Autowired
    private PaymentSagaOrchestrator sagaOrchestrator;

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        sagaOrchestrator.resetWallets();
    }

    @Test
    @DisplayName("Successful Saga: All 4 steps complete, Order is CONFIRMED, Wallet debited")
    void testSuccessfulSagaExecution() {
        String accountId = "ACC-US-5500";
        double initialBalance = sagaOrchestrator.getWalletBalance(accountId); // $10,000.00
        double amount = 2500.00;

        var result = sagaOrchestrator.executePaymentSaga(accountId, amount, false);

        assertThat(result.finalStatus()).isEqualTo(SagaInstance.SagaStatus.COMPLETED);
        assertThat(result.failureReason()).isNull();

        // Verify Order confirmed in database
        OrderEntity order = orderRepository.findById(result.orderId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo("CONFIRMED");

        // Verify Wallet balance reduced
        assertThat(sagaOrchestrator.getWalletBalance(accountId)).isEqualTo(initialBalance - amount);
    }

    @Test
    @DisplayName("Failed Step 3: MUST execute reverse compensation (Refund Wallet, Cancel Order)")
    void testFailedSagaReverseCompensation() {
        String accountId = "ACC-US-5501";
        double initialBalance = sagaOrchestrator.getWalletBalance(accountId); // $10,000.00
        double amount = 3000.00;

        // Simulate FX provider 503 failure at Step 3
        var result = sagaOrchestrator.executePaymentSaga(accountId, amount, true);

        assertThat(result.finalStatus()).isEqualTo(SagaInstance.SagaStatus.COMPENSATED);
        assertThat(result.failureReason()).contains("FX Liquidity Provider 503");

        // Verify Order was cancelled by compensating transaction
        OrderEntity order = orderRepository.findById(result.orderId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo("CANCELLED");

        // Crucial Validation: Customer Wallet MUST be fully refunded back to initial balance!
        assertThat(sagaOrchestrator.getWalletBalance(accountId)).isEqualTo(initialBalance);
    }
}
