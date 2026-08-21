package com.finflow.troubleshooting.module10;

import com.finflow.troubleshooting.module10.entity.LedgerAccountEntity;
import com.finflow.troubleshooting.module10.repository.LedgerAccountRepository;
import com.finflow.troubleshooting.module10.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Module10Application.class)
public class DeterministicLockOrderingTest {

    @Autowired
    private LedgerAccountRepository accountRepository;

    @Autowired
    private TransferService transferService;

    private Long account1Id;
    private Long account2Id;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAllInBatch();
        LedgerAccountEntity acc1 = accountRepository.save(new LedgerAccountEntity("ACC-001", "Alice", new BigDecimal("1000.00")));
        LedgerAccountEntity acc2 = accountRepository.save(new LedgerAccountEntity("ACC-002", "Bob", new BigDecimal("1000.00")));
        this.account1Id = acc1.getId();
        this.account2Id = acc2.getId();
    }

    @Test
    void testConcurrentBidirectionalTransfersSucceedWithoutDeadlockUsingDeterministicOrdering() throws InterruptedException {
        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // Thread A & C transfer from 1 -> 2 ($100 each)
        // Thread B & D transfer from 2 -> 1 ($50 each)
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    if (index % 2 == 0) {
                        transferService.transferDeterministic(account1Id, account2Id, new BigDecimal("100.00"));
                    } else {
                        transferService.transferDeterministic(account2Id, account1Id, new BigDecimal("50.00"));
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Log failure
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(4);

        LedgerAccountEntity acc1 = accountRepository.findById(account1Id).orElseThrow();
        LedgerAccountEntity acc2 = accountRepository.findById(account2Id).orElseThrow();

        // Net change: acc1 lost 2*100 and gained 2*50 = -100 -> $900.00
        // Net change: acc2 gained 2*100 and lost 2*50 = +100 -> $1100.00
        assertThat(acc1.getBalance()).isEqualByComparingTo("900.00");
        assertThat(acc2.getBalance()).isEqualByComparingTo("1100.00");
    }
}
