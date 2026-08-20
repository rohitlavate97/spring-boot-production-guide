package com.finflow.chapter170.unit;

import com.finflow.chapter170.Chapter170Application;
import com.finflow.chapter170.correct.PaymentProcessingServiceCorrect;
import com.finflow.chapter170.exception.PaymentProcessingException;
import com.finflow.chapter170.incorrect.PaymentProcessingServiceIncorrect;
import com.finflow.chapter170.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = Chapter170Application.class)
public class CheckedExceptionRollbackTest {

    @Autowired
    private PaymentProcessingServiceIncorrect incorrectService;

    @Autowired
    private PaymentProcessingServiceCorrect correctService;

    @Autowired
    private PaymentTransactionRepository transactionRepository;

    @BeforeEach
    public void setup() {
        transactionRepository.deleteAll();
    }

    @Test
    public void testIncorrectService_doesNotRollbackOnCheckedException() {
        // Amount <= 0 throws checked PaymentProcessingException
        assertThatThrownBy(() -> incorrectService.processPaymentWithCheckedException(
                "PAY-CHECKED-INCORRECT",
                "CUST-002",
                BigDecimal.ZERO
        )).isInstanceOf(PaymentProcessingException.class);

        // FAILURE DEMONSTRATION: The row was committed to DB because default @Transactional ignores checked exceptions!
        assertThat(transactionRepository.findByPaymentRef("PAY-CHECKED-INCORRECT")).isPresent();
    }

    @Test
    public void testCorrectService_rollsBackOnCheckedException() {
        // Amount <= 0 throws checked PaymentProcessingException with rollbackFor = Exception.class
        assertThatThrownBy(() -> correctService.processPaymentWithCheckedExceptionSafe(
                "PAY-CHECKED-CORRECT",
                "CUST-003",
                BigDecimal.ZERO
        )).isInstanceOf(PaymentProcessingException.class);

        // SUCCESS DEMONSTRATION: The row was cleanly rolled back!
        assertThat(transactionRepository.findByPaymentRef("PAY-CHECKED-CORRECT")).isEmpty();
    }
}
