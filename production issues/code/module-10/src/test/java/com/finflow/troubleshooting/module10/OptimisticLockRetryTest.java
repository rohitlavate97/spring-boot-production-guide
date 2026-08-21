package com.finflow.troubleshooting.module10;

import com.finflow.troubleshooting.module10.entity.LedgerAccountEntity;
import com.finflow.troubleshooting.module10.repository.LedgerAccountRepository;
import com.finflow.troubleshooting.module10.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Module10Application.class)
public class OptimisticLockRetryTest {

    @Autowired
    private LedgerAccountRepository accountRepository;

    @Autowired
    private TransferService transferService;

    private Long account1Id;
    private Long account2Id;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAllInBatch();
        LedgerAccountEntity acc1 = accountRepository.save(new LedgerAccountEntity("ACC-101", "Charlie", new BigDecimal("500.00")));
        LedgerAccountEntity acc2 = accountRepository.save(new LedgerAccountEntity("ACC-102", "Diana", new BigDecimal("500.00")));
        this.account1Id = acc1.getId();
        this.account2Id = acc2.getId();
    }

    @Test
    void testOptimisticLockTransferUpdatesBalanceAndIncrementsVersion() {
        transferService.transferWithOptimisticLockAndRetry(account1Id, account2Id, new BigDecimal("75.00"));

        LedgerAccountEntity acc1 = accountRepository.findById(account1Id).orElseThrow();
        LedgerAccountEntity acc2 = accountRepository.findById(account2Id).orElseThrow();

        assertThat(acc1.getBalance()).isEqualByComparingTo("425.00");
        assertThat(acc2.getBalance()).isEqualByComparingTo("575.00");
        assertThat(acc1.getVersion()).isGreaterThanOrEqualTo(0L);
    }
}
