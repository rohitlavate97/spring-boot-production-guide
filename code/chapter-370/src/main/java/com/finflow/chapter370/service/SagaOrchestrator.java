package com.finflow.chapter370.service;

import com.finflow.chapter370.entity.PaymentOrder;
import com.finflow.chapter370.entity.SagaInstance;
import com.finflow.chapter370.repository.SagaInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Saga Orchestrator coordinating distributed multi-service checkout workflows
 * with automatic compensating transactions on failure.
 */
@Service
public class SagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SagaOrchestrator.class);

    private final SagaInstanceRepository sagaInstanceRepository;
    private final PaymentProcessingService paymentProcessingService;

    public SagaOrchestrator(SagaInstanceRepository sagaInstanceRepository,
                            PaymentProcessingService paymentProcessingService) {
        this.sagaInstanceRepository = sagaInstanceRepository;
        this.paymentProcessingService = paymentProcessingService;
    }

    public SagaInstance executeCheckoutSaga(String orderId, String merchantId, BigDecimal amount,
                                            String currency, boolean simulateLedgerFailure) {
        log.info("[SagaOrchestrator] Starting Checkout Saga for Order '{}' | Amount: {} {}",
                orderId, amount, currency);

        // 1. Initialize Saga Instance
        SagaInstance saga = new SagaInstance(orderId);
        sagaInstanceRepository.save(saga);

        try {
            // STEP 1: Authorize Payment
            saga.setCurrentStep("AUTHORIZE_PAYMENT");
            PaymentOrder paymentOrder = paymentProcessingService.authorizePayment(orderId, merchantId, amount, currency);
            saga.setStatus("PAYMENT_AUTHORIZED");
            sagaInstanceRepository.save(saga);
            log.info("[SagaOrchestrator] Step 1 SUCCESS: Payment authorized for order '{}'", orderId);

            // STEP 2: Post to Distributed Ledger Service
            saga.setCurrentStep("POST_LEDGER");
            if (simulateLedgerFailure) {
                throw new RuntimeException("Ledger Service Error: Account currency mismatch / ledger integrity validation failed!");
            }
            log.info("[SagaOrchestrator] Step 2 SUCCESS: Double-entry journal created for order '{}'", orderId);
            saga.setStatus("LEDGER_COMMITTED");

            // STEP 3: Complete Saga
            saga.setCurrentStep("COMPLETE_ORDER");
            saga.setStatus("COMPLETED");
            sagaInstanceRepository.save(saga);
            log.info("[SagaOrchestrator] Saga COMPLETED successfully for order '{}'", orderId);

            return saga;

        } catch (Exception e) {
            log.error("[SagaOrchestrator] Step failed in Saga '{}' for Order '{}': {}. Initiating ROLLBACK COMPENSATION...",
                    saga.getSagaId(), orderId, e.getMessage());

            // STEP COMPENSATION: Compensate Payment
            saga.setCurrentStep("COMPENSATING_PAYMENT");
            saga.setStatus("COMPENSATING");
            sagaInstanceRepository.save(saga);

            try {
                paymentProcessingService.compensatePayment(orderId);
                saga.setStatus("COMPENSATED_FAILED");
                saga.setCurrentStep("COMPENSATION_COMPLETE");
                log.warn("[SagaOrchestrator] Compensation COMPLETED for order '{}'. Payment reversed via outbox.", orderId);
            } catch (Exception compEx) {
                log.error("[SagaOrchestrator] FATAL: Compensation failed for order '{}'!", orderId, compEx);
                saga.setStatus("COMPENSATION_FAILED_CRITICAL");
            }

            sagaInstanceRepository.save(saga);
            return saga;
        }
    }

    public SagaInstance getSaga(String sagaId) {
        return sagaInstanceRepository.findById(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("Saga instance not found: " + sagaId));
    }
}
