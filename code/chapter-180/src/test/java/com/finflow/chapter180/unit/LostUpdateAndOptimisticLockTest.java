package com.finflow.chapter180.unit;

import com.finflow.chapter180.Chapter180Application;
import com.finflow.chapter180.correct.OptimisticWalletService;
import com.finflow.chapter180.domain.MerchantWallet;
import com.finflow.chapter180.domain.UnversionedWallet;
import com.finflow.chapter180.repository.MerchantWalletRepository;
import com.finflow.chapter180.repository.UnversionedWalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = Chapter180Application.class)
public class LostUpdateAndOptimisticLockTest {

    @Autowired
    private UnversionedWalletRepository unversionedWalletRepository;

    @Autowired
    private MerchantWalletRepository merchantWalletRepository;

    @Autowired
    private OptimisticWalletService optimisticWalletService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final String unversionedMerchantId = "MERCHANT_UNVERSIONED_001";
    private final String versionedMerchantId = "MERCHANT_VERSIONED_001";

    @BeforeEach
    public void setup() {
        unversionedWalletRepository.deleteAll();
        merchantWalletRepository.deleteAll();

        unversionedWalletRepository.saveAndFlush(new UnversionedWallet(
                UUID.randomUUID(),
                unversionedMerchantId,
                BigDecimal.valueOf(1000.00)
        ));

        merchantWalletRepository.saveAndFlush(new MerchantWallet(
                UUID.randomUUID(),
                versionedMerchantId,
                BigDecimal.valueOf(1000.00),
                BigDecimal.ZERO,
                "USD"
        ));
    }

    @Test
    public void testOptimisticLocking_detectsConcurrentModification() throws InterruptedException {
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        AtomicInteger conflictCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Thread 1: loads wallet, pauses, then tries to commit
        executor.submit(() -> {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    MerchantWallet w1 = merchantWalletRepository.findByMerchantId(versionedMerchantId).orElseThrow();
                    latch1.countDown(); // Signal thread 2 to proceed
                    try {
                        latch2.await(); // Wait for thread 2 to commit first
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    w1.debit(BigDecimal.valueOf(200.00));
                    merchantWalletRepository.save(w1);
                });
            } catch (ObjectOptimisticLockingFailureException ex) {
                conflictCount.incrementAndGet();
            }
        });

        // Thread 2: loads wallet, debits $300, and commits immediately
        executor.submit(() -> {
            try {
                latch1.await(); // Wait for thread 1 to read
                transactionTemplate.executeWithoutResult(status -> {
                    MerchantWallet w2 = merchantWalletRepository.findByMerchantId(versionedMerchantId).orElseThrow();
                    w2.debit(BigDecimal.valueOf(300.00));
                    merchantWalletRepository.save(w2);
                });
                latch2.countDown(); // Signal thread 1 to attempt commit
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        executor.shutdown();
        executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

        // Thread 1 should have failed with OptimisticLockingFailureException
        assertThat(conflictCount.get()).isEqualTo(1);

        // Wallet balance reflects Thread 2's commit ($1000 - $300 = $700)
        MerchantWallet finalWallet = merchantWalletRepository.findByMerchantId(versionedMerchantId).orElseThrow();
        assertThat(finalWallet.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(700.00));
    }

    @Test
    public void testDebitWithRetry_successfullyRetriesOnConflict() {
        MerchantWallet updated = optimisticWalletService.debitWithRetry(versionedMerchantId, BigDecimal.valueOf(150.00), 3);
        assertThat(updated.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(850.00));
    }
}
