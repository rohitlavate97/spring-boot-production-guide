package com.finflow.chapter170.correct;

import com.finflow.chapter170.domain.PaymentTransaction;
import com.finflow.chapter170.domain.TransactionStatus;
import com.finflow.chapter170.exception.PaymentProcessingException;
import com.finflow.chapter170.repository.PaymentTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class PaymentProcessingServiceCorrect {

    private final PaymentTransactionRepository transactionRepository;
    private final AuditLogServiceCorrect auditLogService;
    private final FraudCheckServiceCorrect fraudCheckService;

    public PaymentProcessingServiceCorrect(PaymentTransactionRepository transactionRepository,
                                           AuditLogServiceCorrect auditLogService,
                                           FraudCheckServiceCorrect fraudCheckService) {
        this.transactionRepository = transactionRepository;
        this.auditLogService = auditLogService;
        this.fraudCheckService = fraudCheckService;
    }

    /**
     * Correct Flow 1: Safe business rule validation without triggering rollback-only state.
     * Records an independent audit log via REQUIRES_NEW before completing transaction.
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public PaymentTransaction processPaymentSafely(String paymentRef, String customerId, BigDecimal amount) {
        PaymentTransaction tx = new PaymentTransaction(
                UUID.randomUUID(),
                paymentRef,
                customerId,
                amount,
                "USD",
                TransactionStatus.INITIATED,
                Instant.now()
        );
        transactionRepository.save(tx);

        // Evaluate risk without throwing exception across transaction boundary
        FraudCheckServiceCorrect.FraudEvaluation eval = fraudCheckService.evaluateRisk(customerId, amount);

        if (eval.isFraudulent()) {
            tx.setStatus(TransactionStatus.FAILED);
            // Record audit in separate REQUIRES_NEW transaction
            auditLogService.recordAuditLog("PAYMENT_AUTH", paymentRef, "FAILED", eval.reason());
            return tx;
        }

        tx.setStatus(TransactionStatus.SUCCESS);
        auditLogService.recordAuditLog("PAYMENT_AUTH", paymentRef, "SUCCESS", "Payment processed successfully");
        return tx;
    }

    /**
     * Correct Flow 2: Rollback on Checked Exceptions with rollbackFor = Exception.class.
     * Guaranteed to roll back database modifications when PaymentProcessingException is thrown.
     */
    @Transactional(rollbackFor = Exception.class)
    public void processPaymentWithCheckedExceptionSafe(String paymentRef, String customerId, BigDecimal amount)
            throws PaymentProcessingException {
        PaymentTransaction tx = new PaymentTransaction(
                UUID.randomUUID(),
                paymentRef,
                customerId,
                amount,
                "USD",
                TransactionStatus.INITIATED,
                Instant.now()
        );
        transactionRepository.save(tx);

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            // Throws checked exception -> Rolled back because rollbackFor = Exception.class is specified
            throw new PaymentProcessingException("Invalid amount: " + amount);
        }

        tx.setStatus(TransactionStatus.SUCCESS);
    }

    /**
     * Correct Flow 3: Audit log persistence on transaction failure via REQUIRES_NEW bean call.
     * The audit record is committed even though the parent transaction throws RuntimeException and rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void processPaymentWithAuditOnFailure(String paymentRef, String customerId, BigDecimal amount) {
        // Record initiated audit via REQUIRES_NEW (separate transaction commits immediately)
        auditLogService.recordAuditLog("PAYMENT_AUTH", paymentRef, "INITIATED", "Payment initiated");

        PaymentTransaction tx = new PaymentTransaction(
                UUID.randomUUID(),
                paymentRef,
                customerId,
                amount,
                "USD",
                TransactionStatus.INITIATED,
                Instant.now()
        );
        transactionRepository.save(tx);

        // Parent transaction fails
        throw new RuntimeException("Simulated payment gateway network timeout");
    }

    /**
     * Correct Flow 4: Safe Event Publishing after DB Commit using TransactionSynchronization.
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public PaymentTransaction processPaymentWithAfterCommitEvent(String paymentRef, String customerId,
                                                                BigDecimal amount, AtomicBoolean eventPublished) {
        PaymentTransaction tx = new PaymentTransaction(
                UUID.randomUUID(),
                paymentRef,
                customerId,
                amount,
                "USD",
                TransactionStatus.SUCCESS,
                Instant.now()
        );
        transactionRepository.save(tx);

        // Register synchronization hook executed only after physical DB COMMIT
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // Safe to publish Kafka message / trigger downstream side effects
                eventPublished.set(true);
            }
        });

        return tx;
    }
}
