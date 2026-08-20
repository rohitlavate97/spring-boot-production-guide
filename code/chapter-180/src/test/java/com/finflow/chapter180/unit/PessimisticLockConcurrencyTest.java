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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Chapter180Application.class)
public class PessimisticLockConcurrencyTest {

    @Autowired
    private MerchantWalletRepository walletRepository;

    @Autowired
    private PessimisticWalletService pessimisticWalletService;

    private final String merchantId = "MERCHANT_PESSIMISTIC_001";

    @BeforeEach
    public void setup() {
        walletRepository.deleteAll();

        walletRepository.saveAndFlush(new MerchantWallet(
                UUID.randomUUID(),
                merchantId,
                BigDecimal.valueOf(1000.00),
                BigDecimal.ZERO,
                "USD"
        ));
    }

    @Test
    public void testPessimisticLocking_handlesConcurrentDebitsWithoutLostUpdates() throws InterruptedException {
        int threadCount = 10;
        BigDecimal debitPerThread = BigDecimal.valueOf(50.00); // 10 * 50 = $500 total deduction

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    pessimisticWalletService.debitPessimistic(merchantId, debitPerThread);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // Exact final balance: $1000 - $500 = $500.00
        MerchantWallet finalWallet = walletRepository.findByMerchantId(merchantId).orElseThrow();
        assertThat(finalWallet.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(500.00));
    }
}
