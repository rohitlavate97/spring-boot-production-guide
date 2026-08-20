package com.finflow.chapter170.unit;

import com.finflow.chapter170.Chapter170Application;
import com.finflow.chapter170.correct.PaymentProcessingServiceCorrect;
import com.finflow.chapter170.domain.PaymentTransaction;
import com.finflow.chapter170.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Chapter170Application.class)
public class TransactionSynchronizationAfterCommitTest {

    @Autowired
    private PaymentProcessingServiceCorrect correctService;

    @Autowired
    private PaymentTransactionRepository transactionRepository;

    @BeforeEach
    public void setup() {
        transactionRepository.deleteAll();
    }

    @Test
    public void testAfterCommitHook_firesOnlyAfterDatabaseCommitSucceeds() {
        AtomicBoolean eventPublished = new AtomicBoolean(false);

        PaymentTransaction tx = correctService.processPaymentWithAfterCommitEvent(
                "PAY-SYNC-001",
                "CUST-007",
                BigDecimal.valueOf(500.00),
                eventPublished
        );

        assertThat(tx).isNotNull();
        assertThat(transactionRepository.findByPaymentRef("PAY-SYNC-001")).isPresent();

        // Verify the afterCommit hook was executed
        assertThat(eventPublished.get()).isTrue();
    }
}
