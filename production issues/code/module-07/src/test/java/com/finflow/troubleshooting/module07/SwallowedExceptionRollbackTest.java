package com.finflow.troubleshooting.module07;

import com.finflow.troubleshooting.module07.entity.AccountEntity;
import com.finflow.troubleshooting.module07.repository.AccountRepository;
import com.finflow.troubleshooting.module07.service.BankingTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Module07Application.class)
public class SwallowedExceptionRollbackTest {

    @Autowired
    private BankingTransactionService transactionService;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
        accountRepository.save(new AccountEntity("ACC-SWALLOW-1", new BigDecimal("1000.00")));
    }

    @Test
    void testSwallowedExceptionCausesDirtyCommit() {
        // Method catches and swallows RuntimeException internally
        transactionService.transferWithSwallowedExceptionBug("ACC-SWALLOW-1", "ACC-SWALLOW-2", new BigDecimal("300.00"));

        // PROOF: Because the exception was swallowed, Spring's TransactionInterceptor saw normal method return
        // and committed the dirty balance (1000 - 300 = 700)!
        AccountEntity from = accountRepository.findByAccountId("ACC-SWALLOW-1").orElseThrow();
        assertThat(from.getBalance()).isEqualByComparingTo(new BigDecimal("700.00"));
    }
}
