package com.finflow.chapter170.unit;

import com.finflow.chapter170.Chapter170Application;
import com.finflow.chapter170.correct.PaymentProcessingServiceCorrect;
import com.finflow.chapter170.domain.AuditRecord;
import com.finflow.chapter170.incorrect.PaymentProcessingServiceIncorrect;
import com.finflow.chapter170.repository.AuditRecordRepository;
import com.finflow.chapter170.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = Chapter170Application.class)
public class RequiresNewAuditTest {

    @Autowired
    private PaymentProcessingServiceIncorrect incorrectService;

    @Autowired
    private PaymentProcessingServiceCorrect correctService;

    @Autowired
    private PaymentTransactionRepository transactionRepository;

    @Autowired
    private AuditRecordRepository auditRecordRepository;

    @BeforeEach
    public void setup() {
        transactionRepository.deleteAll();
        auditRecordRepository.deleteAll();
    }

    @Test
    public void testSelfInvocation_bypassesRequiresNew_rollsBackAudit() {
        assertThatThrownBy(() -> incorrectService.processWithSelfInvocation(
                "PAY-SELF-INVOKE",
                "CUST-004",
                BigDecimal.valueOf(100.00)
        )).isInstanceOf(RuntimeException.class);

        // FAILURE DEMONSTRATION: Because of self-invocation, the audit record rolled back with the outer transaction
        List<AuditRecord> audits = auditRecordRepository.findAllByReferenceId("PAY-SELF-INVOKE");
        assertThat(audits).isEmpty();
    }

    @Test
    public void testSeparateBeanRequiresNew_commitsAuditEvenWhenParentRollsBack() {
        assertThatThrownBy(() -> correctService.processPaymentWithAuditOnFailure(
                "PAY-SEPARATE-BEAN",
                "CUST-005",
                BigDecimal.valueOf(250.00)
        )).isInstanceOf(RuntimeException.class);

        // SUCCESS DEMONSTRATION: The payment transaction was rolled back
        assertThat(transactionRepository.findByPaymentRef("PAY-SEPARATE-BEAN")).isEmpty();

        // But the audit record was committed via the independent REQUIRES_NEW transaction!
        List<AuditRecord> audits = auditRecordRepository.findAllByReferenceId("PAY-SEPARATE-BEAN");
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getStatus()).isEqualTo("INITIATED");
    }
}
