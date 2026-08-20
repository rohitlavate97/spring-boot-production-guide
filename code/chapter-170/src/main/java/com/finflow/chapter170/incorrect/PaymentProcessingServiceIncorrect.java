package com.finflow.chapter170.incorrect;

import com.finflow.chapter170.domain.AuditRecord;
import com.finflow.chapter170.domain.PaymentTransaction;
import com.finflow.chapter170.domain.TransactionStatus;
import com.finflow.chapter170.exception.FraudDetectedException;
import com.finflow.chapter170.exception.PaymentProcessingException;
import com.finflow.chapter170.repository.AuditRecordRepository;
import com.finflow.chapter170.repository.PaymentTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * INCORRECT IMPLEMENTATION:
 * 1. Catches RuntimeException from inner REQUIRED transaction -> triggers UnexpectedRollbackException.
 * 2. Throws checked PaymentProcessingException without rollbackFor=Exception.class -> DOES NOT ROLL BACK!
 * 3. Calls self-invoked REQUIRES_NEW audit method -> Proxy bypassed, audit rolls back with outer failure.
 */
@Service
public class PaymentProcessingServiceIncorrect {

    private final PaymentTransactionRepository transactionRepository;
    private final AuditRecordRepository auditRecordRepository;
    private final FraudCheckServiceIncorrect fraudCheckService;

    public PaymentProcessingServiceIncorrect(PaymentTransactionRepository transactionRepository,
                                            AuditRecordRepository auditRecordRepository,
                                            FraudCheckServiceIncorrect fraudCheckService) {
        this.transactionRepository = transactionRepository;
        this.auditRecordRepository = auditRecordRepository;
        this.fraudCheckService = fraudCheckService;
    }

    /**
     * Anti-Pattern 1: Rollback-Only Catch Trap.
     * Catches FraudDetectedException from inner method, but the physical transaction was already
     * marked rollback-only by Spring's TransactionInterceptor.
     * At method completion, Spring attempts to commit and throws UnexpectedRollbackException.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public PaymentTransaction processPaymentWithRollbackCatchTrap(String paymentRef, String customerId, BigDecimal amount) {
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

        try {
            // Joins existing transaction and throws RuntimeException
            fraudCheckService.validateFraudRules(customerId, amount);
            tx.setStatus(TransactionStatus.SUCCESS);
        } catch (FraudDetectedException ex) {
            // Naive attempt to swallow exception and update status to FAILED
            tx.setStatus(TransactionStatus.FAILED);
            // CRASH: When this method returns, Spring discovers rollbackOnly = true and throws UnexpectedRollbackException!
        }

        return tx;
    }

    /**
     * Anti-Pattern 2: Checked Exception Rollback Default.
     * Spring default @Transactional only rolls back on RuntimeException and Error.
     * When PaymentProcessingException (checked) is thrown, Spring COMMITS the partial transaction!
     */
    @Transactional
    public void processPaymentWithCheckedException(String paymentRef, String customerId, BigDecimal amount)
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
            // Throws checked exception -> Spring DOES NOT roll back by default!
            throw new PaymentProcessingException("Invalid amount: " + amount);
        }

        tx.setStatus(TransactionStatus.SUCCESS);
    }

    /**
     * Anti-Pattern 3: Self-Invocation bypassing Proxy.
     * Calling recordAuditRequiresNewInternal() directly from this class bypasses the CGLIB proxy.
     * The REQUIRES_NEW annotation is ignored, running in the caller's transaction.
     */
    @Transactional
    public void processWithSelfInvocation(String paymentRef, String customerId, BigDecimal amount) {
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

        // Self-invocation: Proxy is BYPASSED! Runs in existing transaction!
        this.recordAuditRequiresNewInternal(paymentRef, "INITIATED");

        // Force runtime failure in main transaction
        throw new RuntimeException("Simulated payment gateway failure");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAuditRequiresNewInternal(String referenceId, String status) {
        AuditRecord audit = new AuditRecord(
                UUID.randomUUID(),
                "PAYMENT_AUTH",
                referenceId,
                status,
                "Audit recorded internally",
                Instant.now()
        );
        auditRecordRepository.save(audit);
    }
}
