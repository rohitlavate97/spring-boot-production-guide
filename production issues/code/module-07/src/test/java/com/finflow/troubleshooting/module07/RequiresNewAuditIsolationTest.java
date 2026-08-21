package com.finflow.troubleshooting.module07;

import com.finflow.troubleshooting.module07.entity.AccountEntity;
import com.finflow.troubleshooting.module07.repository.AccountRepository;
import com.finflow.troubleshooting.module07.repository.AuditLogRepository;
import com.finflow.troubleshooting.module07.service.BankingTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(classes = Module07Application.class)
public class RequiresNewAuditIsolationTest {

    @Autowired
    private BankingTransactionService transactionService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        accountRepository.deleteAll();
        accountRepository.save(new AccountEntity("ACC-REQ-FROM", new BigDecimal("1000.00")));
        accountRepository.save(new AccountEntity("ACC-REQ-TO", new BigDecimal("500.00")));
    }

    @Test
    void testRequiresNewAuditLogsPersistEvenWhenOuterTransactionRollsBack() {
        // Outer transaction will fail and rollback
        assertThrows(IllegalStateException.class, () ->
                transactionService.transferWithRequiresNewAudit("ACC-REQ-FROM", "ACC-REQ-TO", new BigDecimal("250.00"), true));

        // PROOF 1: Outer transaction accounts rolled back
        AccountEntity from = accountRepository.findByAccountId("ACC-REQ-FROM").orElseThrow();
        assertThat(from.getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));

        // PROOF 2: Inner REQUIRES_NEW transaction committed the audit log entry
        assertThat(auditLogRepository.count()).isEqualTo(1);
    }
}
