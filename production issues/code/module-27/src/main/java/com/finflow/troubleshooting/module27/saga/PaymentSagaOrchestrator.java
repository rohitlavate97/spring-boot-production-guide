package com.finflow.troubleshooting.module27.saga;

import com.finflow.troubleshooting.module27.model.OrderEntity;
import com.finflow.troubleshooting.module27.model.SagaInstance;
import com.finflow.troubleshooting.module27.repository.OrderRepository;
import com.finflow.troubleshooting.module27.repository.SagaInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PaymentSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PaymentSagaOrchestrator.class);

    public record SagaExecutionResult(
            String sagaId,
            String orderId,
            String accountId,
            double amount,
            SagaInstance.SagaStatus finalStatus,
            List<String> executionLog,
            String failureReason
    ) {}

    private final SagaInstanceRepository sagaRepository;
    private final OrderRepository orderRepository;

    // Simulated in-memory wallet balances for testing
    private final Map<String, Double> walletBalances = new ConcurrentHashMap<>();

    public PaymentSagaOrchestrator(SagaInstanceRepository sagaRepository, OrderRepository orderRepository) {
        this.sagaRepository = sagaRepository;
        this.orderRepository = orderRepository;
        resetWallets();
    }

    public void resetWallets() {
        walletBalances.put("ACC-US-5500", 10000.00);
        walletBalances.put("ACC-US-5501", 10000.00);
    }

    /**
     * Executes the Distributed Cross-Border Payment Saga with Reverse Compensation:
     * Step 1: Create Order (OrderService)
     * Step 2: Debit Customer Wallet (WalletService)
     * Step 3: Reserve Foreign Exchange Liquidity (FxService)
     * Step 4: Confirm Order (OrderService)
     */
    @Transactional
    public SagaExecutionResult executePaymentSaga(String accountId, double amount, boolean simulateFxFailure) {
        String sagaId = "SAGA-" + UUID.randomUUID().toString().substring(0, 8);
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);
        List<String> executionLog = new ArrayList<>();

        SagaInstance saga = new SagaInstance(sagaId, "PAYMENT_SETTLEMENT_SAGA", SagaInstance.SagaStatus.STARTED,
                "{\"orderId\":\"" + orderId + "\",\"accountId\":\"" + accountId + "\",\"amount\":" + amount + "}");
        sagaRepository.save(saga);

        boolean step1OrderCreated = false;
        boolean step2WalletDebited = false;
        boolean step3FxReserved = false;

        try {
            // STEP 1: Create Order
            saga.setCurrentStep("STEP_1_CREATE_ORDER");
            OrderEntity order = new OrderEntity(orderId, accountId, BigDecimal.valueOf(amount), "PENDING");
            orderRepository.save(order);
            step1OrderCreated = true;
            executionLog.add("STEP 1 [OrderService]: Created Order " + orderId + " (Status: PENDING)");
            log.info("[SAGA {}] {}", sagaId, executionLog.get(executionLog.size() - 1));

            // STEP 2: Debit Wallet
            saga.setCurrentStep("STEP_2_DEBIT_WALLET");
            Double currentBal = walletBalances.getOrDefault(accountId, 0.0);
            if (currentBal < amount) {
                throw new IllegalStateException("Insufficient wallet balance: $" + currentBal + " < $" + amount);
            }
            walletBalances.put(accountId, currentBal - amount);
            step2WalletDebited = true;
            executionLog.add("STEP 2 [WalletService]: Debited $" + amount + " from " + accountId + ". New balance: $" + walletBalances.get(accountId));
            log.info("[SAGA {}] {}", sagaId, executionLog.get(executionLog.size() - 1));

            // STEP 3: Reserve Foreign Exchange
            saga.setCurrentStep("STEP_3_RESERVE_FX");
            if (simulateFxFailure) {
                throw new IllegalStateException("FX Liquidity Provider 503 Service Unavailable (Simulated Downstream Failure)");
            }
            step3FxReserved = true;
            executionLog.add("STEP 3 [FxService]: Reserved $" + amount + " USD to EUR conversion rate 0.9215");
            log.info("[SAGA {}] {}", sagaId, executionLog.get(executionLog.size() - 1));

            // STEP 4: Confirm Order
            saga.setCurrentStep("STEP_4_CONFIRM_ORDER");
            order.setStatus("CONFIRMED");
            orderRepository.save(order);
            executionLog.add("STEP 4 [OrderService]: Confirmed Order " + orderId + " (Status: CONFIRMED)");
            log.info("[SAGA {}] {}", sagaId, executionLog.get(executionLog.size() - 1));

            // SAGA COMPLETED SUCCESSFULLY
            saga.setStatus(SagaInstance.SagaStatus.COMPLETED);
            saga.setUpdatedAt(Instant.now());
            sagaRepository.save(saga);

            return new SagaExecutionResult(sagaId, orderId, accountId, amount, SagaInstance.SagaStatus.COMPLETED, executionLog, null);

        } catch (Exception ex) {
            log.error("[SAGA {} FAILED AT {}] Error: {}. Initiating reverse compensation...",
                    sagaId, saga.getCurrentStep(), ex.getMessage());

            saga.setStatus(SagaInstance.SagaStatus.COMPENSATING);
            saga.setFailureReason(ex.getMessage());
            executionLog.add("FAILURE TRIGGERED: " + ex.getMessage());

            // -------------------------------------------------------------
            // AUTOMATED REVERSE COMPENSATING TRANSACTIONS:
            // -------------------------------------------------------------
            if (step2WalletDebited) {
                // Compensate Step 2: Refund Wallet
                Double balAfterDebit = walletBalances.getOrDefault(accountId, 0.0);
                walletBalances.put(accountId, balAfterDebit + amount);
                executionLog.add("COMPENSATION 1 [WalletService]: Refunded $" + amount + " to " + accountId + ". Restored balance: $" + walletBalances.get(accountId));
                log.warn("[SAGA {} COMPENSATION] {}", sagaId, executionLog.get(executionLog.size() - 1));
            }

            if (step1OrderCreated) {
                // Compensate Step 1: Cancel Order
                OrderEntity order = orderRepository.findById(orderId).orElse(null);
                if (order != null) {
                    order.setStatus("CANCELLED");
                    orderRepository.save(order);
                    executionLog.add("COMPENSATION 2 [OrderService]: Cancelled Order " + orderId + " (Status: CANCELLED)");
                    log.warn("[SAGA {} COMPENSATION] {}", sagaId, executionLog.get(executionLog.size() - 1));
                }
            }

            saga.setStatus(SagaInstance.SagaStatus.COMPENSATED);
            saga.setUpdatedAt(Instant.now());
            sagaRepository.save(saga);

            return new SagaExecutionResult(sagaId, orderId, accountId, amount, SagaInstance.SagaStatus.COMPENSATED, executionLog, ex.getMessage());
        }
    }

    public double getWalletBalance(String accountId) {
        return walletBalances.getOrDefault(accountId, 0.0);
    }
}
