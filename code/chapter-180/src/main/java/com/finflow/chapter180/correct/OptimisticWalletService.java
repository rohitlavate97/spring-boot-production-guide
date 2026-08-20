package com.finflow.chapter180.correct;

import com.finflow.chapter180.domain.MerchantWallet;
import com.finflow.chapter180.repository.MerchantWalletRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class OptimisticWalletService {

    private final MerchantWalletRepository walletRepository;

    public OptimisticWalletService(MerchantWalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    /**
     * Single optimistic debit attempt.
     * Throws ObjectOptimisticLockingFailureException if another transaction committed first.
     */
    @Transactional
    public MerchantWallet debitOptimistic(String merchantId, BigDecimal amount) {
        MerchantWallet wallet = walletRepository.findByMerchantId(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + merchantId));

        wallet.debit(amount);
        return walletRepository.save(wallet);
    }

    /**
     * Application-level retry mechanism with exponential backoff and jitter.
     */
    public MerchantWallet debitWithRetry(String merchantId, BigDecimal amount, int maxAttempts) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                return debitOptimistic(merchantId, amount);
            } catch (ObjectOptimisticLockingFailureException ex) {
                if (attempt >= maxAttempts) {
                    throw new IllegalStateException("Exhausted " + maxAttempts + " optimistic lock retries for: " + merchantId, ex);
                }
                try {
                    // Backoff with jitter
                    long backoff = (long) (Math.pow(2, attempt) * 20 + Math.random() * 20);
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
            }
        }
    }
}
