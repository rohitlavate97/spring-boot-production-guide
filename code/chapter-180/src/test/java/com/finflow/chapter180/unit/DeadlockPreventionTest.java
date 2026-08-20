package com.finflow.chapter180.unit;

import com.finflow.chapter180.Chapter180Application;
import com.finflow.chapter180.correct.PessimisticWalletService;
import com.finflow.chapter180.domain.MerchantWallet;
import com.finflow.chapter180.repository.MerchantWalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Chapter180Application.class)
public class DeadlockPreventionTest {

    @Autowired
    private MerchantWalletRepository walletRepository;

    @Autowired
    private PessimisticWalletService pessimisticWalletService;

    private final String walletA = "MERCHANT_A";
    private final String walletB = "MERCHANT_B";

    @BeforeEach
    public void setup() {
        walletRepository.deleteAll();

        walletRepository.saveAndFlush(new MerchantWallet(
                UUID.randomUUID(),
                walletA,
                BigDecimal.valueOf(1000.00),
                BigDecimal.ZERO,
                "USD"
        ));

        walletRepository.saveAndFlush(new MerchantWallet(
                UUID.randomUUID(),
                walletB,
                BigDecimal.valueOf(1000.00),
                BigDecimal.ZERO,
                "USD"
        ));
    }

    @Test
    public void testDeadlockFreeTransfer_withConcurrentBidirectionalTransfers() throws InterruptedException {
        int rounds = 20;
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(rounds * 2);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < rounds; i++) {
            // Thread A: Transfer $10 from A to B
            executor.submit(() -> {
                try {
                    startLatch.await();
                    pessimisticWalletService.transferDeadlockFree(walletA, walletB, BigDecimal.valueOf(10.00));
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });

            // Thread B: Transfer $10 from B to A
            executor.submit(() -> {
                try {
                    startLatch.await();
                    pessimisticWalletService.transferDeadlockFree(walletB, walletA, BigDecimal.valueOf(10.00));
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // With canonical ordering, all 40 transfer transactions succeed with 0 deadlocks!
        assertThat(failureCount.get()).isEqualTo(0);

        // Total funds across both wallets remain constant ($2000.00)
        MerchantWallet finalA = walletRepository.findByMerchantId(walletA).orElseThrow();
        MerchantWallet finalB = walletRepository.findByMerchantId(walletB).orElseThrow();
        BigDecimal total = finalA.getAvailableBalance().add(finalB.getAvailableBalance());
        assertThat(total).isEqualByComparingTo(BigDecimal.valueOf(2000.00));
    }
}
