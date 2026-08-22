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

import static org.assertj.core.api.Assertions.assertThat;

class DeadlockFreeTransferTest {

    private WalletBalanceService walletService;

    @BeforeEach
    void setUp() {
        DistributedLockService lockService = new DistributedLockService();
        walletService = new WalletBalanceService(lockService);
        walletService.resetAccounts();
    }

    @Test
    @DisplayName("Concurrent bidirectional transfers (A->B and B->A) MUST NOT deadlock when using sorted lock ordering")
    void testDeadlockFreeConcurrentTransfers() throws Exception {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads * 2);
        List<Callable<Boolean>> tasks = new ArrayList<>();

        // 10 threads transfer ACC-101 -> ACC-102
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> walletService.transferWithLockOrdering("ACC-101", "ACC-102", 10.0, 1000));
        }

        // 10 threads transfer ACC-102 -> ACC-101 simultaneously (AB-BA concurrency)
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> walletService.transferWithLockOrdering("ACC-102", "ACC-101", 10.0, 1000));
        }

        // Must complete without deadlocking!
        List<Future<Boolean>> futures = executor.invokeAll(tasks);
        for (Future<Boolean> f : futures) {
            f.get();
        }
        executor.shutdown();

        // Total money across ACC-101 ($500) and ACC-102 ($500) must remain strictly preserved ($1,000)
        double totalBalance = walletService.getAtomicBalance("ACC-101") + walletService.getAtomicBalance("ACC-102");
        assertThat(totalBalance).isEqualTo(1000.00);
    }
}
