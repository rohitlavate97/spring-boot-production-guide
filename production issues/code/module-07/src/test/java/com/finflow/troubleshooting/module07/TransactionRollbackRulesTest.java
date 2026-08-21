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
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(classes = Module07Application.class)
public class TransactionRollbackRulesTest {

    @Autowired
    private BankingTransactionService transactionService;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
        accountRepository.save(new AccountEntity("ACC-FROM", new BigDecimal("1000.00")));
        accountRepository.save(new AccountEntity("ACC-TO", new BigDecimal("500.00")));
    }

    @Test
    void testCheckedExceptionWithoutRollbackForCommitsChanges() {
        // Calling method that throws checked Exception without rollbackFor=Exception.class
        assertThrows(Exception.class, () ->
                transactionService.transferWithCheckedExceptionBug("ACC-FROM", "ACC-TO", new BigDecimal("200.00")));

        // PROOF OF SPRING DEFAULT: Checked exceptions DO NOT trigger rollback by default!
        // The balance was debited to 800.00!
        AccountEntity from = accountRepository.findByAccountId("ACC-FROM").orElseThrow();
        assertThat(from.getBalance()).isEqualByComparingTo(new BigDecimal("800.00"));
    }

    @Test
    void testProperRollbackRestoresBalances() {
        // Calling method configured with proper rollback
        assertThrows(IllegalStateException.class, () ->
                transactionService.transferWithProperRollback("ACC-FROM", "ACC-TO", new BigDecimal("200.00")));

        // PROOF: Both accounts were rolled back to original balances!
        AccountEntity from = accountRepository.findByAccountId("ACC-FROM").orElseThrow();
        assertThat(from.getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));

        AccountEntity to = accountRepository.findByAccountId("ACC-TO").orElseThrow();
        assertThat(to.getBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
    }
}
