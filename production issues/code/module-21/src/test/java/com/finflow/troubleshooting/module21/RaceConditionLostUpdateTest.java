package com.finflow.troubleshooting.module21;

import com.finflow.troubleshooting.module21.service.DistributedLockService;
import com.finflow.troubleshooting.module21.service.WalletBalanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RaceConditionLostUpdateTest {

    private WalletBalanceService walletService;

    @BeforeEach
    void setUp() {
        DistributedLockService lockService = new DistributedLockService();
        walletService = new WalletBalanceService(lockService);
        walletService.resetAccounts();
    }

    @Test
    @DisplayName("Atomic CAS Debit MUST allow exactly 5 debits from $500 balance when 10 concurrent $100 requests arrive")
    void testAtomicCasPreventsLostUpdatesAndDoubleSpending() throws Exception {
        String accountId = "ACC-101"; // Initial Balance: $500.00
        int concurrentThreads = 10;
        double debitAmount = 100.00;

        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < concurrentThreads; i++) {
            tasks.add(() -> {
                boolean success = walletService.atomicCasDebit(accountId, debitAmount);
                if (success) successCount.incrementAndGet();
                return success;
            });
        }

        List<Future<Boolean>> futures = executor.invokeAll(tasks);
        for (Future<Boolean> f : futures) {
            f.get();
        }
        executor.shutdown();

        // Exact invariants:
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(walletService.getAtomicBalance(accountId)).isEqualTo(0.00);
    }

    @Test
    @DisplayName("Distributed lock debit MUST guarantee mutual exclusion under concurrency")
    void testDistributedLockMutualExclusion() throws Exception {
        String accountId = "ACC-102"; // Initial Balance: $500.00
        int concurrentThreads = 10;
        double debitAmount = 100.00;

        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < concurrentThreads; i++) {
            tasks.add(() -> {
                boolean success = walletService.distributedLockDebit(accountId, debitAmount, 2000);
                if (success) successCount.incrementAndGet();
                return success;
            });
        }

        List<Future<Boolean>> futures = executor.invokeAll(tasks);
        for (Future<Boolean> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertThat(successCount.get()).isLessThanOrEqualTo(5);
        assertThat(walletService.getAtomicBalance(accountId)).isGreaterThanOrEqualTo(0.00);
    }
}
