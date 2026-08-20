package com.finflow.chapter170.unit;

import com.finflow.chapter170.Chapter170Application;
import com.finflow.chapter170.incorrect.PaymentProcessingServiceIncorrect;
import com.finflow.chapter170.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.UnexpectedRollbackException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = Chapter170Application.class)
public class UnexpectedRollbackExceptionTest {

    @Autowired
    private PaymentProcessingServiceIncorrect incorrectService;

    @Autowired
    private PaymentTransactionRepository transactionRepository;

    @BeforeEach
    public void setup() {
        transactionRepository.deleteAll();
    }

    @Test
    public void testRollbackCatchTrap_throwsUnexpectedRollbackException() {
        // Amount 15,000 triggers FraudDetectedException in inner REQUIRED method.
        // Outer method catches it and attempts to commit -> Spring throws UnexpectedRollbackException!
        assertThatThrownBy(() -> incorrectService.processPaymentWithRollbackCatchTrap(
                "PAY-TRAP-1",
                "CUST-001",
                BigDecimal.valueOf(15000.00)
        ))
        .isInstanceOf(UnexpectedRollbackException.class)
        .hasMessageContaining("Transaction silently rolled back because it has been marked as rollback-only");
    }
}
